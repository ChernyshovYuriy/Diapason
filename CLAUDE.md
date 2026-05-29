# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

Diapason is an Android voice range classifier for singers. It listens via microphone, runs YIN pitch detection, and classifies the user's voice against 19 German Fach categories. Published on Google Play (`com.yuriy.diapason`).

## Build & development commands

```bash
# Run all unit tests (pure JVM, no device required)
./gradlew :app:testDebugUnitTest

# Run a specific test class — must use :app:testDebugUnitTest, not :app:test
./gradlew :app:testDebugUnitTest --tests "*.FachClassifierClassifyTest"
./gradlew :app:testDebugUnitTest --tests "*.YinPitchDetectorTest"
./gradlew :app:testDebugUnitTest --tests "*.FixtureRegressionTest"

# Install debug build on connected device
./gradlew installDebug

# Build release bundle
./gradlew :app:bundleRelease
```

## Architecture

Single-module Android app (`:app`), MVVM, Jetpack Compose + Navigation Compose.

**`analyzer/`** — core DSP, no Android context dependency:
- `YinPitchDetector` — implements YIN algorithm (44 100 Hz, threshold 0.15, min confidence 0.80). Step 3 uses a `while` loop with a mutable `var tau` so the inner local-minimum advance loop correctly slides tau before recording `tauEstimate`. A Kotlin `for` loop would create an immutable `val` and silently skip the advance.
- `FachClassifier` — pure functions: `hzToNoteName` (uses `roundToInt()`, not `toInt()`), `estimateDetectedExtremes` (neighbor-validated within 2 semitones, binary search O(log n)), `estimateComfortableRange` (P20–P80 of sorted pitch list), `estimatePassaggio` (max-variance 15-sample sliding window — **variance is in Hz, not semitones**; see `KNOWN_ISSUES.md`), `classify` (scores 14 pts across 5 dimensions: ceiling 0–3, floor 0–2, tessHigh 0–3, tessLow 0–3, passaggio 0–3)
- `VoiceAnalyzer` — drives `AudioRecord` on `Dispatchers.IO`; stays context-free by receiving `VoiceAnalyzerStrings` from the ViewModel. Uses `CopyOnWriteArrayList` for the pitch sample buffer (IO writes, main-thread read after `cancel()`). Takes a snapshot before classifier calls to avoid `ConcurrentModificationException` from `subList`.
- `FachData` — static definitions for all 19 Fach types. Female voice values are standard. Male passaggio values are ~1 semitone below the literature median; see `KNOWN_ISSUES.md`.

**`data/`** — Room database (`diapason.db`, version 1):
- `SessionEntity` / `SessionDao` / `DiapasonDatabase` (singleton via `getInstance`)
- `SessionRepository` interface + `SessionRepositoryImpl`
- Room schema JSON is exported to `app/schemas/` — commit these files alongside any migration

**`ui/screens/`** — one Composable per screen:
- Bottom-nav screens: `analyze`, `guide`, `voice_types`, `history`, `about`
- Full-screen (no bottom bar): `results`, `warm_up_comparison`

**`comparison/`** — warm-up comparison flow (`WarmUpComparisonViewModel`, `ComparisonResult`)

**`analytics/AppAnalytics`** — type-safe Firebase Analytics wrapper, called directly as a singleton from ViewModels and Composables (no DI; matches the existing `AppLogger` pattern). Builds bundles via a small `params { str(...); long(...) }` DSL to avoid the deprecated `bundleOf`. Custom events instrument the analyze funnel (`analysis_started/completed/insufficient/abandoned`), result screen (`result_viewed/dismissed/shared` with dwell-seconds), warm-up flow (`warmup_started/skipped/completed`, `comparison_completed`), history, and re-test reminder funnel (`reminder_opt_in_shown/accepted/dismissed`, `reminder_cancelled`, `reminder_notification_posted`). Standard `screen_view` is fired manually from `DiapasonAppMainView` because Firebase auto-tracks only Activities, not Compose nav routes. User property `app_language` is set in `MainApp.onCreate` from `Locale.getDefault()`.

**`reminder/`** — opt-in weekly re-test notification (the single Week-2 retention lever):
- `ReminderPreferences` — `SharedPreferences` wrapper (`opted_in`, `scheduled_at_ms`).
- `ReminderWorker` — `CoroutineWorker` posting a single notification via `NotificationCompat`; `Channel.ensureRegistered()` is called from `MainApp.onCreate` and is idempotent. Checks `POST_NOTIFICATIONS` at fire time and silently drops if revoked. **ProGuard:** explicitly kept in `proguard-rules.pro` — WorkManager persists the worker FQN as a string in its DB, and R8 obfuscation is only deterministic within a single build, so without an explicit keep an obfuscated rename across app updates would silently drop already-scheduled reminders.
- `ReminderScheduler` — wraps `WorkManager.enqueueUniqueWork` with `ExistingWorkPolicy.REPLACE`. `REMINDER_DELAY_DAYS = 7L`. `bumpIfOptedIn()` is called from `AnalyzeViewModel.stopRecording` and `WarmUpComparisonViewModel.stopRetest` after every successful analysis, so the reminder is always anchored to the user's most recent session — never stale.
- UI lives on `ResultsScreen` (`ReTestReminderCard`); permission requested via Accompanist on Android 13+, granted implicitly on older versions.

