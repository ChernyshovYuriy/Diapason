package com.yuriy.diapason.analyzer

import android.util.Log
import kotlin.math.abs
import kotlin.math.ln

private const val TAG = "FachClassifier"

object FachClassifier {

    // ── Note name utility ──────────────────────────────────────────────────────

    fun hzToNoteName(hz: Float): String {
        if (hz <= 0f) return "—"
        val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        val midi = (12 * ln(hz / 440.0) / ln(2.0) + 69).toInt()
        if (midi !in 0..127) return "%.0f Hz".format(hz)
        return "${noteNames[midi % 12]}${(midi / 12) - 1}"
    }

    // ── Comfortable range (20th–80th percentile) ─────────────────────────────
    //
    // Because every accepted sample represents approximately equal duration (~160 ms),
    // the sorted-percentile approach is time-weighted: P20 is the pitch below which the
    // singer spent only the bottom 20% of their time, and P80 the mirror at the top.
    // This is a defensible proxy for "comfortable range" for Phase 1.

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

        // A candidate is "stable" when at least one other sample (including duplicate
        // values at the same pitch) sits within 2 semitones of it.  We count rather
        // than search directionally so that a cluster of identical values (e.g. five
        // frames all at 523 Hz) correctly qualifies each member as having a neighbor.
        fun hasNeighbor(candidate: Float): Boolean =
            sorted.count { other ->
                val ratio = if (other >= candidate) other / candidate else candidate / other
                ratio <= twoSemitones
            } >= 2

        val stableMin = sorted.firstOrNull { hasNeighbor(it) } ?: sorted.first()
        val stableMax = sorted.lastOrNull  { hasNeighbor(it) } ?: sorted.last()

        return Pair(stableMin, stableMax)
    }

    // ── Passaggio (zone of highest pitch instability) ─────────────────────────

    fun estimatePassaggio(pitches: List<Float>): Float {
        if (pitches.size < 30) return pitches.average().toFloat()
        val windowSize = 15
        var maxVariance = 0.0
        var passaggioHz = pitches.average().toFloat()
        for (i in 0..(pitches.size - windowSize)) {
            val window = pitches.subList(i, i + windowSize)
            val mean = window.average()
            val variance = window.sumOf { (it - mean) * (it - mean) } / windowSize
            if (variance > maxVariance) {
                maxVariance = variance; passaggioHz = mean.toFloat()
            }
        }
        return passaggioHz
    }

    // ── Classification ────────────────────────────────────────────────────────

    /**
     * Scores each Fach definition against [profile] and returns a ranked list.
     *
     * Scoring (max 14 pts):
     *   Upper ceiling match → 0–3 pts
     *   Lower floor match   → 0–2 pts
     *   Tessitura high      → 0–3 pts
     *   Tessitura low       → 0–3 pts
     *   Passaggio proximity → 0–3 pts
     */
    fun classify(profile: VoiceProfile): List<FachMatch> {
        Log.i(TAG, "═══════════════════════════════════════════════════")
        Log.i(TAG, "  FACH CLASSIFICATION")
        Log.i(
            TAG,
            "  Detected   : ${hzToNoteName(profile.detectedMinHz)}–${hzToNoteName(profile.detectedMaxHz)}"
        )
        Log.i(
            TAG,
            "  Comfortable: ${hzToNoteName(profile.comfortableLowHz)}–${hzToNoteName(profile.comfortableHighHz)}"
        )
        Log.i(
            TAG,
            "  Passaggio  : ${hzToNoteName(profile.estimatedPassaggioHz)} (${profile.estimatedPassaggioHz.toInt()} Hz)"
        )
        Log.i(TAG, "  Samples    : ${profile.sampleCount} over ${profile.durationSeconds}s")
        Log.i(TAG, "───────────────────────────────────────────────────")

        val results = ALL_FACH.map { fach ->
            val breakdown = mutableListOf<String>()
            var score = 0

            // 1. Upper ceiling
            val maxRatio = profile.detectedMaxHz / fach.rangeMaxHz
            when (maxRatio) {
                in 0.90f..1.10f -> {
                    score += 3; breakdown += "+3 upper ceiling ≈ ${hzToNoteName(fach.rangeMaxHz)}"
                }

                in 0.80f..1.20f -> {
                    score += 2; breakdown += "+2 upper ceiling near ${hzToNoteName(fach.rangeMaxHz)}"
                }

                in 0.70f..1.30f -> {
                    score += 1; breakdown += "+1 upper ceiling roughly near ${hzToNoteName(fach.rangeMaxHz)}"
                }

                else -> breakdown += "  0 upper ceiling far from ${hzToNoteName(fach.rangeMaxHz)}"
            }

            // 2. Lower floor
            val minRatio = profile.detectedMinHz / fach.rangeMinHz
            when (minRatio) {
                in 0.85f..1.15f -> {
                    score += 2; breakdown += "+2 lower floor ≈ ${hzToNoteName(fach.rangeMinHz)}"
                }

                in 0.70f..1.30f -> {
                    score += 1; breakdown += "+1 lower floor near ${hzToNoteName(fach.rangeMinHz)}"
                }

                else -> breakdown += "  0 lower floor far from ${hzToNoteName(fach.rangeMinHz)}"
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

        Log.i(TAG, "  FULL SCORING TABLE:")
        results.forEach { Log.d(TAG, "  [%2d/14] fachRes=${it.fach.nameRes}".format(it.score)) }

        Log.i(TAG, "  TOP 3 MATCHES:")
        results.take(3).forEachIndexed { i, m ->
            Log.i(TAG, "  #${i + 1}: fachRes=${m.fach.nameRes} — ${m.score}/14")
            m.scoreBreakdown.forEach { Log.i(TAG, "         $it") }
        }
        Log.i(TAG, "═══════════════════════════════════════════════════")

        return results
    }
}
