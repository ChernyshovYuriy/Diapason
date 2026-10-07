package com.yuriy.diapason.ui.screens.analyze

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yuriy.diapason.MainApp
import com.yuriy.diapason.ReviewHelper
import com.yuriy.diapason.R
import com.yuriy.diapason.analytics.AppAnalytics
import com.yuriy.diapason.analyzer.FachClassifier
import com.yuriy.diapason.analyzer.FachDefinition
import com.yuriy.diapason.analyzer.CombinedVoiceProfile
import com.yuriy.diapason.analyzer.FachMatch
import com.yuriy.diapason.analyzer.RecordingGate
import com.yuriy.diapason.analyzer.VoiceAnalyzer
import com.yuriy.diapason.analyzer.VoiceAnalyzerStrings
import com.yuriy.diapason.analyzer.VoiceGroupChoice
import com.yuriy.diapason.analyzer.VoiceProfile
import com.yuriy.diapason.analyzer.VoiceProfileAggregator
import com.yuriy.diapason.data.SessionRecord
import com.yuriy.diapason.data.repository.SessionRepository
import com.yuriy.diapason.data.recordedWith
import com.yuriy.diapason.data.toTimedProfile
import com.yuriy.diapason.insufficientMessage
import com.yuriy.diapason.keepSingingMessage
import com.yuriy.diapason.localizedString
import com.yuriy.diapason.logging.AppLogger
import com.yuriy.diapason.reminder.ReminderScheduler
import com.yuriy.diapason.settings.AudioSourceExperiment
import com.yuriy.diapason.settings.VoiceGroupPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

private const val TAG = "AnalyzeViewModel"

// ── UI State ─────────────────────────────────────────────────────────────────

sealed interface AnalyzeUiState {
    /** Idle — waiting for the user to press Start */
    object Idle : AnalyzeUiState

    /** Actively recording and detecting pitch */
    data class Recording(
        val currentNote: String = "—",
        val currentHz: Float = 0f,
        val sampleCount: Int = 0,
        val statusMessage: String = "",
        /**
         * Stop was pressed below the sample gate: recording continues with a "keep singing"
         * prompt, and the next Stop finishes anyway. Cleared once the gate is reached.
         */
        val earlyStopPrompted: Boolean = false,
    ) : AnalyzeUiState

    /** Processing finished but resulted in insufficient data */
    data class InsufficientData(val reason: String) : AnalyzeUiState

    /** Full analysis result ready — navigate to ResultsScreen */
    data class ResultReady(
        val profile: VoiceProfile,
        val matches: List<FachMatch>
    ) : AnalyzeUiState
}

// ─────────────────────────────────────────────────────────────────────────────

class AnalyzeViewModel(application: Application) : AndroidViewModel(application) {

    private fun getString(resId: Int): String = getApplication<Application>().localizedString(resId)

    private val repository: SessionRepository = (application as MainApp).sessionRepository
    private val reviewHelper = ReviewHelper(application)
    private val reminderScheduler = ReminderScheduler(application)

    private val _reviewTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val reviewTrigger: SharedFlow<Unit> = _reviewTrigger.asSharedFlow()

    private val _uiState = MutableStateFlow<AnalyzeUiState>(AnalyzeUiState.Idle)
    val uiState: StateFlow<AnalyzeUiState> = _uiState.asStateFlow()
    private val audioSourceArm = AudioSourceExperiment(application).arm
    private val analyzer = VoiceAnalyzer(viewModelScope, audioSourceArm.audioSource)

    /**
     * Holds the last result so ResultsScreen can retrieve it after navigation.
     * Backed by a StateFlow so AnalyzeScreen can reactively show/hide the
     * "View Last Result" button without re-running an analysis.
     */
    private val _lastResult = MutableStateFlow<AnalyzeUiState.ResultReady?>(null)
    val lastResultFlow: StateFlow<AnalyzeUiState.ResultReady?> = _lastResult.asStateFlow()

