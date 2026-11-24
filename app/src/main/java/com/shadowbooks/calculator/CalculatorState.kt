package com.shadowbooks.calculator

internal enum class Layer { Base, Shift, Alpha }
internal enum class CalcMenu { Mode, Tools, Hyperbolic, Recall, Store, ClearVariables, History, Help }
internal const val HISTORY_LIMIT = 12

/** Immutable, book-local session. Hiding the panel doesn't clear the entry, memory or answer. */
internal data class CalculatorState(
    val expression: String = "", val cursor: Int = 0, val answer: Double = 0.0,
    val previousAnswer: Double = 0.0, val statement: Int = 0, val remainder: Long? = null,
    val complete: Boolean = false, val error: MathIssue? = null,
    val unit: AngleUnit = AngleUnit.DEG, val format: NumberFormat = NumberFormat.Decimal,
    val layer: Layer = Layer.Base, val menu: CalcMenu? = null,
    val registers: List<Double> = List(registerNames.size) { 0.0 },
    val history: List<String> = emptyList(), val historyIndex: Int = -1, val draft: String = "",
    val worksheet: Worksheet? = null, val sheetVisible: Boolean = false,
) {
    fun openSheet(group: WorkGroup, task: String? = null): CalculatorState {
        val w = worksheet?.takeIf { it.group == group } ?: Worksheet.initial(group).let {
            if (group == WorkGroup.Graph && unit != AngleUnit.DEG) it.edit(3, if (unit == AngleUnit.RAD) "0 2*pi" else "0 400") else it
        }
        return copy(worksheet = if (task == null) w else w.select(task), sheetVisible = true, menu = null, layer = Layer.Base)
    }
    fun chooseAngle(newUnit: AngleUnit): CalculatorState = if (newUnit == unit) copy(menu = null) else
        copy(unit = newUnit, menu = null, error = null, complete = false, statement = 0, remainder = null, worksheet = worksheet?.copy(submitted = false, report = null))

    fun pendingWork(): WorkRequest? = worksheet?.takeIf { sheetVisible && it.submitted && it.report == null }
        ?.let { WorkRequest(it, unit, answer, registers, previousAnswer) }
    fun finishWork(request: WorkRequest, result: SheetReport): CalculatorState =
        if (pendingWork() == request) copy(worksheet = request.sheet.copy(report = result)) else this

    /** Structured results do not overwrite the real register. Handoff is explicit and uses the unrounded value. */
    fun useSheetAnswer(): CalculatorState {
        val n = worksheet?.report?.scalar?.takeIf(Double::isFinite) ?: return this
        val text = n.toString(); return edit(text, text.length).copy(answer = n, previousAnswer = answer, complete = true, sheetVisible = false, menu = null,
            worksheet = worksheet.copy(submitted = false, report = null), history = (history + text).takeLast(HISTORY_LIMIT))
    }
    private fun formulaEntry(): CalculatorState {
        val s = openSheet(WorkGroup.Formula); val w = checkNotNull(s.worksheet)
        if (expression.isBlank()) return s
        val expr = closedEntry(); val symbols = expr.replace("Ans", "").replace(Regex("(?<=[0-9.])E[+−-]?[0-9]+"), "")
        val names = registerNames.filter { it in symbols }
        if (names.isEmpty()) return evaluate()
        if (w.values[0] == expr && worksheet?.group == WorkGroup.Formula) return s
        val bindings = names.joinToString("; ") { "$it=${registers[registerNames.indexOf(it)]}" }
        return s.copy(worksheet = w.edit(0, expr).edit(1, bindings))
    }

    fun press(key: CalcKey, readClipboard: () -> String? = { null }, writeClipboard: (String) -> Unit = {}): CalculatorState {
        val action = key.mark(layer).action
        return when (action) {
            "SHIFT", "ALPHA" -> {
                val wanted = if (action == "SHIFT") Layer.Shift else Layer.Alpha
                copy(layer = if (layer == wanted) Layer.Base else wanted, error = null)
            }
            "COPY" -> {
                writeClipboard(if (!complete || remainder != null) expression else answer.toString())
                copy(layer = Layer.Base, error = null)
            }
            "PASTE" -> paste(readClipboard()) // only an explicit ALPHA+0 reads the system clipboard
            else -> command(action)
        }
    }

    private fun paste(text: String?): CalculatorState {
        val s = copy(layer = Layer.Base, error = null)
        if (text.isNullOrEmpty()) return s
        if (text.length > MAX_INPUT) return s.copy(error = MathIssue.Limit)
        val input = text.trim().replace(Regex("\\s+"), " ")
        if (input.isEmpty()) return s
        return try { s.insert(input, false) } catch (e: CalculationError) { s.copy(error = e.issue) }
    }

    /** Structured results keep their native fields; the user does not have to find the operation in MODE. */
    private fun sheetEntry(group: WorkGroup, id: String, text: String, run: Boolean): CalculatorState {
        val s = openSheet(group,id); val w = checkNotNull(s.worksheet).edit(0,text)
        return s.copy(worksheet = if (run) w.request() else w)
    }

    fun command(label: String): CalculatorState {
        val s = copy(layer = Layer.Base, menu = null, error = null)
        return try {
            when (label) {
                "MODE" -> s.copy(menu = CalcMenu.Mode)
                "2nd" -> s.copy(menu = CalcMenu.Tools)
                "HELP" -> s.copy(menu = CalcMenu.Help)
                "History" -> s.copy(menu = CalcMenu.History)
                "CLRv" -> s.copy(menu = CalcMenu.ClearVariables)
                "MATRIX" -> s.openSheet(WorkGroup.Matrix); "VECTOR" -> s.openSheet(WorkGroup.Vector)
                "STAT" -> s.openSheet(WorkGroup.Statistics); "DISTR" -> s.openSheet(WorkGroup.Distribution)
                "CONST" -> s.openSheet(WorkGroup.Constants); "CONV" -> s.openSheet(WorkGroup.Conversion)
                "CMPLX" -> s.openSheet(WorkGroup.Complex,"expression")
                "Pol" -> s.openSheet(WorkGroup.Complex,"pol"); "Rec" -> s.openSheet(WorkGroup.Complex,"rec")
                "FACT" -> if (expression.isBlank()) s.openSheet(WorkGroup.Exact,"factor") else
                    s.sheetEntry(WorkGroup.Exact,"factor",safeInt(s.value()).toString(),true)
                "solveEquation" -> if (expression.isBlank()) s.openSheet(WorkGroup.Equations,"root") else
                    s.sheetEntry(WorkGroup.Equations,"root",s.closedEntry(),false)
                "relation" -> s.insert("=",true)
                "a/b" -> if (s.afterOperand()) s.insert("÷", true) else s.template("frac(,)")
                "mod", "nPr", "nCr", "gcd", "lcm" -> if (s.afterOperand()) s.insert(" $label ", true)
                    else s.insert("${label.lowercase()}(", false)
                "hyp" -> s.copy(menu = CalcMenu.Hyperbolic)
                "RCL" -> s.copy(menu = CalcMenu.Recall)
                "STO" -> s.copy(menu = CalcMenu.Store)
                "AC" -> s.edit("", 0)
                "DEL" -> if (cursor == 0) s else s.edit(expression.removeRange(cursor - 1, cursor), cursor - 1)
                "←", "→", "⇤", "⇥" -> s.copy(complete = false, remainder = null, statement = 0, cursor = when (label) {
                    "←" -> (cursor - 1).coerceAtLeast(0); "→" -> (cursor + 1).coerceAtMost(expression.length)
                    "⇤" -> 0; else -> expression.length
                })
                "▲", "▼" -> s.recallHistory(if (label == "▲") -1 else 1)
                "CALC" -> if ('=' in expression) s.sheetEntry(WorkGroup.Equations,"root",s.closedEntry(),false) else s.formulaEntry()
                "=" -> s.evaluate(advance = true)
                "M+", "M−" -> {
                    val i = registerNames.indexOf("M"); val value = registers[i] + s.value() * if (label == "M+") 1 else -1
                    if (!value.isFinite()) throw CalculationError(MathIssue.Domain)
                    s.copy(registers = registers.toMutableList().also { it[i] = value })
                }
                "°′″" -> if (!s.afterOperand()) s.template("dms(,,)") else s.evaluate().copy(remainder = null,
                    format = if (complete && format == NumberFormat.Dms) NumberFormat.Decimal else NumberFormat.Dms)
                "S⇄D", "mixedFormat", "DEC", "ENG" -> s.evaluate().copy(remainder = null, format = when (label) {
                    "S⇄D" -> if (format in setOf(NumberFormat.Fraction, NumberFormat.Mixed)) NumberFormat.Decimal else NumberFormat.Fraction
                    "mixedFormat" -> if (format == NumberFormat.Mixed) NumberFormat.Fraction else NumberFormat.Mixed
                    "ENG" -> NumberFormat.Engineering; else -> NumberFormat.Decimal
                })
                else -> {
                    val token = when (label) {
                        "x⁻¹" -> "⁻¹"; "x!" -> "!"; "x²" -> "²"; "x³" -> "³"; "xʸ" -> "^"
                        "(−)" -> "−"; "Exp" -> "ᴇ"
                        "√" -> "sqrt("; "∛" -> "cbrt("; "ⁿ√" -> "root("; "logₐx" -> "logb("
                        "10ˣ" -> "10^("; "eˣ" -> "exp("; "∫dx" -> "int("; "d/dx" -> "diff("; "SOLVE" -> "solve("
                        "ln", "log", "abs", "sin", "cos", "tan", "asin", "acos", "atan", "sinh", "cosh", "tanh",
                        "asinh", "acosh", "atanh", "dms", "randint", "sum", "prod", "cot", "acot", "floor", "ceil", "const", "conv", "mixed",
                        "polr", "pola", "recx", "recy" -> "$label("
                        "rand" -> "rand()"
                        else -> label
                    }
                    s.insert(token, continueAnswer = label in continuationKeys)
                }
            }
        } catch (e: CalculationError) { s.copy(error = e.issue, complete = false) }
            .invalidateIfMemoryChanged(this)
    }

    private fun invalidateIfMemoryChanged(previous: CalculatorState): CalculatorState =
        if (worksheet != null && (answer != previous.answer || previousAnswer != previous.previousAnswer || registers != previous.registers))
            copy(worksheet = worksheet.copy(submitted = false, report = null)) else this

    fun chooseRegister(name: String): CalculatorState {
        val i = registerNames.indexOf(name)
        if (i < 0) return this
        return when (menu) {
            CalcMenu.Store -> try {
                copy(registers = registers.toMutableList().also { it[i] = value() }, menu = null, error = null)
            } catch (e: CalculationError) { copy(menu = null, error = e.issue, complete = false) }
            CalcMenu.Recall -> command(name)
            else -> this
        }.invalidateIfMemoryChanged(this)
    }

    fun clearVariables(): CalculatorState = if (menu != CalcMenu.ClearVariables) this else
        copy(registers = List(registerNames.size) { 0.0 }, menu = null).invalidateIfMemoryChanged(this)

    fun chooseHistory(index: Int): CalculatorState {
        if (index !in history.indices) return this
        val text = history[index]
        return copy(expression = text, cursor = text.length, complete = false, statement = 0, remainder = null,
            historyIndex = index, draft = if (historyIndex < 0) expression else draft, menu = null, error = null)
    }

    // Supply trailing parentheses per statement, never missing operands. Bound the entire restored entry.
    private fun closedEntry(): String = expression.split(':').joinToString(":") { part ->
        var count = 0
        for (c in part) { if (c == '(') count++; if (c == ')') count--; if (count < 0) fail(MathIssue.Syntax) }
        part + ")".repeat(count)
    }.also { if (it.length > MAX_INPUT) fail(MathIssue.Limit) }

    private fun real(text: String) = calculate(text, unit, answer, registers, previousAnswer)
    private fun division(text: String): IntegerDivision? {
        if ("÷R" !in text) return null
        val parts = text.split("÷R"); if (parts.size != 2) fail(MathIssue.Syntax)
        return euclideanDivision(real(parts[0]),real(parts[1]))
    }
    private fun value(): Double {
        if (complete) return answer
        if (expression.isEmpty()) return 0.0
        val part = closedEntry().substringBefore(':')
        return division(part)?.quotient?.toDouble() ?: real(part)
    }
    private fun evaluate(advance: Boolean = false): CalculatorState {
        val entry = closedEntry(); val parts = entry.split(':')
        if (complete && (!advance || statement >= parts.lastIndex)) return this
        if (parts.size > 1 && parts.any { it.isBlank() }) fail(MathIssue.Syntax)
        val i = if (complete) statement + 1 else statement.coerceIn(0,parts.lastIndex)
        val part = parts[i]
        if (parts.size == 1) {
            if ('=' in part) return sheetEntry(WorkGroup.Equations,"root",part,false)
            if ('∠' in part || imaginaryUnit.containsMatchIn(part.replace("Ans", "#"))) return sheetEntry(WorkGroup.Complex,"expression",part,true)
        }
        val div: IntegerDivision?
        val n: Double
        try {
            div = division(part)
            n = div?.quotient?.toDouble() ?: if (entry.isEmpty()) 0.0 else real(part)
        } catch (e: CalculationError) { return copy(error = e.issue, complete = false, statement = i, remainder = null) }
        val recent = if (entry.isEmpty() || history.lastOrNull() == entry) history else (history + entry).takeLast(HISTORY_LIMIT)
        return copy(expression = entry, cursor = if (cursor == expression.length) entry.length else cursor,
            previousAnswer = answer, answer = n, complete = true, statement = i, remainder = div?.remainder,
            history = recent, historyIndex = -1, draft = "")
    }
    private fun edit(text: String, caret: Int) = copy(expression = text, cursor = caret, complete = false,
        statement = 0, remainder = null, historyIndex = -1, draft = "", error = null, format = NumberFormat.Decimal)
    private fun afterOperand(): Boolean {
        if (complete) return true
        val left = expression.take(cursor).trimEnd()
        val last = left.lastOrNull() ?: return false
        return (last.isLetterOrDigit() || last in ")!%²³¹") && !infixAtEnd.containsMatchIn(left)
    }
    /** Start a multi-field entry at the numerator/degree. → crosses the preprinted separators. */
    private fun template(text: String): CalculatorState {
        val s = insert(text, false)
        return s.copy(cursor = s.cursor - text.length + text.indexOf('(') + 1)
    }
    private fun insert(token: String, continueAnswer: Boolean): CalculatorState {
        val base = if (complete) { if (continueAnswer) "Ans" else "" } else expression
        val at = if (complete) base.length else cursor
        // The register E immediately after a number must not be parsed as a scientific exponent.
        val previous = base.getOrNull(at - 1)
        val afterNumber = previous != null && (previous in '0'..'9' || previous == '.')
        val entry = when {
            token in setOf("E", "e") && afterNumber -> "×$token"
            previous in setOf('e', 'i') && token.firstOrNull() in 'a'..'z' -> "×$token"
            token == "ᴇ" && !afterNumber -> if (previous == null || previous in "(,+−×÷^") "1ᴇ" else "×10^"
            else -> token
        }
        if (base.length + entry.length > MAX_INPUT) throw CalculationError(MathIssue.Limit)
        return edit(base.substring(0, at) + entry + base.substring(at), at + entry.length)
    }
    private fun recallHistory(step: Int): CalculatorState {
        if (history.isEmpty() || (historyIndex < 0 && step > 0)) return this
        val next = ((if (historyIndex < 0) history.size else historyIndex) + step).coerceIn(0, history.size)
        if (next == history.size) return edit(draft, draft.length)
        return chooseHistory(next)
    }
    private companion object {
        val imaginaryUnit = Regex("(?<![a-z])i(?![a-z])")
        val infixAtEnd = Regex("(?: (?:mod|nPr|nCr|gcd|lcm)|÷R)$")
        val continuationKeys = setOf(":", "∠", "÷R", "+", "−", "×", "÷", "xʸ", "x²", "x³", "x⁻¹", "x!", "%", "Exp")
    }
}
