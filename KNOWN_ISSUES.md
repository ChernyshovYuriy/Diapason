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

## 5 · HzToNoteNameTest class docblock documents the wrong rounding mode [Cosmetic]

**File:** `HzToNoteNameTest.kt`, line 18

The docblock states "The implementation uses truncating integer conversion (`toInt()`), which floors positive MIDI values." The implementation uses `roundToInt()` (standard rounding). All test frequencies are exact equal-temperament values that yield integer MIDI numbers, so rounding and truncation produce the same result — which is why the tests pass despite the wrong description.

**Fix:** Change the docblock sentence to: "The implementation uses `roundToInt()` (standard rounding to nearest integer)."

---

## 6 · Ceiling/floor scoring weight favored the more fakeable signal [FIXED 2026-09-01]

**File:** `FachClassifier.classify()`

Not from the original 2026-05-27 review — raised during the same-day adversarial-testing session as a pedagogy judgment call. The scorer weighted the upper-ceiling match 0–3 pts (finer, 3-tier bands: 90/80/70%) but the lower-floor match only 0–2 pts (coarser, 2-tier bands: 85/70%). A voice teacher would argue this is backwards: the top of a singer's range is the most trainable/fakeable signal in a single phone-mic take (falsetto/head-voice stretch), while the lowest comfortably-produced pitch (real chest-voice depth) is a more stable Fach indicator.

**Fix:** Swapped which formula applies to which measurement — floor now gets the finer 3-tier scoring (0–3 pts), ceiling gets the coarser 2-tier scoring (0–2 pts). Total stays 14 (no `maxScore`/UI/schema changes needed). All 7 boundary tests in `AdjacentFachDiscriminationTest`'s TIER 2 section were rewritten to test the new tier boundaries (floor: 110/120/130%; ceiling: 115/130%) rather than the old ones — several of the old tests' numeric assertions would have kept passing by coincidence (a floor gain offsetting a ceiling loss) while asserting the wrong reasoning, so they were rewritten rather than left as silently-still-green but misleading.

---

## Inherent architectural limitations

These are not bugs but constraints of the phone-microphone approach. The Guide and Results screens already communicate them.

- **Timbre and vocal weight** cannot be captured by a microphone. Distinctions between adjacent types (Lyric vs. Spinto Soprano; Spinto vs. Dramatic Tenor) rely on vocal color that requires a trained human ear.
- **Spinto Tenor / Dramatic Tenor** and **Lyric Baritone / Kavalierbariton** score 14/14 against each other's reference parameters. These are genuine acoustic overlaps, documented in `AdjacentFachDiscriminationTest`.
- **Passaggio detection accuracy** depends on exercise structure. A scale or arpeggio fixture cannot produce a reliable passaggio estimate (see `CAPTURING.md`). Users who sing freely rather than following the guide will get less reliable results.
- **A detected extreme needs ≥2 corroborating frames (~320 ms) within 2 semitones of each other** — `FachClassifier.estimateDetectedExtremes()`'s neighbor-validation rejects any pitch with no same-side neighbor, regardless of which side it's on. A fast glissando that touches the true floor or ceiling for only a single frame will never register as the detected extreme; if that happens at *both* ends of a session, `detectedMinHz` and `detectedMaxHz` collapse to the same value (the one cluster that *did* get corroborated), even though the singer's real range was much wider. This was investigated as a possible bug (2026-09-01): a fix that falls back to the raw min/max whenever the validated result collapses was tried and reverted, because it broke two pre-existing, deliberately-asserted cases in `EstimateDetectedExtremesStressTest` (`one isolated high outlier is rejected…`, `two isolated outliers at both extremes — both rejected`) — those collapse to the same shape (one genuine cluster + isolated junk) and rely on being rejected, not un-collapsed. Nothing in the collapsed `(min, max)` pair distinguishes "one real cluster, N isolated outliers" from "two real-but-once-sampled extremes around a real cluster" — telling them apart would need information the algorithm doesn't have (frame timing/duration, or a dedicated glissando detector), so this is a deliberate trade-off, not a fixable bug. Evidence: `AdversarialBreakageTest.kt`, `isolated outliers at both ends collapse the range to the only corroborated pitch`. Practical mitigation, if ever revisited: coach users (Guide screen copy) to hold extreme notes for at least ~1/3 second rather than touching them briefly.
