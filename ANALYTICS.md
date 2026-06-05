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
| `app_language` | `MainApp.onCreate` → `AppAnalytics.setLanguage` | From `Locale.getDefault()` |

## Screen tracking

| Event | Params | Fired from |
|---|---|---|
| `screen_view` | `screen_name`, `screen_class` (both = nav route) | `DiapasonAppMainView` `LaunchedEffect(currentRoute)` — manual, because Firebase auto-tracks Activities, not Compose routes |

## Analyze funnel

| Event | Params | Fired from |
|---|---|---|
| `analysis_started` | `flow` | `AnalyzeViewModel` on record start |
| `analysis_completed` | `flow`, `duration_seconds`, `sample_count`, `top_fach_key`, `score`, `max_score` | `AnalyzeViewModel` on successful classification |
| `analysis_insufficient` | `flow`, `sample_count` | Too few samples to classify (< 20-frame gate) |
| `analysis_abandoned` | `flow`, `sample_count` | User leaves before completing |

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
| `history_opened` | `item_count` | `HistoryScreen` opened |

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
