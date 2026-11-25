package com.shadowbooks.calculator

/** One bounded, non-executable grammar shared by the keypad, sampled functions, CMPLX and exact fractions. */
internal interface Arithmetic<T> {
    fun literal(text: String): T
    fun symbol(name: String): T
    fun unary(op: Char, value: T): T
    fun binary(op: Char, left: T, right: T): T
    fun call(name: String, args: List<T>): T
}

internal fun mathText(text: String): String = buildString(text.length) {
    for (c in text) append(when (c) {
        in '٠'..'٩' -> '0' + (c - '٠'); in '۰'..'۹' -> '0' + (c - '۰')
        '٫' -> '.'; '،' -> ','; '؛' -> ';'; '−' -> '-'; '×' -> '*'; '÷' -> '/'; 'θ' -> 'T'; else -> c
    })
}

internal fun fail(issue: MathIssue): Nothing = throw CalculationError(issue)
internal fun finite(value: Double): Double = if (value.isFinite()) value else fail(MathIssue.Domain)
internal fun checkDomain(ok: Boolean) { if (!ok) fail(MathIssue.Domain) }

internal class MathNotation<T>(raw: String, private val math: Arithmetic<T>) {
    init { if (raw.length > MAX_INPUT) fail(MathIssue.Limit) }
    private val text = mathText(raw)
    private var pos = 0
    private var depth = 0
    private val c: Char get() = text.getOrNull(pos) ?: '\u0000'
    private fun skip() { while (c.isWhitespace()) pos++ }
    private fun take(ch: Char): Boolean { skip(); if (c != ch) return false; pos++; return true }
    private fun takeWord(word: String): Boolean {
        if (!text.startsWith(word, pos)) return false
        val next = text.getOrNull(pos + word.length)
        // Keep implicit multiplication by a function (e.g. 2gcd(6,4)). The infix key adds spaces.
        if (next == '(' || next?.isLetter() == true) return false
        pos += word.length; return true
    }
    private fun want(ch: Char) { if (!take(ch)) fail(MathIssue.Syntax) }
    private inline fun nested(f: () -> T): T {
        if (++depth > 32) fail(MathIssue.Limit)
        return try { f() } finally { depth-- }
    }

    fun parse(): T {
        val value = sum(); skip(); if (pos != text.length) fail(MathIssue.Syntax)
        return value
    }
    private fun sum(): T {
        var a = product()
        while (true) a = when {
            take('+') -> math.binary('+', a, product()); take('-') -> math.binary('-', a, product()); else -> return a
        }
    }
    private fun product(): T {
        var a = unary()
        while (true) a = when {
            take('*') -> math.binary('*', a, unary()); take('/') -> math.binary('/', a, unary())
            take('∠') -> math.binary('∠', a, unary())
            takeWord("mod") -> math.binary('%', a, unary()) // distinct from unary postfix percent
            takeWord("nPr") -> math.call("npr", listOf(a, unary())); takeWord("nCr") -> math.call("ncr", listOf(a, unary()))
            takeWord("gcd") -> math.call("gcd", listOf(a, unary())); takeWord("lcm") -> math.call("lcm", listOf(a, unary()))
            c == '(' || c.isLetter() || c == '√' -> math.binary('*', a, unary()); else -> return a
        }
    }
    private fun unary(): T = when {
        take('-') -> math.unary('-', nested { unary() }); take('+') -> nested { unary() }; else -> power()
    }
    private fun power(): T {
        var a = atom()
        while (true) a = when {
            take('!') -> math.unary('!', a); take('%') -> math.unary('%', a)
            take('²') -> math.unary('²', a); take('³') -> math.unary('³', a)
            take('⁻') -> { want('¹'); math.unary('⁻', a) }; else -> break
        }
        return if (take('^')) math.binary('^', a, nested { unary() }) else a
    }
    private fun atom(): T {
        skip()
        if (take('(')) { val a = nested { sum() }; want(')'); return a }
        if (take('π')) return math.symbol("π")
        if (take('√')) return math.call("sqrt", listOf(nested { atom() }))
        if (c in '0'..'9' || c == '.') return number()
        if (text.startsWith("PreAns", pos)) { pos += 6; return math.symbol("PreAns") }
        if (text.startsWith("Ans", pos)) { pos += 3; return math.symbol("Ans") }
        if (c in 'A'..'Z') { val symbol = c.toString(); pos++; return math.symbol(symbol) }
        val start = pos
        while (c in 'a'..'z') pos++
        if (start == pos) fail(MathIssue.Syntax)
        val name = text.substring(start, pos)
        when (name) {
            "pi" -> return math.symbol("π"); "ans" -> return math.symbol("Ans")
            "x", "y", "t" -> return math.symbol(name.uppercase()); "e", "i" -> return math.symbol(name)
        }
        want('(')
        val args = mutableListOf<T>()
        if (!take(')')) {
            do {
                if (args.size == 3) fail(MathIssue.Syntax)
                args += nested { sum() }
            } while (take(','))
            want(')')
        }
        // A fraction template groups both full expressions, in all three arithmetic domains.
        return if (name == "frac" && args.size == 2) math.binary('/', args[0], args[1]) else math.call(name, args)
    }
    private fun number(): T {
        val start = pos
        while (c in '0'..'9') pos++
        if (c == '.') {
            pos++; while (c in '0'..'9') pos++
            if (c == '(') { // Recurring decimal: 0.1(6); integer 2(3) remains implicit multiplication.
                val mark = pos++
                while (c in '0'..'9') pos++
                if (pos > mark + 1 && c == ')') { pos++; return math.literal(text.substring(start, pos)) }
                pos = mark
            }
        }
        if (c == 'ᴇ' || c == 'E' || (c == 'e' && text.getOrNull(pos + 1) in exponentStart)) {
            pos++; if (c == '+' || c == '-') pos++
            val digits = pos; while (c in '0'..'9') pos++
            if (digits == pos) fail(MathIssue.Syntax)
        }
        return math.literal(text.substring(start, pos).replace('ᴇ', 'E'))
    }
    private companion object {
        val exponentStart = ('0'..'9').toSet() + setOf('+', '-')
    }
}
