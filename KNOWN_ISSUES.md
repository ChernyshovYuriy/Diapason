# Known Issues and Planned Improvements

Findings from architectural and musical review (2026-05-27). Ordered by priority.
Items marked **inherent** are structural limitations of the phone-microphone approach, not bugs.

---

## 1 · Passaggio estimation uses Hz variance, not semitone variance [Medium]

**File:** `FachClassifier.estimatePassaggio()` — `FachClassifier.kt`

The sliding-window variance is computed in raw Hz:

```kotlin
val variance = window.sumOf { (it - mean) * (it - mean) } / windowSize
```

One semitone equals more Hz at higher pitches (~12 Hz at A3, ~48 Hz at A5 — a 4× difference per octave). A voice-break oscillation in the bass register produces far less raw variance than the same relative instability in the soprano register, so for sessions that cover a wide range the max-variance window drifts upward of the actual break.

**Practical effect:** Bass and baritone voices are most affected. A baritone singing an exercise from G2 to G4 may get a passaggio estimate 2–4 semitones above their actual break point.

**Fix:** Convert the time-ordered pitch list to semitones before computing variance, then convert the window mean back to Hz:

```kotlin
fun estimatePassaggio(pitches: List<Float>): Float {
    if (pitches.size < 30) return pitches.average().toFloat()
    val semitones = pitches.map { 12.0 * Math.log(it / 16.3516) / Math.log(2.0) }
    val windowSize = 15
    var maxVariance = 0.0
    var passaggioSemitone = semitones.average()
    for (i in 0..(semitones.size - windowSize)) {
        val window = semitones.subList(i, i + windowSize)
        val mean = window.average()
        val variance = window.sumOf { (it - mean) * (it - mean) } / windowSize
        if (variance > maxVariance) { maxVariance = variance; passaggioSemitone = mean }
    }
    return (16.3516 * Math.pow(2.0, passaggioSemitone / 12.0)).toFloat()
}
```

**Tests to update:** `PassaggioEdgeCaseTest`, `AnalyzerScenarioTest` (passaggio scenarios), all five JSON fixture assertions that declare `passaggioNote`.

---

## 2 · Male voice passaggio values are ~1 semitone low in FachData [Low–medium]

**File:** `FachData.kt`, `ALL_FACH`

Comparison against standard Fach literature (Kloiber / Maehder / Melchert, *Handbuch der Oper*):

| Voice type | App value | Literature median |
|---|---|---|
| Lyric Tenor | D#4 · 311 Hz | E4 · 330 Hz |
| Spinto Tenor | D4 · 294 Hz | D#4 · 311 Hz |
| Heldentenor | C#4 · 277 Hz | D4 · 294 Hz |
| Lyric Baritone | A3 · 220 Hz | Bb3 · 233 Hz |
| Kavalierbariton | G#3 · 207 Hz | A3 · 220 Hz |
| Dramatic Baritone | G3 · 196 Hz | G#3 · 207 Hz |
| Bass-Baritone | F#3 · 185 Hz | G#3 · 207 Hz |
| Basso Cantante | F3 · 175 Hz | F#3–G3 · 185–196 Hz |

Female voice values (Coloratura through Dramatic Soprano) are standard.

All existing `FachDataIntegrityTest` constraints (passaggio within tessitura, tessitura within range) are currently satisfied and will remain satisfied after raising values by 1 semitone — verify this after the change.

**Tests to update:** Any `AdjacentFachDiscriminationTest` cases that hard-code passaggio Hz values to match the current table.

---

## 3 · Minimum sample gate of 20 frames is low for reliable classification [Low]

**File:** `VoiceAnalyzer.stop()` line 139; `SessionReplay.buildProfile()`

At ~160 ms per accepted frame, 20 frames = ~3.2 seconds of high-confidence singing. The P20/P80 comfortable-range estimate rests on only 4 data points at each tail. A user who sings for 6–7 seconds with some frames filtered by the confidence gate can receive a classification from a statistically fragile profile.

The passaggio algorithm already enforces a separate 30-frame minimum (`if (pitches.size < 30) return pitches.average()`). Raising the primary gate to 40 frames (~7 seconds of good singing) eliminates the worst-case profiles while remaining well below the 30–45 second session the guide recommends.

**Fix:**
1. Change `if (pitchSamples.size < 20)` → `< 40` in `VoiceAnalyzer.stop()`.
2. Change `if (pitches.size < 20) return null` → `< 40` in `SessionReplay.buildProfile()`.
3. Update `TOO_FEW_VALID_SAMPLES` fixture in `AnalyzerTestFixtures.kt` (currently 10 frames) and `AnalyzerScenarioTest` to reflect the new threshold.

---

## 4 · "Confidence" label on the results screen is a score ratio, not a probability [Low]

**File:** `ResultsScreen.kt`, `ConfidenceBar` composable; `TopMatchCard`

`match.score / match.maxScore * 100%` is displayed as "Confidence: 79%." This is the profile-match score, not a Bayesian confidence. When the top two matches differ by 1 point (e.g. 11/14 vs 10/14 = 79% vs 71%) the display implies more certainty than the algorithm can support.

**Fix options:**
- Relabel to "Match score" or "Profile match."
- Show the margin to the runner-up (`#1 — 79%  |  #2 — 71%`), which gives the user meaningful uncertainty information.

---

## 5 · HzToNoteNameTest class docblock documents the wrong rounding mode [Cosmetic]

**File:** `HzToNoteNameTest.kt`, line 18

The docblock states "The implementation uses truncating integer conversion (`toInt()`), which floors positive MIDI values." The implementation uses `roundToInt()` (standard rounding). All test frequencies are exact equal-temperament values that yield integer MIDI numbers, so rounding and truncation produce the same result — which is why the tests pass despite the wrong description.

**Fix:** Change the docblock sentence to: "The implementation uses `roundToInt()` (standard rounding to nearest integer)."

---

## Inherent architectural limitations

These are not bugs but constraints of the phone-microphone approach. The Guide and Results screens already communicate them.

- **Timbre and vocal weight** cannot be captured by a microphone. Distinctions between adjacent types (Lyric vs. Spinto Soprano; Spinto vs. Dramatic Tenor) rely on vocal color that requires a trained human ear.
- **Spinto Tenor / Dramatic Tenor** and **Lyric Baritone / Kavalierbariton** score 14/14 against each other's reference parameters. These are genuine acoustic overlaps, documented in `AdjacentFachDiscriminationTest`.
- **Passaggio detection accuracy** depends on exercise structure. A scale or arpeggio fixture cannot produce a reliable passaggio estimate (see `CAPTURING.md`). Users who sing freely rather than following the guide will get less reliable results.
