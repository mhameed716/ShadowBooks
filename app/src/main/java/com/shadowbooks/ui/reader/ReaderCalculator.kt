package com.shadowbooks.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.*
import com.shadowbooks.R
import com.shadowbooks.calculator.*
import kotlin.math.roundToInt

// Finish sampled from design/calculator-final-visual-choice.png. Paint the live controls;
// the reference bitmap is not an app asset. None of these brushes contributes to measurement.
private val casing = Brush.verticalGradient(
    0f to Color(0xFF1B333D), .4f to Color(0xFF1A323D), 1f to Color(0xFF152B36),
)
private val railColor = Color(0xFF192F3A)
private val rail = Brush.verticalGradient(0f to railColor, .95f to railColor, 1f to Color(0xFF324A54))
private val grip = Color(0xFF9AABB0)
private val header = Color(0xFF1B333D)
private val headerIcon = Color(0xFFB6E4E5)
private val pale = Color(0xFFE6EFF2)
private val statusInk = Color(0xFFB1C3C8)
private val expressionInk = Color(0xFFD5EAC0)
private val gold = Color(0xFFFFD35E)
private val orange = Color(0xFFF58C6D)

// Dark inset at the top of the LCD, a fine reflected edge at its foot.
private val glass = Color(0xFF151A1D)
private val screen = Brush.verticalGradient(0f to Color(0xFF0D171E), .033f to glass, 1f to glass)
private val bezel = Brush.verticalGradient(0f to Color(0xFF11212B), .97f to Color(0xFF172B34), 1f to Color(0xFF39535C))

private val keyShape = RoundedCornerShape(3.dp)
private val keyRim = Color(0xFF0B1B23)
// A narrow highlight inside a dark rim gives each cap relief without adding a shadow layer per key.
private fun capFinish(lip: Color, top: Color, bottom: Color) = Brush.verticalGradient(
    0f to lip, .022f to lip, .047f to top, 1f to bottom,
)
private val graphiteCap = capFinish(Color(0xFF4A626C), Color(0xFF263E4A), Color(0xFF1F3542))
private val shiftCap = capFinish(Color(0xFFFFDE91), Color(0xFFF1C157), Color(0xFFD79930))
private val alphaCap = capFinish(Color(0xFFE97750), Color(0xFFD25930), Color(0xFFB04222))
private val blueCap = capFinish(Color(0xFF4897DD), Color(0xFF247AD1), Color(0xFF195FA9))

@Composable
internal fun ReaderCalculator(
    state: CalculatorState, onKey: (CalcKey) -> Unit, onState: (CalculatorState) -> Unit,
    onResult: (WorkRequest, SheetReport) -> Unit, onFold: () -> Unit,
    onDrag: (Float) -> Unit, onNudge: (Float) -> Unit, modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.background(casing)) {
        val scale = LocalDensity.current.fontScale
        val stacked = maxWidth < 340.dp || scale > 1.35f
        val displayHeight = (maxHeight * .25f).coerceIn(76.dp * scale.coerceAtLeast(1f), 140.dp * scale.coerceAtLeast(1f))
        val keysHeight = (maxHeight - displayHeight - 54.dp).coerceAtLeast(180.dp)
        Column(Modifier.fillMaxSize()) {
            val resize = stringResource(R.string.calc_resize)
            val grow = stringResource(R.string.calc_grow); val shrink = stringResource(R.string.calc_shrink)
            Column(Modifier.fillMaxWidth().draggable(rememberDraggableState(onDrag), Orientation.Vertical)) {
                Box(Modifier.fillMaxWidth().height(16.dp).background(rail).semantics {
                    contentDescription = resize
                    customActions = listOf(CustomAccessibilityAction(grow) { onNudge(.08f); true },
                        CustomAccessibilityAction(shrink) { onNudge(-.08f); true })
                }, contentAlignment = Alignment.Center) {
                    Spacer(Modifier.width(42.dp).height(4.dp).background(grip, RoundedCornerShape(4.dp)))
                }
                Row(Modifier.fillMaxWidth().heightIn(min = 38.dp).background(header).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.reader_calculator), null, Modifier.size(18.dp), tint = headerIcon)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.calc_header), Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = pale)
                    Row(Modifier.clip(keyShape).clickable(role = Role.Button, onClick = onFold).padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.calc_fold), fontSize = 13.sp, color = pale)
                        Icon(Icons.Filled.ExpandMore, null, Modifier.size(17.dp), tint = pale)
                    }
                }
            }
            // A worksheet replaces only the lower pad. Neither the book nor the draggable header is recreated.
            val sheet = state.worksheet
            if (state.sheetVisible && sheet != null) {
                CalculatorWorksheet(state, sheet, onState, onResult, Modifier.fillMaxWidth().weight(1f))
            } else CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 2.dp)) {
                    CalculatorDisplay(state, displayHeight)
                    if (stacked) {
                        val rowHeight = 42.dp * scale.coerceAtLeast(1f)
                        KeyBank(scienceKeys, state, onKey, Modifier.fillMaxWidth().height(rowHeight * 5))
                        KeyBank(numberKeys, state, onKey, Modifier.fillMaxWidth().height(rowHeight * 4))
                    } else {
                        Row(Modifier.fillMaxWidth().height(keysHeight)) {
                            KeyBank(scienceKeys, state, onKey, Modifier.weight(.51f).fillMaxHeight())
                            Spacer(Modifier.width(2.dp))
                            KeyBank(numberKeys, state, onKey, Modifier.weight(.49f).fillMaxHeight())
                        }
                    }
                }
            }
        }
    }
    CalculatorOptions(state, onState)
}

