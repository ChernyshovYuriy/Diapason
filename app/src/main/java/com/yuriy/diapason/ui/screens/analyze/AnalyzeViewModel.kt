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
import com.yuriy.diapason.analyzer.FachMatch
import com.yuriy.diapason.analyzer.VoiceAnalyzer
import com.yuriy.diapason.analyzer.VoiceAnalyzerStrings
import com.yuriy.diapason.analyzer.VoiceProfile
import com.yuriy.diapason.data.SessionRecord
import com.yuriy.diapason.data.repository.SessionRepository
import com.yuriy.diapason.logging.AppLogger
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
        val statusMessage: String = ""
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

    private fun getString(resId: Int): String = getApplication<Application>().getString(resId)

    private val repository: SessionRepository = (application as MainApp).sessionRepository
    private val reviewHelper = ReviewHelper(application)

    private val _reviewTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val reviewTrigger: SharedFlow<Unit> = _reviewTrigger.asSharedFlow()

    private val _uiState = MutableStateFlow<AnalyzeUiState>(AnalyzeUiState.Idle)
    val uiState: StateFlow<AnalyzeUiState> = _uiState.asStateFlow()
    private val analyzer = VoiceAnalyzer(viewModelScope)

    /**
     * Holds the last result so ResultsScreen can retrieve it after navigation.
     * Backed by a StateFlow so AnalyzeScreen can reactively show/hide the
     * "View Last Result" button without re-running an analysis.
     */
    private val _lastResult = MutableStateFlow<AnalyzeUiState.ResultReady?>(null)
    val lastResultFlow: StateFlow<AnalyzeUiState.ResultReady?> = _lastResult.asStateFlow()

    /** Convenience accessor for ResultsScreen (non-reactive, always up-to-date). */
    val lastResult: AnalyzeUiState.ResultReady? get() = _lastResult.value

    init {
        analyzer.onPitchDetected = { hz, noteName ->
            _uiState.update { current ->
                if (current is AnalyzeUiState.Recording) {
                    current.copy(
                        currentNote = noteName,
                        currentHz = hz,
                        sampleCount = current.sampleCount + 1
                    )
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
        AppLogger.i("$TAG startRecording()")
        AppAnalytics.analysisStarted(AppAnalytics.Flow.Single)
        _uiState.value = AnalyzeUiState.Recording(
            statusMessage = getString(R.string.analyze_status_listening)
        )
        analyzer.start(
            VoiceAnalyzerStrings(
                listeningMessage = getString(R.string.analyze_status_listening_short),
                micInitError = getString(R.string.analyze_status_mic_error),
                tooFewSamples = getString(R.string.analyze_status_too_few_samples)
            )
        )
    }

    fun stopRecording() {
        AppLogger.i("$TAG stopRecording()")
        if (!analyzer.isRunning) return

        val priorSampleCount = (uiState.value as? AnalyzeUiState.Recording)?.sampleCount ?: 0
        _uiState.value = AnalyzeUiState.Recording(
            statusMessage = getString(R.string.analyze_status_analyzing),
            sampleCount = priorSampleCount
        )

        val profile = analyzer.stop(
            tooFewSamplesMessage = getString(R.string.analyze_status_too_few_samples)
        )

        if (profile == null) {
            AppAnalytics.analysisInsufficient(AppAnalytics.Flow.Single, priorSampleCount)
            _uiState.value = AnalyzeUiState.InsufficientData(
                getString(R.string.analyze_error_insufficient)
            )
            return
        }

        val matches = FachClassifier.classify(profile)
        val topMatch = matches.firstOrNull()
        val topFachKey = topMatch?.let { fachKeyOf(it.fach) }
        AppAnalytics.analysisCompleted(
            flow = AppAnalytics.Flow.Single,
            durationSeconds = profile.durationSeconds,
            sampleCount = profile.sampleCount,
            topFachKey = topFachKey,
            score = topMatch?.score,
            maxScore = topMatch?.maxScore,
        )

        val result = AnalyzeUiState.ResultReady(profile = profile, matches = matches)
        _lastResult.value = result // persist across back-navigation
        _uiState.value = result

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
            )
            runCatching { repository.save(record) }
                .onSuccess { AppLogger.i("$TAG Session saved: ${record.topFachKey} (${record.id})") }
                .onFailure { AppLogger.e("$TAG Failed to save session", it) }
        }
    }

    fun resetToIdle() {
        _uiState.value = AnalyzeUiState.Idle
        // NOTE: _lastResult is intentionally NOT cleared here so the user can
        // still tap "View Last Result" before they start a fresh recording.
    }

    override fun onCleared() {
        super.onCleared()
        if (analyzer.isRunning) {
            val abandonedSampleCount =
                (uiState.value as? AnalyzeUiState.Recording)?.sampleCount ?: 0
            AppAnalytics.analysisAbandoned(AppAnalytics.Flow.Single, abandonedSampleCount)
            analyzer.stop(getString(R.string.analyze_status_too_few_samples))
        }
    }

    private fun fachKeyOf(fach: FachDefinition): String? = runCatching {
        getApplication<Application>().resources.getResourceEntryName(fach.nameRes)
    }.getOrNull()
}
