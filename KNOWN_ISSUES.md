# Known Issues and Planned Improvements

Findings from architectural and musical review (2026-05-27). Ordered by priority.
Items marked **inherent** are structural limitations of the phone-microphone approach, not bugs.

---

## 1 · Passaggio estimation uses Hz variance, not semitone variance [FIXED 2026-09-01]

**File:** `FachClassifier.estimatePassaggio()` — `FachClassifier.kt`

~~The sliding-window variance was computed in raw Hz~~ — one semitone equals more Hz at higher pitches (~12 Hz at A3, ~48 Hz at A5 — a 4× difference per octave), so a voice-break oscillation in the bass register produced far less raw variance than the same relative instability in the soprano register, biasing the max-variance window upward of the actual break for sessions covering a wide range.

**A second, deeper bug was found while fixing this** (adversarial testing, 2026-09-01, requested explicitly by the user after re-opening this issue): converting to semitone space alone wasn't enough. Plain variance — in Hz *or* semitones — can't tell a genuine wobble (many small back-and-forth moves near the break) from one clean jump between two unrelated held notes elsewhere in the session, because variance only measures spread, not order. A constructed test proved the algorithm could pick a stable-to-stable transition edge over a real oscillating break entirely. See `AdversarialBreakageTest.kt`.

**Fix (implemented):** `estimatePassaggio()` now runs in semitone space and scores each sliding window on two order-sensitive signals instead of variance alone:
- **reversals** — how many times the frame-to-frame direction actually flips (a flat run or single step has zero; a real wobble has many), weighted exponentially (`2.0.pow(reversals)`) since a window that only *partially* overlaps a real wobble still reverses on nearly every frame and needs to be clearly outscored by one that oscillates throughout.
- **magnitude** — the *median* (not variance) of the qualifying frame-to-frame moves, which is robust to the one large delta a contaminating sample from a neighboring stable block introduces, unlike variance-from-mean which that single outlier would inflate.

A flat run or single step reduces to exactly the old variance-like behavior (one qualifying delta, median-of-one is just that value), which is what keeps the existing no-oscillation fallback tests (`PassaggioEdgeCaseTest` P1/P2/P4/P5, the bimodal chest/head gap case) passing unchanged. Full mechanism and rationale is in the code comment above `estimatePassaggio()`.

**Verified against:** the full existing suite (`PassaggioEdgeCaseTest`, `AnalyzerScenarioTest`'s noisy-glide scenarios, and — the one real hardware-captured ground truth in the whole suite — `FixtureRegressionTest`'s `mezzo_passaggio_exercise.json`), plus new adversarial cases in `AdversarialBreakageTest.kt` covering the original failure, the same failure with session order reversed, and a much larger unrelated jump (24 semitones) next to a noisier genuine break, to confirm the fix isn't curve-fit to one example.

---

## 2 · Male voice passaggio values are ~1 semitone low in FachData [FIXED 2026-09-01]

**File:** `FachData.kt`, `ALL_FACH`

Comparison against standard Fach literature (Kloiber / Maehder / Melchert, *Handbuch der Oper*) found 8 male voice types ~1 semitone low. All 8 were raised to the literature value:

| Voice type | Old value | New value |
|---|---|---|
| Lyric Tenor | D#4 · 311 Hz | E4 · 330 Hz |
| Spinto Tenor | D4 · 294 Hz | D#4 · 311 Hz |
| Heldentenor | C#4 · 277 Hz | D4 · 294 Hz |
| Lyric Baritone | A3 · 220 Hz | Bb3 · 233 Hz |
| Kavalierbariton | G#3 · 207 Hz | A3 · 220 Hz |
| Dramatic Baritone | G3 · 196 Hz | G#3 · 207 Hz |
| Bass-Baritone | F#3 · 185 Hz | G#3 · 207 Hz |
| Basso Cantante | F3 · 175 Hz | F#3 · 185 Hz (literature gives F#3–G3, 185–196 Hz, as a range; used the lower bound for consistency with the ~1-semitone correction applied to the other rows) |

