package com.yuriy.diapason.analyzer

import android.util.Log
import kotlin.math.abs

private const val TAG = "FachClassifier"

object FachClassifier {

    // ── Note name utility ──────────────────────────────────────────────────────

    fun hzToNoteName(hz: Float): String {
        if (hz <= 0f) return "—"
        val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        val midi = (12 * Math.log(hz / 440.0) / Math.log(2.0) + 69).toInt()
        if (midi < 0 || midi > 127) return "%.0f Hz".format(hz)
        return "${noteNames[midi % 12]}${(midi / 12) - 1}"
    }

    // ── Tessitura (20th–80th percentile) ─────────────────────────────────────

    fun estimateTessitura(pitches: List<Float>): Pair<Float, Float> {
        if (pitches.size < 10) return Pair(pitches.minOrNull() ?: 0f, pitches.maxOrNull() ?: 0f)
        val sorted = pitches.sorted()
        return Pair(sorted[(sorted.size * 0.20).toInt()], sorted[(sorted.size * 0.80).toInt()])
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
            "  Abs range  : ${hzToNoteName(profile.absoluteMinHz)}–${hzToNoteName(profile.absoluteMaxHz)}"
        )
        Log.i(
            TAG,
            "  Tessitura  : ${hzToNoteName(profile.tessituraLowHz)}–${hzToNoteName(profile.tessituraHighHz)}"
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
            val maxRatio = profile.absoluteMaxHz / fach.rangeMaxHz
            when {
                maxRatio in 0.90f..1.10f -> {
                    score += 3; breakdown += "+3 upper ceiling ≈ ${hzToNoteName(fach.rangeMaxHz)}"
                }

                maxRatio in 0.80f..1.20f -> {
                    score += 2; breakdown += "+2 upper ceiling near ${hzToNoteName(fach.rangeMaxHz)}"
                }

                maxRatio in 0.70f..1.30f -> {
                    score += 1; breakdown += "+1 upper ceiling roughly near ${hzToNoteName(fach.rangeMaxHz)}"
                }

                else -> breakdown += "  0 upper ceiling far from ${hzToNoteName(fach.rangeMaxHz)}"
            }

            // 2. Lower floor
            val minRatio = profile.absoluteMinHz / fach.rangeMinHz
            when {
                minRatio in 0.85f..1.15f -> {
                    score += 2; breakdown += "+2 lower floor ≈ ${hzToNoteName(fach.rangeMinHz)}"
                }

                minRatio in 0.70f..1.30f -> {
                    score += 1; breakdown += "+1 lower floor near ${hzToNoteName(fach.rangeMinHz)}"
                }

                else -> breakdown += "  0 lower floor far from ${hzToNoteName(fach.rangeMinHz)}"
            }

            // 3. Tessitura high
            val tessHighRatio = profile.tessituraHighHz / fach.tessituraMaxHz
            when {
                tessHighRatio in 0.90f..1.10f -> {
                    score += 3; breakdown += "+3 tessitura high ≈ ${hzToNoteName(fach.tessituraMaxHz)}"
                }

                tessHighRatio in 0.80f..1.20f -> {
                    score += 2; breakdown += "+2 tessitura high near ${hzToNoteName(fach.tessituraMaxHz)}"
                }

                tessHighRatio in 0.70f..1.30f -> {
                    score += 1; breakdown += "+1 tessitura high roughly near ${hzToNoteName(fach.tessituraMaxHz)}"
                }

                else -> breakdown += "  0 tessitura high far from ${hzToNoteName(fach.tessituraMaxHz)}"
            }

            // 4. Tessitura low
            val tessLowRatio = profile.tessituraLowHz / fach.tessituraMinHz
            when {
                tessLowRatio in 0.90f..1.10f -> {
                    score += 3; breakdown += "+3 tessitura low ≈ ${hzToNoteName(fach.tessituraMinHz)}"
                }

                tessLowRatio in 0.80f..1.20f -> {
                    score += 2; breakdown += "+2 tessitura low near ${hzToNoteName(fach.tessituraMinHz)}"
                }

                tessLowRatio in 0.70f..1.30f -> {
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
