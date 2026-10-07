package com.yuriy.diapason.reminder

/**
 * When the Results screen shows the re-test reminder card.
 *
 * Opted-in users always see it (it's where they check or cancel the reminder). Everyone
 * else is offered it once, and only from their [MIN_SESSIONS]th saved session: someone who
 * came back once is the one open to a nudge. In production the card appeared on every
 * result (~7 times per user), 7% accepted and 88% ignored it.
 */
object ReminderOffer {

    const val MIN_SESSIONS = 2

    fun shouldShow(optedIn: Boolean, offerShown: Boolean, savedSessionCount: Int): Boolean =
        optedIn || (!offerShown && savedSessionCount >= MIN_SESSIONS)
}
