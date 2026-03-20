package com.yuriy.diapason.comparison

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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

private const val TAG = "WarmUpComparisonVM"

/** Warm-up duration shown on the timer screen. */
const val WARM_UP_DURATION_SECONDS = 300 // 5 minutes

// ── Stage model ───────────────────────────────────────────────────────────────

sealed interface ComparisonStage {
    /** Introduction / start screen. */
    object Intro : ComparisonStage

    /** User is recording the baseline (before warm-up) session. */
    data class Baseline(
        val currentNote: String = "—",
        val currentHz: Float = 0f,
        val sampleCount: Int = 0,
        val statusMessage: String = "",
    ) : ComparisonStage

    /** Baseline failed — not enough data. */
    data class BaselineInsufficient(val reason: String) : ComparisonStage

    /** Warm-up timer is running. */
    data class WarmUp(
        val remainingSeconds: Int = WARM_UP_DURATION_SECONDS,
        val isRunning: Boolean = true,
    ) : ComparisonStage

    /** User is recording the retest (after warm-up) session. */
    data class Retest(
        val currentNote: String = "—",
        val currentHz: Float = 0f,
        val sampleCount: Int = 0,
        val statusMessage: String = "",
        /** False until the user explicitly taps Start — prevents the UI showing "recording" prematurely. */
        val isRecording: Boolean = false,
    ) : ComparisonStage

    /** Retest failed — not enough data. */
    data class RetestInsufficient(val reason: String) : ComparisonStage

    /** Both sessions complete — comparison ready to display. */
    data class Done(val result: ComparisonResult) : ComparisonStage
}

// ─────────────────────────────────────────────────────────────────────────────

class WarmUpComparisonViewModel(application: Application) : AndroidViewModel(application) {

    private fun str(resId: Int) = getApplication<Application>().getString(resId)

    private val repository: SessionRepository = (application as MainApp).sessionRepository

    private val _stage = MutableStateFlow<ComparisonStage>(ComparisonStage.Intro)
    val stage: StateFlow<ComparisonStage> = _stage.asStateFlow()

    // ── Stored results from each phase ────────────────────────────────────────

    private var baselineProfile: VoiceProfile? = null
    private var baselineMatches: List<FachMatch> = emptyList()

    // ── Audio analyzer (shared across baseline and retest) ────────────────────

    private val analyzer = VoiceAnalyzer(viewModelScope)

    private var timerJob: Job? = null

    // ── Baseline ──────────────────────────────────────────────────────────────

    fun startBaseline() {
        AppLogger.i("$TAG startBaseline()")
        _stage.value =
            ComparisonStage.Baseline(statusMessage = str(R.string.analyze_status_listening))
        attachAnalyzerCallbacksForBaseline()
        analyzer.start(analyzerStrings())
    }

    fun stopBaseline() {
        AppLogger.i("$TAG stopBaseline()")
        if (!analyzer.isRunning) return

        _stage.update {
            if (it is ComparisonStage.Baseline)
                it.copy(statusMessage = str(R.string.analyze_status_analyzing))
            else it
        }

        val profile = analyzer.stop(str(R.string.analyze_status_too_few_samples))
        if (profile == null) {
            _stage.value = ComparisonStage.BaselineInsufficient(
                str(R.string.analyze_error_insufficient)
            )
            return
        }

        baselineProfile = profile
        baselineMatches = FachClassifier.classify(profile)

        persistSession(profile, baselineMatches)

        // Automatically transition to warm-up stage
        _stage.value = ComparisonStage.WarmUp(
            remainingSeconds = WARM_UP_DURATION_SECONDS,
            isRunning = false // user must explicitly start timer
        )
    }

    fun retryBaseline() {
        _stage.value = ComparisonStage.Intro
    }

    // ── Warm-up timer ─────────────────────────────────────────────────────────

