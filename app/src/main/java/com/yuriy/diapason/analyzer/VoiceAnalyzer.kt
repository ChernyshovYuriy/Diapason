package com.yuriy.diapason.analyzer

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.yuriy.diapason.logging.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

private const val SAMPLE_RATE = 44100
private const val YIN_THRESHOLD = 0.15
// internal (not private): FachClassifier.classify() needs this to recognize when a
// Fach's own rangeMinHz sits below what the mic can ever register — see the floor
// scoring special case there and KNOWN_ISSUES.md's Contrabass Oktavist entry.
internal const val MIN_PITCH_HZ = 60f
private const val MAX_PITCH_HZ = 2200f
private const val MIN_YIN_CONFIDENCE = 0.80f
// read() error codes this many times in a row = the mic is gone (a call took it, the
// device's audio server restarted). Then the session ends instead of spinning forever.
private const val MAX_CONSECUTIVE_READ_ERRORS = 10
private const val READ_RETRY_DELAY_MS = 20L
// internal: RecordingGate shows progress toward it and holds an early Stop below it.
internal const val MIN_ACCEPTED_SAMPLES = 40

@SuppressLint("MissingPermission")
private fun defaultRecorder(source: Int, bufferSize: Int): AudioRecord? = runCatching {
    AudioRecord(source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
}.onFailure { AppLogger.e("AudioRecord($source) threw", it) }.getOrNull()

/**
 * Localised strings required by [VoiceAnalyzer].
 * Resolved by the caller (e.g. AnalyzeViewModel) so this class stays context-free.
 */
data class VoiceAnalyzerStrings(
    val listeningMessage: String,
    val micInitError: String,
    val tooFewSamples: String
)

/**
 * @param audioSource the `MediaRecorder.AudioSource` to record from. Defaults to `MIC`;
 *   the audio-source experiment passes `VOICE_RECOGNITION` for half of installs, because
 *   OEM noise suppression on `MIC` is suspected of eating sustained sung notes on some
 *   brands (completion ranged 47–78% by brand in production).
 */
class VoiceAnalyzer(
    private val scope: CoroutineScope,
    private val audioSource: Int = MediaRecorder.AudioSource.MIC,
    /** Opens a recorder for (source, bufferSize); internal so tests can make it fail. */
    internal val createRecorder: (source: Int, bufferSize: Int) -> AudioRecord? = ::defaultRecorder,
) {

    var onPitchDetected: ((hz: Float, noteName: String) -> Unit)? = null
    var onStatusUpdate: ((message: String) -> Unit)? = null

    /**
     * The microphone stopped delivering audio mid-session (persistent read() errors). The
     * session has already ended — [isRunning] is false — so the caller should leave its
     * recording state. Invoked on the IO thread.
     */
    var onRecordingError: (() -> Unit)? = null

    private var audioRecord: AudioRecord? = null
    private var analyzerJob: Job? = null

    // CopyOnWriteArrayList is used instead of mutableListOf because pitchSamples is
    // written on Dispatchers.IO and read on the calling thread immediately after
    // cancel(). cancel() sends a signal but does not join — the coroutine may still
    // be in pitchSamples.add() when stop() reads the list. COWAL is write-safe
    // without locking reads, which is exactly this access pattern.
    private val pitchSamples = CopyOnWriteArrayList<Float>()
    private var sessionStartMs = 0L
    private var lastLoggedNote = ""

    val isRunning: Boolean get() = analyzerJob?.isActive == true

    /**
     * True when the last [start] couldn't initialise [audioSource] and fell back to `MIC`,
     * so the caller can report it — a fallback install isn't really in the experiment arm.
     */
    var usedFallbackSource: Boolean = false
        private set

    /**
     * Seconds since the current session started; 0 when nothing is running. Read it
     * before [stop] — the insufficient and abandoned paths get no [VoiceProfile] to
     * carry a duration, so callers log this instead.
     */
    val elapsedSeconds: Float
        get() = if (isRunning) (System.currentTimeMillis() - sessionStartMs) / 1000f else 0f

    /**
     * Starts a session. Returns false — after posting [VoiceAnalyzerStrings.micInitError]
     * — if the microphone couldn't be opened or started, so the caller can leave its
     * "recording" state instead of showing a Stop button that has nothing to stop.
     * Returns true if a session is now running (including one that already was).
     */
    @SuppressLint("MissingPermission")
    fun start(strings: VoiceAnalyzerStrings): Boolean {
        if (isRunning) return true
        pitchSamples.clear()
        lastLoggedNote = ""
        sessionStartMs = System.currentTimeMillis()

        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = minBuffer * 4

        usedFallbackSource = false
        audioRecord = createRecorder(audioSource, bufferSize)
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED &&
            audioSource != MediaRecorder.AudioSource.MIC
        ) {
            AppLogger.w("AudioRecord source $audioSource failed to initialize — falling back to MIC")
            audioRecord?.release()
            audioRecord = createRecorder(MediaRecorder.AudioSource.MIC, bufferSize)
            usedFallbackSource = true
        }

        val record = audioRecord
        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
            AppLogger.e("AudioRecord failed to initialize")
            record?.release()   // release the failed instance to avoid a resource leak
            audioRecord = null
            onStatusUpdate?.invoke(strings.micInitError)
            return false
        }

        // Throws IllegalStateException on some devices when the mic is held elsewhere.
        val started = runCatching { record.startRecording() }
            .onFailure { AppLogger.e("AudioRecord.startRecording() failed", it) }
            .isSuccess
        if (!started) {
            record.release()
            audioRecord = null
            onStatusUpdate?.invoke(strings.micInitError)
            return false
        }
        AppLogger.i("Session started — SR=$SAMPLE_RATE Hz, buffer=$bufferSize bytes")
        onStatusUpdate?.invoke(strings.listeningMessage)

        analyzerJob = scope.launch(Dispatchers.IO) {
            val audioBuffer = ShortArray(bufferSize / 2)
            val floatBuffer = FloatArray(audioBuffer.size)
            var frameCount = 0
            var consecutiveErrors = 0
            var micLost = false

            // This coroutine owns the recorder's teardown (finally below): release happens
            // on the same thread as read(), never concurrently with one. stop() only calls
            // AudioRecord.stop(), which is the documented way to unblock a pending read().
            try {
                while (isActive) {
                    val read = record.read(audioBuffer, 0, audioBuffer.size)
                    if (read < 0) {
                        // ERROR_INVALID_OPERATION / ERROR_DEAD_OBJECT / ERROR: retry briefly,
                        // then give up rather than spin at full CPU with no audio.
                        if (++consecutiveErrors >= MAX_CONSECUTIVE_READ_ERRORS) {
                            AppLogger.e("AudioRecord.read() kept failing ($read) — ending session")
                            micLost = isActive
                            break
                        }
                        delay(READ_RETRY_DELAY_MS)
                        continue
                    }
                    if (read == 0) {
                        delay(READ_RETRY_DELAY_MS)
                        continue
                    }
                    consecutiveErrors = 0
                    frameCount++

                    for (i in 0 until read) floatBuffer[i] = audioBuffer[i] / 32768f

                    val (pitchHz, confidence) = YinPitchDetector.detect(
                        floatBuffer.copyOf(read), SAMPLE_RATE.toFloat(), YIN_THRESHOLD
                    )

                    if (frameCount % 10 == 0) {
                        AppLogger.d(
                            "Frame $frameCount: pitch=${if (pitchHz > 0) "%.1fHz".format(pitchHz) else "—"} conf=${
                                "%.3f".format(
                                    confidence
                                )
                            }"
                        )
                    }

                    if (pitchHz in MIN_PITCH_HZ..MAX_PITCH_HZ && confidence >= MIN_YIN_CONFIDENCE) {
                        val noteName = FachClassifier.hzToNoteName(pitchHz)
                        pitchSamples.add(pitchHz)

                        if (noteName != lastLoggedNote) {
                            val elapsed = (System.currentTimeMillis() - sessionStartMs) / 1000f
                            AppLogger.i(
                                "[%5.1fs] %-4s  %.1f Hz  conf=%.3f  n=%d".format(
                                    elapsed, noteName, pitchHz, confidence, pitchSamples.size
                                )
                            )
                            lastLoggedNote = noteName
                        }
                        onPitchDetected?.invoke(pitchHz, noteName)
                    }
                }
            } finally {
                runCatching { record.stop() }
                record.release()
            }
            if (micLost) onRecordingError?.invoke()
        }
        return true
    }

    fun stop(tooFewSamplesMessage: String): VoiceProfile? {
        if (!isRunning) return null
        analyzerJob?.cancel()
        // Unblocks a read() in progress; the reader coroutine releases the recorder.
        runCatching { audioRecord?.stop() }
        audioRecord = null

        val duration = (System.currentTimeMillis() - sessionStartMs) / 1000f
        AppLogger.i("Session stopped — %.1fs, %d valid samples".format(duration, pitchSamples.size))

        // 40 frames (~7s of confident singing at ~160ms/frame) rather than 20 (~3.2s):
        // the comfortable-range estimate rests on P20/P80 of this list, and 20 frames
        // gives only 4 data points at each tail — too fragile for a reliable estimate.
        // See KNOWN_ISSUES.md #3.
        if (pitchSamples.size < MIN_ACCEPTED_SAMPLES) {
            AppLogger.w("Insufficient samples (${pitchSamples.size} < $MIN_ACCEPTED_SAMPLES) — need more singing")
            onStatusUpdate?.invoke(tooFewSamplesMessage)
            return null
        }

        // Take a snapshot so subList() calls in the classifiers operate on a plain List,
        // not on COWSubList which throws ConcurrentModificationException if the IO
        // coroutine adds one more sample before fully stopping.
        val snapshot = pitchSamples.toList()

        val (detectedMin, detectedMax) = FachClassifier.estimateDetectedExtremes(snapshot)
        val (comfortableLow, comfortableHigh) = FachClassifier.estimateComfortableRange(snapshot)
        val passaggio = FachClassifier.estimatePassaggio(snapshot)

        logHistogram(snapshot)

        AppLogger.i(
            "Profile: detected=${FachClassifier.hzToNoteName(detectedMin)}–${
                FachClassifier.hzToNoteName(
                    detectedMax
                )
            } " +
                    "comfortable=${FachClassifier.hzToNoteName(comfortableLow)}–${
                        FachClassifier.hzToNoteName(
                            comfortableHigh
                        )
                    } " +
                    "pass=${FachClassifier.hzToNoteName(passaggio)}"
        )

        return VoiceProfile(
            detectedMinHz = detectedMin,
            detectedMaxHz = detectedMax,
            comfortableLowHz = comfortableLow,
            comfortableHighHz = comfortableHigh,
            estimatedPassaggioHz = passaggio,
            sampleCount = snapshot.size,
            durationSeconds = duration
        )
    }

    private fun logHistogram(pitches: List<Float>) {
        data class Band(val label: String, val lo: Float, val hi: Float)

        val bands = listOf(
            Band("C2–B2    (65–123 Hz)", 65f, 123f),
            Band("C3–B3   (130–246 Hz)", 123f, 246f),
            Band("C4–B4   (261–493 Hz)", 246f, 493f),
            Band("C5–B5   (523–987 Hz)", 493f, 987f),
            Band("C6–B6 (1047–1975 Hz)", 987f, 2100f)
        )
        val maxCount =
            bands.maxOf { b -> pitches.count { it in b.lo..b.hi }.toFloat() }.coerceAtLeast(1f)
        AppLogger.i("Pitch histogram:")
        bands.forEach { b ->
            val cnt = pitches.count { it in b.lo..b.hi }
            val pct = (cnt * 100f / pitches.size).toInt()
            val bar = "█".repeat((cnt * 28f / maxCount).toInt())
            AppLogger.i("  ${b.label} | %3d%% $bar ($cnt)".format(pct))
        }
    }
}
