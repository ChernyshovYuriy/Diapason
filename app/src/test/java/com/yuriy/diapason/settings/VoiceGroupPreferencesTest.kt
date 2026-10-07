package com.yuriy.diapason.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.yuriy.diapason.analyzer.VoiceGroupChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [VoiceGroupPreferences] remembers the last Male · Female · Not sure choice. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VoiceGroupPreferencesTest {

    private fun prefs() = VoiceGroupPreferences(ApplicationProvider.getApplicationContext<Application>())

    @Test
    fun `a fresh install has no choice, so the first-run prompt is shown`() {
        assertNull(prefs().choice)
    }

    @Test
    fun `every choice round-trips, including Not sure`() {
        VoiceGroupChoice.entries.forEach { choice ->
            prefs().choice = choice
            assertEquals(choice, prefs().choice)
        }
    }

    @Test
    fun `setting null clears the choice`() {
        prefs().choice = VoiceGroupChoice.MALE
        prefs().choice = null
        assertNull(prefs().choice)
    }
}
