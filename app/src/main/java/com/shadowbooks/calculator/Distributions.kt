package com.shadowbooks.calculator

import kotlin.math.floor
import org.apache.commons.math3.distribution.BinomialDistribution
import org.apache.commons.math3.distribution.ChiSquaredDistribution
import org.apache.commons.math3.distribution.IntegerDistribution
import org.apache.commons.math3.distribution.NormalDistribution
import org.apache.commons.math3.distribution.PoissonDistribution
import org.apache.commons.math3.distribution.RealDistribution
import org.apache.commons.math3.special.Beta
import org.apache.commons.math3.special.Gamma

internal enum class Law(val title: String) { Binomial("ثنائي الحدين"), Poisson("بواسون"), Normal("الطبيعي"), ChiSquare("كاي تربيع") }
internal enum class Probability(val title: String) { Density("كتلة / كثافة"), Cdf("تراكمي ≤"), Interval("بين حدّين"), Quantile("معكوس التراكمي") }

/** Discrete intervals include BOTH endpoints. Continuous density is not the probability of a point. */
internal fun distribution(law: Law, op: Probability, params: List<Double>, x: Double, upper: Double?): SheetReport {
    val a = params.first()
    if (op == Probability.Interval) checkDomain(upper != null && upper >= x)
    if (op == Probability.Quantile) checkDomain(x in 0.0..1.0)
    // Non-null exactly when op is Interval (checked above); kept as a plain value so
    // no force-unwrap remains on any calculation path.
    val intervalUpper = if (op == Probability.Interval) upper ?: fail(MathIssue.Syntax) else 0.0
    val discrete: IntegerDistribution? = when (law) {
        Law.Binomial -> {
            val n = safeInt(a); checkDomain(n in 0..1_000_000L && params[1] in 0.0..1.0); BinomialDistribution(null, n.toInt(), params[1])
        }
        Law.Poisson -> {
            checkDomain(a in 0.0..100_000.0)
            if (a == 0.0) BinomialDistribution(null, 0, 0.0) else PoissonDistribution(null, a, 1e-14, 100_000)
        }
        else -> null
    }
    if (discrete != null) {
        // Compute either tail directly. Subtracting two CDFs rounded to 1 would silently erase
        // a perfectly representable rare event, e.g. Binomial(100,.01) between 19 and 20.
        val lo = discrete.supportLowerBound; val hi = discrete.supportUpperBound
        fun tail(at: Double, right: Boolean = false): Double {
            val k = floor(at)
            if (k < lo) return if (right) 1.0 else 0.0
            if (k >= hi) return if (right) 0.0 else 1.0
            if (law == Law.Poisson) return if (right) Gamma.regularizedGammaP(k+1,a,1e-14,100_000)
                else Gamma.regularizedGammaQ(k+1,a,1e-14,100_000)
            val smallIsRight = k >= discrete.numericalMean
            val small = if (smallIsRight) Beta.regularizedBeta(params[1],k+1,a-k,1e-14,100_000)
                else if (k == 0.0) kotlin.math.exp(a*kotlin.math.ln1p(-params[1]))
                else Beta.regularizedBeta(1-params[1],a-k,k+1,1e-14,100_000)
            return if (right == smallIsRight) small else if (right && k == 0.0) -kotlin.math.expm1(a*kotlin.math.ln1p(-params[1])) else 1-small
        }
        return scalarReport(when (op) {
            Probability.Density -> if (x != floor(x) || x < lo || x > hi) 0.0 else discrete.probability(x.toInt())
            Probability.Cdf -> tail(x)
            Probability.Interval -> {
                val l = maxOf(kotlin.math.ceil(x),lo.toDouble()); val h = minOf(floor(intervalUpper),hi.toDouble())
                when { h < l -> 0.0; h == l -> discrete.probability(l.toInt()); l > discrete.numericalMean -> tail(l-1,true)-tail(h,true)
                    else -> tail(h)-tail(l-1) }.coerceIn(0.0,1.0)
            }
            Probability.Quantile -> {
                if (x == 1.0 && law == Law.Poisson && a > 0.0) return SheetReport("+∞")
                if (x == 1.0) return scalarReport(hi.toDouble())
                var left = lo; var right = hi
                while (left < right) {
                    val mid = left + (right-left)/2; val reached = if (x <= .5) tail(mid.toDouble()) >= x else tail(mid.toDouble(),true) <= 1-x
                    if (reached) right = mid else left = mid+1
                }
                left.toDouble()
            }
        })
    }
    val d: RealDistribution = when (law) {
        Law.Normal -> { checkDomain(params[1] > 0); NormalDistribution(null, a, params[1]) }
        Law.ChiSquare -> { checkDomain(a > 0 && a <= 100_000); ChiSquaredDistribution(null, a, 1e-12) }
        else -> fail(MathIssue.Syntax)
    }
    val value = when (op) {
        Probability.Density -> if (law == Law.ChiSquare && x == 0.0) when { a < 2 -> Double.POSITIVE_INFINITY; a == 2.0 -> 0.5; else -> 0.0 } else d.density(x)
        Probability.Cdf -> d.cumulativeProbability(x)
        Probability.Interval -> when {
            d is NormalDistribution -> d.probability(x, intervalUpper)
            x > a -> Gamma.regularizedGammaQ(a/2, x/2) - Gamma.regularizedGammaQ(a/2, intervalUpper/2)
            else -> d.cumulativeProbability(intervalUpper) - d.cumulativeProbability(x)
        }.coerceIn(0.0,1.0)
        Probability.Quantile -> d.inverseCumulativeProbability(x)
    }
    if (value.isInfinite() && (op == Probability.Quantile && (x == 0.0 || x == 1.0) || op == Probability.Density && x == 0.0 && law == Law.ChiSquare && a < 2))
        return SheetReport(if (value > 0) "+∞" else "−∞")
    return scalarReport(value)
}
