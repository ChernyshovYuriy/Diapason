# Capturing and Sanitizing Analyzer Traces for Test Fixtures

This guide explains how to record a real vocal session from a development build,
export the pitch data from Logcat, sanitize it for privacy, and add it to the
regression fixture suite without touching a single line of Kotlin.

---

## Why JSON fixtures instead of raw audio?

Fixture files in `app/src/test/resources/fixtures/` store only **derived pitch
data** — one `(hz, confidence)` pair per YIN frame.  Raw PCM is never stored.

| Property | Effect |
|---|---|
| No voice prints or personal audio | Safe to commit to version control |
| ~3–8 KB per session | Readable diffs in code review |
| Same JSON → same analysis result | Deterministic, no flakiness |
| Human-readable Hz values | Reviewers can sanity-check the fixture without running code |

---

## Step 1 — Run a development build and enable verbose logging

`VoiceAnalyzer` emits one `Log.i` line per unique note change at
the `VoiceAnalyzer` tag.  A typical line looks like:

```
I/VoiceAnalyzer: [  5.3s] E4    329.6 Hz  conf=0.930  n=42
```

| Field | Meaning |
|---|---|
| `5.3s` | Elapsed session time (discard — not stored in fixtures) |
| `E4` | Note name (discard — derived from Hz, not stored) |
| `329.6 Hz` | YIN fundamental frequency → `"hz"` |
| `conf=0.930` | YIN confidence → `"confidence"` |
| `n=42` | Running accepted-sample count (discard) |

**To capture:**
1. Connect a physical device (emulators have unreliable microphone input).
2. Run a debug build: `./gradlew installDebug`
3. Open Logcat in Android Studio, filter tag = `VoiceAnalyzer`.
4. Press **Start** in the app and sing the exercise you want to capture.
5. Press **Stop** when finished.
6. In Logcat, select all lines containing `Hz  conf=` and copy them.

> **Tip:** for a clean passaggio-detection fixture, sing a slow chromatic glide
> through your register break; for a range fixture, sing from your lowest to
> your highest comfortable note and back.

---

## Step 2 — Extract the raw data lines

From the copied Logcat output, keep only the lines with `Hz  conf=` — these
are the per-pitch accepted-sample lines.  Discard all other log lines (session
start/stop, histogram, classification).

Example input (three lines):

```
2025-03-01 14:22:05.341  I/VoiceAnalyzer: [  4.1s] D4    293.7 Hz  conf=0.921  n=35
2025-03-01 14:22:05.502  I/VoiceAnalyzer: [  4.3s] E4    329.6 Hz  conf=0.934  n=36
2025-03-01 14:22:05.664  I/VoiceAnalyzer: [  4.4s] E4    329.6 Hz  conf=0.930  n=37
```

Extract `hz` and `confidence` from each line:

| Logcat token | JSON key |
|---|---|
| `293.7 Hz` | `"hz": 293.7` |
| `conf=0.921` | `"confidence": 0.921` |

Discard everything else (timestamp, elapsed time, note name, sample counter).

---

## Step 3 — Create the fixture JSON file

Create a new file `app/src/test/resources/fixtures/<id>.json`.
The filename stem is the fixture's `id` and is also what you add to the test.

```jsonc
{
  "id": "lyric_baritone_arpeggio",
  "description": "Lyric baritone arpeggio exercise C3–G4. Comfortable zone E3–E4. Passaggio around C4.",
  "voiceType": "Lyric Baritone",
  "source": "logcat_export",
  "capturedAt": "2025-03-01",
  "frames": [
    {"hz": 130.8, "confidence": 0.921},
    {"hz": 164.8, "confidence": 0.934},
    {"hz": 196.0, "confidence": 0.917},
    ...
  ],
  "assertions": {
    "detectedMinNote":     "C3",
    "detectedMaxNote":     "G4",
    "comfortableLowNote":  "E3",
    "comfortableHighNote": "E4",
    "passaggioNote":       "C4",
    "minAcceptedFrames":   35,
    "semitoneTol":         2,
    "passaggioTol":        3
  }
}
```

### Assertion field guidance

| Field | Default tolerance | How to choose the value |
|---|---|---|
| `detectedMinNote` | ±2 semitones | The lowest note you genuinely sang (not a single accidental squeak). |
| `detectedMaxNote` | ±2 semitones | The highest note with at least two clean frames. |
| `comfortableLowNote` | ±2 semitones | The approximate P20 note — where the bottom 20% of your session falls. |
| `comfortableHighNote` | ±2 semitones | The approximate P80 note. |
| `passaggioNote` | ±3–4 semitones | The register-break area — use a wider tolerance (3–4 st) because passaggio spans a small band, not a single pitch. |
| `minAcceptedFrames` | — | Set to ~75–80% of your raw frame count.  This guards against the filter becoming over-aggressive. |

**Omit any assertion field you cannot determine confidently** — the corresponding
test will be skipped for that fixture rather than asserting a wrong value.

### Tolerances: be conservative, not fragile

Two semitones equals one whole step.  A tolerance of ±2 semitones means:

- Assertion for `"G3"` (196 Hz) will pass for anything in [175 Hz, 220 Hz] (F3–A3).