    /** Convenience accessor for ResultsScreen (non-reactive, always up-to-date). */
    val lastResult: AnalyzeUiState.ResultReady? get() = _lastResult.value

    private val voiceGroupPreferences = VoiceGroupPreferences(application)

    /** The Male · Female · Not sure switch; null until the first-run prompt is answered. */
    private val _voiceChoice = MutableStateFlow(voiceGroupPreferences.choice)
    val voiceChoice: StateFlow<VoiceGroupChoice?> = _voiceChoice.asStateFlow()

    /** Unanswered can't reach a recording (Start asks first); treat it as "Not sure" if it does. */
    private val effectiveChoice: VoiceGroupChoice get() = _voiceChoice.value ?: VoiceGroupChoice.UNSURE

    /**
     * The last [VoiceProfileAggregator.MAX_SESSIONS] sessions combined into one profile,
     * refreshed after every saved analysis; null until at least two recent sessions exist.
     */
    private val _combinedProfile = MutableStateFlow<CombinedVoiceProfile?>(null)
    val combinedProfile: StateFlow<CombinedVoiceProfile?> = _combinedProfile.asStateFlow()

    /** Saved sessions of any voice choice, refreshed with [combinedProfile]. */
    private val _savedSessionCount = MutableStateFlow(0)
    val savedSessionCount: StateFlow<Int> = _savedSessionCount.asStateFlow()

    init {
        analyzer.onPitchDetected = { hz, noteName ->
            _uiState.update { current ->
                if (current is AnalyzeUiState.Recording) {
                    val sampleCount = current.sampleCount + 1
                    val gateJustReached = current.earlyStopPrompted && RecordingGate.isEnough(sampleCount)
                    current.copy(
                        currentNote = noteName,
                        currentHz = hz,
                        sampleCount = sampleCount,
                        earlyStopPrompted = current.earlyStopPrompted && !gateJustReached,
                        statusMessage = if (gateJustReached) getString(R.string.analyze_status_enough)
                        else current.statusMessage,
                    )
                } else current
            }
        }

        analyzer.onRecordingError = {
            // The session already ended inside the analyzer; leave "Recording" with a
            // message instead of a Stop button that has nothing left to stop.
            AppAnalytics.analysisMicError(AppAnalytics.Flow.Single, AppAnalytics.MicErrorPhase.Recording)
            _uiState.update { current ->
                if (current is AnalyzeUiState.Recording) {
                    AnalyzeUiState.InsufficientData(getString(R.string.analyze_error_mic_lost))
                } else current
            }
        }

        analyzer.onStatusUpdate = { message ->
            _uiState.update { current ->
                if (current is AnalyzeUiState.Recording) current.copy(statusMessage = message)
                else current
            }
        }
    }

    fun startRecording() {
        // VoiceAnalyzer.start() already no-ops internally if already running, but
        // without this mirror guard a redundant call (e.g. a double-tap before the
        // UI visually responds) still resets _uiState to a fresh Recording(sampleCount
        // = 0) while the real, still-running analyzer keeps accumulating from the
        // original session — desyncing the visible counter from the real buffer.
        if (analyzer.isRunning) return
        AppLogger.i("$TAG startRecording()")
        _uiState.value = AnalyzeUiState.Recording(
            statusMessage = getString(R.string.analyze_status_listening)
        )
        val started = analyzer.start(
            VoiceAnalyzerStrings(
                listeningMessage = getString(R.string.analyze_status_listening_short),
                micInitError = getString(R.string.analyze_status_mic_error),
                tooFewSamples = getString(R.string.analyze_status_too_few_samples)
            )
        )
        // After start(), so a failed experiment source is reported as the fallback.
        AppAnalytics.setAudioSource(
            if (analyzer.usedFallbackSource) AudioSourceExperiment.FALLBACK_ANALYTICS_VALUE else audioSourceArm.analyticsValue
        )
        if (!started) {
            // Not "Recording": there's nothing to stop. The error state offers Try Again.
            AppAnalytics.analysisMicError(AppAnalytics.Flow.Single, AppAnalytics.MicErrorPhase.Start)
            _uiState.value = AnalyzeUiState.InsufficientData(getString(R.string.analyze_status_mic_error))
            return
        }
        AppAnalytics.analysisStarted(AppAnalytics.Flow.Single)
    }

