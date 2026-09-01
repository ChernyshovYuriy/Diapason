package com.yuriy.diapason.analyzer

import com.yuriy.diapason.logging.AppLogger
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

object FachClassifier {

    // ── Note name utility ──────────────────────────────────────────────────────

    fun hzToNoteName(hz: Float): String {
        // hz <= 0f also rejects NaN/negative values (NaN fails every IEEE comparison);
        // !hz.isFinite() additionally catches NaN and +/-Infinity explicitly, since NaN
        // would otherwise reach roundToInt() below and throw IllegalArgumentException.
        if (hz <= 0f || !hz.isFinite()) return "—"
        val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        val midi = (12 * ln(hz / 440.0) / ln(2.0) + 69).roundToInt()
        // Locale.ROOT: this is a number rendered into an otherwise Latin-script UI, not
        // localized prose — the device's default locale must not change its digit glyphs
        // (e.g. Persian renders Eastern Arabic-Indic digits and a different decimal mark
        // for an unqualified .format() call).
        if (midi !in 0..127) return "%.0f Hz".format(Locale.ROOT, hz)
        return "${noteNames[midi % 12]}${(midi / 12) - 1}"
    }

    // ── Comfortable range (20th–80th percentile) ─────────────────────────────
    //
    // Because every accepted sample represents approximately equal duration (~160 ms),
    // the sorted-percentile approach is time-weighted: P20 is the pitch below which the
    // singer spent only the bottom 20% of their time, and P80 the mirror at the top.
    // This is a defensible proxy for "comfortable range".

    fun estimateComfortableRange(pitches: List<Float>): Pair<Float, Float> {
        if (pitches.size < 10) return Pair(pitches.minOrNull() ?: 0f, pitches.maxOrNull() ?: 0f)
        val sorted = pitches.sorted()
        return Pair(sorted[(sorted.size * 0.20).toInt()], sorted[(sorted.size * 0.80).toInt()])
    }

    // ── Detected extremes (neighbor-validated min/max) ────────────────────────
    //
    // A pitch qualifies as the detected extreme only when at least one other accepted
    // sample sits within 2 semitones of it (frequency ratio ≤ 2^(2/12) ≈ 1.1225).
    // This prevents a single stray high-confidence frame from claiming the floor or
    // ceiling of the session.  If no neighbor exists (very sparse dataset) we fall
    // back to the raw min/max so the function always returns a valid result.

    fun estimateDetectedExtremes(pitches: List<Float>): Pair<Float, Float> {
        if (pitches.size < 4) {
            return Pair(pitches.minOrNull() ?: 0f, pitches.maxOrNull() ?: 0f)
        }
        val sorted = pitches.sorted()
        val twoSemitones = 1.1225f   // 2^(2/12)

        // A candidate is "stable" when at least one other sample sits within 2 semitones
        // of it (ratio <= 1.1225).  This prevents a single stray high-confidence frame
        // from claiming the floor or ceiling of the session.
        //
        // Implementation: binary-search the sorted list to the insertion point for the
        // candidate, then check only the immediate neighbours at that point.
        // This is O(log n) per call vs the previous O(n) full scan — on a 3 600-sample
        // session (10-minute recording) this reduces comparisons from ~26 M to ~240.
        //
        // Duplicate values (e.g. five frames at 523 Hz) are handled naturally: the
        // neighbour immediately beside the insertion point IS the duplicate (ratio 1.0),
        // so the candidate is correctly accepted.
        fun hasNeighbor(candidate: Float): Boolean {
            var lo = 0
            var hi = sorted.size - 1
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (sorted[mid] < candidate) lo = mid + 1 else hi = mid
            }
            // lo is now the index of the first element >= candidate
            val lowerOk = lo > 0 && (candidate / sorted[lo - 1]) <= twoSemitones
            val upperOk = lo < sorted.size - 1 && (sorted[lo + 1] / candidate) <= twoSemitones
            // selfDuplicate: another element at exactly this pitch counts as a neighbour
            val selfDuplicate = (lo > 0 && sorted[lo - 1] == candidate) ||
                    (lo + 1 < sorted.size && sorted[lo + 1] == candidate)
            return lowerOk || upperOk || selfDuplicate
        }

