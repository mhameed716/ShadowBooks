package com.shadowbooks.ui.reader

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/** Single source of truth for swipes, the number field, bookmarks, pinch and zoom buttons. */
internal class ReaderViewport(
    val list: LazyListState,
    val count: Int,
    private val estimatedAspect: Float,
) {
    var size by mutableStateOf(IntSize.Zero)
        private set
    var fit by mutableStateOf(PageFit.WholePage)
        private set
    var zoom by mutableFloatStateOf(1f)
        private set
    var panFraction by mutableFloatStateOf(0.5f)
        private set
    private val aspects = mutableStateMapOf<Int, Float>()

    val currentIndex by derivedStateOf {
        val info = list.layoutInfo
        val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2
        (info.visibleItemsInfo.firstOrNull { middle < it.offset + it.size }?.index
            ?: list.firstVisibleItemIndex).coerceIn(0, count - 1)
    }

    fun noteAspect(index: Int, aspect: Float) { aspects[index] = aspect }

    fun layout(index: Int): PageLayout = layoutPage(size.width, size.height, aspects[index] ?: estimatedAspect, fit, zoom)

    /** Keep the same point on the same physical PDF sheet under the center of the resized pane. */
    fun resize(next: IntSize) {
        if (next == size || next.width <= 0 || next.height <= 0) return
        val middle = size.height / 2f
        val item = list.layoutInfo.visibleItemsInfo.firstOrNull { middle >= it.offset && middle < it.offset + it.size }
        val old = item?.takeIf { size.width > 0 && size.height > 0 }?.let { layout(it.index) }
        size = next
        if (item != null && old != null) {
            val offset = resizedPageOffset(old, middle - item.offset, layout(item.index), next.height)
            list.requestScrollToItem(item.index, offset)
        }
    }

    fun jump(index: Int) {
        list.requestScrollToItem(index.coerceIn(0, count - 1))
    }

    fun setPageFit(mode: PageFit) {
        val index = currentIndex
        fit = mode
        zoom = 1f
        panFraction = 0.5f
        jump(index)
    }

    fun dragHorizontally(index: Int, dx: Float) {
        val overflow = layout(index).width - size.width
        if (overflow > 0f) panFraction = (panFraction - dx / overflow).coerceIn(0f, 1f)
    }

    fun zoomBy(factor: Float) = transform(Offset(size.width / 2f, size.height / 2f), Offset.Zero, factor)

    /** Keep the PDF point under the fingers anchored while the lazy items change size. */
    fun transform(at: Offset, pan: Offset, factor: Float) {
        if (size.width <= 0 || size.height <= 0 || !factor.isFinite() || factor <= 0f) return
        val targetZoom = (zoom * factor).coerceIn(1f, 4f)
        if (targetZoom == zoom && pan == Offset.Zero) return
        val item = list.layoutInfo.visibleItemsInfo.firstOrNull { at.y < it.offset + it.size } ?: return
        val before = layout(item.index)
        val x = (at.x - before.left(size.width, panFraction)) / before.width
        val y = (at.y - item.offset - before.top) / before.height
        zoom = targetZoom
        val after = layout(item.index)
        val overflow = after.width - size.width
        panFraction = if (overflow > 0f) ((x * after.width - at.x - pan.x) / overflow).coerceIn(0f, 1f) else 0.5f
        val offset = after.top + y * after.height - at.y - pan.y
        list.requestScrollToItem(item.index, offset.roundToInt())
    }

    companion object {
        /** The LazyListState saves its physical index and pixel offset separately. Keep the same
         * page geometry while restoring that offset, even before its bitmap has loaded again. */
        fun saver(list: LazyListState, count: Int, estimate: Float) = listSaver<ReaderViewport, Any>(
            save = { view ->
                val index = list.firstVisibleItemIndex
                listOf(view.fit.name, view.zoom, view.panFraction, index, view.aspects[index] ?: estimate)
            },
            restore = { saved ->
                ReaderViewport(list, count, estimate).apply {
                    fit = PageFit.entries.firstOrNull { it.name == saved[0] } ?: PageFit.WholePage
                    zoom = (saved[1] as Float).coerceIn(1f, 4f)
                    panFraction = (saved[2] as Float).coerceIn(0f, 1f)
                    val index = saved[3] as Int
                    if (index in 0 until count) noteAspect(index, saved[4] as Float)
                }
            },
        )
    }
}
