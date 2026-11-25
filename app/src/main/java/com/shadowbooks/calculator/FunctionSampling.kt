package com.shadowbooks.calculator

import kotlin.math.*

/** Domain holes are expected (1/X, sqrt(X)); malformed formulae and exhausted budgets are NOT holes. */
private fun RealFormula.sample(x: Double, y: Double = 0.0, t: Double = 0.0): Double? = try { at(x,y,t) }
catch (e: CalculationError) { if (e.issue == MathIssue.Domain || e.issue == MathIssue.DivisionByZero) null else throw e }

internal fun SheetContext.table(first: String, second: String, steps: List<Double>): SheetReport {
    need(steps.size == 3, "أدخل البداية والنهاية والخطوة بالترتيب.")
    val (a,b,h) = steps; checkDomain(h != 0.0)
    val n = finite((b-a)/h); checkDomain(n >= 0 && (a == b || a + h != a))
    val last = floor(n + 4*Math.ulp(n)); if (last > 79) fail(MathIssue.Limit)
    val f = formula(first); val g = second.takeIf { it.isNotBlank() }?.let { formula(it) }
    return SheetReport(buildString {
        append(if (g == null) "X   |   f(X)" else "X   |   f(X)   |   g(X)")
        for (i in 0..last.toInt()) {
            val x = if (i > 0 && abs(i - n) <= 4*Math.ulp(n)) b else finite(a + i*h)
            append("\n${fmt(x)}   |   ${f.sample(x, registers[7])?.let(::fmt) ?: "—"}")
            if (g != null) append("   |   ${g.sample(x, registers[7])?.let(::fmt) ?: "—"}")
        }
    })
}

internal data class PlotPoint(val x: Double, val y: Double)
/** Segment coordinates are clipped to [0,1]², increasing upwards. */
internal data class PlotSegment(val from: PlotPoint, val to: PlotPoint)
internal data class PlotCurve(val label: String, val segments: List<PlotSegment>)
internal data class PlotRange(val low: Double, val high: Double) {
    val span = high - low
    fun fraction(v: Double) = (v-low)/span
    fun at(u: Double) = low + span*u
}
internal data class FunctionPlot(val x: PlotRange, val y: PlotRange, val curves: List<PlotCurve>)

internal fun SheetContext.graph(lines: String, horizontal: String, vertical: String, parameter: String): SheetReport {
    fun range(s: String): PlotRange {
        val v = list(s, 2); need(v.size == 2 && v[0] < v[1], "لكل مجال أدخل حدّين: الأصغر ثم الأكبر.")
        val r = PlotRange(v[0], v[1]); if (!r.span.isFinite() || r.at(1.0 / 512) == r.low) fail(MathIssue.Limit); return r
    }
    val xr = range(horizontal); val yr = range(vertical); val tr = range(parameter)
    val expressions = mathText(lines).lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    need(expressions.size in 1..4, "أدخل من منحنى واحد إلى أربعة منحنيات؛ كل منحنى في سطر.")
    val curves = expressions.map { line ->
        val side = line.split('='); val param = line.split(';')
        val segments = when {
            param.size == 2 -> {
                val fx = formula(param[0].removePrefix("x=").removePrefix("X=")); val fy = formula(param[1].removePrefix("y=").removePrefix("Y="))
                trace { u -> val t = tr.at(u); val x = fx.sample(registers[6], registers[7], t); val y = fy.sample(registers[6], registers[7], t)
                    if (x == null || y == null) null else PlotPoint(xr.fraction(x), yr.fraction(y)) }
            }
            side.size == 2 && side.first().trim().equals("r", ignoreCase = true) -> {
                val f = formula(side[1]); trace { u -> val t = tr.at(u); f.sample(registers[6], registers[7], t)?.let { r ->
                    PlotPoint(xr.fraction(r*circular("cos",t,angle)), yr.fraction(r*circular("sin",t,angle))) } }
            }
            side.size == 1 || (side.size == 2 && side.first().trim().equals("y", ignoreCase = true) && !side[1].contains('Y', ignoreCase = true)) -> {
                val f = formula(side.last()); trace { u -> f.sample(xr.at(u),registers[7])?.let { PlotPoint(u,yr.fraction(it)) } }
            }
            side.size == 2 -> {
                val lhs = formula(side[0]); val rhs = formula(side[1])
                contour { p -> val x = xr.at(p.x); val y = yr.at(p.y); val l = lhs.sample(x,y); val r = rhs.sample(x,y)
                    if (l == null || r == null) null else (l-r).takeIf(Double::isFinite) }
            }
            else -> fail(MathIssue.Syntax)
        }
        PlotCurve(line, segments)
    }
    need(curves.any { it.segments.isNotEmpty() }, "لا يوجد منحنى ظاهر في هذا المجال؛ تحقّق من الصيغة وحدود المحاور.")
    return SheetReport("X: ${fmt(xr.low)} … ${fmt(xr.high)}\nY: ${fmt(yr.low)} … ${fmt(yr.high)}", plot = FunctionPlot(xr,yr,curves))
}

