package com.yuriy.diapason.comparison

import org.junit.Assert.assertEquals
import org.junit.Test

/** [WarmUpPlan]: the five timed steps of the guided warm-up. */
class WarmUpPlanTest {

    @Test
    fun `the plan lasts two minutes and matches the timer constant`() {
        assertEquals(120, WarmUpPlan.TOTAL_SECONDS)
        assertEquals(WARM_UP_DURATION_SECONDS, WarmUpPlan.TOTAL_SECONDS)
    }

    @Test
    fun `each step starts where the previous one ends`() {
        assertEquals(listOf(0, 30, 50, 80, 105), WarmUpPlan.STEP_SECONDS.indices.map { WarmUpPlan.stepStartSeconds(it) })
        assertEquals(WarmUpPlan.TOTAL_SECONDS, WarmUpPlan.stepEndSeconds(WarmUpPlan.STEP_SECONDS.lastIndex))
    }

    @Test
    fun `stepIndexAt switches exactly at each boundary`() {
        assertEquals(0, WarmUpPlan.stepIndexAt(0))
        assertEquals(0, WarmUpPlan.stepIndexAt(29))
        assertEquals(1, WarmUpPlan.stepIndexAt(30))
        assertEquals(2, WarmUpPlan.stepIndexAt(50))
        assertEquals(3, WarmUpPlan.stepIndexAt(80))
        assertEquals(4, WarmUpPlan.stepIndexAt(119))
    }

    @Test
    fun `after the end the last step stays current`() {
        assertEquals(4, WarmUpPlan.stepIndexAt(120))
        assertEquals(4, WarmUpPlan.stepIndexAt(999))
        assertEquals(0, WarmUpPlan.secondsLeftInStep(999))
    }

    @Test
    fun `secondsLeftInStep counts down within a step`() {
        assertEquals(30, WarmUpPlan.secondsLeftInStep(0))
        assertEquals(1, WarmUpPlan.secondsLeftInStep(29))
        assertEquals(20, WarmUpPlan.secondsLeftInStep(30))
    }

    @Test
    fun `the passaggio hint goes on the octave slides step`() {
        // Step order: lip trills, humming, scales, octave slides, sustained notes.
        assertEquals(3, WarmUpPlan.PASSAGGIO_STEP)
    }
}
