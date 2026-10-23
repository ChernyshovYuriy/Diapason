# Analytics events

Reference for every custom event logged through `AppAnalytics`
(`app/src/main/java/com/yuriy/diapason/analytics/AppAnalytics.kt`).

Reserved Firebase events (`first_open`, `session_start`, `app_remove`, …) are
recorded automatically by the SDK and are **not** listed here.

The `flow` parameter is one of: `single` (free recording), `range_test` (the guided range test, from 2.7.1), `baseline`, `retest` (from 2.7.1 both also run the range test's exercises — compare with earlier versions only knowing the exercise changed)
(`AppAnalytics.Flow`).

## User properties

| Property | Set from | Notes |
|---|---|---|
| `audio_source` | `AnalyzeViewModel` / `WarmUpComparisonViewModel` after each recording starts → `AppAnalytics.setAudioSourceMic` | Always `mic` from 2.7.1 — the 2.7.0 A/B (`mic` / `voice_recognition` / `mic_fallback`) ended with `voice_recognition` leaving more takes too short (22.0% vs 14.4%). Re-set on every recording so former `voice_recognition` installs stop reporting it |
| `app_language` | `MainApp.setCollectionEnabled(true)` → `AppAnalytics.setLanguage` | From `Locale.getDefault()`. Set only once collection is on (every launch for a consenting user, and right after Agree) — a property set while collection is off is dropped, which left it empty for ~80% of users before 2026-10 |

## Screen tracking

| Event | Params | Fired from |
|---|---|---|
| `screen_view` | `screen_name`, `screen_class` (both = nav route) | `DiapasonAppMainView` `LaunchedEffect(currentRoute)` — manual, because Firebase auto-tracks Activities, not Compose routes. Automatic Activity screen reporting is disabled in the manifest (`google_analytics_automatic_screen_reporting_enabled=false`). In the BigQuery export the params appear as `firebase_screen` / `firebase_screen_class` |

## Analyze funnel

| Event | Params | Fired from |
|---|---|---|
| `analysis_started` | `flow` | `AnalyzeViewModel` / `WarmUpComparisonViewModel` once recording has actually begun (since 2.7 not logged when the mic fails to start — see `analysis_mic_error`) |
| `analysis_completed` | `flow`, `duration_seconds`, `sample_count`, `top_fach_key`, `voice_group`, `reading`, `score`, `max_score`, `runner_up_gap`, `detected_min_midi`, `detected_max_midi`, `comfortable_low_midi`, `comfortable_high_midi`, `passaggio_midi` | `AnalyzeViewModel` / `WarmUpComparisonViewModel` on successful classification. `reading` = the voice-type reading Results shows (`VoiceTypeReader`): `soprano`, or `soprano|mezzo_soprano` for "between" — Analyze screen only (`single`, `range_test`), from 2.7.1. `top_fach_key` is still the top-ranked Fach, kept for continuity though no longer shown. `runner_up_gap` = top score − second score; the `*_midi` params are MIDI note numbers (A4 = 69), omitted when a pitch has none |
| `analysis_insufficient` | `flow`, `sample_count`, `duration_seconds` | Too few samples to classify (< 40-sample gate, `MIN_ACCEPTED_SAMPLES`) |
| `analysis_mic_error` | `flow`, `phase` (`start` / `recording`) | The microphone couldn't be opened/started (`start`), or stopped delivering audio mid-recording (`recording`, persistent `read()` errors). The attempt ends in an error state; no outcome event follows |
| `analysis_stop_too_early` | `flow`, `sample_count`, `duration_seconds` | Stop pressed below the 40-sample gate: recording continues with a "keep singing" prompt and the button becomes "Stop anyway". Follow it to the next outcome event to count recovered takes |
| `analysis_abandoned` | `flow`, `sample_count`, `duration_seconds` | A recording ends without the user pressing Stop: the Analyze screen or warm-up flow receives `ON_STOP` (navigated away, backgrounded, system back) — not on a configuration change — via `onScreenStopped()`; also the warm-up flow's Exit button, and `onCleared` as a fallback. All three flows |

## Voice group

| Event | Params | Fired from |
|---|---|---|
| `voice_group_selected` | `voice_group` (`male` / `female` / `unsure`), `source` (`first_run` / `change` / `mismatch_check`) | `AnalyzeViewModel.setVoiceChoice` — the prompt before the first recording (`first_run`), the Analyze screen's voice switch (`change`), or Switch on the voice-check prompt (`mismatch_check`). `voice_group` on `analysis_completed` uses the same values; `unsure` means all 19 Fach competed |
| `voice_group_mismatch` | `voice_group` (chosen), `suggested_group`, `action` (`switch` / `keep` / `dismiss` / `discard`) | `AnalyzeViewModel.resolveVoiceGroupCheck` — an Analyze-screen take (`single` or `range_test`) fit the other voice group by `VoiceGroupCheck.MARGIN`+ points and the singer answered the "Check the voice setting" prompt. `analysis_completed` follows (with the final `voice_group`) except after `discard`, so count `discard` as an attempt outcome. Not asked again after `keep` until the switch changes |

## Result screen

| Event | Params | Fired from |
|---|---|---|
| `result_viewed` | `top_fach_key` | `ResultsScreen` shown |
| `result_dismissed` | `top_fach_key`, `dwell_seconds` | Leaving the result screen |
| `result_shared` | `top_fach_key` | Share action |
| `result_next_step` | `target` (`voice_type` / `history` / `warmup`), `top_fach_key` | A "What's next" link on `ResultsScreen` |

## Warm-up comparison

| Event | Params | Fired from |
|---|---|---|
| `warmup_started` | `duration_seconds` | Guided warm-up started (120 s total since 2.7 — `WarmUpPlan`; 300 s before) |
| `warmup_skipped` | `remaining_seconds` | Warm-up skipped early |
| `warmup_completed` | — | Warm-up timer finished |
| `comparison_completed` | `before_fach`, `after_fach`, `comfortable_widened` (0/1), `detected_widened` (0/1) | `WarmUpComparisonViewModel` after retest |

## History

| Event | Params | Fired from |
|---|---|---|
| `history_chart_viewed` | `session_count`, `range_tests_only` (0/1) | `HistoryScreen` when "Your range over time" is drawn — from 2.7.1. `session_count` = sessions comparable with the latest (same voice choice and recording kind) |
| `history_opened` | `item_count` | `HistoryScreen` opened; logged after the first non-Loading state, so `item_count` is the real size (before 2026-10 it read the state once at launch and logged 0 for nearly every visit) |

## Combined voice profile

| Event | Params | Fired from |
|---|---|---|
| `combined_profile_viewed` | `session_count`, `consistent_count`, `reading` | `ResultsScreen` when the reading comes from several recent sessions (from 2.7.1). `reading` as on `analysis_completed` |

## Microphone permission

| Event | Params | Fired from |
|---|---|---|
| `mic_permission_result` | `granted` (0/1) | `AnalyzeScreen` when the system microphone-permission dialog closes (from 2.7.1). On a grant the recording the user tapped now starts at once; before, they had to tap Start again |

## Privacy consent

| Event | Params | Fired from |
|---|---|---|
| `privacy_consent_accepted` | — | `PrivacyConsentGate` after the user taps Agree on first launch. No matching "declined"/"shown" event exists on purpose — collection is off by default (`firebase_analytics_collection_enabled`/`firebase_crashlytics_collection_enabled` = `false` in the manifest) until this event fires, so a decline is never itself collected. |

## Re-test reminder funnel

| Event | Params | Fired from |
|---|---|---|
| `reminder_opt_in_shown` | — | `ReTestReminderCard` offered on `ResultsScreen`. Since 2.7 the offer appears once per install, from the second saved session (`ReminderOffer`); not fired when an opted-in user sees their scheduled reminder |
| `reminder_opt_in_accepted` | — | User opts in |
| `reminder_opt_in_dismissed` | — | User dismisses the card |
| `reminder_cancelled` | — | Opt-out after previously opting in |
| `reminder_notification_posted` | — | `ReminderWorker` posts the weekly notification |

## Notes

- `top_fach_key` / `before_fach` / `after_fach` fall back to `"unknown"` when null.
- `duration_seconds`, `score`, `max_score`, `sample_count` are logged as longs.
- All events are also mirrored to Logcat (tag `AppAnalytics`) on debug builds via `AppLogger`.
