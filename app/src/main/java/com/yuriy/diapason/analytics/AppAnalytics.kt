package com.yuriy.diapason.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.yuriy.diapason.analyzer.FachClassifier
import com.yuriy.diapason.analyzer.FachMatch
import com.yuriy.diapason.analyzer.VoiceGroup
import com.yuriy.diapason.analyzer.VoiceProfile
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

    /**
     * Firebase defaults collection to off via manifest meta-data
     * (`firebase_analytics_collection_enabled=false`) so nothing is recorded before the
     * user agrees to the privacy policy. Called with `true` once consent is granted (and
     * again on every launch of a returning consenting user, since [init] must always run
     * first for [analytics] to be non-null); never called with `false` — declining just
     * leaves collection at its manifest default.
     */
    fun setCollectionEnabled(enabled: Boolean) {
        analytics?.setAnalyticsCollectionEnabled(enabled)
    }

    // ── User properties ──────────────────────────────────────────────────────

    fun setLanguage(language: String) {
        analytics?.setUserProperty(USER_PROP_LANGUAGE, language)
    }

    /**
     * The audio-source experiment arm actually recording for this install ("mic",
     * "voice_recognition", or "mic_fallback" when the arm's source failed to initialise).
     * A user property so every event can be split by it in BigQuery.
     */
    fun setAudioSource(value: String) {
        analytics?.setUserProperty(USER_PROP_AUDIO_SOURCE, value)
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

    /**
     * Besides the headline result, logs the profile's range as MIDI note numbers and
     * the gap between the top two scores, so result stability can be analysed later
     * ("one voice, borderline" vs "a different voice") without guessing from Fach keys.
     */
    fun analysisCompleted(
        flow: Flow,
        profile: VoiceProfile,
        matches: List<FachMatch>,
        topFachKey: String?,
        voiceGroup: VoiceGroup?,
    ) {
        val top = matches.getOrNull(0)
        val runnerUp = matches.getOrNull(1)
        logEvent(EVENT_ANALYSIS_COMPLETED) {
            str(PARAM_FLOW, flow.value)
            long(PARAM_DURATION_SECONDS, profile.durationSeconds.toLong())
            long(PARAM_SAMPLE_COUNT, profile.sampleCount.toLong())
            str(PARAM_TOP_FACH_KEY, topFachKey ?: VALUE_UNKNOWN)
            str(PARAM_VOICE_GROUP, voiceGroupValue(voiceGroup))
            long(PARAM_SCORE, (top?.score ?: 0).toLong())
            long(PARAM_MAX_SCORE, (top?.maxScore ?: 0).toLong())
            if (top != null && runnerUp != null) {
                long(PARAM_RUNNER_UP_GAP, (top.score - runnerUp.score).toLong())
            }
            midi(PARAM_DETECTED_MIN_MIDI, profile.detectedMinHz)
            midi(PARAM_DETECTED_MAX_MIDI, profile.detectedMaxHz)
            midi(PARAM_COMFORTABLE_LOW_MIDI, profile.comfortableLowHz)
            midi(PARAM_COMFORTABLE_HIGH_MIDI, profile.comfortableHighHz)
            midi(PARAM_PASSAGGIO_MIDI, profile.estimatedPassaggioHz)
        }
    }

    fun analysisInsufficient(flow: Flow, sampleCount: Int, durationSeconds: Float) {
        logEvent(EVENT_ANALYSIS_INSUFFICIENT) {
            str(PARAM_FLOW, flow.value)
            long(PARAM_SAMPLE_COUNT, sampleCount.toLong())
            long(PARAM_DURATION_SECONDS, durationSeconds.toLong())
        }
    }

    /**
     * Stop was pressed below the sample gate, so recording continued with a "keep singing"
     * prompt instead of failing. Compare with the outcome that follows to see how many
     * would-be failures the prompt recovered.
     */
    fun analysisStopTooEarly(flow: Flow, sampleCount: Int, durationSeconds: Float) {
        logEvent(EVENT_ANALYSIS_STOP_TOO_EARLY) {
            str(PARAM_FLOW, flow.value)
            long(PARAM_SAMPLE_COUNT, sampleCount.toLong())
            long(PARAM_DURATION_SECONDS, durationSeconds.toLong())
        }
    }

    fun analysisAbandoned(flow: Flow, sampleCount: Int, durationSeconds: Float) {
        logEvent(EVENT_ANALYSIS_ABANDONED) {
            str(PARAM_FLOW, flow.value)
            long(PARAM_SAMPLE_COUNT, sampleCount.toLong())
            long(PARAM_DURATION_SECONDS, durationSeconds.toLong())
        }
    }

    // ── Voice group ──────────────────────────────────────────────────────────

    enum class VoiceGroupSource(val value: String) {
        /** Answered in the prompt shown before the first recording. */
        FirstRun("first_run"),
        /** Changed later from the Analyze screen. */
        Change("change"),
    }

    fun voiceGroupSelected(voiceGroup: VoiceGroup?, source: VoiceGroupSource) {
        logEvent(EVENT_VOICE_GROUP_SELECTED) {
            str(PARAM_VOICE_GROUP, voiceGroupValue(voiceGroup))
            str(PARAM_SOURCE, source.value)
        }
    }

    /** "male" / "female", or "unsure" for the full-table "Not sure" answer. */
    private fun voiceGroupValue(voiceGroup: VoiceGroup?): String =
        voiceGroup?.name?.lowercase() ?: VALUE_UNSURE

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

    // ── Privacy consent ──────────────────────────────────────────────────────

    /**
     * Fired right after collection is switched on, so this is the earliest event
     * collection can ever record — there is no matching "declined" event, since
     * declining means collection stays off and nothing about that choice is recorded.
     */
    fun privacyConsentAccepted() {
        logEvent(EVENT_PRIVACY_CONSENT_ACCEPTED) {}
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
        /** Omitted (not logged as 0) when [hz] has no note number, so it can't skew averages. */
        fun midi(key: String, hz: Float) { FachClassifier.hzToMidi(hz)?.let { long(key, it.toLong()) } }
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
    private const val EVENT_ANALYSIS_STOP_TOO_EARLY = "analysis_stop_too_early"
    private const val EVENT_RESULT_VIEWED = "result_viewed"
    private const val EVENT_RESULT_DISMISSED = "result_dismissed"
    private const val EVENT_RESULT_SHARED = "result_shared"
    private const val EVENT_WARMUP_STARTED = "warmup_started"
    private const val EVENT_WARMUP_SKIPPED = "warmup_skipped"
    private const val EVENT_WARMUP_COMPLETED = "warmup_completed"
    private const val EVENT_COMPARISON_COMPLETED = "comparison_completed"
    private const val EVENT_HISTORY_OPENED = "history_opened"
    private const val EVENT_VOICE_GROUP_SELECTED = "voice_group_selected"
    private const val EVENT_PRIVACY_CONSENT_ACCEPTED = "privacy_consent_accepted"
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
    private const val PARAM_RUNNER_UP_GAP = "runner_up_gap"
    private const val PARAM_VOICE_GROUP = "voice_group"
    private const val PARAM_SOURCE = "source"
    private const val PARAM_DETECTED_MIN_MIDI = "detected_min_midi"
    private const val PARAM_DETECTED_MAX_MIDI = "detected_max_midi"
    private const val PARAM_COMFORTABLE_LOW_MIDI = "comfortable_low_midi"
    private const val PARAM_COMFORTABLE_HIGH_MIDI = "comfortable_high_midi"
    private const val PARAM_PASSAGGIO_MIDI = "passaggio_midi"

    private const val USER_PROP_LANGUAGE = "app_language"
    private const val USER_PROP_AUDIO_SOURCE = "audio_source"

    private const val VALUE_UNKNOWN = "unknown"
    private const val VALUE_UNSURE = "unsure"
}
