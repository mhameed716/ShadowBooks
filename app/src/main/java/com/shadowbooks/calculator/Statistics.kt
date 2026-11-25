package com.shadowbooks.calculator

import kotlin.math.*
import org.apache.commons.math3.linear.Array2DRowRealMatrix
import org.apache.commons.math3.linear.ArrayRealVector
import org.apache.commons.math3.linear.QRDecomposition

internal fun describe(values: List<Double>, counts: List<Double> = emptyList()): SheetReport {
    need(values.isNotEmpty() && (counts.isEmpty() || counts.size == values.size), "أدخل تكرارًا لكل قيمة أو اترك خانة التكرارات فارغة.")
    val weights = if (counts.isEmpty()) List(values.size) { 1L } else counts.map { safeInt(it).also { k -> checkDomain(k in 0..1_000_000L) } }
    val total = weights.sum(); checkDomain(total in 1..1_000_000L)
    val rows = values.zip(weights).filter { it.second > 0 }.sortedBy { it.first }
    var n = 0L; var mean = 0.0; var m2 = 0.0; var sum = 0.0; var sq = 0.0
    for ((x,k) in rows) {
        n += k; val delta = x - mean; mean += delta * (k.toDouble()/n)
        m2 += k * delta * (x - mean); sum += k*x; sq += k*x*x
    }
    fun order(index: Long): Double {
        var end = 0L; for ((x,k) in rows) { end += k; if (index < end) return x }; return rows.last().first
    }
    val median = order((n-1)/2) / 2 + order(n/2) / 2
    return SheetReport("n = $n\nΣx = ${fmt(sum)}\nΣx² = ${fmt(sq)}\nx̄ = ${fmt(mean)}\n" +
        "σx = ${fmt(sqrt(m2.coerceAtLeast(0.0)/n))}\nsx = ${if (n > 1) fmt(sqrt(m2.coerceAtLeast(0.0)/(n-1))) else "—"}\n" +
        "min = ${fmt(rows.first().first)}\nMed = ${fmt(median)}\nmax = ${fmt(rows.last().first)}")
}
internal enum class Regression(val equation: String) {
    Linear("y = a + bx"), Quadratic("y = a + bx + cx²"), Log("y = a + b ln(x)"), Exp("y = a exp(bx)"),
    Geometric("y = a bˣ"), Power("y = a xᵇ"), Inverse("y = a + b/x"),
}
internal fun regression(kind: Regression, xs: List<Double>, ys: List<Double>, predict: Double?): SheetReport {
    val degree = if (kind == Regression.Quadratic) 2 else 1
    need(xs.size == ys.size && xs.size > degree, "يلزم عدد متساوٍ من قيم X وY ونقاط كافية لهذا النموذج.")
    fun transform(x: Double) = when (kind) {
        Regression.Log, Regression.Power -> finite(ln(x)); Regression.Inverse -> finite(divide(1.0,x)); else -> x
    }
    val logY = kind in setOf(Regression.Exp, Regression.Power, Regression.Geometric)
    val x = xs.map(::transform); val y = if (logY) ys.map { finite(ln(it)) } else ys
    val centre = finite(x.average()); val range = finite(x.maxOf { abs(it - centre) })
    need(range > 0, "لا يمكن تحديد معاملات الانحدار من قيم X متطابقة.")
    val u = x.map { (it - centre) / range }
    val design = Array2DRowRealMatrix(u.map { if (degree == 1) doubleArrayOf(1.0,it) else doubleArrayOf(1.0,it,it*it) }.toTypedArray(), false)
    val qr = QRDecomposition(design, 1e-12).solver
    need(qr.isNonSingular, "النقاط لا تحدّد معاملات هذا النموذج.")
    val k = qr.solve(ArrayRealVector(y.toDoubleArray(), false)).toArray(); val k2 = k.getOrElse(2) { 0.0 }
    val h = centre / range; val intercept = k[0] - h*k[1] + h*h*k2
    val a = if (logY) exp(intercept) else intercept; val slope = (k[1] - 2*h*k2) / range
    val b = if (kind == Regression.Geometric) exp(slope) else slope
    fun fitted(value: Double): Double {
        val t = (transform(value)-centre)/range; val v = k[0] + t*(k[1] + t*k2); return finite(if (logY) exp(v) else v)
    }
    val yMean = ys.average(); val ss = ys.sumOf { (it-yMean).pow(2) }; val err = xs.indices.sumOf { (ys[it]-fitted(xs[it])).pow(2) }
    val result = predict?.let(::fitted)
    return SheetReport(buildString {
        append("${kind.equation}\na = ${fmt(a)}\nb = ${fmt(b)}")
        if (degree == 2) append("\nc = ${fmt(k2/range/range)}")
        if (degree == 1) {
            val ym = y.average(); val v = y.map { it - ym }; val uMean = u.average(); val un = norm(u.map { it-uMean }); val vn = norm(v)
            append("\nr = " + if (vn == 0.0) "—" else fmt(u.indices.sumOf { (u[it]-uMean)/un*(v[it]/vn) }.coerceIn(-1.0,1.0)))
        }
        append("\nR² = " + if (ss == 0.0) "—" else fmt(1 - finite(err) / finite(ss)))
        if (result != null) append("\nŷ = ${fmt(result)}")
    }, scalar = result)
}