Female voice values (Coloratura through Dramatic Soprano) were already standard, unchanged. `FachDataIntegrityTest`'s passaggio-within-tessitura/range constraints were verified to still hold for all 8 rows. `AdjacentFachDiscriminationTest`'s hardcoded Lyric Tenor / Spinto Tenor / Basso Cantante perfect-match literals were updated to match; the Lyric Baritone/Kavalierbariton tie test already read `fach.passaggioHz` live and needed no change.

Note: Bass-Baritone and Dramatic Baritone now land on the identical reference value (G#3 · 207 Hz) per the doc's own table — left as-is rather than second-guessed, since adjacent Fach types sharing a passaggio reference is already a documented, accepted pattern elsewhere (see "Inherent architectural limitations" below).

---

## 3 · Minimum sample gate of 20 frames is low for reliable classification [FIXED 2026-09-01]

**File:** `VoiceAnalyzer.stop()`; `SessionReplay.buildProfile()` (test-only mirror)

At ~160 ms per accepted frame, 20 frames = ~3.2 seconds of high-confidence singing gave only 4 data points at each P20/P80 tail — a statistically fragile profile.

Raised to `MIN_ACCEPTED_SAMPLES = 40` (~7 seconds) in `VoiceAnalyzer.stop()`, and the mirrored `< 20` check in the test-only `SessionReplay.buildProfile()`. Updated `TOO_FEW_VALID_SAMPLES`'s doc comment, `LONG_SILENCE_GAPS` (bumped from 30 to 45 voiced frames so it stays above the new floor), and the 19/20-sample boundary tests in `AnalyzerInvariantTest` (now 39/40). All 5 real JSON fixtures already declare `minAcceptedFrames >= 50`, comfortably above the new floor, so no fixture data needed changing.

**Product tradeoff, not just a technical one:** a BigQuery usage review (2026-09-01) found ~44% of everyone who opens the app never completes an analysis at all. This change makes some previously-successful 20–39-frame sessions newly fail with "insufficient samples" — trading classification reliability for a (currently unmeasured) increase to that existing top-of-funnel gap. Applied on explicit user instruction; worth watching the `analysis_insufficient` event rate after this ships.

---

## 4 · "Confidence" label on the results screen is a score ratio, not a probability [FIXED 2026-09-01]

**File:** `ResultsScreen.kt`, `ConfidenceBar` composable; `TopMatchCard`; `strings.xml` (all 5 locales)

`match.score / match.maxScore * 100%` was displayed as "Confidence: 79%," implying more statistical certainty than a profile-match ratio supports.

Went with the simple relabel option (not the margin-to-runner-up option, which would have required threading the full ranked results into `TopMatchCard` instead of just the top match): `results_confidence_label` changed from "Classification confidence" to "Match score" in `values/strings.xml`, with equivalent wording changes in `values-fr/it/es/pt`. `guide_step5_body`'s "confidence score" prose updated to "match score" in all 5 locales for consistency. No layout or data-flow changes — `TopMatchCard` already had everything it needed for a pure string change.

---

## 5 · HzToNoteNameTest class docblock documents the wrong rounding mode [FIXED 2026-09-01]

**File:** `HzToNoteNameTest.kt`, line 18

The docblock stated "The implementation uses truncating integer conversion (`toInt()`), which floors positive MIDI values." The implementation uses `roundToInt()` (standard rounding). All test frequencies were exact equal-temperament values yielding integer MIDI numbers, so rounding and truncation produced the same result — which is why the tests passed despite the wrong description; the rounding path itself, specifically the tie-break rule exactly halfway between two notes, was never exercised by any of them.

**Fix:** corrected the docblock sentence to describe `roundToInt()`, and — while closing coverage gaps found in a second, separate audit pass — added tests that actually exercise the rounding behavior: `roundToInt()`'s tie-break rule (an exact .5 tie rounds toward positive infinity) tested directly, plus the finest boundary `hzToNoteName` can actually be asked to cross (a genuine bit-exact IEEE tie was checked empirically and found unreachable via a real `Float` Hz input at this precision — the closest constructible round-trip lands at 60.49999993811784, and the next representable `Float` jumps straight past 60.5).

---

## 6 · Ceiling/floor scoring weight favored the more fakeable signal [FIXED 2026-09-01]

**File:** `FachClassifier.classify()`

Not from the original 2026-05-27 review — raised during the same-day adversarial-testing session as a pedagogy judgment call. The scorer weighted the upper-ceiling match 0–3 pts (finer, 3-tier bands: 90/80/70%) but the lower-floor match only 0–2 pts (coarser, 2-tier bands: 85/70%). A voice teacher would argue this is backwards: the top of a singer's range is the most trainable/fakeable signal in a single phone-mic take (falsetto/head-voice stretch), while the lowest comfortably-produced pitch (real chest-voice depth) is a more stable Fach indicator.

**Fix:** Swapped which formula applies to which measurement — floor now gets the finer 3-tier scoring (0–3 pts), ceiling gets the coarser 2-tier scoring (0–2 pts). Total stays 14 (no `maxScore`/UI/schema changes needed). All 7 boundary tests in `AdjacentFachDiscriminationTest`'s TIER 2 section were rewritten to test the new tier boundaries (floor: 110/120/130%; ceiling: 115/130%) rather than the old ones — several of the old tests' numeric assertions would have kept passing by coincidence (a floor gain offsetting a ceiling loss) while asserting the wrong reasoning, so they were rewritten rather than left as silently-still-green but misleading.

---

## 7 · Contrabass Oktavist's floor is below the app's detection limit [MITIGATED 2026-09-01]

**File:** `FachClassifier.classify()`; `VoiceAnalyzer.kt` (`MIN_PITCH_HZ`, now `internal`)

Found during an adversarial audit pass. `VoiceAnalyzer` discards every sample below
`MIN_PITCH_HZ = 60 Hz`, but Contrabass Oktavist's `rangeMinHz` is 43 Hz — a real,
literature-grounded value for this voice type (Russian Orthodox liturgical basses
singing an octave below the bass line), not an error. No session can ever produce
a `detectedMinHz` below 60 Hz, so the floor ratio (`detectedMinHz / 43`) could never
land closer than ≈1.4 — outside even the loosest tolerance band — meaning this one
Fach scored 0/3 on the floor dimension for every physically possible session,
regardless of how well a real Oktavist sang.

**This is a mitigation, not a fix — the underlying measurement gap is untouched.**
Three directions were weighed against what a professional singer/voice teacher
would actually want, since this is a singers' app:
- Lowering `MIN_PITCH_HZ` globally to reach 43 Hz was rejected: 50/60 Hz mains hum
  sits almost exactly in the 43–60 Hz gap and is a clean, high-confidence periodic
  signal YIN would happily report as a real pitch — a noise-robustness cost paid by
  every user to serve the single rarest classification in the whole table.
- Quietly raising Contrabass Oktavist's `rangeMinHz` to something the mic can reach
  was rejected: that misrepresents the voice type with a number grounded in the
  tool's limitation rather than vocal literature, echoing the opposite (correct)
  call already made for issue #2 above.
- Instead: both numbers stay honest, and `classify()` stops treating "the mic
  couldn't measure that low" as evidence *against* the Fach. A detected floor
  sitting at the sensor's own hard limit (within 2 semitones of `MIN_PITCH_HZ`) now
  scores as inconclusive (+2, the "near" tier) instead of "far" (0) — but only for
  a Fach whose own range floor is itself below `MIN_PITCH_HZ`; every other Fach,
  and even Oktavist itself when the detected floor is clearly elsewhere, keep
  today's ratio-tier scoring unchanged. `MIN_PITCH_HZ` was changed from `private`
  to `internal` so `FachClassifier` reads the same real constant `VoiceAnalyzer`
  gates on, rather than duplicating the literal.

**Verified against:** the full suite, plus six new targeted tests in
`FachClassifierClassifyTest` — Oktavist scores inconclusive (13/14) with the floor
at the sensor limit and again at the exact 2-semitone tolerance boundary
(`MIN_PITCH_HZ * 1.1225f`, computed the same way production does rather than a
hand-typed literal); falls through to the normal 0-point tier both with a floor
clearly elsewhere (150 Hz) and just past that same boundary; Basso Profundo
(whose own 65 Hz floor *is* measurable) scores via the normal tiers at a 60 Hz
detected floor, confirming the special case doesn't leak into fachs that don't
need it; and a whole-profile ranking check confirms a genuine Basso Profundo
session (detected floor 62 Hz, inside Oktavist's trigger zone) still ranks Basso
Profundo first — the mitigation can't flip a ranking on its own when every other
dimension points elsewhere.

**Still open, by design:** the app genuinely cannot verify a singer's true floor
below 60 Hz. A true Oktavist will never see their full historical range confirmed
by this tool — only that the floor dimension no longer counts against them for it.

---

## 8 · `estimatePassaggio` cannot reliably tell a register break from ordinary vibrato [Inherent]

**File:** `FachClassifier.estimatePassaggio()`

Found during a second, separate adversarial audit pass, aimed specifically at the
reversal+median fix from issue #1 above. That fix scores a sliding window on
`magnitude² × 2^reversals` — direction-reversal count weighted exponentially — to
tell a genuine wobble apart from a single clean jump. It does **not** tell a
genuine wobble apart from a *controlled* wobble: ordinary vocal vibrato is, by
definition, a small, fast, regularly-alternating pitch oscillation — exactly the
shape the algorithm is built to reward. A constructed session (stable block, real
196/220 Hz register-break oscillation, stable block, a *single held note*
decorated with ordinary ±1-semitone vibrato, stable block) had the vibrato note
outscore the genuine break outright.

**Why this is inherent, not a fixable bug:** three pitch-only mitigations were
tried and rejected, each for a concrete reason rather than difficulty:
- **Oscillation-rate discrimination** (downweight windows oscillating at a typical
  5–7 Hz vibrato rate) — defeated by the app's own sampling rate. At ~160 ms/frame
  (≈6.25 fps), the Nyquist limit is ~3.1 Hz, *below* typical vibrato rate. Real
  vibrato in that range is structurally aliased: the exact same true vibrato can
  sample as anything from flat to perfectly-alternating-every-frame depending on
  phase, so "does this window oscillate at vibrato rate" cannot be answered
  reliably from this data.
- **Session-relative instability threshold** (only count a window as a break
  candidate if it's meaningfully more unstable than the rest of the session) —
  doesn't discriminate the actual failure case: most of a real session is flat
  either way, so the real break and a vibrato'd note both clear a session-wide
  baseline equally and remain in direct, unresolved competition.
- **Before/after net pitch drift** (prefer windows where the surrounding stable
  blocks differ meaningfully in mean pitch, since a genuine register transition
  moves from one resting pitch to another while a decorated note returns to
  roughly the same one) — would false-positive on ordinary ascending/descending
  scale and arpeggio exercises, where every window's surroundings differ in mean
  pitch by construction.

A real fix needs a signal the app doesn't currently capture at all — amplitude/RMS
energy per frame, since a genuine break is often accompanied by a dip or catch in
loudness that controlled vibrato isn't, or a much higher pitch-sampling rate to
make rate-based discrimination viable without aliasing. Both are substantial,
cross-cutting projects (new field through `VoiceAnalyzer`'s audio loop, the
`PitchSample`/DSL fixture model, the JSON fixture format in `CAPTURING.md`, and a
**freshly re-captured** real hardware fixture, since the existing
`mezzo_passaggio_exercise.json` has no amplitude data) — not follow-up fixes to
issue #1.

**Mitigation shipped instead — coaching, not code:** this is exactly the situation
a voice teacher already has a standard answer for. When diagnosing a register
break in person, a teacher asks the student to sing **straight tone, no
vibrato** — specifically because vibrato and register instability can look and
sound alike. `guide_step4_body` (all 5 locales) now asks the user to do the same
during the range-covering exercise this app uses to locate the passaggio.

---

## 9 · A double-tap on Start could desync the visible sample counter [FIXED 2026-09-01]

**File:** `AnalyzeViewModel.startRecording()`, `WarmUpComparisonViewModel.startBaseline()`/`startRetest()`

Found during a second, separate adversarial audit pass. `VoiceAnalyzer.start()` already
guards against being called while already running (`if (isRunning) return`, before it
clears its sample buffer or touches `AudioRecord`), but none of the three ViewModel call
sites mirrored that guard. A redundant call — e.g. a double-tap on Start before the UI
visually responds — still reset the ViewModel's own observable state to a fresh
`Recording(sampleCount = 0)` / `Baseline(sampleCount = 0)` / `Retest(sampleCount = 0)`,
even though the real, still-running analyzer kept accumulating from the original
session untouched. The eventual classification was unaffected (it's built from the
real underlying buffer, not the UI counter), but the live counter shown to the user
during recording would desync from reality until it caught back up.

**Fix:** added the identical `if (analyzer.isRunning) return` guard to all three call
sites, as the very first line before any state reset or analytics event.

**Verified against:** the full suite, plus 4 new regression tests — 2 in a new
`AnalyzeViewModelTest` (previously the only untested ViewModel in the app) and 2 added
to the existing `WarmUpComparisonViewModelTest`. All 4 were confirmed to actually fail
against the pre-fix code (via a temporary `git stash` of the fix, not just written and
assumed correct) before being locked in as regression guards. Testing this required
checking, rather than assuming, that `VoiceAnalyzer.start()` can genuinely succeed
under this suite's Robolectric setup — `CLAUDE.md`'s "pure JVM, no Robolectric" claim
is stale; `WarmUpComparisonViewModelTest`, `HistoryViewModelTest`, and `SessionDaoTest`
already use `RobolectricTestRunner`, and its `AudioRecord` shadow reports
`STATE_INITIALIZED`, letting `analyzer.isRunning` genuinely become `true` in a test.

---

## 10 · Live numeric readouts used default-locale formatting [FIXED 2026-09-01]

**File:** `AnalyzeScreen.kt`, `CompareRecordScreen.kt`, `WarmUpTimerScreen.kt`, `FachClassifier.hzToNoteName()`

Found during a second, separate adversarial audit pass. Four `.format()` calls with
numeric conversions (`%.1f`, `%d:%02d`, `%.0f`) had no explicit `Locale`, so Java's
`Formatter` applied the JVM's *default* locale's digit glyphs and decimal separator —
not just its punctuation. Verified empirically in this project's own JVM (not assumed):
`"%.1f".format(440.0f)` under a Persian (`fa`) default locale renders `"۴۴۰٫۰"` — Eastern
Arabic-Indic digits *and* the Arabic decimal separator, not a period. Two of the four
sites are the live pitch readout shown during every single recording
(`AnalyzeScreen`/`CompareRecordScreen`), one is the warm-up countdown timer
(`WarmUpTimerScreen`), and one is `hzToNoteName`'s rarely-reached out-of-MIDI-range
fallback. Since the app added Persian support (`1c9528c`), this was live and reachable,
not theoretical.

**Fix:** added `Locale.ROOT` to all four `.format()` calls — these are numbers in an
otherwise Latin-script UI, not localized prose, so digit glyphs must not vary with the
device's language setting (unlike a genuinely localized decimal separator, e.g.
`fr`/`it`/`es`/`pt`'s comma, which is correct behavior and untouched here).

Two of the four call sites (`AnalyzeScreen.kt`, `CompareRecordScreen.kt`) were inline
expressions inside `@Composable` functions with no test seam; extracted each into a
small `internal fun formatHz(hz: Float): String`, matching the pattern
`WarmUpTimerScreen.kt`'s own `formatTime()` already used, so both could be unit-tested
directly instead of only reasoned about. `formatTime()` itself was `private` and was
changed to `internal` for the same reason.

**Verified against:** the full suite, plus 5 new tests across `HzToNoteNameTest`,
`AnalyzeScreenTest`, `CompareRecordScreenTest` (new files), and `WarmUpTimerScreenTest`
(new file) — each asserting the formatted output stays Western-digit under a Persian
default locale. Confirmed all of them actually fail against the pre-fix code: the
`hzToNoteName` one with a genuine locale-mismatch assertion failure, and the three
extraction-dependent ones by the stronger signal of not compiling at all without the
fix (`formatHz` didn't exist yet; `formatTime` was inaccessible).

---

## 11 · `ComparisonResult`'s passaggio gate was tied to the wrong constant [FIXED 2026-09-01]

**File:** `ComparisonResult.compute()`, `FachClassifier.estimatePassaggio()`

Found during a second, separate adversarial audit pass. `ComparisonResult.compute()`
omitted the passaggio delta when either session had fewer than a hardcoded `30`
samples — but that literal predates issue #3 above, which raised
`VoiceAnalyzer.MIN_ACCEPTED_SAMPLES` from 20 to 40. Since every real `VoiceProfile`
now has `sampleCount >= 40 > 30`, the check was always true and the `else null` branch
was dead in production — not wrong, just vacuous, with a doc comment describing a
state that could no longer occur.

The real bug wasn't the number being stale — 30 was still numerically correct — it
was that it was tied to the **wrong constant's job**. `VoiceAnalyzer.MIN_ACCEPTED_SAMPLES`
answers "is there enough data for a profile at all"; `estimatePassaggio()`'s own
(previously bare, unnamed) `30`-sample threshold answers a different question — "was
the real windowed passaggio algorithm used, or just a plain average" — and that second
question is what `ComparisonResult` actually needs to know before showing a
before/after passaggio comparison. The two constants only *happened* to agree because
40 > 30; had they diverged the other way, `ComparisonResult` would have shown a
comparison built from at least one side's plain-average fallback.

**Fix:** extracted `estimatePassaggio()`'s bare `30` into `FachClassifier.PASSAGGIO_MIN_SAMPLES`
(`internal`, not `private`), and pointed `ComparisonResult.compute()` at that same
constant instead of its own hardcoded `30`. No behavior change today (still 30, still
vacuously satisfied by every real profile) — the value is that if either threshold
ever moves independently again, `ComparisonResult` stays correct because it's now
asking the right question rather than duplicating a number that happened to match.

**Verified against:** the full suite. `ComparisonResultTest`'s 4 existing
passaggio-threshold tests were updated to reference `FachClassifier.PASSAGGIO_MIN_SAMPLES`
at the exact boundary (and one below it) instead of a hardcoded `30`/`29`, so they can't
silently drift from the real constant again. No new test was needed to prove a
behavior change, since this is a consistency fix with an unchanged numeric outcome,
not a wrong-result bug.

---

## 12 · `estimateComfortableRange`'s percentile index truncated instead of rounding [FIXED 2026-09-01]

**File:** `FachClassifier.estimateComfortableRange()`

Found during a second, separate adversarial audit pass. `sorted[(sorted.size * 0.20).toInt()]`
used `.toInt()`, which truncates (floors) rather than rounds. Truncation always rounds
*down*, so for a non-round-by-5/10 sample count both P20 and P80 land slightly below
their intended percentile — e.g. for 13 samples, P20's index is `0.20 * 13 = 2.6`;
truncating always gives 2 (15.4% of the data below it, undershooting the intended 20%),
while rounding correctly gives 3 (23.1% below, closer to 20%). Every existing test in
the suite happened to use a sample count divisible by 5 or 10, where truncation and
rounding agree, which is why this went unnoticed.

**Fix:** changed both indices from `.toInt()` to `.roundToInt()`. No sample count used
anywhere else in the suite was affected (verified — full suite green unchanged); the
fix only changes behavior for non-round-by-5/10 sample counts, which real sessions can
easily have (accepted-sample counts depend on how long and how confidently someone sang).

**Verified against:** the full suite, plus 2 new tests in `FachClassifierTest`
constructing lists where truncation and rounding disagree (13 samples for P20, 17 for
P80) and asserting the rounded index wins. Confirmed both fail against the pre-fix
code via a targeted mutation (reverted to `.toInt()`, confirmed exactly those 2 tests
failed, then restored).

---

## 13 · `HzDelta.isMeaningful`'s flat percentage was an asymmetric proxy for a semitone [FIXED 2026-09-01]

**File:** `ComparisonResult.kt` — `HzDelta.isMeaningful`, `comfortableRangeWidened`, `detectedRangeWidened`

Found during a second, separate adversarial audit pass. `isMeaningful` used a flat
`>= 5.9%` threshold as a proxy for "at least one semitone." A semitone is a fixed
*ratio* (2^(1/12) ≈ 1.0595), not a fixed percentage, so the same flat threshold
corresponds to different true semitone distances depending on direction: a −5.9%
change is ≈1.05 true semitones, but a +5.9% change is only ≈0.99 true semitones —
just under a full semitone. Verified numerically: a +5.925% rise (400 → 423.7 Hz)
clears the old flat threshold but is only ~0.997 true semitones, meaning the old
formula called a sub-semitone change "meaningful."

Separately, `comfortableRangeWidened` and `detectedRangeWidened` re-derived the same
flat-percentage formula independently rather than calling `HzDelta.isMeaningful` —
duplicating both the asymmetry bug and missing `isMeaningful`'s zero/negative-Hz guard.

**Fix:** `isMeaningful` now computes the true semitone distance
(`12 * log2(afterHz / beforeHz)`) and compares against `1.0`, matching the semitone-space
math already standard elsewhere in this app (`FachClassifier`). `comfortableRangeWidened`
and `detectedRangeWidened` now call `isMeaningful` directly instead of duplicating the
formula, so the two can no longer drift out of sync and both inherit the guard against
non-positive Hz values for free.

**Verified against:** the full suite (all existing `isMeaningful`/`*RangeWidened` tests
already used magnitudes comfortably inside or outside the ~1-semitone mark, so none were
sensitive to the old formula's specific asymmetry and all still pass unchanged), plus 2
new tests: the +5.925%-rise case above, and a negative-`beforeHz` guard case the old
`!= 0f` check didn't cover. Confirmed both fail against the pre-fix code via a targeted
mutation, then restored.

---

## Inherent architectural limitations

These are not bugs but constraints of the phone-microphone approach. The Guide and Results screens already communicate them.

- **Timbre and vocal weight** cannot be captured by a microphone. Distinctions between adjacent types (Lyric vs. Spinto Soprano; Spinto vs. Dramatic Tenor) rely on vocal color that requires a trained human ear.
- **Spinto Tenor / Dramatic Tenor** and **Lyric Baritone / Kavalierbariton** score 14/14 against each other's reference parameters. These are genuine acoustic overlaps, documented in `AdjacentFachDiscriminationTest`.
- **Passaggio detection accuracy** depends on exercise structure. A scale or arpeggio fixture cannot produce a reliable passaggio estimate (see `CAPTURING.md`). Users who sing freely rather than following the guide will get less reliable results.
- **Contrabass Oktavist's true floor (down to 43 Hz in the literature) can never be confirmed by this app** — `VoiceAnalyzer`'s 60 Hz detection floor is below it, and lowering that floor globally would trade mains-hum robustness for every user to serve the rarest classification in the table (see issue #7 above for the scoring-side mitigation; the detection gap itself is not fixable without a different microphone/DSP approach).
- **A detected extreme needs ≥2 corroborating frames (~320 ms) within 2 semitones of each other** — `FachClassifier.estimateDetectedExtremes()`'s neighbor-validation rejects any pitch with no same-side neighbor, regardless of which side it's on. A fast glissando that touches the true floor or ceiling for only a single frame will never register as the detected extreme; if that happens at *both* ends of a session, `detectedMinHz` and `detectedMaxHz` collapse to the same value (the one cluster that *did* get corroborated), even though the singer's real range was much wider. This was investigated as a possible bug (2026-09-01): a fix that falls back to the raw min/max whenever the validated result collapses was tried and reverted, because it broke two pre-existing, deliberately-asserted cases in `EstimateDetectedExtremesStressTest` (`one isolated high outlier is rejected…`, `two isolated outliers at both extremes — both rejected`) — those collapse to the same shape (one genuine cluster + isolated junk) and rely on being rejected, not un-collapsed. Nothing in the collapsed `(min, max)` pair distinguishes "one real cluster, N isolated outliers" from "two real-but-once-sampled extremes around a real cluster" — telling them apart would need information the algorithm doesn't have (frame timing/duration, or a dedicated glissando detector), so this is a deliberate trade-off, not a fixable bug. Evidence: `AdversarialBreakageTest.kt`, `isolated outliers at both ends collapse the range to the only corroborated pitch`. Practical mitigation, if ever revisited: coach users (Guide screen copy) to hold extreme notes for at least ~1/3 second rather than touching them briefly.

- **When NO sample in a session has any same-side neighbor at all, neighbor-validation is bypassed entirely, not collapsed** — a related but distinct shape from the collapse case above. If every pairwise gap between samples exceeds 2 semitones (e.g. a fast arpeggio/glissando where each note is captured for exactly one frame and none repeat), `firstOrNull{hasNeighbor}`/`lastOrNull{hasNeighbor}` both return null and the function falls back to the raw, completely unvalidated `sorted.first()`/`sorted.last()` — the opposite of "prevents a single stray high-confidence frame from claiming the floor or ceiling." Found during a second, separate adversarial audit pass (2026-09-01); `EstimateDetectedExtremesStressTest`'s existing `all-isolated worst case` test only ever checked that production agrees with an equally-flawed O(n²) reference implementation, never whether the shared result was actually sane — both implementations share this exact gap, so agreement proved nothing about correctness. Not fixed: the one architecturally sound fix (treat temporal adjacency — frames close together in *time* — as a second, independent form of corroboration alongside value-adjacency, so a genuine fast musical gesture can be told apart from a single spurious frame) is a rewrite of comparable scope and risk to `estimatePassaggio`'s reversal/median fix, and this shape is rare in practice: it requires a session where the singer never holds a single note anywhere, directly contradicting the Guide's own "hold each note for at least 2–3 seconds" instruction (`guide_step3_body`), and YIN's confidence tends to drop during genuinely fast pitch movement anyway, making the isolated-high-confidence-single-frame shape harder to produce with a real voice than to construct synthetically. Evidence: `AdversarialBreakageTest.kt`, `when nothing in the session has any neighbor, validation is completely bypassed`; `EstimateDetectedExtremesStressTest.kt`'s `all-isolated worst case produces same result as reference`, now asserting the actual bypass values rather than only cross-implementation agreement.