    fun stopRecording() {
        AppLogger.i("$TAG stopRecording()")
        if (!analyzer.isRunning) return

        val recording = uiState.value as? AnalyzeUiState.Recording
        val priorSampleCount = recording?.sampleCount ?: 0
        val elapsedSeconds = analyzer.elapsedSeconds

        // First Stop below the gate: keep recording and say how much longer, instead of
        // throwing the take away. The next Stop finishes regardless.
        if (recording != null && !recording.earlyStopPrompted && !RecordingGate.isEnough(priorSampleCount)) {
            AppAnalytics.analysisStopTooEarly(AppAnalytics.Flow.Single, priorSampleCount, elapsedSeconds)
            _uiState.value = recording.copy(
                earlyStopPrompted = true,
                statusMessage = getApplication<Application>().keepSingingMessage(priorSampleCount, elapsedSeconds),
            )
            return
        }
        _uiState.value = AnalyzeUiState.Recording(
            statusMessage = getString(R.string.analyze_status_analyzing),
            sampleCount = priorSampleCount
        )

        val profile = analyzer.stop(
            tooFewSamplesMessage = getString(R.string.analyze_status_too_few_samples)
        )

        if (profile == null) {
            AppAnalytics.analysisInsufficient(AppAnalytics.Flow.Single, priorSampleCount, elapsedSeconds)
            _uiState.value = AnalyzeUiState.InsufficientData(
                getApplication<Application>().insufficientMessage(priorSampleCount)
            )
            return
        }

        val voiceChoice = effectiveChoice
        val voiceGroup = voiceChoice.group
        val matches = FachClassifier.classify(profile, voiceGroup)
        val topMatch = matches.firstOrNull()
        val topFachKey = topMatch?.let { fachKeyOf(it.fach) }
        AppAnalytics.analysisCompleted(
            flow = AppAnalytics.Flow.Single,
            profile = profile,
            matches = matches,
            topFachKey = topFachKey,
            voiceGroup = voiceGroup,
        )

        val result = AnalyzeUiState.ResultReady(profile = profile, matches = matches)
        _lastResult.value = result // persist across back-navigation
        _uiState.value = result

        // If the user previously opted into the re-test reminder, push it out so it always
        // sits ~30 days from their most recent session rather than from the original opt-in.
        reminderScheduler.bumpIfOptedIn()

        if (reviewHelper.recordAnalysisAndCheckShouldPrompt()) {
            _reviewTrigger.tryEmit(Unit)
        }

        // ── Persist session to local database ─────────────────────────────
        viewModelScope.launch(Dispatchers.IO) {
            val record = SessionRecord(
                id = UUID.randomUUID().toString(),
                timestampMs = System.currentTimeMillis(),
                durationSeconds = profile.durationSeconds,
                detectedMinHz = profile.detectedMinHz,
                detectedMaxHz = profile.detectedMaxHz,
                comfortableLowHz = profile.comfortableLowHz,
                comfortableHighHz = profile.comfortableHighHz,
                passaggioHz = profile.estimatedPassaggioHz,
                sampleCount = profile.sampleCount,
                // topFachKey is the resource entry name ("fach_name_lyric_soprano") rather than
                // the translated string so the DB value is locale-independent. HistoryScreen
                // resolves it back to the display language at read time.
                topFachKey = topFachKey,
                topFachScore = topMatch?.score,
                topFachMaxScore = topMatch?.maxScore,
                isPartial = false,
                voiceGroupChoice = voiceChoice,
            )
            runCatching { repository.save(record) }
                .onSuccess { AppLogger.i("$TAG Session saved: ${record.topFachKey} (${record.id})") }
                .onFailure { AppLogger.e("$TAG Failed to save session", it) }
            // After the save, so the session just recorded is part of the combination.
            refreshCombinedProfile()
        }
    }

