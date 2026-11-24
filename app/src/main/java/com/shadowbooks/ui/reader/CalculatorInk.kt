package com.shadowbooks.ui.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import kotlin.math.roundToInt

/** Typeset the template boxes in the printed positions. No bitmap or font from the external app. */
internal fun keyInk(legend: String) = buildAnnotatedString {
    val raised = SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = .70.em)
    when (legend) {
        "x^□", "e^□" -> { append(legend.first()); withStyle(raised) { append('□') } }
        "Logₐx" -> { append("Log"); withStyle(SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = .75.em)) { append('a') }; append('x') }
        "°′″" -> append("° ′ ″") // Three distinct marks, not a run of apostrophes.
        "2nd", "i", "π" -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(legend) }
        else -> append(legend)
    }
}

@Composable
internal fun FractionKeyGlyph(font: TextUnit, ink: Color) {
    val em = with(LocalDensity.current) { font.toDp() }
    Canvas(Modifier.size(em * .62f, em)) {
        val w = size.width; val h = size.height
        drawRect(ink, Offset(.20f*w, .10f*h), Size(.60f*w, .29f*h))
        drawLine(ink, Offset(.04f*w, h/2), Offset(.96f*w,h/2), h*.06f)
        drawRect(ink, Offset(.20f*w, .61f*h), Size(.60f*w, .29f*h))
    }
}

/** The radicand is *under* the bar. The square-root key has a filled slot; the two indexed
 * templates have an open slot and a small 3 or index box at the upper left. */
@Composable
internal fun RadicalKeyGlyph(action: String, font: TextUnit, ink: Color) {
    val em = with(LocalDensity.current) { font.toDp() }
    val indexed = action != "√"
    Canvas(Modifier.size(em * if (indexed) 1.15f else .81f, em)) {
        val h = size.height; val x = if (indexed) h * .34f else 0f
        val radical = Path().apply {
            moveTo(x + .03f*h, .56f*h); lineTo(x + .15f*h, .51f*h); lineTo(x + .26f*h, .84f*h)
            lineTo(x + .40f*h, .18f*h); lineTo(x + .78f*h, .18f*h)
        }
        val stroke = Stroke(width = .065f * h)
        drawPath(radical, ink, style = stroke)
        val slot = Offset(x + .45f*h, .31f*h); val box = Size(.25f*h, .45f*h)
        if (!indexed) drawRect(ink, slot, box) else {
            drawRect(ink, slot, box, style = stroke)
            if (action == "ⁿ√") drawRect(ink, Offset(.06f*h,.12f*h), Size(.26f*h,.32f*h), style = stroke)
            else drawPath(Path().apply {
                moveTo(.055f*h,.15f*h); cubicTo(.34f*h,-.02f*h,.40f*h,.28f*h,.16f*h,.29f*h)
                cubicTo(.42f*h,.28f*h,.33f*h,.59f*h,.05f*h,.45f*h)
            }, ink, style = stroke)
        }
    }
}

/** Measure the whole legend (including both coloured marks) before fitting it. Only ink can shrink:
 * the established key cap, border, row weights and clickable area never change or clip a suffix. */
@Composable
internal fun FittedKeyInk(modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { children, bounds ->
        val marks = children.map { it.measure(Constraints()) }
        val width = bounds.maxWidth; val height = bounds.maxHeight // Both bounded by the existing cell.
        val gap = if (marks.size > 1) 2.dp.toPx() else 0f
        val naturalWidth = marks.sumOf { it.width } + gap
        val scale = minOf(1f, width / naturalWidth.coerceAtLeast(1f), height.toFloat() / (marks.maxOfOrNull { it.height } ?: 1).coerceAtLeast(1))
        val margin = (width - naturalWidth * scale) / (2 * marks.size.coerceAtLeast(1))
        layout(width, height) {
            var x = margin
            for (ink in marks) {
                val left = x.roundToInt(); val top = ((height - ink.height * scale) / 2).roundToInt()
                if (scale == 1f) ink.place(left, top) else ink.placeWithLayer(left, top) {
                    transformOrigin = TransformOrigin(0f, 0f); scaleX = scale; scaleY = scale
                }
                x += (ink.width + gap) * scale + 2 * margin
            }
        }
    }
}
