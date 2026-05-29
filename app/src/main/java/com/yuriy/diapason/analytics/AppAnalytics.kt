package com.yuriy.diapason.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.yuriy.diapason.logging.AppLogger

private const val TAG = "AppAnalytics"

/**
 * Type-safe Firebase Analytics wrapper.
 *
 * Events are designed around the funnel we actually need to measure:
 *   first_open → screen_view(analyze) → analysis_started → analysis_completed
 *     → result_viewed → result_shared | result_dismissed
 *
 * Reserved Firebase event names (first_open, session_start, app_remove, etc.) are
 * recorded automatically by the SDK and not duplicated here.
 */
object AppAnalytics {

    enum class Flow(val value: String) {
        Single("single"),
        Baseline("baseline"),
        Retest("retest"),
    }

    private var analytics: FirebaseAnalytics? = null

    fun init(context: Context) {
        analytics = FirebaseAnalytics.getInstance(context)
    }

    // ── User properties ──────────────────────────────────────────────────────

    fun setLanguage(language: String) {
        analytics?.setUserProperty(USER_PROP_LANGUAGE, language)
    }

    // ── Screen tracking (Compose nav routes) ─────────────────────────────────

    fun trackScreen(route: String) {
        log("screen_view $route")
        analytics?.logEvent(
            FirebaseAnalytics.Event.SCREEN_VIEW,
            params {
                str(FirebaseAnalytics.Param.SCREEN_NAME, route)
                str(FirebaseAnalytics.Param.SCREEN_CLASS, route)
            },
        )
    }

    // ── Analyze funnel ───────────────────────────────────────────────────────

    fun analysisStarted(flow: Flow) {
        logEvent(EVENT_ANALYSIS_STARTED) { str(PARAM_FLOW, flow.value) }
    }

    fun analysisCompleted(
        flow: Flow,
        durationSeconds: Float,
        sampleCount: Int,
        topFachKey: String?,
        score: Int?,
        maxScore: Int?,
    ) {
        logEvent(EVENT_ANALYSIS_COMPLETED) {
            str(PARAM_FLOW, flow.value)
            long(PARAM_DURATION_SECONDS, durationSeconds.toLong())
            long(PARAM_SAMPLE_COUNT, sampleCount.toLong())
            str(PARAM_TOP_FACH_KEY, topFachKey ?: VALUE_UNKNOWN)
            long(PARAM_SCORE, (score ?: 0).toLong())
            long(PARAM_MAX_SCORE, (maxScore ?: 0).toLong())
        }
    }

    fun analysisInsufficient(flow: Flow, sampleCount: Int) {
        logEvent(EVENT_ANALYSIS_INSUFFICIENT) {
            str(PARAM_FLOW, flow.value)
            long(PARAM_SAMPLE_COUNT, sampleCount.toLong())
        }
    }

    fun analysisAbandoned(flow: Flow, sampleCount: Int) {
        logEvent(EVENT_ANALYSIS_ABANDONED) {
            str(PARAM_FLOW, flow.value)
            long(PARAM_SAMPLE_COUNT, sampleCount.toLong())
        }
    }

    // ── Result screen ────────────────────────────────────────────────────────

    fun resultViewed(topFachKey: String?) {
        logEvent(EVENT_RESULT_VIEWED) { str(PARAM_TOP_FACH_KEY, topFachKey ?: VALUE_UNKNOWN) }
    }

    fun resultDismissed(topFachKey: String?, dwellSeconds: Long) {
        logEvent(EVENT_RESULT_DISMISSED) {
            str(PARAM_TOP_FACH_KEY, topFachKey ?: VALUE_UNKNOWN)
            long(PARAM_DWELL_SECONDS, dwellSeconds)
        }
    }

    fun resultShared(topFachKey: String?) {
        logEvent(EVENT_RESULT_SHARED) { str(PARAM_TOP_FACH_KEY, topFachKey ?: VALUE_UNKNOWN) }
    }

    // ── Warm-up comparison ───────────────────────────────────────────────────

    fun warmupStarted(durationSeconds: Int) {
        logEvent(EVENT_WARMUP_STARTED) { long(PARAM_DURATION_SECONDS, durationSeconds.toLong()) }
    }

    fun warmupSkipped(remainingSeconds: Int) {
        logEvent(EVENT_WARMUP_SKIPPED) { long(PARAM_REMAINING_SECONDS, remainingSeconds.toLong()) }
    }

    fun warmupCompleted() {
        logEvent(EVENT_WARMUP_COMPLETED) {}
    }

