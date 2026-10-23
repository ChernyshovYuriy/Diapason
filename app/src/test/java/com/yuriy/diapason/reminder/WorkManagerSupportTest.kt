package com.yuriy.diapason.reminder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * WorkManager reports failures that happen after [androidx.work.WorkManager.initialize] returns
 * (e.g. its database can't be opened before the user unlocks — Xiaomi, Android 15) only through
 * the configuration's initialization exception handler; without one it throws on a background
 * thread and kills the app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkManagerSupportTest {

    @Test
    fun `a post-initialization failure marks WorkManager unavailable instead of crashing`() {
        val handler = WorkManagerSupport.configuration().initializationExceptionHandler
        assertNotNull(handler)

        handler!!.accept(
            IllegalStateException("WorkManager can't be accessed from direct boot")
        )

        assertFalse(WorkManagerSupport.isAvailable)
    }
}