This is intentional: we are testing **algorithmic behavior**, not pinning an
exact floating-point frame boundary.  Tighten a tolerance only when you have
specific reason to believe a tighter band is reliable for that fixture.

---

## Step 4 — Register the fixture in FixtureRegressionTest

Open `FixtureRegressionTest.kt` and add your filename stem to the list:

```kotlin
@JvmStatic
@Parameterized.Parameters(name = "{0}")
fun fixtureNames(): List<String> = listOf(
    "lyric_tenor_warmup",
    "lyric_soprano_scale",
    "dramatic_mezzo_full_range",
    "bass_baritone_exercise",
    "mezzo_passaggio_exercise",
    "lyric_baritone_arpeggio"    // ← add here
)
```

Run the tests to verify all assertions pass:

```bash
./gradlew :app:test --tests "*.FixtureRegressionTest"
```

Each fixture runs 10 parameterized test cases.  The output shows the fixture
name in parentheses so failures are immediately locatable:

```
fixture detected min matches expected note[lyric_baritone_arpeggio] PASSED
fixture passaggio matches expected note[lyric_baritone_arpeggio]    PASSED
...
```

---

## Sanitization checklist

Before committing a `logcat_export` fixture, verify:

- [ ] No singer name, username, or other identifier in any field.
- [ ] `capturedAt` contains only a date (`YYYY-MM-DD`), not a time or timezone.
- [ ] `description` describes the exercise or voice type, **not** a person.
      ✓ `"baritone warmup exercise C3–F4"`
      ✗ `"John's warmup 14 March"`
- [ ] Raw audio files (`.pcm`, `.wav`, `.m4a`) are **not** in the commit.
- [ ] Each frame object contains only `hz` and `confidence` — no other fields
      from the original Logcat line.
- [ ] You have not included the running sample-count (`n=…`) or elapsed time
      in any field.

---

## Fixture format reference

```
fixtures/<id>.json
│
├── id              String    Machine-readable; must match filename stem exactly.
├── description     String    Human-readable scenario summary (no names).
├── voiceType       String    Informational; not used in assertions.
│                             e.g. "Lyric Tenor", "Dramatic Mezzo-Soprano"
├── source          String    "synthetic" | "logcat_export"
├── capturedAt      String    ISO-8601 date (YYYY-MM-DD) or "synthetic".
│
├── frames          Array     Ordered list of accepted YIN frames.
│   └── []
│       ├── hz          Float   YIN F0 estimate in Hertz (> 0).
│       └── confidence  Float   Normalized YIN confidence in [0, 1].
│
└── assertions      Object    Optional behavioral expectations.
    ├── detectedMinNote     String?  Note name e.g. "G3". Null = skip test.
    ├── detectedMaxNote     String?  Note name e.g. "A5". Null = skip test.
    ├── comfortableLowNote  String?  Note name. Null = skip test.
    ├── comfortableHighNote String?  Note name. Null = skip test.
    ├── passaggioNote       String?  Note name. Null = skip test.
    ├── minAcceptedFrames   Int      Minimum frames after filter (default 20).
    ├── semitoneTol         Int      Tolerance for range assertions (default 2).
    └── passaggioTol        Int      Tolerance for passaggio assertion (default 3).
```

**Note name format:** standard scientific pitch notation with sharps only —
`C`, `C#`, `D`, `D#`, `E`, `F`, `F#`, `G`, `G#`, `A`, `A#`, `B` followed by
the octave number.  Middle C is `C4`.

---

## DSL fixtures vs JSON fixtures

| | Kotlin DSL (`buildSession { … }`) | JSON fixtures |
|---|---|---|
| **Location** | `AnalyzerTestFixtures.kt` | `fixtures/*.json` |
| **Best for** | Precise edge-case engineering | Real-session regression |
| **Pitch source** | Exact mathematical values | Real YIN output |
| **Confidences** | Usually 1.0 (controlled) | Actual YIN confidence |
| **Review** | Code review | Side-by-side with Logcat |

Both live in the same test run.  DSL fixtures pin algorithmic edge cases
precisely; JSON fixtures guard real-world accuracy across different vocal styles
and microphone conditions.  Use both.

---

## Troubleshooting

**`Fixture not found on test classpath`**
The JSON file must be under `app/src/test/resources/fixtures/` and the filename
stem must exactly match the string in `fixtureNames()`.

**`fixture loads and has enough accepted frames` fails**
Either the fixture has very few confident frames, or `SessionReplay.MIN_CONFIDENCE`
was raised.  Check how many frames in your fixture have `confidence >= 0.80` and
lower `minAcceptedFrames` accordingly, or re-capture with a better mic environment.

**`fixture detected min matches expected note` fails with a large semitone diff**
The declared `detectedMinNote` may be off.  Recompute: sort your accepted Hz values
and apply [FachClassifier.estimateDetectedExtremes] manually (or add a debug print
in a local test run) to see what the algorithm produces.

**Passaggio test fails consistently**
Increase `passaggioTol` to 4 or 5 for this fixture.  The passaggio algorithm is
a heuristic based on sliding-window variance and is inherently noisier than the
percentile-based range calculations.