@Composable
private fun CalculatorDisplay(state: CalculatorState, height: Dp) {
    val inputLabel = stringResource(R.string.calc_input); val outputLabel = stringResource(R.string.calc_output)
    val status = buildList {
        add(state.unit.name); add(when (state.format) {
            NumberFormat.Decimal -> "DEC"; NumberFormat.Fraction -> "FRAC"; NumberFormat.Engineering -> "ENG"; NumberFormat.Dms -> "DMS"; NumberFormat.Mixed -> "a b/c"
        })
        if (state.complete && ':' in state.expression) add("${state.statement + 1}/${1 + state.expression.count { it == ':' }}")
        if (state.registers.last() != 0.0) add("M")
        if (state.layer != Layer.Base) add(state.layer.name.uppercase())
    }.joinToString("   ")
    Column(Modifier.fillMaxWidth().height(height).background(screen).border(1.dp, bezel, RectangleShape)
        .padding(horizontal = 7.dp, vertical = 3.dp)) {
        Text(status, style = TextStyle(fontSize = 10.sp, fontFamily = FontFamily.Monospace), color = statusInk, maxLines = 1)
        val scroll = rememberScrollState()
        var viewport by remember { mutableIntStateOf(0) }; var caret by remember { mutableIntStateOf(0) }
        // The entry is stored in plain function words; the screen prints the keypad symbols.
        val pretty = remember(state.expression) { prettyExpression(state.expression) }
        val shownCursor = pretty.indexMap[state.cursor.coerceIn(0, state.expression.length)]
            .coerceIn(0, pretty.text.length)
        val input = buildAnnotatedString {
            append(pretty.text.take(shownCursor))
            if (!state.complete) withStyle(SpanStyle(color = orange)) { append('│') }
            append(pretty.text.drop(shownCursor))
            val caretShift = if (state.complete) 0 else 1
            fun styled(range: IntRange, style: SpanStyle) {
                var start = range.first
                var end = range.last + 1
                if (start >= shownCursor) start += caretShift
                if (end > shownCursor) end += caretShift
                if (end > start) addStyle(style, start, end)
            }
            val raisedStyle = SpanStyle(fontSize = 12.sp, baselineShift = BaselineShift.Superscript)
            val loweredStyle = SpanStyle(fontSize = 12.sp, baselineShift = BaselineShift.Subscript)
            pretty.raised.forEach { styled(it, raisedStyle) }
            pretty.lowered.forEach { styled(it, loweredStyle) }
        }
        LaunchedEffect(caret, viewport, scroll.maxValue) {
            val at = when {
                caret < scroll.value -> caret
                caret > scroll.value + viewport - 12 -> caret - viewport + 12
                else -> scroll.value
            }
            if (viewport > 0) scroll.scrollTo(at.coerceIn(0, scroll.maxValue))
        }
        Box(Modifier.fillMaxWidth().weight(1f).onSizeChanged { viewport = it.width }.horizontalScroll(scroll), contentAlignment = Alignment.CenterStart) {
            Text(input, modifier = Modifier.semantics { contentDescription = "$inputLabel: ${state.expression}" },
                softWrap = false, maxLines = 1, color = expressionInk,
                style = TextStyle(fontSize = 17.sp, fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr),
                onTextLayout = { layout -> if (layout.layoutInput.text == input) caret = layout.getCursorRect(shownCursor).left.roundToInt() })
        }
        val error = state.error
        if (error != null) {
            Text(stringResource(when (error) {
                MathIssue.Syntax -> R.string.calc_syntax; MathIssue.Domain -> R.string.calc_domain
                MathIssue.DivisionByZero -> R.string.calc_zero; MathIssue.Limit -> R.string.calc_limit
            }), Modifier.align(Alignment.End).semantics { liveRegion = LiveRegionMode.Polite },
                style = TextStyle(fontSize = 13.sp, textDirection = TextDirection.Rtl), color = Color(0xFFFFC4B6))
        } else {
            val value = when {
                state.complete && state.remainder != null -> "${formatNumber(state.answer)}  R ${state.remainder}"
                state.complete -> formatNumber(state.answer, state.format)
                state.expression.isEmpty() -> "0"
                else -> ""
            }
            Box(Modifier.align(Alignment.End).horizontalScroll(rememberScrollState()).clearAndSetSemantics {
                contentDescription = "$outputLabel: $value"; liveRegion = LiveRegionMode.Polite
            }) {
                if (state.format == NumberFormat.Fraction && '/' in value)
                    FractionBar(value.substringBefore('/'), value.substringAfter('/'), pale, compact = true)
                else Text(value, softWrap = false, maxLines = 1, color = pale,
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 21.sp, textDirection = TextDirection.Ltr))
            }
        }
    }
}

