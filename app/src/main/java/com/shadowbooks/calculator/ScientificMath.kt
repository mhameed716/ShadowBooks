package com.shadowbooks.calculator

import kotlin.math.*

internal enum class AngleUnit(val turn: Double) { DEG(360.0), RAD(2 * PI), GRAD(400.0) }
internal enum class MathIssue { Syntax, DivisionByZero, Domain, Limit }
internal class CalculationError(val issue: MathIssue) : IllegalArgumentException(issue.name)
internal const val MAX_INPUT = 256
internal val registerNames = listOf("A", "B", "C", "D", "E", "F", "X", "Y", "M")

private typealias Expression = (Double) -> Double

internal class EvaluationBudget(private var remaining: Int = 65_536) {
    fun charge() { if (--remaining < 0) fail(MathIssue.Limit) }
}

/** Compiled once per function, not once per pixel. A budget is shared by the whole table/plot.
 * X is passed by value so a nested integral or derivative never changes the outer X binding. */
internal class RealFormula(
    text: String, private val angle: AngleUnit, private val ans: Double,
    private val registers: List<Double>, private val budget: EvaluationBudget = EvaluationBudget(),
    private val previousAnswer: Double = 0.0,
) : Arithmetic<Expression> {
    private var y = registers[7]
    private var t = 0.0
    private val tree = MathNotation(text, this).parse()

    fun at(x: Double = registers[6], y: Double = registers[7], t: Double = 0.0): Double {
        this.y = y; this.t = t
        return tree(x)
    }
    private fun node(f: Expression): Expression = { x -> budget.charge(); finite(f(x)) }
    override fun literal(text: String): Expression {
        val n = realLiteral(text); return node { n }
    }
    override fun symbol(name: String): Expression = when (name) {
        "X" -> node { it }; "Y" -> node { y }; "T" -> node { t }; "Ans" -> node { ans }; "PreAns" -> node { previousAnswer }
        "π" -> node { PI }; "e" -> node { E }
        else -> { val i = registerNames.indexOf(name); if (i < 0) fail(MathIssue.Syntax); node { registers[i] } }
    }
    override fun unary(op: Char, value: Expression) = node { x ->
        val v = value(x)
        when (op) {
            '-' -> -v; '!' -> factorial(v); '%' -> v / 100; '²' -> v * v; '³' -> v * v * v
            '⁻' -> divide(1.0, v); else -> fail(MathIssue.Syntax)
        }
    }
    override fun binary(op: Char, left: Expression, right: Expression): Expression {
        if (op == '∠') fail(MathIssue.Syntax)
        return node { x ->
            val a = left(x); val b = right(x)
            when (op) { '+' -> a+b; '-' -> a-b; '*' -> a*b; '/' -> divide(a,b); '^' -> a.pow(b)
                '%' -> euclideanDivision(a,b).remainder.toDouble(); else -> fail(MathIssue.Syntax) }
        }
    }
    override fun call(name: String, args: List<Expression>): Expression {
        val n = when (name) {
            "rand" -> 0
            "logb", "root", "diff", "solve", "ncr", "npr", "mod", "gcd", "lcm", "randint", "conv", "polr", "pola", "recx", "recy" -> 2
            "int", "dms", "sum", "prod", "mixed" -> 3
            "sin", "cos", "tan", "asin", "acos", "atan", "cot", "acot", "sinh", "cosh", "tanh", "asinh", "acosh", "atanh",
            "sqrt", "cbrt", "abs", "ln", "log", "exp", "const", "floor", "ceil" -> 1
            else -> fail(MathIssue.Syntax)
        }
        if (args.size != n) fail(MathIssue.Syntax)
        if (n == 0) return node { kotlin.random.Random.nextDouble() }
        val a = args[0]
        if (name == "int") return node { x -> integrate(a, args[1](x), args[2](x)) }
        if (name == "diff") return node { x -> derivative(a, args[1](x)) }
        if (name == "solve") return node { x -> solve(a, args[1](x)) }
        if (name == "sum" || name == "prod") return node { x ->
            val lo = safeInt(args[1](x)); val hi = safeInt(args[2](x)); checkDomain(lo <= hi)
            if (hi - lo > 10_000) fail(MathIssue.Limit)
            var total = if (name == "sum") 0.0 else 1.0
            for (k in lo..hi) { val v = a(k.toDouble()); total = finite(if (name == "sum") total + v else total * v) }
            total
        }
        if (name == "dms") return node { x ->
            val d = a(x); val m = args[1](x); val s = args[2](x)
            checkDomain(d == floor(d) && m == floor(m) && m in 0.0..59.0 && s >= 0 && s < 60)
            Math.copySign(abs(d) + m / 60 + s / 3600, d)
        }
        if (name == "mixed") return node { x ->
            val whole = safeInt(a(x)); val num = safeInt(args[1](x)); val den = safeInt(args[2](x))
            checkDomain(den > 0 && num in 0 until den)
            whole + (if (whole < 0) -1 else 1) * num.toDouble() / den
        }
        if (n == 2) return node { x ->
            val u = a(x); val v = args[1](x)
            when (name) {
                "logb" -> { checkDomain(u > 0 && u != 1.0 && v > 0); ln(v) / ln(u) }
                "root" -> { checkDomain(u != 0.0 && (v >= 0 || abs(u % 2) == 1.0)); if (v < 0) -(-v).pow(1/u) else v.pow(1/u) }
                "ncr", "npr" -> arrangements(u, v, name == "ncr")
                "mod" -> euclideanDivision(u,v).remainder.toDouble()
                "randint" -> { val lo = safeInt(u); val hi = safeInt(v); checkDomain(lo <= hi); kotlin.random.Random.nextLong(lo, hi + 1).toDouble() }
                "gcd", "lcm" -> {
                    val aInt = safeInt(u).toBigInteger().abs(); val bInt = safeInt(v).toBigInteger().abs()
                    val gcd = aInt.gcd(bInt)
                    val r = if (name == "gcd" || gcd.signum() == 0) gcd else aInt / gcd * bInt
                    if (r.bitLength() > 53) fail(MathIssue.Limit); r.toDouble()
                }
                "conv" -> convertUnit(u, catalogIndex(v))
                "polr" -> hypot(u,v); "pola" -> argument(u,v,angle)
                "recx" -> u * circular("cos", v, angle); "recy" -> u * circular("sin", v, angle)
                else -> fail(MathIssue.Syntax)
            }
        }
        val rad = 2 * PI / angle.turn
        return node { x ->
            val v = a(x)
            when (name) {
                "sin", "cos", "tan" -> circular(name, v, angle)
                "cot" -> divide(circular("cos",v,angle), circular("sin",v,angle)); "acot" -> atan2(1.0,v) / rad
                "asin" -> asin(v) / rad; "acos" -> acos(v) / rad; "atan" -> atan(v) / rad
                "sinh" -> sinh(v); "cosh" -> cosh(v); "tanh" -> tanh(v)
                "asinh" -> asinh(v); "acosh" -> acosh(v); "atanh" -> atanh(v)
                "sqrt" -> sqrt(v); "cbrt" -> Math.cbrt(v); "abs" -> abs(v)
                "log" -> log10(v); "ln" -> ln(v); "exp" -> exp(v); "floor" -> floor(v); "ceil" -> ceil(v)
                "const" -> physicalConstants[catalogIndex(v)].value
                else -> fail(MathIssue.Syntax)
            }
        }
    }
}