    fun startWarmUpTimer() {
        AppLogger.i("$TAG startWarmUpTimer()")
        val current = _stage.value as? ComparisonStage.WarmUp ?: return
        _stage.value = current.copy(isRunning = true)

        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var remaining = current.remainingSeconds
            while (remaining > 0) {
                delay(1_000)
                remaining--
                _stage.update {
                    if (it is ComparisonStage.WarmUp) it.copy(remainingSeconds = remaining)
                    else it
                }
            }
            // Timer finished — advance to retest, waiting for user to tap Start
            _stage.value = ComparisonStage.Retest(isRecording = false)
        }
    }

    fun skipWarmUpTimer() {
        AppLogger.i("$TAG skipWarmUpTimer()")
        timerJob?.cancel()
        _stage.value = ComparisonStage.Retest(isRecording = false)
    }

    // ── Retest ────────────────────────────────────────────────────────────────

    fun startRetest() {
        AppLogger.i("$TAG startRetest()")
        _stage.value = ComparisonStage.Retest(
            isRecording = true,
            statusMessage = str(R.string.analyze_status_listening),
        )
        attachAnalyzerCallbacksForRetest()
        analyzer.start(analyzerStrings())
    }

    fun stopRetest() {
        AppLogger.i("$TAG stopRetest()")
        if (!analyzer.isRunning) return

        _stage.update {
            if (it is ComparisonStage.Retest)
                it.copy(statusMessage = str(R.string.analyze_status_analyzing))
            else it
        }

        val profile = analyzer.stop(str(R.string.analyze_status_too_few_samples))
        if (profile == null) {
            _stage.value = ComparisonStage.RetestInsufficient(
                str(R.string.analyze_error_insufficient)
            )
            return
        }

        val retestMatches = FachClassifier.classify(profile)
        persistSession(profile, retestMatches)

        val baseline = baselineProfile
        if (baseline == null) {
            // Shouldn't happen; reset to start
            AppLogger.e("$TAG Baseline profile missing during retest completion — resetting")
            _stage.value = ComparisonStage.Intro
            return
        }

        val result = ComparisonResult.compute(
            before = baseline,
            beforeTopMatch = baselineMatches.firstOrNull(),
            after = profile,
            afterTopMatch = retestMatches.firstOrNull(),
        )
        _stage.value = ComparisonStage.Done(result)
    }

    fun retryRetest() {
        _stage.value = ComparisonStage.Retest(isRecording = false)
    }

    // ── Reset ─────────────────────────────────────────────────────────────────

    fun resetToIntro() {
        timerJob?.cancel()
        if (analyzer.isRunning) analyzer.stop(str(R.string.analyze_status_too_few_samples))
        baselineProfile = null
        baselineMatches = emptyList()
        _stage.value = ComparisonStage.Intro
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun analyzerStrings() = VoiceAnalyzerStrings(
        listeningMessage = str(R.string.analyze_status_listening_short),
        micInitError = str(R.string.analyze_status_mic_error),
        tooFewSamples = str(R.string.analyze_status_too_few_samples),
    )

    private fun attachAnalyzerCallbacksForBaseline() {
        analyzer.onPitchDetected = { hz, note ->
            _stage.update {
                if (it is ComparisonStage.Baseline)
                    it.copy(currentNote = note, currentHz = hz, sampleCount = it.sampleCount + 1)
                else it
            }
        }
        analyzer.onStatusUpdate = { msg ->
            _stage.update {
                if (it is ComparisonStage.Baseline) it.copy(statusMessage = msg) else it
            }
        }
    }

    private fun attachAnalyzerCallbacksForRetest() {
        analyzer.onPitchDetected = { hz, note ->
            _stage.update {
                if (it is ComparisonStage.Retest)
                    it.copy(
                        currentNote = note,
                        currentHz = hz,
                        sampleCount = it.sampleCount + 1,
                        isRecording = true
                    )
                else it
            }
        }
        analyzer.onStatusUpdate = { msg ->
            _stage.update {
                if (it is ComparisonStage.Retest) it.copy(statusMessage = msg) else it
            }
        }
    }

    private fun persistSession(profile: VoiceProfile, matches: List<FachMatch>) {
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
                    // translated string so the DB value is locale-independent.
                    getApplication<Application>().resources.getResourceEntryName(it.fach.nameRes)
                },
                topFachScore = topMatch?.score,
                topFachMaxScore = topMatch?.maxScore,
                isPartial = false,
            )
            runCatching { repository.save(record) }
                .onSuccess { AppLogger.i("$TAG Comparison session saved: ${record.topFachKey}") }
                .onFailure { AppLogger.e("$TAG Failed to save comparison session", it) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        if (analyzer.isRunning) analyzer.stop(str(R.string.analyze_status_too_few_samples))
    }
}
