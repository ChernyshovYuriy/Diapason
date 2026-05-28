package com.yuriy.diapason.analyzer

/**
 * Pure Kotlin implementation of the YIN pitch detection algorithm.
 *
 * Reference: de Cheveigné & Kawahara, "YIN, a fundamental frequency estimator
 * for speech and music," JASA 111(4), 2002.
 */
object YinPitchDetector {

    /**
     * Detects F0 of a monophonic audio frame.
     *
     * @param buffer     Normalized PCM samples [-1.0, 1.0]
     * @param sampleRate Audio sample rate in Hz
     * @param threshold  YIN threshold (0.10–0.20; lower = stricter)
     * @return Pair(pitchHz, confidence); pitchHz == -1f if none found
     */
    fun detect(buffer: FloatArray, sampleRate: Float, threshold: Double): Pair<Float, Float> {
        val bufferSize = buffer.size
        val yinBuffer = DoubleArray(bufferSize / 2)

        // Step 1 — Difference function
        yinBuffer[0] = 1.0
        for (tau in 1 until yinBuffer.size) {
            var sum = 0.0
            for (j in 0 until yinBuffer.size) {
                val delta = buffer[j].toDouble() - buffer[j + tau].toDouble()
                sum += delta * delta
            }
            yinBuffer[tau] = sum
        }

        // Step 2 — Cumulative Mean Normalized Difference
        var runningSum = 0.0
        for (tau in 1 until yinBuffer.size) {
            runningSum += yinBuffer[tau]
            yinBuffer[tau] = if (runningSum == 0.0) 1.0 else yinBuffer[tau] * tau / runningSum
        }
        yinBuffer[0] = 1.0

        // Step 3 — Absolute threshold with local-minimum advance
        // tau must be a var so the inner loop can advance it to the true minimum
        // within the sub-threshold region (the original for-loop val could not be mutated).
        var tauEstimate = -1
        var tau = 2
        while (tau < yinBuffer.size - 1) {
            if (yinBuffer[tau] < threshold) {
                while (tau + 1 < yinBuffer.size && yinBuffer[tau + 1] < yinBuffer[tau]) tau++
                tauEstimate = tau
                break
            }
            tau++
        }

        if (tauEstimate == -1) {
            var minVal = Double.MAX_VALUE
            for (tau in 2 until yinBuffer.size) {
                if (yinBuffer[tau] < minVal) {
                    minVal = yinBuffer[tau]
                    tauEstimate = tau
                }
            }
        }

        if (tauEstimate <= 0) return Pair(-1f, 0f)

        // Step 4 — Parabolic interpolation for sub-sample accuracy
        val refinedTau = parabolicInterpolation(yinBuffer, tauEstimate)
        val confidence = (1.0 - yinBuffer[tauEstimate]).toFloat().coerceIn(0f, 1f)
        val pitchHz = (sampleRate / refinedTau).toFloat()

        return Pair(pitchHz, confidence)
    }

    private fun parabolicInterpolation(yinBuffer: DoubleArray, tauEstimate: Int): Double {
        val x0 = if (tauEstimate < 1) tauEstimate else tauEstimate - 1
        val x2 = if (tauEstimate + 1 < yinBuffer.size) tauEstimate + 1 else tauEstimate

        if (x0 == tauEstimate) return if (yinBuffer[tauEstimate] <= yinBuffer[x2]) tauEstimate.toDouble() else x2.toDouble()
        if (x2 == tauEstimate) return if (yinBuffer[tauEstimate] <= yinBuffer[x0]) tauEstimate.toDouble() else x0.toDouble()

        val s0 = yinBuffer[x0]
        val s1 = yinBuffer[tauEstimate]
        val s2 = yinBuffer[x2]
        return tauEstimate + (s2 - s0) / (2.0 * (2.0 * s1 - s2 - s0))
    }
}
