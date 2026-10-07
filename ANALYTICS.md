# Analytics events

Reference for every custom event logged through `AppAnalytics`
(`app/src/main/java/com/yuriy/diapason/analytics/AppAnalytics.kt`).

Reserved Firebase events (`first_open`, `session_start`, `app_remove`, …) are
recorded automatically by the SDK and are **not** listed here.

The `flow` parameter is one of: `single`, `baseline`, `retest`
(`AppAnalytics.Flow`).

## User properties

| Property | Set from | Notes |
|---|---|---|
| `app_language` | `MainApp.setCollectionEnabled(true)` → `AppAnalytics.setLanguage` | From `Locale.getDefault()`. Set only once collection is on (every launch for a consenting user, and right after Agree) — a property set while collection is off is dropped, which left it empty for ~80% of users before 2026-10 |

## Screen tracking

| Event | Params | Fired from |
|---|---|---|
| `screen_view` | `screen_name`, `screen_class` (both = nav route) | `DiapasonAppMainView` `LaunchedEffect(currentRoute)` — manual, because Firebase auto-tracks Activities, not Compose routes. Automatic Activity screen reporting is disabled in the manifest (`google_analytics_automatic_screen_reporting_enabled=false`). In the BigQuery export the params appear as `firebase_screen` / `firebase_screen_class` |

## Analyze funnel

| Event | Params | Fired from |
|---|---|---|
| `analysis_started` | `flow` | `AnalyzeViewModel` on record start |
| `analysis_completed` | `flow`, `duration_seconds`, `sample_count`, `top_fach_key`, `voice_group`, `score`, `max_score`, `runner_up_gap`, `detected_min_midi`, `detected_max_midi`, `comfortable_low_midi`, `comfortable_high_midi`, `passaggio_midi` | `AnalyzeViewModel` / `WarmUpComparisonViewModel` on successful classification. `runner_up_gap` = top score − second score; the `*_midi` params are MIDI note numbers (A4 = 69), omitted when a pitch has none |
| `analysis_insufficient` | `flow`, `sample_count`, `duration_seconds` | Too few samples to classify (< 40-sample gate, `MIN_ACCEPTED_SAMPLES`) |
| `analysis_abandoned` | `flow`, `sample_count`, `duration_seconds` | A recording ends without the user pressing Stop: the Analyze screen or warm-up flow receives `ON_STOP` (navigated away, backgrounded, system back) — not on a configuration change — via `onScreenStopped()`; also the warm-up flow's Exit button, and `onCleared` as a fallback. All three flows |

## Voice group

| Event | Params | Fired from |
|---|---|---|
| `voice_group_selected` | `voice_group` (`male` / `female` / `unsure`), `source` (`first_run` / `change`) | `AnalyzeViewModel.setVoiceGroup` — the prompt before the first recording (`first_run`) or the Analyze screen's voice chip (`change`). `voice_group` on `analysis_completed` uses the same values; `unsure` means all 19 Fach competed |

## Result screen

| Event | Params | Fired from |
|---|---|---|
| `result_viewed` | `top_fach_key` | `ResultsScreen` shown |
| `result_dismissed` | `top_fach_key`, `dwell_seconds` | Leaving the result screen |
| `result_shared` | `top_fach_key` | Share action |

## Warm-up comparison

| Event | Params | Fired from |
|---|---|---|
| `warmup_started` | `duration_seconds` | Warm-up timer started |
| `warmup_skipped` | `remaining_seconds` | Warm-up skipped early |
| `warmup_completed` | — | Warm-up timer finished |
| `comparison_completed` | `before_fach`, `after_fach`, `comfortable_widened` (0/1), `detected_widened` (0/1) | `WarmUpComparisonViewModel` after retest |

## History

| Event | Params | Fired from |
|---|---|---|
| `history_opened` | `item_count` | `HistoryScreen` opened; logged after the first non-Loading state, so `item_count` is the real size (before 2026-10 it read the state once at launch and logged 0 for nearly every visit) |

## Privacy consent

| Event | Params | Fired from |
|---|---|---|
| `privacy_consent_accepted` | — | `PrivacyConsentGate` after the user taps Agree on first launch. No matching "declined"/"shown" event exists on purpose — collection is off by default (`firebase_analytics_collection_enabled`/`firebase_crashlytics_collection_enabled` = `false` in the manifest) until this event fires, so a decline is never itself collected. |

## Re-test reminder funnel

| Event | Params | Fired from |
|---|---|---|
| `reminder_opt_in_shown` | — | `ReTestReminderCard` shown on `ResultsScreen` |
| `reminder_opt_in_accepted` | — | User opts in |
| `reminder_opt_in_dismissed` | — | User dismisses the card |
| `reminder_cancelled` | — | Opt-out after previously opting in |
| `reminder_notification_posted` | — | `ReminderWorker` posts the weekly notification |

## Notes

- `top_fach_key` / `before_fach` / `after_fach` fall back to `"unknown"` when null.
- `duration_seconds`, `score`, `max_score`, `sample_count` are logged as longs.
- All events are also mirrored to Logcat (tag `AppAnalytics`) on debug builds via `AppLogger`.