    /**
     * Sets the Male · Female · Not sure switch for the *next* recording; it stays as the
     * default afterwards. The last result is deliberately left as it was recorded: the
     * switch is per recording, so flipping it usually means a different singer is about
     * to sing (a teacher's next student), and re-ranking the previous take would put that
     * student's result in the wrong half of the table — and contradict their History row.
     * The combined profile does switch to sessions recorded with the new choice.
     */
    fun setVoiceChoice(choice: VoiceGroupChoice, source: AppAnalytics.VoiceGroupSource) {
        voiceGroupPreferences.choice = choice
        _voiceChoice.value = choice
        AppAnalytics.voiceGroupSelected(choice.group, source)
        viewModelScope.launch(Dispatchers.IO) { refreshCombinedProfile() }
    }

    private suspend fun refreshCombinedProfile() {
        val choice = effectiveChoice
        runCatching {
            val all = repository.getAll()
            _savedSessionCount.value = all.size
            VoiceProfileAggregator.combine(
                // Same choice only: with a per-recording switch, a session recorded as the
                // other voice group is most likely a different singer on the same phone.
                sessions = all.recordedWith(choice).map { it.toTimedProfile() },
                nowMs = System.currentTimeMillis(),
                group = choice.group,
            )
        }
            .onSuccess { _combinedProfile.value = it }
            .onFailure { AppLogger.e("$TAG Failed to combine recent sessions", it) }
    }

    fun resetToIdle() {
        _uiState.value = AnalyzeUiState.Idle
        // NOTE: _lastResult is intentionally NOT cleared here so the user can
        // still tap "View Last Result" before they start a fresh recording.
    }

    /**
     * The Analyze screen stopped being visible — navigated away from, or the app went to
     * the background — while a recording may still be running. Ends it as abandoned and
     * returns to Idle rather than leaving the mic open off-screen (Android silences
     * background capture anyway, so the session couldn't have continued usefully).
     *
     * This, not [onCleared], is the real abandon path: this ViewModel is activity-scoped,
     * so onCleared only runs when the activity finishes, and a swipe-away kills the
     * process without calling it at all — which is why `analysis_abandoned` never fired
     * in production before this existed.
     */
    fun onScreenStopped() {
        if (abandonIfRecording()) _uiState.value = AnalyzeUiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        // Deliberately discards the profile here even if enough samples had already
        // accumulated to classify — the user never chose to stop and save this
        // session, so nothing is persisted on their behalf. Confirmed with the author,
        // 2026-09-01, rather than left as an open question. Kept as a fallback for the
        // rare case the screen-stopped path didn't run first.
        abandonIfRecording()
    }

    /** Logs `analysis_abandoned` and stops the analyzer; returns false if nothing was running. */
    private fun abandonIfRecording(): Boolean {
        if (!analyzer.isRunning) return false
        val abandonedSampleCount = (uiState.value as? AnalyzeUiState.Recording)?.sampleCount ?: 0
        AppAnalytics.analysisAbandoned(
            AppAnalytics.Flow.Single, abandonedSampleCount, analyzer.elapsedSeconds
        )
        // stop()'s return value is intentionally unused; this is purely to release AudioRecord.
        analyzer.stop(getString(R.string.analyze_status_too_few_samples))
        return true
    }

    private fun fachKeyOf(fach: FachDefinition): String? = runCatching {
        getApplication<Application>().resources.getResourceEntryName(fach.nameRes)
    }.getOrNull()
}
