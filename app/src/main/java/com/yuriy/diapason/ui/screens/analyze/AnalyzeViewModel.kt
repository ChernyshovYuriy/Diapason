package com.yuriy.diapason.ui.screens.analyze

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yuriy.diapason.MainApp
import com.yuriy.diapason.R
import com.yuriy.diapason.analyzer.FachClassifier
import com.yuriy.diapason.analyzer.FachMatch
import com.yuriy.diapason.analyzer.VoiceAnalyzer
import com.yuriy.diapason.analyzer.VoiceAnalyzerStrings
import com.yuriy.diapason.analyzer.VoiceProfile
import com.yuriy.diapason.data.SessionRecord
import com.yuriy.diapason.data.repository.SessionRepository
import com.yuriy.diapason.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

        _uiState.value = AnalyzeUiState.Recording(
            statusMessage = getString(R.string.analyze_status_analyzing),
            sampleCount = (uiState.value as? AnalyzeUiState.Recording)?.sampleCount ?: 0
        )

        val profile = analyzer.stop(
            tooFewSamplesMessage = getString(R.string.analyze_status_too_few_samples)
        )

        if (profile == null) {
            _uiState.value = AnalyzeUiState.InsufficientData(
                getString(R.string.analyze_error_insufficient)
            )
            return
        }

        val matches = FachClassifier.classify(profile)
        val result = AnalyzeUiState.ResultReady(profile = profile, matches = matches)
        _lastResult.value = result // persist across back-navigation
        _uiState.value = result

        // ── Persist session to local database ─────────────────────────────
        viewModelScope.launch(Dispatchers.IO) {
            val topMatch = matches.firstOrNull()
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
                topFachKey = topMatch?.let {
                    // Store the resource entry name ("fach_name_lyric_soprano") rather than the
                    // translated string so the DB value is locale-independent. HistoryScreen
                    // resolves it back to the display language at read time.
                    getApplication<Application>().resources.getResourceEntryName(it.fach.nameRes)
                },
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
            analyzer.stop(getString(R.string.analyze_status_too_few_samples))
        }
    }
}
