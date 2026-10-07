package com.yuriy.diapason.ui.screens.history

import com.yuriy.diapason.data.SessionRecord
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln

/** One session on the range chart; pitches as fractional MIDI note numbers (A4 = 69). */
data class RangePoint(
    val timestampMs: Long,
    val comfortableLow: Float,
    val comfortableHigh: Float,
    val detectedMin: Float,
    val detectedMax: Float,
    val passaggio: Float,
)

/**
 * Chart data for "Your range over time": points oldest → newest, and a vertical axis
 * snapped outward to whole octaves ([minMidi] and [maxMidi] are both Cs) so the grid
 * lines fall on C2, C3, C4…
 */
data class RangeProgress(
    val points: List<RangePoint>,
    val minMidi: Int,
    val maxMidi: Int,
) {
    /** MIDI numbers of the C lines to label, low to high. */
    val octaveLines: List<Int> get() = (minMidi..maxMidi step 12).toList()
}

/** Fewer than this many sessions is a dot, not a trend. */
const val RANGE_PROGRESS_MIN_SESSIONS = 2

/** More than this many and the chart stops being readable on a phone. */
const val RANGE_PROGRESS_MAX_SESSIONS = 20

/**
 * Builds the chart from [sessions] (any order): the newest [RANGE_PROGRESS_MAX_SESSIONS],
 * plotted oldest first. Null when fewer than [RANGE_PROGRESS_MIN_SESSIONS] remain.
 *
 * Plots in semitones rather than Hz so equal musical distances look equal — an octave
 * near the bass is ~65 Hz wide but ~520 Hz wide near a soprano's top, which would flatten
 * every low voice's movement on a linear axis.
 */
fun buildRangeProgress(sessions: List<SessionRecord>): RangeProgress? {
    val recent = sessions
        .sortedByDescending { it.timestampMs }
        .take(RANGE_PROGRESS_MAX_SESSIONS)
        .sortedBy { it.timestampMs }
    if (recent.size < RANGE_PROGRESS_MIN_SESSIONS) return null

    val points = recent.map { s ->
        RangePoint(
            timestampMs = s.timestampMs,
            comfortableLow = midi(s.comfortableLowHz),
            comfortableHigh = midi(s.comfortableHighHz),
            detectedMin = midi(s.detectedMinHz),
            detectedMax = midi(s.detectedMaxHz),
            passaggio = midi(s.passaggioHz),
        )
    }
    val lowest = points.minOf { minOf(it.detectedMin, it.comfortableLow) }
    val highest = points.maxOf { maxOf(it.detectedMax, it.comfortableHigh) }
    // Snap outward to Cs; guarantee at least one octave of height.
    val minC = floor(lowest / 12f).toInt() * 12
    val maxC = maxOf(ceil(highest / 12f).toInt() * 12, minC + 12)
    return RangeProgress(points, minMidi = minC, maxMidi = maxC)
}

private fun midi(hz: Float): Float = (12.0 * ln(hz / 440.0) / ln(2.0) + 69.0).toFloat()
