package com.yuriy.diapason.ui.screens.analyze

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.yuriy.diapason.analytics.AppAnalytics
import com.yuriy.diapason.analyzer.ALL_FACH
import com.yuriy.diapason.analyzer.FachClassifier
import com.yuriy.diapason.analyzer.RecordingGate
import com.yuriy.diapason.analyzer.VoiceAnalyzer
import com.yuriy.diapason.analyzer.VoiceGroup
import com.yuriy.diapason.analyzer.VoiceGroupChoice
import com.yuriy.diapason.analyzer.VoiceProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [AnalyzeViewModel]'s recording lifecycle.
 *
 * [VoiceAnalyzer] constructs a real `AudioRecord`, so — matching the established
 * pattern in `WarmUpComparisonViewModelTest` — these run under Robolectric rather
 * than pure JVM. Robolectric's `AudioRecord` shadow reports `STATE_INITIALIZED`,
 * so `analyzer.start()` genuinely succeeds and `isRunning` becomes true, letting
 * these tests exercise the real start/stop guards rather than only reasoning
 * about them (verified directly before writing this file: a throwaway experiment
 * confirmed `startRecording()` runs cleanly end-to-end under Robolectric, with no
 * crash from the `AppAnalytics`/Firebase calls inside it either).
 *
 * This is the first test file for [AnalyzeViewModel] — previously untested,
 * flagged as a coverage gap in the 2026-09-01 audit.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnalyzeViewModelTest {

    private lateinit var viewModel: AnalyzeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = AnalyzeViewModel(
            ApplicationProvider.getApplicationContext<Application>()
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Baseline sanity ───────────────────────────────────────────────────────

    @Test
    fun `initial state is Idle`() = runTest {
        assertTrue(viewModel.uiState.value is AnalyzeUiState.Idle)
    }

    @Test
    fun `startRecording from Idle transitions to Recording`() = runTest {
        viewModel.startRecording()
        assertTrue(viewModel.uiState.value is AnalyzeUiState.Recording)
    }

    @Test
    fun `stopRecording without a prior startRecording is a no-op and does not crash`() {
        // Mirrors VoiceAnalyzer.stop()'s own "not running" guard at the ViewModel
        // layer, which was tested directly for VoiceAnalyzer itself but never at
        // this layer — found during a second, separate adversarial audit pass.
        viewModel.stopRecording()
        assertTrue(
            "stopRecording() with nothing running must leave the state at Idle",
            viewModel.uiState.value is AnalyzeUiState.Idle
        )
    }

    // ── BUG-04 regression: a redundant start() must not desync the visible counter ──
    //
    // VoiceAnalyzer.start() already no-ops internally if already running, but
    // without a mirroring guard in the ViewModel, a redundant call (e.g. a
    // double-tap on Start before the UI visually responds) still reset _uiState
    // to a fresh Recording(sampleCount = 0) while the real, still-running
    // analyzer kept accumulating from the original session — desyncing the
    // visible counter from the real pitch-sample buffer. See KNOWN_ISSUES.md.

    @Test
    fun `startRecording while already recording does not reset the visible sample count`() {
        viewModel.startRecording()
        // Simulate samples having already accumulated during the real, still-running
        // session — mirrors what a legitimate recording in progress looks like partway
        // through, without needing to feed real audio through YIN.
        forceUiState(
            AnalyzeUiState.Recording(currentNote = "A4", currentHz = 440f, sampleCount = 12)
        )

        // A redundant start() call must be a complete no-op.
        viewModel.startRecording()

        val state = viewModel.uiState.value
        assertTrue("Expected Recording but got $state", state is AnalyzeUiState.Recording)
        assertEquals(
            "A redundant startRecording() call must not reset the sample count",
            12, (state as AnalyzeUiState.Recording).sampleCount
        )
    }

    @Test
    fun `startRecording while already recording does not change the current note or pitch`() {
        viewModel.startRecording()
        forceUiState(
            AnalyzeUiState.Recording(currentNote = "A4", currentHz = 440f, sampleCount = 12)
        )

        viewModel.startRecording()

        val state = viewModel.uiState.value as AnalyzeUiState.Recording
        assertEquals("A4", state.currentNote)
        assertEquals(440f, state.currentHz, 0.01f)
    }

    // ── onScreenStopped — the real abandon path ──────────────────────────────
    //
    // analysis_abandoned used to fire only from onCleared(), which an activity-scoped
    // ViewModel almost never reaches (a swipe-away kills the process without it), so
    // the event never fired in production. onScreenStopped() is driven by the screen's
    // ON_STOP instead.

    @Test
    fun `onScreenStopped while recording returns to Idle and stops the analyzer`() {
        viewModel.startRecording()
        forceUiState(AnalyzeUiState.Recording(currentNote = "A4", currentHz = 440f, sampleCount = 12))

        viewModel.onScreenStopped()

        assertTrue(viewModel.uiState.value is AnalyzeUiState.Idle)
        // If the analyzer were still running, startRecording()'s isRunning guard would
        // no-op and leave the state at Idle — reaching a fresh Recording proves it stopped.
        viewModel.startRecording()
        val state = viewModel.uiState.value
        assertTrue("Expected Recording but got $state", state is AnalyzeUiState.Recording)
        assertEquals(0, (state as AnalyzeUiState.Recording).sampleCount)
    }

    @Test
    fun `onScreenStopped when not recording leaves the state untouched`() {
        val insufficient = AnalyzeUiState.InsufficientData("too few")
        forceUiState(insufficient)

        viewModel.onScreenStopped()

        assertEquals(insufficient, viewModel.uiState.value)
    }

    // ── setVoiceChoice ────────────────────────────────────────────────────────

    @Test
    fun `setVoiceChoice records the choice and exposes it`() {
        assertNull("unanswered until the first-run prompt", viewModel.voiceChoice.value)

        viewModel.setVoiceChoice(VoiceGroupChoice.FEMALE, AppAnalytics.VoiceGroupSource.FirstRun)

        assertEquals(VoiceGroupChoice.FEMALE, viewModel.voiceChoice.value)
    }

    @Test
    fun `setVoiceChoice leaves the previous result as it was recorded`() {
        val tenor = ALL_FACH.first { it.rangeMinHz == 130f && it.rangeMaxHz == 523f }
        val profile = VoiceProfile(
            tenor.rangeMinHz, tenor.rangeMaxHz, tenor.tessituraMinHz, tenor.tessituraMaxHz,
            tenor.passaggioHz, 60, 30f,
        )
        forceLastResult(AnalyzeUiState.ResultReady(profile, FachClassifier.classify(profile)))

        viewModel.setVoiceChoice(VoiceGroupChoice.FEMALE, AppAnalytics.VoiceGroupSource.Change)

        // The switch is per recording: flipping it for the next singer must not move the
        // previous singer's result into the other half of the table.
        val last = viewModel.lastResult!!
        assertEquals(profile, last.profile)
        assertTrue(last.matches.any { it.fach.voiceGroup == VoiceGroup.MALE })
        assertEquals(FachClassifier.classify(profile), last.matches)
    }

    // ── Early Stop below the sample gate ──────────────────────────────────────
    //
    // 40% of insufficient results in production were 20–39 samples: users pressed Stop
    // seconds short. The first Stop below the gate now keeps recording with a prompt.

    @Test
    fun `first stop below the gate keeps recording and prompts`() {
        viewModel.startRecording()
        forceUiState(AnalyzeUiState.Recording(sampleCount = 12))

        viewModel.stopRecording()

        val state = viewModel.uiState.value
        assertTrue("Expected Recording but got $state", state is AnalyzeUiState.Recording)
        assertTrue((state as AnalyzeUiState.Recording).earlyStopPrompted)
        assertEquals(12, state.sampleCount)
    }

    @Test
    fun `second stop below the gate finishes as insufficient, showing the progress`() {
        viewModel.startRecording()
        forceUiState(AnalyzeUiState.Recording(sampleCount = 12))
        viewModel.stopRecording()

        viewModel.stopRecording()

        val state = viewModel.uiState.value
        assertTrue("Expected InsufficientData but got $state", state is AnalyzeUiState.InsufficientData)
        assertTrue((state as AnalyzeUiState.InsufficientData).reason.contains("12"))
    }

    @Test
    fun `stop at the gate does not prompt`() {
        viewModel.startRecording()
        forceUiState(AnalyzeUiState.Recording(sampleCount = RecordingGate.MIN_SAMPLES))

        viewModel.stopRecording()

        // The real analyzer heard nothing, so this ends insufficient — but without a prompt.
        assertTrue(viewModel.uiState.value is AnalyzeUiState.InsufficientData)
    }

    @Test
    fun `reaching the gate after a prompt clears it`() {
        viewModel.startRecording()
        forceUiState(
            AnalyzeUiState.Recording(sampleCount = RecordingGate.MIN_SAMPLES - 1, earlyStopPrompted = true)
        )

        analyzerPitchCallback().invoke(440f, "A4")

        val state = viewModel.uiState.value as AnalyzeUiState.Recording
        assertEquals(RecordingGate.MIN_SAMPLES, state.sampleCount)
        assertFalse("the next Stop must finish normally", state.earlyStopPrompted)
    }

    // ── Microphone failure ────────────────────────────────────────────────────

    @Test
    fun `a microphone that fails to start leaves Recording for the error state`() {
        // Before the fix the state stayed Recording with a Stop button that did nothing.
        replaceAnalyzer(VoiceAnalyzer(TestScope(UnconfinedTestDispatcher()), createRecorder = { _, _ -> null }))

        viewModel.startRecording()

        val state = viewModel.uiState.value
        assertTrue("Expected InsufficientData but got $state", state is AnalyzeUiState.InsufficientData)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Directly set the ViewModel's private `_uiState`, mirroring the `forceStage`
     * pattern already established in `WarmUpComparisonViewModelTest` — needed here
     * to simulate mid-session state without feeding real audio through YIN.
     */
    /** The ViewModel's pitch callback, as the real analyzer would call it per accepted frame. */
    private fun analyzerPitchCallback(): (Float, String) -> Unit {
        val field = AnalyzeViewModel::class.java.getDeclaredField("analyzer")
        field.isAccessible = true
        return (field.get(viewModel) as VoiceAnalyzer).onPitchDetected!!
    }

    private fun replaceAnalyzer(analyzer: VoiceAnalyzer) {
        val field = AnalyzeViewModel::class.java.getDeclaredField("analyzer")
        field.isAccessible = true
        field.set(viewModel, analyzer)
    }

    private fun forceLastResult(result: AnalyzeUiState.ResultReady) {
        val field = AnalyzeViewModel::class.java.getDeclaredField("_lastResult")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(viewModel) as MutableStateFlow<AnalyzeUiState.ResultReady?>
        flow.value = result
    }

    private fun forceUiState(state: AnalyzeUiState) {
        val field = AnalyzeViewModel::class.java.getDeclaredField("_uiState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(viewModel) as MutableStateFlow<AnalyzeUiState>
        flow.value = state
    }
}
