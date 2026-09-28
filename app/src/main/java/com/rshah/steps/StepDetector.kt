package com.rshah.steps

import kotlin.math.max
import kotlin.math.sqrt

/**
 * Accelerometer step detector.
 * 1. |a| is orientation independent; a slow low-pass estimates gravity and is subtracted.
 * 2. A fast low-pass smooths jitter.
 * 3. Local maxima above an adaptive threshold are step candidates.
 * 4. Candidates closer than 250 ms are ignored; a step is only counted after
 *    WARMUP consecutive steps with gaps under 1.2 s (rejects random shakes).
 */
class StepDetector(private val onSteps: (Int) -> Unit) {
    var sensitivity = 0.5f   // 0 = least sensitive, 1 = most

    private var gravity = 9.81f
    private var smooth = 0f
    private var p2 = 0f
    private var p1 = 0f
    private var t1 = 0L
    private var lastStepNs = 0L
    private var peakAvg = 0f
    private var streak = 0
    private var counting = false

    fun onSample(x: Float, y: Float, z: Float, tNs: Long) {
        val m = sqrt(x * x + y * y + z * z)
        gravity += 0.01f * (m - gravity)
        smooth += 0.35f * ((m - gravity) - smooth)
        val cur = smooth

        if (p1 > p2 && p1 >= cur) checkPeak(p1, t1)
        p2 = p1; p1 = cur; t1 = tNs
    }

    private fun checkPeak(peak: Float, t: Long) {
        val base = 2.4f - 1.6f * sensitivity          // 2.4 .. 0.8 m/s²
        if (peak < max(base, 0.5f * peakAvg)) return

        val gapMs = if (lastStepNs == 0L) Long.MAX_VALUE else (t - lastStepNs) / 1_000_000
        if (gapMs < 250) return

        if (gapMs > 1200) {
            streak = 1; counting = false; peakAvg = peak
        } else {
            streak++
            peakAvg = 0.8f * peakAvg + 0.2f * peak
        }
        lastStepNs = t

        if (counting) onSteps(1)
        else if (streak >= WARMUP) { counting = true; onSteps(streak) }
    }

    private companion object { const val WARMUP = 5 }
}
