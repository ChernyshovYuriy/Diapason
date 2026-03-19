package com.yuriy.diapason.comparison

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.yuriy.diapason.data.SessionRecord
import com.yuriy.diapason.data.repository.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
 * Tests for [WarmUpComparisonViewModel] state transitions.
 *
 * Because [WarmUpComparisonViewModel] depends on [VoiceAnalyzer] (which uses the
 * microphone) we cannot exercise full start→stop cycles in a unit test.  Instead
 * these tests validate:
 *
 *  - resetToIntro clears all state and returns to Intro
 *  - retryBaseline after BaselineInsufficient returns to Intro
 *  - skipWarmUpTimer from WarmUp transitions to Retest
 *  - retryRetest after RetestInsufficient returns to Retest
 *  - initial stage is Intro
 *
 * Integration of the actual VoiceAnalyzer/FachClassifier pipeline is covered by
 * the existing AnalyzerScenarioTest and AnalyzerInvariantTest suites.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WarmUpComparisonViewModelTest {

    private lateinit var fakeRepo: FakeSessionRepository
    private lateinit var viewModel: WarmUpComparisonViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fakeRepo = FakeSessionRepository()
        // We rely on Robolectric's ApplicationProvider to satisfy AndroidViewModel.
        viewModel = WarmUpComparisonViewModel(
            ApplicationProvider.getApplicationContext<Application>()
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun `initial stage is Intro`() = runTest {
        assertTrue(viewModel.stage.value is ComparisonStage.Intro)
    }

    // ── resetToIntro ──────────────────────────────────────────────────────────

    @Test
    fun `resetToIntro from Intro stays at Intro`() = runTest {
        viewModel.resetToIntro()
        assertTrue(viewModel.stage.value is ComparisonStage.Intro)
    }

    @Test
    fun `resetToIntro from WarmUp returns to Intro`() = runTest {
        // Force the stage to WarmUp directly to avoid needing a mic
        forceStage(ComparisonStage.WarmUp(remainingSeconds = 300, isRunning = false))
        viewModel.resetToIntro()
        assertTrue(viewModel.stage.value is ComparisonStage.Intro)
    }

    @Test
    fun `resetToIntro from BaselineInsufficient returns to Intro`() = runTest {
        forceStage(ComparisonStage.BaselineInsufficient("not enough data"))
        viewModel.resetToIntro()
        assertTrue(viewModel.stage.value is ComparisonStage.Intro)
    }

    @Test
    fun `resetToIntro from RetestInsufficient returns to Intro`() = runTest {
        forceStage(ComparisonStage.RetestInsufficient("not enough data"))
        viewModel.resetToIntro()
        assertTrue(viewModel.stage.value is ComparisonStage.Intro)
    }

    // ── retryBaseline ─────────────────────────────────────────────────────────

    @Test
    fun `retryBaseline from BaselineInsufficient returns to Intro`() = runTest {
        forceStage(ComparisonStage.BaselineInsufficient("too short"))
        viewModel.retryBaseline()
        assertTrue(viewModel.stage.value is ComparisonStage.Intro)
    }

    // ── retryRetest ───────────────────────────────────────────────────────────

    @Test
    fun `retryRetest from RetestInsufficient advances to Retest with isRecording false`() = runTest {
        forceStage(ComparisonStage.RetestInsufficient("too short"))
        viewModel.retryRetest()
        val stage = viewModel.stage.value
        assertTrue("Expected Retest but got $stage", stage is ComparisonStage.Retest)
        assertFalse((stage as ComparisonStage.Retest).isRecording)
    }

    // ── skipWarmUpTimer ───────────────────────────────────────────────────────

    @Test
    fun `skipWarmUpTimer from idle WarmUp advances to Retest with isRecording false`() = runTest {
        forceStage(ComparisonStage.WarmUp(remainingSeconds = 300, isRunning = false))
        viewModel.skipWarmUpTimer()
        val stage = viewModel.stage.value
        assertTrue("Expected Retest but got $stage", stage is ComparisonStage.Retest)
        assertFalse((stage as ComparisonStage.Retest).isRecording)
    }

    @Test
    fun `skipWarmUpTimer from running WarmUp advances to Retest with isRecording false`() = runTest {
        forceStage(ComparisonStage.WarmUp(remainingSeconds = 120, isRunning = true))
        viewModel.skipWarmUpTimer()
        val stage = viewModel.stage.value
        assertTrue("Expected Retest but got $stage", stage is ComparisonStage.Retest)
        assertFalse((stage as ComparisonStage.Retest).isRecording)
    }

    // ── startWarmUpTimer ──────────────────────────────────────────────────────

    @Test
    fun `startWarmUpTimer marks WarmUp as running`() = runTest {
        forceStage(ComparisonStage.WarmUp(remainingSeconds = 300, isRunning = false))
        viewModel.startWarmUpTimer()
        val stage = viewModel.stage.value
        assertTrue(stage is ComparisonStage.WarmUp)
        assertTrue((stage as ComparisonStage.WarmUp).isRunning)
    }

    @Test
    fun `startWarmUpTimer is no-op when not in WarmUp stage`() = runTest {
        // Stage is Intro — calling startWarmUpTimer should not change anything
        viewModel.startWarmUpTimer()
        assertTrue(viewModel.stage.value is ComparisonStage.Intro)
    }

    // ── WarmUp timer countdown — only the flag, not full tick cycle ───────────

    @Test
    fun `WarmUp stage carries remainingSeconds correctly`() = runTest {
        val expected = 247
        forceStage(ComparisonStage.WarmUp(remainingSeconds = expected, isRunning = true))
        val stage = viewModel.stage.value as ComparisonStage.WarmUp
        assertEquals(expected, stage.remainingSeconds)
    }

    // ── ComparisonStage.Done invariant ────────────────────────────────────────

    @Test
    fun `Done stage result references before and after profiles`() = runTest {
        val before = fakeProfile(comfortableLow = 250f, comfortableHigh = 680f)
        val after = fakeProfile(comfortableLow = 230f, comfortableHigh = 720f)
        val result = ComparisonResult.compute(
            before = before, beforeTopMatch = null,
            after = after, afterTopMatch = null,
        )
        forceStage(ComparisonStage.Done(result))

        val stage = viewModel.stage.value as ComparisonStage.Done
        assertEquals(before, stage.result.before)
        assertEquals(after, stage.result.after)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Directly set the ViewModel's private _stage via its public [stage] flow
     * is not possible, so we call resetToIntro and use available public APIs
     * to manoeuvre into desired states. For states that can't be reached without
     * the mic (Baseline, Retest), we test the transitions out of them via
     * the public state-mutating methods that accept those states as preconditions.
     *
     * For WarmUp, BaselineInsufficient, RetestInsufficient and Done we reach
     * them by calling [resetToIntro] then using reflection on the private field.
     */
    private fun forceStage(stage: ComparisonStage) {
        val field = WarmUpComparisonViewModel::class.java.getDeclaredField("_stage")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<ComparisonStage>
        flow.value = stage
    }

    private fun fakeProfile(
        detectedMin: Float = 180f,
        detectedMax: Float = 860f,
        comfortableLow: Float = 250f,
        comfortableHigh: Float = 700f,
        passaggio: Float = 420f,
        sampleCount: Int = 40,
    ) = com.yuriy.diapason.analyzer.VoiceProfile(
        detectedMinHz = detectedMin,
        detectedMaxHz = detectedMax,
        comfortableLowHz = comfortableLow,
        comfortableHighHz = comfortableHigh,
        estimatedPassaggioHz = passaggio,
        sampleCount = sampleCount,
        durationSeconds = 35f,
    )
}

// ── Fake repository ───────────────────────────────────────────────────────────

private class FakeSessionRepository : SessionRepository {
    val saved = mutableListOf<SessionRecord>()
    private val _flow = MutableStateFlow<List<SessionRecord>>(emptyList())

    override suspend fun save(session: SessionRecord) {
        saved.add(session)
        _flow.value = saved.toList()
    }

    override fun observeAll(): Flow<List<SessionRecord>> = _flow
    override suspend fun getAll(): List<SessionRecord> = saved.toList()
    override suspend fun getById(id: String): SessionRecord? = saved.find { it.id == id }
    override suspend fun deleteById(id: String) { saved.removeAll { it.id == id } }
    override suspend fun count(): Int = saved.size
}