**`MainApp`** — `onCreate` initialises `AppLogger.setDebug`, `FirebaseApp.initializeApp`, `AppAnalytics.init`, `AppAnalytics.setLanguage`, and `ReminderWorker.Channel.ensureRegistered` (idempotent — safe on every launch). Holds `sessionRepository` as an application-scoped lazy singleton. Tests inject a fake repository via ViewModel constructor parameters.

**Navigation** (`DiapasonAppMainView`): `AnalyzeViewModel` is activity-scoped so `ResultsScreen` can read `lastResult` from the same instance. The bottom bar is hidden for `Results` and `WarmUpComparison` routes. A `LaunchedEffect(currentRoute)` fires `AppAnalytics.trackScreen` on every nav change.

**`logging/AppLogger`** — thin wrapper; debug logging is enabled only on `FLAG_DEBUGGABLE` builds.

## ProGuard / R8

`proguard-rules.pro` keeps Crashlytics line numbers (`-keepattributes SourceFile,LineNumberTable`) and **one** application-level rule: a `-keep` for `com.yuriy.diapason.reminder.ReminderWorker` plus its `(Context, WorkerParameters)` constructor. Reason: WorkManager persists the worker's FQN as a string at enqueue time and resolves it via `Class.forName` at fire time, which can happen across app updates. R8 is only deterministic within a single build, so without this keep a rename in v(N+1) would `ClassNotFoundException` and silently drop reminders scheduled by v(N). Firebase Analytics needs no rules — event names are string literals, not reflected APIs. WorkManager itself ships consumer rules that keep `<init>(Context, WorkerParameters)` across all `ListenableWorker` subclasses but does **not** keep class names — that's why our explicit `-keep` is required.

## Localisation

Strings live in `res/values-xx/strings.xml` for `en`, `fr`, `it`, `es`, `pt`. When adding a new string, add it to all five files. Active locales are declared in `build.gradle.kts` via `localeFilters`.

## Testing

All tests are pure JVM (no Robolectric, no emulator). `android.util.Log` is stubbed via `testOptions { unitTests { isReturnDefaultValues = true } }` in `build.gradle.kts`.

**Two fixture styles** (both run in the same suite):
- **Kotlin DSL** (`AnalyzerTestFixtures.kt` / `buildSession { … }`) — precise edge-case engineering with controlled Hz values and a fluent builder (`sustainedNote`, `stepUp`, `noisyGlide`, `silenceGap`, `isolatedSpike`, `fadingConfidence`)
- **JSON fixtures** (`app/src/test/resources/fixtures/*.json`) — real-session regression; each frame is `{"hz": …, "confidence": …}` captured from Logcat

To add a JSON fixture: export `VoiceAnalyzer`-tagged Logcat lines, strip everything except `hz` and `confidence`, write the fixture JSON, then register the filename stem in `FixtureRegressionTest.fixtureNames()`. Full workflow is in `app/src/test/CAPTURING.md`.

**Passaggio fixtures** require a specific session structure: stable block below the break → rapid oscillation straddling the break → stable block above. Scale or arpeggio fixtures must set `passaggioNote: null` because Hz variance grows with pitch so the top of any ascending run dominates the window.

**Test files:**

| File | What it covers |
|---|---|
| `YinPitchDetectorTest` | YIN accuracy (≤20 cents), confidence, silence, noise, fallback path, Step 3 regression |
| `FachClassifierTest` | `hzToNoteName`, `estimateComfortableRange`, `estimateDetectedExtremes`, `estimatePassaggio` |
| `HzToNoteNameTest` | Note name correctness across all octaves, ALL_FACH passaggio/range values |
| `FachClassifierClassifyTest` | `classify()` scoring — perfect-match profiles, cross-category separation, result invariants |
| `AdjacentFachDiscriminationTest` | Hardest adjacent-pair boundaries; documents the 2 known indistinguishable ties |
| `FachDataIntegrityTest` | ALL_FACH table constraints: ordering, containment, unique IDs, positive values |
| `PassaggioEdgeCaseTest` | Passaggio and comfortable-range edge cases: bimodal, uniform, confidence fades |
| `EstimateDetectedExtremesStressTest` | Stress/property tests for neighbor-validated extremes |
| `AnalyzerScenarioTest` | End-to-end vocal scenarios using DSL fixtures |
| `AnalyzerInvariantTest` | Properties that must hold for all inputs (comfortable ⊆ detected) |
| `FixtureRegressionTest` | Parameterised regression against five JSON fixtures (10 tests × 5 fixtures = 50 cases) |
| `FixtureLoaderTest` | JSON loader, assertion helper, `toPitchSamples` bridge |

## Known limitations

See `KNOWN_ISSUES.md` for prioritised findings from architectural and musical review:
- Passaggio estimation is in Hz space (biased toward high registers)
- Male voice passaggio values in FachData are ~1 semitone below literature median
- Minimum sample gate of 20 frames is low for reliable P20/P80
- "Confidence" label on results screen is a score ratio, not a statistical confidence
