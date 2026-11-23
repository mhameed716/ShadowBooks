package com.shadowbooks.ui.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shadowbooks.R
import com.shadowbooks.calculator.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Edits and results stay inside the reader split. Heavy sampling/solving never runs in composition or on Main. */
@Composable
internal fun CalculatorWorksheet(s: CalculatorState, w: Worksheet, change: (CalculatorState) -> Unit,
    onResult: (WorkRequest, SheetReport) -> Unit, modifier: Modifier) {
    val notify by rememberUpdatedState(onResult); val request = s.pendingWork()
    LaunchedEffect(request) {
        if (request != null) notify(request, withContext(Dispatchers.Default) { request.run() })
    }
    val focus = LocalFocusManager.current
    var picker by remember(w.group) { mutableStateOf(false) }
    if (picker) TaskPicker(w, { picker = false }) { id -> change(s.copy(worksheet = w.select(id))); picker = false }
    Surface(modifier, color = MaterialTheme.colorScheme.surface) {
        key(w.group, w.taskId) {
            val scroll = rememberScrollState(); val tasks = worksheetTasks.getValue(w.group)
            LaunchedEffect(w.report) { if (w.report != null) scroll.scrollTo(0) }
            Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 10.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { focus.clearFocus(); change(s.copy(menu = CalcMenu.Mode)) }) { Text("MODE · ${s.unit.name}") }
                    TextButton(onClick = { focus.clearFocus(); change(s.copy(sheetVisible = false)) }) { Text(stringResource(R.string.calc_keypad)) }
                }
                Text(w.group.title, style = MaterialTheme.typography.titleMedium)
                if (tasks.size > 1) OutlinedButton(onClick = { focus.clearFocus(); picker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("${w.task.title}   ▾")
                } else Text(w.task.title, style = MaterialTheme.typography.labelLarge)
                w.report?.let { r -> WorksheetResult(r, onAnswer = { change(s.useSheetAnswer()) }, onReuse = {
                    val text = r.reuse; if (text != null && w.values.isNotEmpty()) change(s.copy(worksheet = w.edit(0,text)))
                }) }
                Text(w.task.hint, style = MaterialTheme.typography.bodyMedium)
                w.task.fields.forEachIndexed { index, field ->
                    var overLimit by remember { mutableStateOf(false) }
                    OutlinedTextField(value = w.values[index], onValueChange = { text ->
                        overLimit = text.length > FIELD_LIMIT
                        if (!overLimit) change(s.copy(worksheet = w.edit(index,text)))
                    }, modifier = Modifier.fillMaxWidth(), singleLine = !field.multiline, maxLines = if (field.multiline) 5 else 1,
                        isError = overLimit, label = { Text(field.label) },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr, textAlign = TextAlign.Left))
                    if (overLimit) Text(stringResource(R.string.calc_field_limit), color = MaterialTheme.colorScheme.error)
                }
                val busy = w.submitted && w.report == null
                Button(enabled = !busy, onClick = { focus.clearFocus(); change(s.copy(worksheet = w.request())) }) {
                    Text(stringResource(if (busy) R.string.calc_busy else if (w.group == WorkGroup.Graph) R.string.calc_draw else R.string.calc_run))
                }
            }
        }
    }
}

@Composable
private fun TaskPicker(w: Worksheet, close: () -> Unit, select: (String) -> Unit) {
    val tasks = worksheetTasks.getValue(w.group); var filter by remember { mutableStateOf("") }
    val matches = tasks.filter { mathText(it.title).contains(mathText(filter.trim()), ignoreCase = true) }
    AlertDialog(onDismissRequest = close, title = { Text(w.group.title) }, confirmButton = {
        TextButton(onClick = close) { Text(stringResource(R.string.reader_close)) }
    }, text = {
        Column(Modifier.fillMaxWidth()) {
            if (tasks.size > 12) OutlinedTextField(filter, { filter = it.take(64) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.calc_find_task)) })
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 290.dp)) {
                items(matches, key = { it.id }) { t -> TextButton(onClick = { select(t.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text((if (w.taskId == t.id) "✓  " else "") + t.title, Modifier.fillMaxWidth())
                } }
            }
            if (matches.isEmpty()) Text(stringResource(R.string.calc_no_match))
        }
    })
}

