package com.shadowbooks.calculator

/**
 * Keypad-symbol view of an entry, for the calculator screen only. The stored expression keeps
 * its plain function words (`sqrt(`, `frac(`, `logb(` …) so evaluation, history and the saved
 * session are untouched; this printer re-expresses the same entry with the symbols printed on
 * the keys: √, ∛, a raised index for roots and powers, a lowered base for log, ° ′ ″ for dms,
 * and a slanted fraction bar with a raised numerator and a lowered denominator.
 *
 * [indexMap] translates every caret position in the stored expression to the matching caret
 * position in [text], so the screen cursor stays on the same token after re-symbolising.
 */
internal data class PrettyExpression(
    val text: String,
    val raised: List<IntRange>,
    val lowered: List<IntRange>,
    val indexMap: IntArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PrettyExpression) return false
        return text == other.text && raised == other.raised && lowered == other.lowered &&
            indexMap.contentEquals(other.indexMap)
    }

    override fun hashCode(): Int {
        var result = text.hashCode()
        result = 31 * result + raised.hashCode()
        result = 31 * result + lowered.hashCode()
        result = 31 * result + indexMap.contentHashCode()
        return result
    }
}

internal fun prettyExpression(expression: String): PrettyExpression = PrettyPrinter(expression).render()

private enum class SpanKind { None, Raised, Lowered }

private class PrettyPrinter(private val src: String) {
    private var pos = 0
    private val out = StringBuilder()
    private val raised = mutableListOf<IntRange>()
    private val lowered = mutableListOf<IntRange>()
    private val map = IntArray(src.length + 1) { -1 }

    fun render(): PrettyExpression {
        // Even an empty entry has one valid caret position: index 0 in both texts.
        map[0] = 0
        renderUntil(src.length)
        // Any internal position never anchored (inside a replaced word) snaps to the last
        // printed position at or before it, keeping the map monotonic.
        for (i in 1..src.length) if (map[i] < 0) map[i] = map[i - 1]
        map[src.length] = out.length
        return PrettyExpression(out.toString(), raised.toList(), lowered.toList(), map)
    }

    // ----- low-level emission with caret anchoring -----

    private fun anchor() {
        if (map[pos] < 0) map[pos] = out.length
    }

    private fun copy() {
        anchor()
        out.append(src[pos])
        pos++
        anchor()
    }

    private fun copyRun(count: Int) = repeat(count) { copy() }

    /** Consume [count] internal chars that print as [display] (or as nothing). */
    private fun replace(count: Int, display: String) {
        val start = out.length
        repeat(count) {
            if (map[pos] < 0) map[pos] = start
            pos++
        }
        out.append(display)
        if (pos <= src.length) map[pos] = out.length
    }

    private fun drop(count: Int = 1) = replace(count, "")

    private fun emit(text: String) {
        anchor()
        out.append(text)
    }

    // ----- structure -----

    private fun renderUntil(limit: Int) {
        while (pos < limit && pos < src.length) {
            val c = src[pos]
            when {
                c == '^' -> {
                    drop()
                    raisedPrimary()
                }
                c.isLetter() -> {
                    var end = pos
                    while (end < src.length && src[end].isLetter()) end++
                    if (end < src.length && src[end] == '(') renderFunction(end - pos) else copyRun(end - pos)
                }
                else -> copy()
            }
        }
    }

    private fun renderFunction(wordLength: Int) {
        val word = src.substring(pos, pos + wordLength)
        when (word) {
            "sqrt" -> {
                replace(wordLength, "√")
                copy() // '('
            }
            "cbrt" -> {
                replace(wordLength, "∛")
                copy() // '('
            }
            "exp" -> {
                // The eˣ key: print e with its parenthesised exponent raised.
                replace(wordLength, "e")
                val start = out.length
                val close = matchingClose(pos)
                when {
                    close < 0 -> copy()
                    simpleGroup(pos, close) -> {
                        drop() // '('
                        renderUntil(close)
                        drop() // ')'
                    }
                    else -> renderUntil(close + 1)
                }
                if (out.length > start) raised += start until out.length
            }
            "frac", "root", "logb", "dms", "mixed" -> {
                drop(wordLength)
                renderStructured(word)
            }
            else -> {
                copyRun(wordLength)
                copy() // '('
            }
        }
    }

