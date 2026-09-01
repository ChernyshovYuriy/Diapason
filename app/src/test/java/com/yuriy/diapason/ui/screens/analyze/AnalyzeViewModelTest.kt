package com.yuriy.diapason.ui.screens.analyze

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Directly set the ViewModel's private `_uiState`, mirroring the `forceStage`
     * pattern already established in `WarmUpComparisonViewModelTest` — needed here
     * to simulate mid-session state without feeding real audio through YIN.
     */
    private fun forceUiState(state: AnalyzeUiState) {
        val field = AnalyzeViewModel::class.java.getDeclaredField("_uiState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(viewModel) as MutableStateFlow<AnalyzeUiState>
        flow.value = state
    }
}
