package com.shadowbooks.ui.reader

import android.os.Bundle
import androidx.compose.runtime.saveable.Saver
import com.shadowbooks.calculator.AngleUnit
import com.shadowbooks.calculator.CalculatorState
import com.shadowbooks.calculator.HISTORY_LIMIT
import com.shadowbooks.calculator.Layer
import com.shadowbooks.calculator.MAX_INPUT
import com.shadowbooks.calculator.MathIssue
import com.shadowbooks.calculator.NumberFormat
import com.shadowbooks.calculator.registerNames
import com.shadowbooks.calculator.FIELD_LIMIT
import com.shadowbooks.calculator.SheetReport
import com.shadowbooks.calculator.WorkGroup
import com.shadowbooks.calculator.Worksheet

/** Small primitive-only state in the same saved-state scope as this volume's reader. */
internal val calculatorSaver = Saver<CalculatorState, Bundle>(
    save = { s -> Bundle().apply {
        putString("text", s.expression); putInt("cursor", s.cursor); putBoolean("complete", s.complete)
        putDouble("ans", s.answer); putDouble("prevAns", s.previousAnswer); putDoubleArray("vars", s.registers.toDoubleArray())
        putInt("statement", s.statement); putString("remainder", s.remainder?.toString())
        putString("unit", s.unit.name); putString("format", s.format.name); putString("layer", s.layer.name)
        putString("error", s.error?.name); putStringArrayList("history", ArrayList(s.history))
        putInt("index", s.historyIndex); putString("draft", s.draft)
        putBoolean("sheetVisible", s.sheetVisible)
        s.worksheet?.let { w ->
            putString("group", w.group.name); putString("task", w.taskId); putStringArrayList("fields", ArrayList(w.values))
            putBoolean("submitted", w.submitted)
            // Never parcel canvas samples. Recreate those from the saved formula and ranges off the UI thread.
            w.report?.takeIf { it.plot == null && it.text.length <= 8192 }?.let { r ->
                putString("report", r.text); putBoolean("problem", r.problem); r.scalar?.let { putDouble("scalar", it) }
                putString("reuse", r.reuse?.takeIf { it.length <= FIELD_LIMIT })
                putString("num", r.fraction?.first); putString("den", r.fraction?.second)
            }
        }
    } },
    restore = { b ->
        val text = b.getString("text").orEmpty().take(MAX_INPUT)
        val history = b.getStringArrayList("history").orEmpty().takeLast(HISTORY_LIMIT).map { it.take(MAX_INPUT) }
        val sheet = WorkGroup.entries.firstOrNull { it.name == b.getString("group") }?.let { group ->
            val w = Worksheet.initial(group, b.getString("task")); val fields = b.getStringArrayList("fields")
            if (fields == null || w.taskId != b.getString("task") || fields.size != w.values.size || fields.any { it.length > FIELD_LIMIT }) w
            else w.copy(values = fields.toList(), submitted = b.getBoolean("submitted"), report = b.getString("report")?.let { text ->
                val num = b.getString("num"); val den = b.getString("den")
                SheetReport(text.take(8192), scalar = b.getDouble("scalar").takeIf { b.containsKey("scalar") && it.isFinite() },
                    reuse = b.getString("reuse"), fraction = if (num != null && den != null) num to den else null, problem = b.getBoolean("problem"))
            })
        }
        CalculatorState(
            worksheet = sheet, sheetVisible = sheet != null && b.getBoolean("sheetVisible"),
            expression = text, cursor = b.getInt("cursor").coerceIn(0, text.length),
            complete = b.getBoolean("complete"), answer = b.getDouble("ans").takeIf { it.isFinite() } ?: 0.0,
            previousAnswer = b.getDouble("prevAns").takeIf(Double::isFinite) ?: 0.0,
            statement = b.getInt("statement").coerceIn(0, text.count { it == ':' }),
            remainder = b.getString("remainder")?.toLongOrNull()?.takeIf { it >= 0 && b.getBoolean("complete") },
            registers = b.getDoubleArray("vars")?.takeIf { it.size == registerNames.size && it.all(Double::isFinite) }
                ?.toList() ?: List(registerNames.size) { 0.0 },
            unit = AngleUnit.entries.firstOrNull { it.name == b.getString("unit") } ?: AngleUnit.DEG,
            layer = Layer.entries.firstOrNull { it.name == b.getString("layer") } ?: Layer.Base,
            format = NumberFormat.entries.firstOrNull { it.name == b.getString("format") } ?: NumberFormat.Decimal,
            error = MathIssue.entries.firstOrNull { it.name == b.getString("error") }, history = history,
            historyIndex = b.getInt("index", -1).coerceIn(-1, history.lastIndex), draft = b.getString("draft").orEmpty().take(MAX_INPUT),
        )
    },
)
