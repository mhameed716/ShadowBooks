package com.shadowbooks.calculator

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import kotlin.math.abs
import org.apache.commons.math3.fraction.BigFraction

private const val EXACT_BITS = 2048
private fun bounded(q: BigFraction): BigFraction {
    if (maxOf(q.numerator.bitLength(), q.denominator.bitLength()) > EXACT_BITS) fail(MathIssue.Limit)
    return q
}

private fun exactLiteral(s: String): BigFraction {
    if ('(' in s) {
        val head = s.substringBefore('('); val period = s.substringAfter('(').dropLast(1)
        val prefix = head.replace(".", "").ifEmpty { "0" }.toBigInteger()
        val nines = BigInteger.TEN.pow(period.length) - BigInteger.ONE
        return bounded(BigFraction(prefix * nines + period.toBigInteger(), BigInteger.TEN.pow(head.substringAfter('.').length) * nines))
    }
    val n = s.toBigDecimalOrNull() ?: fail(MathIssue.Syntax)
    if (n.scale() !in -600..600) fail(MathIssue.Limit)
    return bounded(if (n.scale() < 0) BigFraction(n.toBigIntegerExact()) else BigFraction(n.unscaledValue(), BigInteger.TEN.pow(n.scale())))
}
internal fun realLiteral(s: String): Double = if ('(' in s) approximate(exactLiteral(s)) else finite(s.toDoubleOrNull() ?: fail(MathIssue.Syntax))
internal fun approximate(q: BigFraction): Double = BigDecimal(q.numerator).divide(BigDecimal(q.denominator), MathContext.DECIMAL128).toDouble()

/** Keeps rational results exact throughout; this path never reconstructs a fraction from a Double. */
internal fun exactCalculation(text: String): BigFraction = MathNotation(text, object : Arithmetic<BigFraction> {
    override fun literal(text: String) = exactLiteral(text)
    override fun symbol(name: String): BigFraction = fail(MathIssue.Syntax)
    override fun unary(op: Char, value: BigFraction): BigFraction = when (op) {
        '-' -> value.negate(); '%' -> bounded(value.divide(100)); '²' -> bounded(value.pow(2)); '³' -> bounded(value.pow(3))
        '⁻' -> binary('/', BigFraction.ONE, value)
        '!' -> {
            val n = whole(value); checkDomain(n >= BigInteger.ZERO && n <= 300.toBigInteger()); var p = BigInteger.ONE
            for (i in 2..n.toInt()) p *= i.toBigInteger(); bounded(BigFraction(p))
        }
        else -> fail(MathIssue.Syntax)
    }
    override fun binary(op: Char, left: BigFraction, right: BigFraction): BigFraction = bounded(when (op) {
        '+' -> left.add(right); '-' -> left.subtract(right); '*' -> left.multiply(right)
        '/' -> { if (right.numerator.signum() == 0) fail(MathIssue.DivisionByZero); left.divide(right) }
        '%' -> { val d = whole(right).abs(); if (d.signum() == 0) fail(MathIssue.DivisionByZero); BigFraction(whole(left).mod(d)) }
        '^' -> {
            val e = whole(right); if (e.abs() > 1024.toBigInteger()) fail(MathIssue.Limit)
            if (e.signum() < 0 && left.numerator.signum() == 0) fail(MathIssue.DivisionByZero)
            val bits = maxOf(left.numerator.abs().bitLength(), left.denominator.bitLength())
            if ((bits - 1) * abs(e.toInt()) > EXACT_BITS) fail(MathIssue.Limit)
            left.pow(e.toInt())
        }
        else -> fail(MathIssue.Syntax)
    })
    override fun call(name: String, args: List<BigFraction>): BigFraction {
        if (name == "mod" && args.size == 2) return binary('%',args[0],args[1])
        if (name in setOf("ncr", "npr") && args.size == 2) {
            val n = whole(args[0]); val r = whole(args[1]); checkDomain(n.signum() >= 0 && r.signum() >= 0 && r <= n)
            if (n > 1_000_000.toBigInteger()) fail(MathIssue.Limit)
            val count = if (name == "ncr") minOf(r, n-r).toInt() else r.toInt()
            var v = BigInteger.ONE
            for (i in 1..count) {
                v *= (n.toInt()-i+1).toBigInteger(); if (name == "ncr") v /= i.toBigInteger()
                if (v.bitLength() > EXACT_BITS) fail(MathIssue.Limit)
            }
            return BigFraction(v)
        }
        if (name != "mixed" || args.size != 3) fail(MathIssue.Syntax)
        val (w, n, d) = args.map(::whole)
        checkDomain(d.signum() > 0 && n.signum() >= 0 && n < d)
        val f = BigFraction(n, d); return bounded(if (w.signum() < 0) args[0].subtract(f) else args[0].add(f))
    }
    private fun whole(q: BigFraction): BigInteger { checkDomain(q.denominator == BigInteger.ONE); return q.numerator }
}).parse()

internal fun mixedText(q: BigFraction): String {
    val (w, n) = q.numerator.abs().divideAndRemainder(q.denominator)
    val sign = if (q.numerator.signum() < 0) "−" else ""
    return sign + when { n.signum() == 0 -> "$w"; w.signum() == 0 -> "$n/${q.denominator}"; else -> "$w  $n/${q.denominator}" }
}
internal fun recurringText(q: BigFraction): String {
    val (w, rest) = q.numerator.abs().divideAndRemainder(q.denominator)
    val sign = if (q.numerator.signum() < 0) "−" else ""; if (rest.signum() == 0) return "$sign$w"
    var r = rest; val seen = mutableMapOf<BigInteger, Int>(); val digits = StringBuilder()
    while (r.signum() != 0 && r !in seen && digits.length < 160) {
        seen[r] = digits.length
        val (digit, next) = (r * BigInteger.TEN).divideAndRemainder(q.denominator); digits.append(digit); r = next
    }
    val cycle = seen[r]
    val tail = when { cycle != null -> "${digits.substring(0, cycle)}(${digits.substring(cycle)})"; r.signum() != 0 -> "$digits…"; else -> "$digits" }
    return "$sign$w.$tail"
}

/** Trial division is bounded (at most 500,000 odd candidates), exact integer input only. */
internal fun primeFactors(n: Long): String {
    checkDomain(n != 0L && n in -1_000_000_000_000L..1_000_000_000_000L)
    var left = abs(n); if (left == 1L) return n.toString()
    val parts = mutableListOf<String>(); if (n < 0) parts.add("−1")
    var p = 2L
    while (p*p <= left) {
        var k = 0; while (left % p == 0L) { k++; left /= p }
        if (k > 0) parts += if (k == 1) "$p" else "$p^$k"
        p = if (p == 2L) 3 else p + 2
    }
    if (left > 1) parts += "$left"
    return parts.joinToString(" × ")
}
