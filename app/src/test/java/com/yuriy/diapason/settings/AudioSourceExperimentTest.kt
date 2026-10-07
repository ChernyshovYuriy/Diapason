package com.yuriy.diapason.settings

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

/** [AudioSourceExperiment]: a 50/50 arm assigned once per install and kept. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioSourceExperimentTest {

    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    /** A Random whose nextBoolean() always returns [value]. */
    private fun fixed(value: Boolean) = object : Random() {
        override fun nextBits(bitCount: Int) = 0
        override fun nextBoolean() = value
    }

    @Test
    fun `both arms are reachable`() {
        assertEquals(AudioSourceExperiment.Arm.VOICE_RECOGNITION, AudioSourceExperiment(app, fixed(true)).arm)
        // Forget the stored arm, as on a fresh install.
        app.getSharedPreferences("diapason_experiments", Context.MODE_PRIVATE).edit().clear().commit()
        assertEquals(AudioSourceExperiment.Arm.MIC, AudioSourceExperiment(app, fixed(false)).arm)
    }

    @Test
    fun `the first assignment is kept regardless of later randomness`() {
        val first = AudioSourceExperiment(app, fixed(true)).arm
        repeat(5) {
            assertEquals(first, AudioSourceExperiment(app, fixed(false)).arm)
        }
    }
}
