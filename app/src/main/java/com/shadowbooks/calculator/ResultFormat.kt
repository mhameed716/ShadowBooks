package com.shadowbooks.calculator

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

internal enum class NumberFormat { Decimal, Fraction, Mixed, Engineering, Dms }
private val precision = MathContext(12, RoundingMode.HALF_EVEN)

/** Formatting never changes the unrounded Ans register. Only return a fraction if it round-trips. */
internal fun formatNumber(v: Double, style: NumberFormat = NumberFormat.Decimal): String {
    require(v.isFinite())
    if (v == 0.0) return "0"
    val n = BigDecimal.valueOf(v).round(precision).stripTrailingZeros()
    when (style) {
        NumberFormat.Fraction, NumberFormat.Mixed -> simpleFraction(v)?.let { ratio ->
            if (style == NumberFormat.Fraction || '/' !in ratio) return ratio
            val nAbs = ratio.substringBefore('/').removePrefix("−").toLong(); val d = ratio.substringAfter('/').toLong()
            if (nAbs < d) return ratio
            return (if (v < 0) "−" else "") + "${nAbs / d} ${nAbs % d}/$d"
        }
        NumberFormat.Engineering -> {
            val order = n.precision() - n.scale() - 1
            val exponent = Math.floorDiv(order, 3) * 3
            return n.scaleByPowerOfTen(-exponent).toPlainString() + if (exponent == 0) "" else "E$exponent"
        }
        NumberFormat.Dms -> if (abs(v) < 1e9) {
            val ticks = (abs(v) * 3_600_000).roundToLong()
            val seconds = BigDecimal.valueOf(ticks % 60_000, 3).stripTrailingZeros().toPlainString()
            return (if (v < 0) "−" else "") + "${ticks / 3_600_000}° ${ticks / 60_000 % 60}′ $seconds″"
        }
        NumberFormat.Decimal -> Unit
    }
    return if (abs(v) >= 1e-9 && abs(v) < 1e12) n.toPlainString() else n.toString()
}

private fun simpleFraction(v: Double): String? {
    if (abs(v) > 1e9 || abs(v) < 1e-6 || v == floor(v)) return null
    var rest = abs(v); var p0 = 0L; var p1 = 1L; var q0 = 1L; var q1 = 0L
    repeat(32) {
        val a = floor(rest)
        if (a > 1e9) return null
        val q = q1 * a.toLong() + q0
        if (q !in 1..10_000) return null
        val p = p1 * a.toLong() + p0
        if (abs(p.toDouble() / q - abs(v)) <= 4 * Math.ulp(v)) {
            return (if (v < 0) "−" else "") + if (q == 1L) "$p" else "$p/$q"
        }
        val tail = rest - a; if (tail == 0.0) return null
        rest = 1 / tail; p0 = p1; p1 = p; q0 = q1; q1 = q
    }
    return null
}