/** Test the midpoint before joining samples. Refine curved spans and break at poles instead of drawing vertical bridges. */
private fun trace(sample: (Double) -> PlotPoint?): List<PlotSegment> {
    val pieces = mutableListOf<PlotSegment>()
    fun step(lo: Double, hi: Double, a: PlotPoint?, b: PlotPoint?, depth: Int) {
        if (a == null || b == null) return
        val mid = (lo+hi)/2; val m = sample(mid) ?: return
        if (max(abs(m.x-a.x/2-b.x/2), abs(m.y-a.y/2-b.y/2)) > .004) {
            if (depth < 3) { step(lo,mid,a,m,depth+1); step(mid,hi,m,b,depth+1) }; return
        }
        clip(a,b)?.let(pieces::add)
    }
    var a = sample(0.0)
    for (i in 1..192) { val hi = i/192.0; val b = sample(hi); step((i-1)/192.0,hi,a,b,0); a = b }
    return pieces
}
private fun clip(a: PlotPoint, b: PlotPoint): PlotSegment? {
    // Liang–Barsky, before converting to Canvas floats (large off-screen values must never reach Canvas).
    val dx = b.x-a.x; val dy = b.y-a.y; if (!dx.isFinite() || !dy.isFinite()) return null
    var lo = 0.0; var hi = 1.0
    fun bound(p: Double, q: Double): Boolean {
        if (p == 0.0) return q >= 0
        val r = q/p; if (p < 0) lo = max(lo,r) else hi = min(hi,r); return lo <= hi
    }
    if (!bound(-dx,a.x) || !bound(dx,1-a.x) || !bound(-dy,a.y) || !bound(dy,1-a.y)) return null
    fun at(u: Double) = PlotPoint((a.x+u*dx).coerceIn(0.0,1.0), (a.y+u*dy).coerceIn(0.0,1.0))
    return PlotSegment(at(lo),at(hi))
}

/** Marching squares; ambiguous saddle cells use the sign at their centre. Cache shared grid corners. */
private fun contour(eval: (PlotPoint) -> Double?): List<PlotSegment> {
    val n = 64; fun point(i: Int,j: Int) = PlotPoint(i.toDouble()/n,j.toDouble()/n)
    val grid = Array(n+1) { i -> Array(n+1) { j -> eval(point(i,j)) } }; val out = mutableListOf<PlotSegment>()
    for (i in 0 until n) for (j in 0 until n) {
        val v = listOf(grid[i][j] ?: continue, grid[i+1][j] ?: continue, grid[i+1][j+1] ?: continue, grid[i][j+1] ?: continue)
        if (v.all { it >= 0 } || v.all { it < 0 }) continue
        val p = listOf(point(i,j),point(i+1,j),point(i+1,j+1),point(i,j+1)); val cuts = mutableListOf<PlotPoint>()
        for (k in 0..3) {
            val l = (k+1)%4; if ((v[k] < 0) == (v[l] < 0)) continue
            edgeRoot(p[k],p[l],v[k],v[l],eval)?.let(cuts::add)
        }
        if (cuts.size == 2) out += PlotSegment(cuts[0],cuts[1])
        if (cuts.size == 4) {
            val center = eval(PlotPoint((i+.5)/n,(j+.5)/n)) ?: continue
            val shift = if ((center < 0) == (v[0] < 0)) 0 else 1
            out += PlotSegment(cuts[shift], cuts[shift+1]); out += PlotSegment(cuts[(shift+2)%4], cuts[(shift+3)%4])
        }
    }
    return out
}

/** A sign change at a pole is not a zero. Require the residual to improve on BOTH endpoints;
 * bisect when linear interpolation doesn't suffice (strong curvature near a crossing). */
private fun edgeRoot(a: PlotPoint, b: PlotPoint, va: Double, vb: Double, eval: (PlotPoint) -> Double?): PlotPoint? {
    if (va == 0.0) return a; if (vb == 0.0) return b
    val scale = max(abs(va),abs(vb)); val target = .03 * min(abs(va),abs(vb))
    var f = (va/scale)/(va/scale-vb/scale); var lo = 0.0; var hi = 1.0
    repeat(16) {
        val p = PlotPoint(a.x+f*(b.x-a.x),a.y+f*(b.y-a.y)); val v = eval(p) ?: return null
        if (abs(v) <= target) return p
        if ((v < 0) == (va < 0)) lo = f else hi = f
        f = (lo+hi)/2
    }
    return null
}