    fun comparisonCompleted(
        beforeFachKey: String?,
        afterFachKey: String?,
        comfortableRangeWidened: Boolean,
        detectedRangeWidened: Boolean,
    ) {
        logEvent(EVENT_COMPARISON_COMPLETED) {
            str(PARAM_BEFORE_FACH, beforeFachKey ?: VALUE_UNKNOWN)
            str(PARAM_AFTER_FACH, afterFachKey ?: VALUE_UNKNOWN)
            long(PARAM_COMFORTABLE_WIDENED, if (comfortableRangeWidened) 1L else 0L)
            long(PARAM_DETECTED_WIDENED, if (detectedRangeWidened) 1L else 0L)
        }
    }

    // ── History ──────────────────────────────────────────────────────────────

    fun historyOpened(itemCount: Int) {
        logEvent(EVENT_HISTORY_OPENED) { long(PARAM_ITEM_COUNT, itemCount.toLong()) }
    }

    // ── Re-test reminder funnel ──────────────────────────────────────────────

    fun reminderOptInShown() {
        logEvent(EVENT_REMINDER_OPT_IN_SHOWN) {}
    }

    fun reminderOptInAccepted() {
        logEvent(EVENT_REMINDER_OPT_IN_ACCEPTED) {}
    }

    fun reminderOptInDismissed() {
        logEvent(EVENT_REMINDER_OPT_IN_DISMISSED) {}
    }

    fun reminderCancelled() {
        logEvent(EVENT_REMINDER_CANCELLED) {}
    }

    fun reminderNotificationPosted() {
        logEvent(EVENT_REMINDER_NOTIFICATION_POSTED) {}
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private class ParamBuilder {
        val bundle = Bundle()
        fun str(key: String, value: String) { bundle.putString(key, value) }
        fun long(key: String, value: Long) { bundle.putLong(key, value) }
    }

    private fun params(build: ParamBuilder.() -> Unit): Bundle =
        ParamBuilder().apply(build).bundle

    private fun logEvent(name: String, build: ParamBuilder.() -> Unit) {
        val bundle = params(build)
        log("$name ${bundleSummary(bundle)}")
        analytics?.logEvent(name, bundle)
    }

    private fun bundleSummary(b: Bundle): String =
        if (b.isEmpty) "{}"
        else b.keySet().joinToString(prefix = "{", postfix = "}") { k ->
            @Suppress("DEPRECATION")
            "$k=${b.get(k)}"
        }

    private fun log(msg: String) = AppLogger.d("$TAG $msg")

    // ── Constants ────────────────────────────────────────────────────────────

    private const val EVENT_ANALYSIS_STARTED = "analysis_started"
    private const val EVENT_ANALYSIS_COMPLETED = "analysis_completed"
    private const val EVENT_ANALYSIS_INSUFFICIENT = "analysis_insufficient"
    private const val EVENT_ANALYSIS_ABANDONED = "analysis_abandoned"
    private const val EVENT_RESULT_VIEWED = "result_viewed"
    private const val EVENT_RESULT_DISMISSED = "result_dismissed"
    private const val EVENT_RESULT_SHARED = "result_shared"
    private const val EVENT_WARMUP_STARTED = "warmup_started"
    private const val EVENT_WARMUP_SKIPPED = "warmup_skipped"
    private const val EVENT_WARMUP_COMPLETED = "warmup_completed"
    private const val EVENT_COMPARISON_COMPLETED = "comparison_completed"
    private const val EVENT_HISTORY_OPENED = "history_opened"
    private const val EVENT_REMINDER_OPT_IN_SHOWN = "reminder_opt_in_shown"
    private const val EVENT_REMINDER_OPT_IN_ACCEPTED = "reminder_opt_in_accepted"
    private const val EVENT_REMINDER_OPT_IN_DISMISSED = "reminder_opt_in_dismissed"
    private const val EVENT_REMINDER_CANCELLED = "reminder_cancelled"
    private const val EVENT_REMINDER_NOTIFICATION_POSTED = "reminder_notification_posted"

    private const val PARAM_FLOW = "flow"
    private const val PARAM_DURATION_SECONDS = "duration_seconds"
    private const val PARAM_SAMPLE_COUNT = "sample_count"
    private const val PARAM_TOP_FACH_KEY = "top_fach_key"
    private const val PARAM_SCORE = "score"
    private const val PARAM_MAX_SCORE = "max_score"
    private const val PARAM_DWELL_SECONDS = "dwell_seconds"
    private const val PARAM_REMAINING_SECONDS = "remaining_seconds"
    private const val PARAM_BEFORE_FACH = "before_fach"
    private const val PARAM_AFTER_FACH = "after_fach"
    private const val PARAM_COMFORTABLE_WIDENED = "comfortable_widened"
    private const val PARAM_DETECTED_WIDENED = "detected_widened"
    private const val PARAM_ITEM_COUNT = "item_count"

    private const val USER_PROP_LANGUAGE = "app_language"

    private const val VALUE_UNKNOWN = "unknown"
}
