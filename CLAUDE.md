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
- `FachClassifier` — pure functions: `hzToNoteName` (uses `roundToInt()`, not `toInt()`), `estimateDetectedExtremes` (neighbor-validated within 2 semitones, binary search O(log n)), `estimateComfortableRange` (P20–P80 of sorted pitch list), `estimatePassaggio` (15-sample sliding window in **semitone space**, scored by direction reversals (weighted 2^n) × median frame-to-frame move — not plain variance, which can't tell a wobble from one clean jump; falls back to a plain average below `PASSAGGIO_MIN_SAMPLES = 30`), `classify(profile, group)` (scores 14 pts across 5 dimensions: floor 0–3, ceiling 0–2, tessHigh 0–3, tessLow 0–3, passaggio 0–3 — floor gets the finer tiers because a ceiling is easier to fake; with a `VoiceGroup` it ranks only that half of the table, with `null` all 19), `summarize` (family-first `ResultSummary`: `categoryRes` as headline, top Fach as the leaning, runner-up when within `CLOSE_RUNNER_UP_POINTS`)
- `VoiceAnalyzer(scope, audioSource)` — drives `AudioRecord` on `Dispatchers.IO` (falls back to `MIC` if the requested source fails, reported via `usedFallbackSource`); stays context-free by receiving `VoiceAnalyzerStrings` from the ViewModel. Uses `CopyOnWriteArrayList` for the pitch sample buffer (IO writes, main-thread read after `cancel()`). Takes a snapshot before classifier calls to avoid `ConcurrentModificationException` from `subList`. Accepts frames ≥ `MIN_PITCH_HZ = 60` Hz; `stop()` requires `MIN_ACCEPTED_SAMPLES = 40`. Callers guard with `if (analyzer.isRunning) return` before `start()` (double-tap fix). The test-only `SessionReplay` in `AnalyzerTestFixtures.kt` mirrors these constants and the `stop()` profile-building logic — change both together.
- `RecordingGate` — progress toward `MIN_ACCEPTED_SAMPLES` and a seconds-left estimate from the session's own sample rate. Both recording flows hold the first Stop below the gate ("keep singing… / Stop anyway") instead of failing; `RecordingGateProgress` is the shared progress UI.
- `FachData` — static definitions for all 19 Fach types (female and male passaggio values both aligned to the literature median). Each carries a `VoiceGroup` (`FEMALE` = soprano/mezzo/contralto, `MALE` = everything else, countertenor included): pitch can't separate the overlapping halves, so the user states it once.
- `VoiceProfileAggregator` — combines the last 5 sessions within 30 days recorded with the current voice choice (author's choice; no other window) into a per-dimension median profile, classified once, with "consistent in k of n". `SessionRecord.toTimedProfile()` (in `SessionMapper`) feeds it.
- Numeric UI readouts and `hzToNoteName` format with `Locale.ROOT` (default-locale formatting renders non-Latin digits under e.g. Persian).

**`data/`** — Room database (`diapason.db`, version 2; v2 added `sessions.voice_group`, migration `MIGRATION_1_2` in `DiapasonDatabase.kt`):
- `SessionEntity` / `SessionDao` / `DiapasonDatabase` (singleton via `getInstance`)
- `SessionRepository` interface + `SessionRepositoryImpl`
- Room schema JSON is exported to `app/schemas/` — commit these files alongside any migration. `app/schemas` is also added to the **debug** assets so `MigrationTestHelper` (`DatabaseMigrationTest`, Robolectric) can open old versions — Robolectric reads merged debug assets, not test assets
- `List<SessionRecord>.recordedWith(choice)` — sessions eligible for the combined profile: same voice choice only; pre-v2 rows (null choice) never

**`ui/screens/`** — one Composable per screen:
- Bottom-nav screens: `analyze`, `guide`, `voice_types`, `history`, `about`. History opens with "Your range over time" (`RangeProgressChart`, data from the pure `buildRangeProgress`: semitone axis snapped to Cs, newest 20 sessions, current voice choice only)
- Full-screen (no bottom bar): `results`, `warm_up_comparison`

**`comparison/`** — warm-up comparison flow (`WarmUpComparisonViewModel`, `ComparisonResult`, `WarmUpPlan`). The warm-up is a 2-minute guided sequence of five timed steps (`WarmUpPlan.STEP_SECONDS`; `WARM_UP_DURATION_SECONDS` must equal its total — tested); the running step is highlighted, and the octave-slides step shows the baseline's passaggio note when the baseline had ≥ `PASSAGGIO_MIN_SAMPLES`. The result screen deliberately shows no before/after Fach — a warm-up changes comfort and access, not voice type.

**`settings/AudioSourceExperiment`** — per-install 50/50 A/B of `MIC` vs `VOICE_RECOGNITION` (suspected OEM noise suppression on `MIC`); reported as the `audio_source` user property.

**`settings/VoiceGroupPreferences`** — the last `VoiceGroupChoice` (`MALE` / `FEMALE` / `UNSURE`; null = never answered → first-run explanation dialog on Start). It only pre-selects the **per-recording** Male · Female · Not sure switch above Start (`VoiceGroupSwitch`), which is visible on every recording so teachers and shared phones switch it per singer; each session saves the choice it was recorded with. Changing it re-ranks the result on screen. Singer profiles are the planned next step (`KNOWN_ISSUES.md`, "Planned · Singer profiles").

**`analytics/AppAnalytics`** — type-safe Firebase Analytics wrapper, called directly as a singleton from ViewModels and Composables (no DI; matches the existing `AppLogger` pattern). Builds bundles via a small `params { str(...); long(...) }` DSL to avoid the deprecated `bundleOf`. Custom events instrument the analyze funnel (`analysis_started/completed/insufficient/abandoned`), result screen (`result_viewed/dismissed/shared` with dwell-seconds), warm-up flow (`warmup_started/skipped/completed`, `comparison_completed`), history, privacy consent (`privacy_consent_accepted`), and re-test reminder funnel (`reminder_opt_in_shown/accepted/dismissed`, `reminder_cancelled`, `reminder_notification_posted`). Standard `screen_view` is fired manually from `DiapasonAppMainView` because Firebase auto-tracks only Activities, not Compose nav routes. User property `app_language` is set from `Locale.getDefault()` inside `MainApp.setCollectionEnabled(true)`. `analysis_abandoned` fires from each recording screen's `ON_STOP` (`onScreenStopped()`), not just `onCleared` — the activity-scoped `AnalyzeViewModel` almost never reaches `onCleared`. The full event catalogue (params + fire sites) is in `ANALYTICS.md` — keep it in sync when adding or renaming events.

**`consent/`** — first-launch privacy consent gate (Huawei AppGallery Review Guidelines rule 7.5 / PIPL: no personal-info-collecting SDK may run before the user has agreed):
- `PrivacyConsentPreferences` — `SharedPreferences` wrapper (`granted`), same shape as `ReminderPreferences`.
- `PrivacyConsentGate` — a Composable rendered first in `DiapasonAppMainView`; shows a non-dismissible `AlertDialog` (no back-press/outside-tap dismiss) until `granted` is true, then renders nothing. Agree persists `granted`, calls `MainApp.setCollectionEnabled(true)`, and fires `AppAnalytics.privacyConsentAccepted()`; Disagree finishes the Activity. There is no "declined" event — collection stays off, so declining is never itself collected.
- Both `firebase_analytics_collection_enabled` and `firebase_crashlytics_collection_enabled` are set to `false` in `AndroidManifest.xml`, because Firebase auto-initializes both SDKs via a `ContentProvider` that runs before `MainApp.onCreate`. `MainApp.onCreate` re-enables collection on every launch for a user whose `PrivacyConsentPreferences.granted` is already true, so that preference — not Firebase's own persisted flag — stays the single source of truth.

**`reminder/`** — opt-in weekly re-test notification (the single Week-2 retention lever):
- `ReminderPreferences` — `SharedPreferences` wrapper (`opted_in`, `scheduled_at_ms`).
- `ReminderWorker` — `CoroutineWorker` posting a single notification via `NotificationCompat`; `Channel.ensureRegistered()` is called from `MainApp.onCreate` and is idempotent. Checks `POST_NOTIFICATIONS` at fire time and silently drops if revoked. **ProGuard:** explicitly kept in `proguard-rules.pro` — WorkManager persists the worker FQN as a string in its DB, and R8 obfuscation is only deterministic within a single build, so without an explicit keep an obfuscated rename across app updates would silently drop already-scheduled reminders.
- `ReminderScheduler` — wraps `WorkManager.enqueueUniqueWork` with `ExistingWorkPolicy.REPLACE`. `REMINDER_DELAY_DAYS = 7L`. `bumpIfOptedIn()` is called from `AnalyzeViewModel.stopRecording` and `WarmUpComparisonViewModel.stopRetest` after every successful analysis, so the reminder is always anchored to the user's most recent session — never stale.
- UI lives on `ResultsScreen` (`ReTestReminderCard`); permission requested via Accompanist on Android 13+, granted implicitly on older versions. Offered once per install, from the second saved session (`ReminderOffer`, `ReminderPreferences.offerShown`); always shown to opted-in users so they can cancel.

**`MainApp`** — `onCreate` initialises `AppLogger.setDebug`, `FirebaseApp.initializeApp`, `AppAnalytics.init`, and `ReminderWorker.Channel.ensureRegistered` (idempotent — safe on every launch). `MainApp.setCollectionEnabled(true)` also sets the `app_language` user property — it must run after collection is on, or Firebase drops it. Holds `sessionRepository` as an application-scoped lazy singleton. Tests inject a fake repository via ViewModel constructor parameters.

**Navigation** (`DiapasonAppMainView`): `ResultsScreen`'s "What's next" links leave Results like the bottom bar does (after `resetToIdle()`); "Learn about…" passes the Fach to Voice Types via the destination entry's `savedStateHandle` (`VOICE_TYPES_FOCUS_KEY`), so the route stays plain `voice_types`. `AnalyzeViewModel` is activity-scoped so `ResultsScreen` can read `lastResult` from the same instance. The bottom bar is hidden for `Results` and `WarmUpComparison` routes. A `LaunchedEffect(currentRoute)` fires `AppAnalytics.trackScreen` on every nav change.

**`LocaleAwareContext`** — `localizedString` / `localizedQuantityString` resolve ViewModel-side text against the in-app language override (the Application context doesn't reliably see it); `RecordingGateMessages.kt` builds the gate prompts with them.

**`logging/AppLogger`** — thin wrapper; debug logging is enabled only on `FLAG_DEBUGGABLE` builds.

## ProGuard / R8

`proguard-rules.pro` keeps Crashlytics line numbers (`-keepattributes SourceFile,LineNumberTable`) and **one** application-level rule: a `-keep` for `com.yuriy.diapason.reminder.ReminderWorker` plus its `(Context, WorkerParameters)` constructor. Reason: WorkManager persists the worker's FQN as a string at enqueue time and resolves it via `Class.forName` at fire time, which can happen across app updates. R8 is only deterministic within a single build, so without this keep a rename in v(N+1) would `ClassNotFoundException` and silently drop reminders scheduled by v(N). Firebase Analytics needs no rules — event names are string literals, not reflected APIs. WorkManager itself ships consumer rules that keep `<init>(Context, WorkerParameters)` across all `ListenableWorker` subclasses but does **not** keep class names — that's why our explicit `-keep` is required.

## Localisation

Strings live in `res/values-xx/strings.xml` for `en`, `fr`, `it`, `es`, `pt`, `zh`, `fa`, `ar`. When adding a new string, add it to all eight files — `LocalizationParityTest` fails on a missing/extra key, a placeholder mismatch, or a `localeFilters` entry it doesn't cover. Plurals need every CLDR quantity for the locale (`many` for fr/it/es/pt; `zero`/`one`/`two`/`few`/`many`/`other` for ar). Roles and singer names in `strings_non_translatable.xml` are deliberately untranslated; language names there are in their own script. Active locales are declared in `build.gradle.kts` via `localeFilters`.

## Testing

All tests run on the JVM (no emulator). Most are plain JUnit; ViewModel, `VoiceAnalyzer`, and Room DAO tests use `@RunWith(RobolectricTestRunner::class)` (Robolectric's `AudioRecord` shadow lets `VoiceAnalyzer.start()` genuinely run). `android.util.Log` is stubbed via `testOptions { unitTests { isReturnDefaultValues = true } }` in `build.gradle.kts`.

**Two fixture styles** (both run in the same suite):
- **Kotlin DSL** (`AnalyzerTestFixtures.kt` / `buildSession { … }`) — precise edge-case engineering with controlled Hz values and a fluent builder (`sustainedNote`, `stepUp`, `noisyGlide`, `silenceGap`, `isolatedSpike`, `fadingConfidence`)
- **JSON fixtures** (`app/src/test/resources/fixtures/*.json`) — real-session regression; each frame is `{"hz": …, "confidence": …}` captured from Logcat

To add a JSON fixture: export `VoiceAnalyzer`-tagged Logcat lines, strip everything except `hz` and `confidence`, write the fixture JSON, then register the filename stem in `FixtureRegressionTest.fixtureNames()`. Full workflow is in `app/src/test/CAPTURING.md`.

**Passaggio fixtures** require a specific session structure: stable block below the break → rapid oscillation straddling the break → stable block above. Scale or arpeggio fixtures must set `passaggioNote: null` — an ascending run has no oscillation, so no reliable passaggio can be estimated from it (see `CAPTURING.md`).

Test classes are named after what they cover (`*ClassifyTest`, `*StressTest`, `*ViewModelTest`, …). `AdversarialBreakageTest` holds constructed counterexamples that each `KNOWN_ISSUES.md` fix was built against. When fixing a bug, the established practice is to confirm the new test fails against the pre-fix code (e.g. `git stash` the fix) before trusting it.

## Known limitations

Every numbered issue in `KNOWN_ISSUES.md` (#1–13) is fixed, mitigated, or documented as inherent; read an issue's entry before touching the code it names, since several record rejected alternative fixes and why. The "Inherent architectural limitations" section lists deliberate trade-offs (timbre can't be measured, adjacent-Fach acoustic overlaps, 60 Hz detection floor vs. Contrabass Oktavist, single-frame extremes not counted, vibrato indistinguishable from a register break) — don't "fix" these without reading the rationale.