    /** Split the argument list starting at the '(' under [pos]; close is -1 while still typing. */
    private fun splitArguments(openPos: Int): Pair<List<Pair<Int, Int>>, Int> {
        val args = mutableListOf<Pair<Int, Int>>()
        var depth = 0
        var argStart = openPos + 1
        var i = openPos
        while (i < src.length) {
            when (src[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) {
                        args += argStart to i
                        return args to i
                    }
                }
                ',' -> if (depth == 1) {
                    args += argStart to i
                    argStart = i + 1
                }
            }
            i++
        }
        args += argStart to src.length
        return args to -1
    }

    private fun renderStructured(word: String) {
        val (args, close) = splitArguments(pos)
        drop() // '(' itself is not printed for structured templates
        when (word) {
            "frac" -> {
                if (args.isNotEmpty()) renderArg(args[0], SpanKind.Raised, wrap = true)
                emit("⁄")
                if (args.size >= 2) {
                    dropCommaBefore(args[1])
                    renderArg(args[1], SpanKind.Lowered, wrap = true)
                }
            }
            "root" -> {
                if (args.isNotEmpty()) renderArg(args[0], SpanKind.Raised, wrap = true)
                emit("√")
                if (args.size >= 2) {
                    dropCommaBefore(args[1])
                    emit("(")
                    renderArg(args[1], SpanKind.None, wrap = false)
                    emit(")")
                }
            }
            "logb" -> {
                emit("log")
                if (args.isNotEmpty()) renderArg(args[0], SpanKind.Lowered, wrap = true)
                if (args.size >= 2) {
                    dropCommaBefore(args[1])
                    emit("(")
                    renderArg(args[1], SpanKind.None, wrap = false)
                    emit(")")
                }
            }
            "dms" -> {
                args.forEachIndexed { index, arg ->
                    renderArg(arg, SpanKind.None, wrap = false)
                    if (index < args.lastIndex) {
                        dropCommaBefore(args[index + 1])
                        emit(when (index) { 0 -> "°"; 1 -> "′"; else -> "″" })
                    } else if (args.size == 3) {
                        emit("″")
                    }
                }
            }
            "mixed" -> {
                if (args.isNotEmpty()) renderArg(args[0], SpanKind.None, wrap = false)
                if (args.size >= 2) {
                    dropCommaBefore(args[1])
                    emit(" ")
                    renderArg(args[1], SpanKind.Raised, wrap = true)
                    emit("⁄")
                    if (args.size >= 3) {
                        dropCommaBefore(args[2])
                        renderArg(args[2], SpanKind.Lowered, wrap = true)
                    }
                }
            }
        }
        if (close >= 0) drop() // ')'
    }

    private fun dropCommaBefore(nextArg: Pair<Int, Int>) {
        if (pos == nextArg.first - 1 && pos < src.length && src[pos] == ',') drop()
    }

    private fun renderArg(range: Pair<Int, Int>, kind: SpanKind, wrap: Boolean) {
        val start = out.length
        val useWrap = wrap && needsParens(range)
        if (useWrap) emit("(")
        renderUntil(range.second)
        if (useWrap) emit(")")
        if (out.length > start) when (kind) {
            SpanKind.Raised -> raised += start until out.length
            SpanKind.Lowered -> lowered += start until out.length
            SpanKind.None -> Unit
        }
    }

    /** A numerator/denominator/index containing a top-level operator needs its grouping shown. */
    private fun needsParens(range: Pair<Int, Int>): Boolean {
        var depth = 0
        for (i in range.first until range.second) {
            when (src[i]) {
                '(' -> depth++
                ')' -> depth--
                else -> if (depth == 0 && i > range.first && src[i] in "+−×÷^:∠") return true
            }
        }
        return false
    }

    private fun matchingClose(openPos: Int): Int {
        var depth = 0
        var i = openPos
        while (i < src.length) {
            when (src[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return -1
    }

    /** After '^', the following primary (group, call, signed number or symbol) prints raised. */
    private fun raisedPrimary() {
        val start = out.length
        if (pos < src.length) {
            if (src[pos] in "+−-") copy()
            when {
                pos >= src.length -> Unit
                src[pos] == '(' -> {
                    val close = matchingClose(pos)
                    when {
                        close < 0 -> copy()
                        simpleGroup(pos, close) -> {
                            drop() // '('
                            renderUntil(close)
                            drop() // ')'
                        }
                        else -> renderUntil(close + 1)
                    }
                }
                src[pos].isLetter() -> {
                    var end = pos
                    while (end < src.length && src[end].isLetter()) end++
                    if (end < src.length && src[end] == '(') {
                        val close = matchingClose(end)
                        if (close >= 0) renderUntil(close + 1) else renderUntil(end)
                    } else {
                        renderUntil(end)
                    }
                }
                else -> {
                    var consumed = false
                    while (pos < src.length && (src[pos].isDigit() || src[pos] == '.')) {
                        copy()
                        consumed = true
                    }
                    if (!consumed) copy()
                }
            }
        }
        if (out.length > start) raised += start until out.length
    }

    /** True when the parenthesised group holds one primary, so its parens can be dropped raised. */
    private fun simpleGroup(openPos: Int, closePos: Int): Boolean {
        var depth = 0
        for (i in openPos + 1 until closePos) {
            when (src[i]) {
                '(' -> depth++
                ')' -> depth--
                else -> if (depth == 0 && src[i] in "+−×÷^,:∠") return false
            }
        }
        return true
    }
}
