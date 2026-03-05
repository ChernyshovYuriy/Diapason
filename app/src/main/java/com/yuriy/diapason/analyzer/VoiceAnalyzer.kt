package com.yuriy.diapason.analyzer

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "VoiceAnalyzer"
private const val SAMPLE_RATE = 44100
private const val YIN_THRESHOLD = 0.15
private const val MIN_PITCH_HZ = 60f
private const val MAX_PITCH_HZ = 2200f
private const val MIN_YIN_CONFIDENCE = 0.80f

/**
 * Localised strings required by [VoiceAnalyzer].
 * Resolved by the caller (e.g. [AnalyzeViewModel]) so this class stays context-free.
 */
data class VoiceAnalyzerStrings(
    val listeningMessage: String,
    val micInitError: String,
    val tooFewSamples: String
)

class VoiceAnalyzer(private val scope: CoroutineScope) {

    var onPitchDetected: ((hz: Float, noteName: String) -> Unit)? = null
    var onStatusUpdate: ((message: String) -> Unit)? = null

    private var audioRecord: AudioRecord? = null
    private var analyzerJob: Job? = null
    private val pitchSamples = mutableListOf<Float>()
    private var sessionStartMs = 0L
    private var lastLoggedNote = ""

    val isRunning: Boolean get() = analyzerJob?.isActive == true

    @SuppressLint("MissingPermission")
    fun start(strings: VoiceAnalyzerStrings) {
        if (isRunning) return
        pitchSamples.clear()
        lastLoggedNote = ""
        sessionStartMs = System.currentTimeMillis()

        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = minBuffer * 4

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            onStatusUpdate?.invoke(strings.micInitError)
            return
        }

        audioRecord?.startRecording()
        Log.i(TAG, "Session started — SR=$SAMPLE_RATE Hz, buffer=$bufferSize bytes")
        onStatusUpdate?.invoke(strings.listeningMessage)

        analyzerJob = scope.launch(Dispatchers.IO) {
            val audioBuffer = ShortArray(bufferSize / 2)
            val floatBuffer = FloatArray(audioBuffer.size)
            var frameCount = 0

            while (isActive) {
                val read = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: break
                if (read <= 0) continue
                frameCount++

                for (i in 0 until read) floatBuffer[i] = audioBuffer[i] / 32768f

                val (pitchHz, confidence) = YinPitchDetector.detect(
                    floatBuffer.copyOf(read), SAMPLE_RATE.toFloat(), YIN_THRESHOLD
                )

                if (frameCount % 10 == 0) {
                    Log.v(
                        TAG,
                        "Frame $frameCount: pitch=${if (pitchHz > 0) "%.1fHz".format(pitchHz) else "—"} conf=${"%.3f".format(confidence)}"
                    )
                }

                if (pitchHz in MIN_PITCH_HZ..MAX_PITCH_HZ && confidence >= MIN_YIN_CONFIDENCE) {
                    val noteName = FachClassifier.hzToNoteName(pitchHz)
                    pitchSamples.add(pitchHz)

                    if (noteName != lastLoggedNote) {
                        val elapsed = (System.currentTimeMillis() - sessionStartMs) / 1000f
                        Log.i(
                            TAG, "[%5.1fs] %-4s  %.1f Hz  conf=%.3f  n=%d".format(
                                elapsed, noteName, pitchHz, confidence, pitchSamples.size
                            )
                        )
                        lastLoggedNote = noteName
                    }
                    onPitchDetected?.invoke(pitchHz, noteName)
                }
            }
        }
    }

    fun stop(tooFewSamplesMessage: String): VoiceProfile? {
        if (!isRunning) return null
        analyzerJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        val duration = (System.currentTimeMillis() - sessionStartMs) / 1000f
        Log.i(TAG, "Session stopped — %.1fs, %d valid samples".format(duration, pitchSamples.size))

        if (pitchSamples.size < 20) {
            Log.w(TAG, "Insufficient samples (${pitchSamples.size} < 20) — need more singing")
            onStatusUpdate?.invoke(tooFewSamplesMessage)
            return null
        }

        val minHz = pitchSamples.min()
        val maxHz = pitchSamples.max()
        val (tessLow, tessHigh) = FachClassifier.estimateTessitura(pitchSamples)
        val passaggio = FachClassifier.estimatePassaggio(pitchSamples)

        logHistogram(pitchSamples)

        Log.i(
            TAG,
            "Profile: min=${FachClassifier.hzToNoteName(minHz)} max=${FachClassifier.hzToNoteName(maxHz)} " +
                    "tess=${FachClassifier.hzToNoteName(tessLow)}–${FachClassifier.hzToNoteName(tessHigh)} " +
                    "pass=${FachClassifier.hzToNoteName(passaggio)}"
        )

        return VoiceProfile(
            absoluteMinHz = minHz,
            absoluteMaxHz = maxHz,
            tessituraLowHz = tessLow,
            tessituraHighHz = tessHigh,
            estimatedPassaggioHz = passaggio,
            sampleCount = pitchSamples.size,
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
        Log.i(TAG, "Pitch histogram:")
        bands.forEach { b ->
            val cnt = pitches.count { it in b.lo..b.hi }
            val pct = (cnt * 100f / pitches.size).toInt()
            val bar = "█".repeat((cnt * 28f / maxCount).toInt())
            Log.i(TAG, "  ${b.label} | %3d%% $bar ($cnt)".format(pct))
        }
    }
}
