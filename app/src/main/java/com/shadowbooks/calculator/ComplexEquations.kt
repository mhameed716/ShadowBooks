package com.shadowbooks.calculator

import kotlin.math.*
import org.apache.commons.math3.analysis.solvers.LaguerreSolver
import org.apache.commons.math3.complex.Complex

private fun checked(z: Complex): Complex {
    finite(z.real); finite(z.imaginary)
    // Unary minus of a positive real produces -0i in IEEE arithmetic. On the real axis use the
    // principal branch (sqrt(-4) = +2i, arg(-1) = +π); preserve genuine nonzero imaginary parts.
    return if (z.imaginary == 0.0) Complex(z.real) else z
}
private val Complex.zero get() = real == 0.0 && imaginary == 0.0
private fun Complex.realOnly(): Double { checkDomain(imaginary == 0.0); return real }
internal fun rectangular(r: Double, theta: Double, unit: AngleUnit): Complex = checked(Complex(
    r * circular("cos", theta, unit), r * circular("sin", theta, unit),
))
internal fun complexText(z: Complex): String {
    val a = z.real; val b = z.imaginary
    if (b == 0.0) return fmt(a)
    val im = if (abs(b) == 1.0) "i" else fmt(abs(b)) + "i"
    if (a == 0.0) return (if (b < 0) "−" else "") + im
    return fmt(a) + (if (b < 0) " − " else " + ") + im
}
internal fun complexReport(z: Complex, angle: AngleUnit): SheetReport {
    val arg = if (z.zero) "—" else fmt(argument(z.real, z.imaginary, angle)) + " " + angle.name
    val polar = fmt(z.abs()) + " ∠ " + arg
    return SheetReport("${complexText(z)}\nr ∠ θ = $polar", scalar = z.real.takeIf { z.imaginary == 0.0 },
        reuse = "(${z.real}+(${z.imaginary})*i)")
}
internal fun SheetContext.complexExpression(text: String): Complex = MathNotation(text, object : Arithmetic<Complex> {
    override fun literal(text: String) = Complex(realLiteral(text))
    override fun symbol(name: String): Complex = when (name) {
        "π" -> Complex(PI); "e" -> Complex(E); "i" -> Complex.I; "Ans" -> Complex(ans); "PreAns" -> Complex(previousAnswer)
        else -> { val i = registerNames.indexOf(name); if (i < 0) fail(MathIssue.Syntax); Complex(registers[i]) }
    }
    override fun unary(op: Char, value: Complex): Complex = checked(when (op) {
        '-' -> value.negate(); '!' -> Complex(factorial(value.realOnly())); '%' -> value.divide(100.0)
        '²' -> value.multiply(value); '³' -> value.multiply(value).multiply(value); '⁻' -> binary('/', Complex.ONE, value)
        else -> fail(MathIssue.Syntax)
    })
    override fun binary(op: Char, left: Complex, right: Complex): Complex = checked(when (op) {
        '+' -> left.add(right); '-' -> left.subtract(right); '*' -> left.multiply(right)
        '/' -> { if (right.zero) fail(MathIssue.DivisionByZero); left.divide(right) }
        '%' -> Complex(euclideanDivision(left.realOnly(),right.realOnly()).remainder.toDouble())
        '∠' -> rectangular(left.realOnly(), right.realOnly(), angle)
        '^' -> {
            // Integer powers stay on the axes; exp(log(i)*2) otherwise leaves a spurious imaginary part.
            if (right.imaginary == 0.0 && right.real == floor(right.real) && abs(right.real) <= 65536) {
                var p = abs(right.real.toInt()); var v = if (right.real < 0) binary('/', Complex.ONE, left) else left
                var result = Complex.ONE
                while (p > 0) { if (p % 2 == 1) result = result.multiply(v); p /= 2; if (p > 0) v = v.multiply(v) }; result
            } else if (left.zero && right.imaginary == 0.0 && right.real > 0) Complex.ZERO else left.pow(right)
        }
        else -> fail(MathIssue.Syntax)
    })
    override fun call(name: String, args: List<Complex>): Complex {
        if (args.size == 2) return checked(when (name) {
            "rec" -> rectangular(args[0].realOnly(), args[1].realOnly(), angle)
            "mod" -> binary('%',args[0],args[1])
            "root" -> binary('^', args[1], binary('/', Complex.ONE, args[0]))
            "logb" -> { checkDomain(!args[0].zero && !args[1].zero && !args[0].subtract(1.0).zero); binary('/', args[1].log(), args[0].log()) }
            else -> fail(MathIssue.Syntax)
        })
        if (args.size != 1) fail(MathIssue.Syntax)
        val z = args[0]; val rad = 2 * PI / angle.turn
        return checked(when (name) {
            "re" -> Complex(z.real); "im" -> Complex(z.imaginary); "abs" -> Complex(z.abs()); "conj" -> z.conjugate()
            "arg" -> Complex(argument(z.real,z.imaginary,angle)); "sqrt" -> z.sqrt(); "exp" -> z.exp(); "ln" -> z.log(); "log" -> z.log().divide(ln(10.0))
            "sin", "cos", "tan" -> if (z.imaginary == 0.0) Complex(circular(name, z.real, angle)) else {
                val a = z.multiply(rad); when (name) { "sin" -> a.sin(); "cos" -> a.cos(); else -> a.tan() }
            }
            "cot" -> if (z.imaginary == 0.0) Complex(divide(circular("cos",z.real,angle),circular("sin",z.real,angle)))
                else binary('/', z.multiply(rad).cos(), z.multiply(rad).sin())
            "acot" -> if (z.imaginary == 0.0) Complex(atan2(1.0,z.real)/rad) else Complex(PI/2).subtract(z.atan()).divide(rad)
            "asin" -> z.asin().divide(rad); "acos" -> z.acos().divide(rad); "atan" -> z.atan().divide(rad)
            "sinh" -> z.sinh(); "cosh" -> z.cosh(); "tanh" -> z.tanh()
            "cbrt" -> binary('^', z, Complex(1.0/3))
            "asinh" -> if (z.imaginary == 0.0) Complex(asinh(z.real)) else z.add(z.multiply(z).add(1.0).sqrt()).log()
            "acosh" -> z.add(z.add(1.0).sqrt().multiply(z.subtract(1.0).sqrt())).log()
            "atanh" -> Complex.ONE.add(z).log().subtract(Complex.ONE.subtract(z).log()).multiply(.5)
            else -> fail(MathIssue.Syntax)
        })
    }
}).parse()

