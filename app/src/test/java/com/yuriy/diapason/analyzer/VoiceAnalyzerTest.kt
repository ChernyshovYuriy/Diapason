package com.yuriy.diapason.analyzer

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Direct tests for [VoiceAnalyzer]'s start/stop lifecycle.
 *
 * Previously untested — everything else in the suite validates the analyzer's
 * *acceptance-policy* logic indirectly through the hand-maintained
 * `SessionReplay` mirror (see `AnalyzerTestFixtures.kt`), never the real class.
 * Closed during a second, separate audit pass.
 *
 * [VoiceAnalyzer] constructs a real `AudioRecord`, so this runs under
 * Robolectric — verified directly before writing this file (a throwaway
 * scratch test, not assumed) that its `AudioRecord` shadow reports
 * `STATE_INITIALIZED`, so `start()` genuinely succeeds and the state machine
 * can be exercised for real rather than only reasoned about.
 *
 * No real audio ever flows under Robolectric's shadow, so every session here
 * accumulates zero pitch samples — `stop()` always takes the "insufficient
 * samples" path, never the success path. That's fine: the success-path
 * *classification* logic is already covered thoroughly elsewhere
 * (`FachClassifierClassifyTest`, `AnalyzerScenarioTest` via `SessionReplay`).
 * What's uniquely valuable here is the *lifecycle* — start/stop guards,
 * idempotency, and resource-reset behavior — which nothing else exercises
 * directly. The `AudioRecord`-init-failure branch is not covered here; forcing
 * it would need Robolectric shadow configuration beyond what was verified for
 * this pass, and it was already read carefully (release + null + callback,
 * straightforward) during the original architectural review.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VoiceAnalyzerTest {

    private val strings = VoiceAnalyzerStrings(
        listeningMessage = "listening",
        micInitError = "mic error",
        tooFewSamples = "too few samples"
    )

    private fun newAnalyzer() = VoiceAnalyzer(TestScope(UnconfinedTestDispatcher()))

    @Test
    fun `isRunning is false before start is ever called`() {
        val analyzer = newAnalyzer()
        assertFalse(analyzer.isRunning)
    }

    @Test
    fun `stop without start returns null and does not crash`() {
        val analyzer = newAnalyzer()
        val profile = analyzer.stop("too few")
        assertNull(profile)
        assertFalse(analyzer.isRunning)
    }

    @Test
    fun `start sets isRunning to true`() {
        val analyzer = newAnalyzer()
        analyzer.start(strings)
        assertTrue(analyzer.isRunning)
    }

    @Test
    fun `calling start while already running is a no-op`() {
        val analyzer = newAnalyzer()
        var listeningCallCount = 0
        analyzer.onStatusUpdate = { if (it == strings.listeningMessage) listeningCallCount++ }

        analyzer.start(strings)
        analyzer.start(strings) // redundant call — e.g. a double-tap before the UI responds

        assertTrue(analyzer.isRunning)
        assertEquals(
            "start() already guards internally (if (isRunning) return) — a redundant call " +
                    "must not re-fire the listening status update or restart the session",
            1, listeningCallCount
        )
    }

    @Test
    fun `stop with no accumulated samples returns null and fires the too-few-samples message`() {
        val analyzer = newAnalyzer()
        var statusMessage: String? = null
        analyzer.onStatusUpdate = { statusMessage = it }

        analyzer.start(strings)
        val profile = analyzer.stop(strings.tooFewSamples)

        assertNull(profile)
        assertFalse(analyzer.isRunning)
        assertEquals(strings.tooFewSamples, statusMessage)
    }

    @Test
    fun `calling stop twice after a session is idempotent`() {
        val analyzer = newAnalyzer()
        analyzer.start(strings)
        analyzer.stop(strings.tooFewSamples)

        val secondStop = analyzer.stop(strings.tooFewSamples)

        assertNull(secondStop)
        assertFalse(analyzer.isRunning)
    }

    @Test
    fun `a full stop-then-start cycle can successfully restart recording`() {
        val analyzer = newAnalyzer()
        analyzer.start(strings)
        analyzer.stop(strings.tooFewSamples)

        analyzer.start(strings)

        assertTrue(
            "AudioRecord release/reset on stop() must not block a subsequent start()",
            analyzer.isRunning
        )
    }
}
