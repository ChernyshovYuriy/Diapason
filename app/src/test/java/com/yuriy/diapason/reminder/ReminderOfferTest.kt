package com.yuriy.diapason.reminder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [ReminderOffer]: offered once, from the second saved session; always shown when opted in. */
class ReminderOfferTest {

    @Test
    fun `not offered on the first session`() {
        assertFalse(ReminderOffer.shouldShow(optedIn = false, offerShown = false, savedSessionCount = 1))
    }

    @Test
    fun `offered from the second session`() {
        assertTrue(ReminderOffer.shouldShow(optedIn = false, offerShown = false, savedSessionCount = 2))
        assertTrue(ReminderOffer.shouldShow(optedIn = false, offerShown = false, savedSessionCount = 9))
    }

    @Test
    fun `never offered again once shown`() {
        assertFalse(ReminderOffer.shouldShow(optedIn = false, offerShown = true, savedSessionCount = 9))
    }

    @Test
    fun `always shown to an opted-in user, who manages the reminder there`() {
        assertTrue(ReminderOffer.shouldShow(optedIn = true, offerShown = true, savedSessionCount = 1))
    }
}
