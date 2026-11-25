package com.shadowbooks.ui.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.shadowbooks.R
import com.shadowbooks.calculator.*

@Composable
internal fun CalculatorOptions(s: CalculatorState, change: (CalculatorState) -> Unit) {
    val menu = s.menu ?: return
    val close = { change(s.copy(menu = null)) }
    AlertDialog(onDismissRequest = close, confirmButton = {
        TextButton(onClick = close) { Text(stringResource(R.string.reader_close)) }
    }, title = { Text(stringResource(when (menu) {
        CalcMenu.Mode -> R.string.calc_modes; CalcMenu.Tools -> R.string.calc_tools
        CalcMenu.Hyperbolic -> R.string.calc_hyperbolic; CalcMenu.Recall -> R.string.calc_recall; CalcMenu.Store -> R.string.calc_store
        CalcMenu.History -> R.string.calc_history; CalcMenu.ClearVariables -> R.string.calc_clear_vars; CalcMenu.Help -> R.string.calc_help
    })) }, text = {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            when (menu) {
                CalcMenu.Mode -> {
                    Text(stringResource(R.string.calc_unit), style = MaterialTheme.typography.labelLarge)
                    val units = mapOf(AngleUnit.DEG.name to stringResource(R.string.calc_degrees),
                        AngleUnit.RAD.name to stringResource(R.string.calc_radians), AngleUnit.GRAD.name to stringResource(R.string.calc_grads))
                    Choices(units.keys.toList(), label = { (if (it == s.unit.name) "✓ " else "") + units.getValue(it) }) {
                        change(s.chooseAngle(AngleUnit.valueOf(it)))
                    }
                    Text(stringResource(R.string.calc_modes_hint), style = MaterialTheme.typography.bodyMedium)
                    WorkGroup.entries.forEach { group ->
                        TextButton(onClick = { change(s.openSheet(group)) }, modifier = Modifier.fillMaxWidth()) {
                            Text(group.title, Modifier.fillMaxWidth())
                        }
                    }
                }
                CalcMenu.Tools -> {
                    Text(stringResource(R.string.calc_tools_hint), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.calc_templates), Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        textAlign = TextAlign.Left, fontFamily = FontFamily.Monospace)
                    Choices(listOf("X", ",", "π", "e", "∫dx", "d/dx", "SOLVE", "nPr", "nCr", "sum", "mixed",
                        "prod", "gcd", "lcm", "rand", "randint", "const", "conv", "polr", "pola", "recx", "recy",
                        "dms", "DEC", "⇤", "⇥")) { change(s.command(it)) }
                }
                CalcMenu.Help -> Text(stringResource(R.string.calc_key_help))
                CalcMenu.History -> {
                    if (s.history.isEmpty()) Text(stringResource(R.string.calc_empty_history))
                    for (i in s.history.indices.reversed()) TextButton(onClick = { change(s.chooseHistory(i)) }, modifier = Modifier.fillMaxWidth()) {
                        Text(prettyExpression(s.history[i]).text, textAlign = TextAlign.Left, fontFamily = FontFamily.Monospace)
                    }
                }
                CalcMenu.ClearVariables -> {
                    Text(stringResource(R.string.calc_clear_vars_hint))
                    TextButton(onClick = { change(s.clearVariables()) }) { Text(stringResource(R.string.calc_clear_vars)) }
                }
                CalcMenu.Hyperbolic -> Choices(listOf("sinh", "cosh", "tanh", "asinh", "acosh", "atanh")) { change(s.command(it)) }
                CalcMenu.Recall, CalcMenu.Store -> Choices(registerNames, label = { name ->
                    "$name\n${formatNumber(s.registers[registerNames.indexOf(name)])}"
                }) { change(s.chooseRegister(it)) }
            }
        }
    })
}

@Composable
private fun Choices(names: List<String>, label: (String) -> String = { it }, pick: (String) -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        names.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { name -> TextButton(onClick = { pick(name) }, modifier = Modifier.weight(1f)) {
                    Text(label(name), textAlign = TextAlign.Center)
                } }
            }
        }
    }
}