@Composable
private fun KeyBank(rows: List<List<CalcKey>>, state: CalculatorState, onKey: (CalcKey) -> Unit, modifier: Modifier) {
    Column(modifier) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().weight(1f)) {
                row.forEach { key ->
                    val shown = key.mark(state.layer)
                    val cap = when (key.base.action) { "SHIFT" -> shiftCap; "ALPHA" -> alphaCap; "DEL", "AC" -> blueCap; else -> graphiteCap }
                    val active = (key.base.action == "SHIFT" && state.layer == Layer.Shift) || (key.base.action == "ALPHA" && state.layer == Layer.Alpha)
                    val spoken = when (shown.action) {
                        "a/b" -> stringResource(R.string.calc_key_fraction); "√" -> stringResource(R.string.calc_key_root)
                        "xʸ" -> stringResource(R.string.calc_key_power); else -> shown.text
                    }
                    val description = listOfNotNull(spoken, key.shift?.let { "SHIFT: ${it.text}" }, key.alpha?.let { "ALPHA: ${it.text}" }).joinToString(". ")
                    // Keep the cap inscription in place even when its coloured action is armed.
                    val font = when { row.size == 5 -> 21.sp; key.base.text.length <= 2 -> 17.sp; else -> 14.sp }
                    Column(Modifier.weight(1f).fillMaxHeight().padding(.7.dp).clip(keyShape)
                        .clickable(role = Role.Button) { onKey(key) }
                        .clearAndSetSemantics {
                            contentDescription = description
                            if (key.base.action == "SHIFT" || key.base.action == "ALPHA") selected = active
                        }, horizontalAlignment = Alignment.CenterHorizontally) {
                        FittedKeyInk(Modifier.fillMaxWidth().height(10.dp * LocalDensity.current.fontScale)) {
                            key.shift?.let { KeyLabel(it, 8.sp, gold, small = true) }
                            key.alpha?.let { KeyLabel(it, 8.sp, orange, small = true) }
                        }
                        Box(Modifier.fillMaxWidth().weight(1f).background(cap, keyShape)
                            .border(if (active) 1.5.dp else .7.dp, if (active) Color.White else keyRim, keyShape),
                            contentAlignment = Alignment.Center) {
                            FittedKeyInk(Modifier.fillMaxSize().padding(horizontal = 1.5.dp, vertical = 1.dp)) {
                                KeyLabel(key.base, font, if (key.base.action == "SHIFT") glass else Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyLabel(mark: KeyMark, font: TextUnit, ink: Color, small: Boolean = false) {
    when (mark.action) {
        "a/b" -> FractionKeyGlyph(font, ink)
        "√", "∛", "ⁿ√" -> RadicalKeyGlyph(mark.action, font, ink)
        else -> Text(remember(mark.text) { keyInk(mark.text) }, color = ink, maxLines = 1, softWrap = false,
            style = TextStyle(fontSize = font, lineHeight = if (small) 9.sp else TextUnit.Unspecified,
                fontWeight = if (small) FontWeight.Medium else FontWeight.Normal,
                fontFamily = FontFamily.SansSerif, textDirection = TextDirection.Ltr))
    }
}