/** Descending real coefficients. Stable quadratic (q/a, c/q); bounded Laguerre for higher degrees. */
internal fun polynomialRoots(coefficients: List<Double>): List<Complex> {
    need(coefficients.size in 2..13, "أدخل معاملات درجة من ١ إلى ١٢، من أعلى قوة إلى الحدّ الثابت.")
    need(coefficients.first() != 0.0, "معامل أعلى قوة يجب ألا يكون صفرًا.")
    val nonZero = coefficients.dropLastWhile { it == 0.0 }
    val zeros = List(coefficients.size - nonZero.size) { Complex.ZERO }
    val scale = nonZero.maxOf { abs(it) }; val a = nonZero.map { it / scale }
    if (a.zip(nonZero).any { (scaled, v) -> scaled == 0.0 && v != 0.0 }) fail(MathIssue.Limit)
    val roots = when (a.size) {
        1 -> emptyList(); 2 -> listOf(Complex(-a[1] / a[0]))
        3 -> {
            val (l,b,c) = a; val d = b*b - 4*l*c
            when {
                d < 0 -> { val z = Complex(-0.5*b/l, 0.5*sqrt(-d)/abs(l)); listOf(z,z.conjugate()) }
                d == 0.0 -> List(2) { Complex(-0.5*b/l) }
                else -> { val q = -0.5 * (b + Math.copySign(sqrt(d), b)); listOf(Complex(q/l), Complex(c/q)) }
            }
        }
        else -> LaguerreSolver(1e-14, 1e-14, 1e-15).solveAllComplex(a.reversed().toDoubleArray(), 0.0, 4096).toList()
    }
    for (z in roots) {
        checked(z)
        val residual = a.fold(Complex.ZERO) { s,n -> s.multiply(z).add(n) }.abs()
        val magnitude = a.fold(0.0) { s,n -> s * z.abs() + abs(n) }
        if (!residual.isFinite() || !magnitude.isFinite() || residual > 2e-8 * magnitude) fail(MathIssue.Limit)
    }
    return (roots + zeros).sortedWith(compareBy({ it.real }, { it.imaginary }))
}