@Composable
private fun WorksheetResult(report: SheetReport, onAnswer: () -> Unit, onReuse: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp)) {
        Text(stringResource(R.string.calc_output), style = MaterialTheme.typography.labelLarge)
        report.plot?.let { PlotCanvas(it) }
        if (report.problem) Text(report.text, Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        else CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                report.fraction?.let { (n,d) -> FractionBar(n,d,MaterialTheme.colorScheme.onSurface) }
                SelectionContainer {
                    Text(report.text, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, softWrap = false,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr))
                }
            }
        }
        if (report.scalar != null) TextButton(onClick = onAnswer) { Text(stringResource(R.string.calc_use_answer)) }
        if (report.reuse != null && report.reuse.length <= FIELD_LIMIT) TextButton(onClick = onReuse) { Text(stringResource(R.string.calc_reuse)) }
    }
}

@Composable
internal fun FractionBar(n: String, d: String, ink: Color, compact: Boolean = false) {
    val size = if (compact) 12.sp else 16.sp
    val type = androidx.compose.ui.text.TextStyle(fontSize = size, lineHeight = size, fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr)
    Column(Modifier.width(IntrinsicSize.Max).clearAndSetSemantics { contentDescription = "$n/$d" }, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(n, style = type, color = ink, softWrap = false); Spacer(Modifier.fillMaxWidth().height(1.dp).background(ink))
        Text(d, style = type, color = ink, softWrap = false)
    }
}

private val curveColors = listOf(Color(0xFF90E9D8), Color(0xFFFFC76B), Color(0xFFBFA8FF), Color(0xFFFFA5B5))
@Composable
private fun PlotCanvas(plot: FunctionPlot) {
    val legend = plot.curves.mapIndexed { i,c -> "${i+1}: ${c.label}" }
    val description = stringResource(R.string.calc_plot_description, legend.joinToString("؛ "))
    Column(Modifier.fillMaxWidth().background(Color(0xFF142029)).padding(4.dp)) {
        Canvas(Modifier.fillMaxWidth().height(200.dp).semantics { contentDescription = description }) {
            // Equal units have equal screen lengths: a polar circle must not turn into an ellipse on a wide phone.
            val ratio = plot.x.span / plot.y.span
            val width = minOf(size.width.toDouble(), size.height * ratio).toFloat()
            val height = minOf(size.height.toDouble(), size.width / ratio).toFloat()
            val l = (size.width-width)/2; val b = (size.height+height)/2
            fun point(x: Double, y: Double) = Offset(l+x.toFloat()*width,b-y.toFloat()*height)
            val grid = Color(0xFF3B4B57); val axis = Color(0xFF99A7AB)
            for (i in 0..4) { val k = i/4.0; drawLine(grid,point(k,0.0),point(k,1.0)); drawLine(grid,point(0.0,k),point(1.0,k)) }
            val x0 = plot.x.fraction(0.0); val y0 = plot.y.fraction(0.0)
            if (x0 in 0.0..1.0) drawLine(axis,point(x0,0.0),point(x0,1.0),1.dp.toPx())
            if (y0 in 0.0..1.0) drawLine(axis,point(0.0,y0),point(1.0,y0),1.dp.toPx())
            plot.curves.forEachIndexed { i,c -> for ((a,z) in c.segments) drawLine(curveColors[i],point(a.x,a.y),point(z.x,z.y),1.5.dp.toPx()) }
        }
        plot.curves.forEachIndexed { i,c -> Text(legend[i] + if (c.segments.isEmpty()) "  (∅)" else "", color = curveColors[i],
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr)) }
    }
}
