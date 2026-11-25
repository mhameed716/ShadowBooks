package com.shadowbooks.ui.reader

import kotlin.math.roundToInt

internal enum class PageFit { WholePage, PageWidth }

/** All coordinates are physical pixels, independent of the direction of the Arabic controls. */
internal data class PageLayout(val width: Float, val height: Float, val slotHeight: Float) {
    val top: Float get() = (slotHeight - height) / 2f

    fun left(viewWidth: Int, panFraction: Float): Float {
        val space = viewWidth - width
        return if (space >= 0f) space / 2f else space * panFraction.coerceIn(0f, 1f)
    }
}

internal fun layoutPage(viewWidth: Int, viewHeight: Int, aspect: Float, fit: PageFit, zoom: Float): PageLayout {
    val ratio = aspect.takeIf { it.isFinite() && it > 0f } ?: 0.707f
    val width = when (fit) {
        PageFit.WholePage -> minOf(viewWidth.toFloat(), viewHeight * ratio)
        PageFit.PageWidth -> viewWidth.toFloat()
    } * zoom
    // A pathological PDF sheet must not overflow Compose's packed layout constraints.
    val boundedWidth = minOf(width, 30_000f * ratio)
    val height = boundedWidth / ratio
    return PageLayout(boundedWidth, height, maxOf(viewHeight.toFloat(), height))
}

internal fun resizedPageOffset(before: PageLayout, localY: Float, after: PageLayout, viewportHeight: Int): Int {
    val fraction = ((localY - before.top) / before.height).coerceIn(0f, 1f)
    return (after.top + fraction * after.height - viewportHeight / 2f).roundToInt()
}

internal fun Int.arabicDigits(): String = toString().map { ('٠'.code + it.digitToInt()).toChar() }.joinToString("")

/** Accept Arabic-Indic, Eastern Arabic and Latin input; do not navigate on an invalid page. */
internal fun pageIndexForInput(text: String, pageCount: Int): Int? {
    val digits = buildString {
        for (c in text.trim()) append(c.digitToIntOrNull() ?: return null)
    }
    return digits.toIntOrNull()?.takeIf { it in 1..pageCount }?.minus(1)
}
