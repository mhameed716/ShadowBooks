package com.shadowbooks.calculator

import org.apache.commons.math3.exception.MathIllegalArgumentException
import org.apache.commons.math3.exception.MathIllegalStateException

/** UI-neutral descriptions, examples and dispatch. A task cannot be listed without its calculation. */
internal enum class WorkGroup(val title: String) {
    Formula("تعويض في صيغة · CALC"), Complex("الأعداد المركّبة · CMPLX"), Equations("المعادلات والأنظمة · EQN"),
    Matrix("المصفوفات · MAT"), Vector("المتجهات · VCT"), Statistics("الإحصاء والانحدار · STAT"),
    Distribution("التوزيعات الاحتمالية · DIST"), Table("جدول الدوال · TABLE"), Graph("الرسم البياني · GRAPH"),
    Exact("الكسور والأعداد الصحيحة"), Conversion("تحويل الوحدات · CONV"), Constants("الثوابت الفيزيائية · CONST"),
}
internal const val FIELD_LIMIT = 1024
internal data class InputField(val label: String, val example: String, val multiline: Boolean = false)
internal data class SheetTask(
    val id: String, val title: String, val hint: String, val fields: List<InputField>,
    val compute: SheetContext.(List<String>) -> SheetReport,
)
internal data class SheetReport(
    val text: String, val scalar: Double? = null, val reuse: String? = null,
    val fraction: Pair<String, String>? = null, val plot: FunctionPlot? = null, val problem: Boolean = false,
)
internal fun fmt(n: Double) = formatNumber(finite(n))
internal fun scalarReport(value: Double, unit: String = "") = SheetReport((fmt(value) + " " + unit).trimEnd(), scalar = value)
internal class InputError(message: String) : IllegalArgumentException(message)
internal fun need(ok: Boolean, message: String) { if (!ok) throw InputError(message) }

internal class SheetContext(val angle: AngleUnit, val ans: Double, val registers: List<Double>, val previousAnswer: Double = 0.0) {
    private val budget = EvaluationBudget(1_500_000)
    fun formula(text: String, vars: List<Double> = registers) = RealFormula(text, angle, ans, vars, budget, previousAnswer)
    fun number(text: String) = formula(text).at()
    fun list(text: String, max: Int = 128): List<Double> = cellTokens(mathText(text), max).map(::number)
}

/** Top-level commas or whitespace separate cells. Commas/spaces inside e.g. logb(2, 8) belong to that cell. */
private fun cellTokens(text: String, max: Int): List<String> {
    val values = mutableListOf<String>(); var depth = 0; val token = StringBuilder(); var sinceComma = false
    fun flush() {
        if (token.isEmpty()) return
        if (values.size >= max) fail(MathIssue.Limit)
        values += token.toString(); token.clear(); sinceComma = true
    }
    for (c in text.trim()) {
        if (depth == 0 && (c == ',' || c.isWhitespace())) {
            flush(); if (c == ',') { if (!sinceComma) fail(MathIssue.Syntax); sinceComma = false }
        } else {
            token.append(c); if (c == '(') depth++; if (c == ')') depth--
            if (depth < 0) fail(MathIssue.Syntax)
        }
    }
    flush(); if (!sinceComma || depth != 0) fail(MathIssue.Syntax); return values
}

internal data class WorkRequest(val sheet: Worksheet, val unit: AngleUnit, val ans: Double, val registers: List<Double>, val previousAnswer: Double = 0.0) {
    fun run() = sheet.run(SheetContext(unit, ans, registers, previousAnswer))
}

internal data class Worksheet(
    val group: WorkGroup, val taskId: String, val values: List<String>,
    val submitted: Boolean = false, val report: SheetReport? = null,
) {
    val task: SheetTask get() = worksheetTasks.getValue(group).first { it.id == taskId }
    fun select(id: String): Worksheet {
        if (taskId == id) return this
        val other = initial(group, id)
        return other.copy(values = other.task.fields.map { f ->
            val old = task.fields.indexOfFirst { it.label == f.label }
            if (old >= 0 && values[old] != task.fields[old].example) values[old] else f.example
        })
    }
    fun edit(index: Int, text: String): Worksheet {
        if (text.length > FIELD_LIMIT || index !in values.indices) return this
        return copy(values = values.toMutableList().also { it[index] = text }, submitted = false, report = null)
    }
    fun request() = copy(submitted = true, report = null)
    fun run(context: SheetContext): SheetReport {
        return try {
            if (values.size != task.fields.size || values.any { it.length > FIELD_LIMIT }) fail(MathIssue.Limit)
            task.compute(context, values)
        } catch (e: CalculationError) {
            errorReport(when (e.issue) {
                MathIssue.Syntax -> "تحقّق من الصيغة والفواصل وعدد القيم."
                MathIssue.DivisionByZero -> "لا يمكن القسمة على صفر."
                MathIssue.Domain -> "القيمة خارج مجال هذه العملية أو خارج المدى العددي."
                MathIssue.Limit -> "بلغ الحساب حدّ الحجم أو الدقّة. جرّب مدخلات أو مجالًا أصغر."
            })
        } catch (e: InputError) {
            errorReport(e.message.orEmpty())
        } catch (_: MathIllegalStateException) {
            errorReport("لم تتقارب الطريقة العددية ضمن الحدّ المتاح.")
        } catch (_: MathIllegalArgumentException) {
            errorReport("تحقّق من الأبعاد ومجال القيم.")
        } catch (_: ArithmeticException) {
            errorReport("النتيجة غير معرّفة لهذه القيم.")
        } catch (_: IllegalArgumentException) {
            // NumberFormatException, require() failures and other library argument checks
            // must surface as an in-sheet problem, never as an app crash.
            errorReport("تحقّق من الأبعاد ومجال القيم.")
        } catch (_: RuntimeException) {
            errorReport("تعذّر إتمام هذا الحساب لهذه القيم.")
        }
    }
    private fun errorReport(message: String) = SheetReport(message, problem = true)
    companion object {
        fun initial(group: WorkGroup, id: String? = null): Worksheet {
            val tasks = worksheetTasks.getValue(group); val task = tasks.firstOrNull { it.id == id } ?: tasks.first()
            return Worksheet(group, task.id, task.fields.map { it.example })
        }
    }
}