        val stableMin = sorted.firstOrNull { hasNeighbor(it) } ?: sorted.first()
        val stableMax = sorted.lastOrNull { hasNeighbor(it) } ?: sorted.last()

        return Pair(stableMin, stableMax)
    }

    // ── Passaggio (zone of highest pitch instability) ─────────────────────────
    //
    // A passaggio is a genuine register break: the voice wavering back and forth
    // near a specific pitch, not just any two notes that happen to be far apart.
    // Plain window variance can't tell those apart — it only measures spread, not
    // order, so a real wobble (many small back-and-forth moves) and one clean
    // jump between two held notes produce the same "spread" if the numbers work
    // out similarly. The search below runs in semitone space (a semitone is worth
    // ~4x more Hz at soprano register than bass register, so raw-Hz variance
    // always drifted toward the higher voice — KNOWN_ISSUES.md #1) and scores
    // each window on two order-sensitive signals instead of variance alone:
    //
    //  - reversals: how many times the frame-to-frame direction actually flips.
    //    A flat run or a single step has zero; a real wobble has many. Weighted
    //    exponentially (2^reversals) because a *partial* overlap with a real
    //    wobble — e.g. one contaminating sample from an adjacent stable block —
    //    still reverses on almost every frame, so a linear weight isn't enough
    //    margin to stop that near-miss from beating a window that's genuinely
    //    oscillating throughout.
    //  - magnitude: the MEDIAN (not variance) of the qualifying frame-to-frame
    //    moves. Median is robust to the one big delta a contaminating sample
    //    from a neighboring block introduces — that one outlier doesn't move the
    //    median the way it would inflate a variance-from-mean calculation, so a
    //    window doesn't get an unearned magnitude boost just for grabbing a
    //    single sample from a much more distant register.
    //
    // A flat run or single step has zero reversals and one qualifying delta —
    // median of one value is just that value — so this reduces to exactly the
    // old variance-like behavior for the no-oscillation fallback case (e.g. a
    // bimodal chest/head session with no transition frames). See
    // AdversarialBreakageTest for the constructed case this was built against.

    private const val PASSAGGIO_WINDOW_SIZE = 15

    // Minimum frame-to-frame move (in semitones) to count as a real move at all.
    // Filters out hardware/measurement jitter on an otherwise-held note, which
    // real captured sessions (not just clean synthetic ones) do exhibit.
    private const val MIN_MOVE_DELTA_SEMITONES = 0.15

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid]
    }

    fun estimatePassaggio(pitches: List<Float>): Float {
        if (pitches.size < 30) return pitches.average().toFloat()

        val semitones = pitches.map { 12.0 * ln(it.toDouble()) / ln(2.0) }
        val windowSize = PASSAGGIO_WINDOW_SIZE

        var bestScore = -1.0
        var bestVariance = -1.0
        var passaggioSemitone = semitones.average()

        for (i in 0..(semitones.size - windowSize)) {
            val window = semitones.subList(i, i + windowSize)
            val mean = window.average()
            val variance = window.sumOf { (it - mean) * (it - mean) } / windowSize

            val qualifyingDeltas = mutableListOf<Double>()
            var reversals = 0
            var prevDelta = 0.0
            for (j in 1 until window.size) {
                val delta = window[j] - window[j - 1]
                if (abs(delta) >= MIN_MOVE_DELTA_SEMITONES) {
                    qualifyingDeltas += delta
                    if (prevDelta != 0.0 && (delta > 0) != (prevDelta > 0)) reversals++
                    prevDelta = delta
                }
            }
            val magnitude = median(qualifyingDeltas.map { abs(it) })
            val score = magnitude * magnitude * 2.0.pow(reversals)

            // On an exact score tie — e.g. a single clean jump between two flat
            // blocks scores identically no matter how centered the window is on
            // that jump, since median-of-one ignores window position — prefer the
            // more centered/balanced window, matching what plain variance did
            // before for that case (KNOWN_ISSUES.md #1's bimodal fallback).
            if (score > bestScore || (score == bestScore && variance > bestVariance)) {
                bestScore = score
                bestVariance = variance
                passaggioSemitone = mean
            }
        }

        return 2.0.pow(passaggioSemitone / 12.0).toFloat()
    }

    // ── Classification ────────────────────────────────────────────────────────

    /**
     * Scores each Fach definition against [profile] and returns a ranked list.
     *
     * Scoring (max 14 pts):
     *   Lower floor match   → 0–3 pts  (weighted higher than ceiling — see below)
     *   Upper ceiling match → 0–2 pts
     *   Tessitura high      → 0–3 pts
     *   Tessitura low       → 0–3 pts
     *   Passaggio proximity → 0–3 pts
     *
     * Floor outweighs ceiling on purpose: the lowest comfortably-produced pitch is a
     * harder-to-fake Fach signal than the top of the range, which a singer can stretch
     * with falsetto/head-voice technique in a single take.
     *
     * Floor scoring special case: a Fach whose own [FachDefinition.rangeMinHz] sits
     * below [MIN_PITCH_HZ] (currently only Contrabass Oktavist, 43 Hz vs. the mic's
     * 60 Hz floor) can never produce a `detectedMinHz` close enough to its true range
     * minimum to score above 0 — not because the singer's range is wrong, but because
     * the ratio-tier math is comparing against a number the hardware can never confirm.
     * When the detected floor sits at that hardware limit, this is scored as
     * inconclusive (the "near" tier) rather than "far" — the app shouldn't turn its own
     * measurement gap into evidence against the singer. See KNOWN_ISSUES.md.
     */
    fun classify(profile: VoiceProfile): List<FachMatch> {
        AppLogger.i("═══════════════════════════════════════════════════")
        AppLogger.i("  FACH CLASSIFICATION")
        AppLogger.i(
            "  Detected   : ${hzToNoteName(profile.detectedMinHz)}–${hzToNoteName(profile.detectedMaxHz)}"
        )
        AppLogger.i(
            "  Comfortable: ${hzToNoteName(profile.comfortableLowHz)}–${hzToNoteName(profile.comfortableHighHz)}"
        )
        AppLogger.i(
            "  Passaggio  : ${hzToNoteName(profile.estimatedPassaggioHz)} (${profile.estimatedPassaggioHz.toInt()} Hz)"
        )
        AppLogger.i("  Samples    : ${profile.sampleCount} over ${profile.durationSeconds}s")
        AppLogger.i("───────────────────────────────────────────────────")

        val results = ALL_FACH.map { fach ->
            val breakdown = mutableListOf<String>()
            var score = 0

            // 1. Lower floor — weighted higher (0-3, finer-grained tolerance bands) than
            // the ceiling below. The lowest comfortably-produced pitch (real chest-voice
            // depth) is a harder-to-fake, more stable Fach indicator than the top of the
            // range, which a singer can stretch with falsetto/head-voice technique in a
            // single phone-mic take. See KNOWN_ISSUES.md "Ceiling vs floor scoring weight".
            //
            // Special case: this fach's own range floor is below what the mic can ever
            // register (currently only Contrabass Oktavist, 43 Hz vs. MIN_PITCH_HZ=60 Hz).
            // A detected floor sitting at that hardware limit is inconclusive, not
            // disconfirming — score it as "near" rather than run it through ratio tiers
            // that can mathematically never clear even the loosest band. If the detected
            // floor is clearly NOT near the sensor limit (e.g. a soprano profile being
            // ranked against this fach), that's real evidence and falls through to the
            // normal tiers below. See KNOWN_ISSUES.md.
            val twoSemitonesUp = 1.1225f   // 2^(2/12) — same tolerance used elsewhere in this file
            val floorIsUnmeasurable = fach.rangeMinHz < MIN_PITCH_HZ
            val atSensorFloor = profile.detectedMinHz <= MIN_PITCH_HZ * twoSemitonesUp
            val minRatio = profile.detectedMinHz / fach.rangeMinHz
            when {
                floorIsUnmeasurable && atSensorFloor -> {
                    score += 2
                    breakdown += "+2 lower floor inconclusive — true floor may be below " +
                            "this app's ${MIN_PITCH_HZ.toInt()} Hz detection limit"
                }

                minRatio in 0.90f..1.10f -> {
                    score += 3; breakdown += "+3 lower floor ≈ ${hzToNoteName(fach.rangeMinHz)}"
                }

                minRatio in 0.80f..1.20f -> {
                    score += 2; breakdown += "+2 lower floor near ${hzToNoteName(fach.rangeMinHz)}"
                }

                minRatio in 0.70f..1.30f -> {
                    score += 1; breakdown += "+1 lower floor roughly near ${hzToNoteName(fach.rangeMinHz)}"
                }

                else -> breakdown += "  0 lower floor far from ${hzToNoteName(fach.rangeMinHz)}"
            }

            // 2. Upper ceiling — weighted lower (0-2) than the floor above; see comment there.
            val maxRatio = profile.detectedMaxHz / fach.rangeMaxHz
            when (maxRatio) {
                in 0.85f..1.15f -> {
                    score += 2; breakdown += "+2 upper ceiling ≈ ${hzToNoteName(fach.rangeMaxHz)}"
                }

                in 0.70f..1.30f -> {
                    score += 1; breakdown += "+1 upper ceiling near ${hzToNoteName(fach.rangeMaxHz)}"
                }

                else -> breakdown += "  0 upper ceiling far from ${hzToNoteName(fach.rangeMaxHz)}"
            }

            // 3. Comfortable range high
            val tessHighRatio = profile.comfortableHighHz / fach.tessituraMaxHz
            when (tessHighRatio) {
                in 0.90f..1.10f -> {
                    score += 3; breakdown += "+3 tessitura high ≈ ${hzToNoteName(fach.tessituraMaxHz)}"
                }

                in 0.80f..1.20f -> {
                    score += 2; breakdown += "+2 tessitura high near ${hzToNoteName(fach.tessituraMaxHz)}"
                }

                in 0.70f..1.30f -> {
                    score += 1; breakdown += "+1 tessitura high roughly near ${hzToNoteName(fach.tessituraMaxHz)}"
                }

                else -> breakdown += "  0 tessitura high far from ${hzToNoteName(fach.tessituraMaxHz)}"
            }

            // 4. Comfortable range low
            val tessLowRatio = profile.comfortableLowHz / fach.tessituraMinHz
            when (tessLowRatio) {
                in 0.90f..1.10f -> {
                    score += 3; breakdown += "+3 tessitura low ≈ ${hzToNoteName(fach.tessituraMinHz)}"
                }

                in 0.80f..1.20f -> {
                    score += 2; breakdown += "+2 tessitura low near ${hzToNoteName(fach.tessituraMinHz)}"
                }

                in 0.70f..1.30f -> {
                    score += 1; breakdown += "+1 tessitura low roughly near ${hzToNoteName(fach.tessituraMinHz)}"
                }

                else -> breakdown += "  0 tessitura low far from ${hzToNoteName(fach.tessituraMinHz)}"
            }

            // 5. Passaggio
            val passDiff = abs(profile.estimatedPassaggioHz - fach.passaggioHz)
            val tol = fach.passaggioHz * 0.10f
            when {
                passDiff <= tol -> {
                    score += 3; breakdown += "+3 passaggio ≈ ${hzToNoteName(fach.passaggioHz)}"
                }

                passDiff <= tol * 2 -> {
                    score += 2; breakdown += "+2 passaggio near ${hzToNoteName(fach.passaggioHz)}"
                }

                passDiff <= tol * 3.5f -> {
                    score += 1; breakdown += "+1 passaggio roughly near ${hzToNoteName(fach.passaggioHz)}"
                }

                else -> breakdown += "  0 passaggio far from ${hzToNoteName(fach.passaggioHz)}"
            }

            FachMatch(fach = fach, score = score, scoreBreakdown = breakdown)
        }.sortedByDescending { it.score }

        AppLogger.i("  FULL SCORING TABLE:")
        results.forEach {
            AppLogger.d("  [%2d/14] fachRes=${it.fach.nameRes}".format(it.score))
        }

        AppLogger.i("  TOP 3 MATCHES:")
        results.take(3).forEachIndexed { i, m ->
            AppLogger.i("  #${i + 1}: fachRes=${m.fach.nameRes} — ${m.score}/14")
            m.scoreBreakdown.forEach { AppLogger.i("         $it") }
        }
        AppLogger.i("═══════════════════════════════════════════════════")

        return results
    }
}
