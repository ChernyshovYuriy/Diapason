# Diapason

Available at [Google Play](https://play.google.com/store/apps/details?id=com.yuriy.diapason)

## Project status

> 🚧 **This is a brand-new project.** Development has just started, it is a work in progress, and it is **not released on Google Play** yet.

**Diapason** is a **Voice Range Classifier** for singers and voice teachers.

It helps users explore and categorise vocal range using the classical **German Fach system**. The app listens to sustained notes through the device microphone, builds an acoustic profile in real time, and matches it against **19 documented voice types** — from **Coloratura Soprano** to **Contrabass Oktavist**.

> Diapason is intended as an educational starting point. Timbre and vocal weight — essential for a definitive Fach — require a trained human ear and cannot be captured by a microphone alone.

## Tech stack

- Kotlin
- Jetpack Compose
- Material 3

## Core methodology

- **Pitch detection:** YIN algorithm (de Cheveigné & Kawahara, JASA 2002)
- **Voice taxonomy:** German Fach system (19 categories)

## Languages & localisation

Currently, the app is available in **English only**.

Planned localisation support will use Android string resources (`res/values-xx/strings.xml`) for static UI text. Potential translation approaches for dynamic content include:

- Android built-in (`strings.xml`): free, zero runtime cost
- Google ML Kit Translation: free, on-device, 58 languages
- Google Cloud Translation API: pay-per-use, 100+ languages
- DeepL API (free tier): 500k chars/month, 31 languages

## Development

### Running the tests

All analyzer tests are pure JVM — no device or emulator needed:

```bash
./gradlew :app:test
```

To run a specific test class:

```bash
./gradlew :app:test --tests "*.FachClassifierClassifyTest"
./gradlew :app:test --tests "*.YinPitchDetectorTest"
./gradlew :app:test --tests "*.FixtureRegressionTest"
```

### Test structure

| File | What it covers |
|---|---|
| `FachClassifierTest` | `hzToNoteName`, `estimateComfortableRange`, `estimateDetectedExtremes`, `estimatePassaggio` — unit cases and edge cases |
| `FachClassifierClassifyTest` | `classify()` scoring — perfect-match profiles, cross-category separation, result invariants |
| `YinPitchDetectorTest` | YIN pitch detection — accuracy, confidence, silence, noise, edge-case buffers |
| `AnalyzerScenarioTest` | End-to-end vocal scenarios using the DSL fixture builder |
| `AnalyzerInvariantTest` | Properties that must hold for all inputs (e.g. comfortable ⊆ detected) |
| `FixtureRegressionTest` | Parameterised regression against five JSON fixtures in `src/test/resources/fixtures/` |
| `FixtureLoaderTest` | JSON loader, assertion helper, and `toPitchSamples` bridge |

### Adding a regression fixture from a real session

See **[CAPTURING.md](CAPTURING.md)** for the full workflow: exporting pitch data from Logcat, converting it to the fixture JSON format, writing behavioural assertions, and registering the fixture in `FixtureRegressionTest`.

## References

- de Cheveigné & Kawahara — *JASA 111(4), 2002* (YIN paper)
- Kloiber / Maehder / Melchert — *Handbuch der Oper* (Fach taxonomy)

## Feedback

Found a bug or have a feature request? Open an issue on the project repository.

---

## License
Diapason is licensed under the Apache License 2.0. See the [LICENSE](LICENSE) file for details.

© 2026 Chernyshov Yurii · Diapason