internal fun calculate(text: String, angle: AngleUnit = AngleUnit.DEG, ans: Double = 0.0,
    registers: List<Double> = List(registerNames.size) { 0.0 }, previousAnswer: Double = 0.0): Double =
    RealFormula(text, angle, ans, registers, previousAnswer = previousAnswer).at()

internal fun divide(a: Double, b: Double): Double {
    if (b == 0.0) fail(MathIssue.DivisionByZero)
    return a / b
}
internal fun safeInt(v: Double): Long {
    checkDomain(v.isFinite() && v == floor(v) && abs(v) <= 9_007_199_254_740_991.0)
    return v.toLong()
}
internal data class IntegerDivision(val quotient: Long, val remainder: Long)
internal fun euclideanDivision(a: Double, b: Double): IntegerDivision {
    val n = safeInt(a); val d = safeInt(b); if (d == 0L) fail(MathIssue.DivisionByZero)
    val r = Math.floorMod(n, abs(d)); return IntegerDivision((n-r)/d, r)
}
internal fun argument(x: Double, y: Double, unit: AngleUnit): Double {
    checkDomain(x != 0.0 || y != 0.0); return atan2(y, x) * (unit.turn / (2 * PI))
}

internal fun circular(name: String, value: Double, unit: AngleUnit): Double {
    val angle = value % unit.turn
    val quarter = angle / (unit.turn / 4)
    if (quarter == floor(quarter)) {
        val q = Math.floorMod(quarter.toInt(), 4)
        return when (name) {
            "sin" -> when (q) { 1 -> 1.0; 3 -> -1.0; else -> 0.0 }
            "cos" -> when (q) { 0 -> 1.0; 2 -> -1.0; else -> 0.0 }
            else -> if (q % 2 == 1) fail(MathIssue.Domain) else 0.0
        }
    }
    val radians = angle * (2 * PI / unit.turn)
    return when (name) { "sin" -> sin(radians); "cos" -> cos(radians); else -> tan(radians) }
}

internal fun factorial(n: Double): Double {
    if (n != floor(n) || n !in 0.0..170.0) fail(MathIssue.Domain)
    var v = 1.0; for (i in 2..n.toInt()) v *= i
    return v
}

private fun arrangements(n: Double, r: Double, combinations: Boolean): Double {
    if (n != floor(n) || r != floor(r) || n !in 0.0..10000.0 || r !in 0.0..n) fail(MathIssue.Domain)
    val k = (if (combinations) min(r, n - r) else r).toInt()
    var v = 1.0
    for (i in 1..k) v = finite(v * ((n - i + 1) / if (combinations) i.toDouble() else 1.0))
    return v
}

private fun derivative(f: Expression, at: Double): Double {
    val h = 1e-5 * max(abs(at), 1.0)
    return finite((f(at + h) - f(at - h)) / (2 * h))
}

/** Composite Simpson quadrature with refinement; don't present an unconverged estimate as exact. */
private fun integrate(f: Expression, a: Double, b: Double): Double {
    if (a == b) return 0.0
    var steps = 8; var previous = Double.NaN
    while (steps <= 1024) {
        val h = (b - a) / steps
        var sum = f(a) + f(b)
        for (i in 1 until steps) sum += (if (i % 2 == 0) 2 else 4) * f(a + i * h)
        val value = finite(sum * h / 3)
        if (abs(previous - value) <= 1e-9 * max(1.0, abs(value))) return value
        previous = value; steps *= 2
    }
    fail(MathIssue.Limit)
}

private fun solve(f: Expression, guess: Double): Double {
    var x = guess
    repeat(40) {
        val y = f(x)
        if (y == 0.0) return x
        val slope = derivative(f, x)
        if (slope == 0.0) fail(MathIssue.Domain)
        val step = y / slope
        x = finite(x - step)
        if (abs(step) <= 1e-12 * max(1.0, abs(x)) && abs(f(x)) < 1e-10) return x
    }
    fail(MathIssue.Limit)
}
