package com.shadowbooks.ui.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.shadowbooks.R
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException

// White paper -> navy, black ink -> ivory. The file and cached bitmaps remain untouched.
private val nightFilter = ColorFilter.colorMatrix(
    ColorMatrix(floatArrayOf(
        -0.92f, 0f, 0f, 0f, 245f,
        0f, -0.85f, 0f, 0f, 239f,
        0f, 0f, -0.75f, 0f, 233f,
        0f, 0f, 0f, 1f, 0f,
    )),
)

@Composable
internal fun ReaderPages(
    document: ReaderDocument,
    view: ReaderViewport,
    title: String,
    night: Boolean,
    focus: Boolean,
    onReveal: () -> Unit,
) {
    val reveal = if (focus) Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClickLabel = stringResource(R.string.reader_reveal),
        onClick = onReveal,
    ) else Modifier
    Box(Modifier.fillMaxSize().onSizeChanged(view::resize).then(reveal).pointerInput(view) {
        // Intercept only multi-touch. One finger belongs to the native lazy list (drag/fling)
        // or to horizontal pan on an enlarged page; a pinch must not accidentally turn a page.
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var pinching = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } >= 2) {
                    pinching = true
                    val center = event.calculateCentroid(useCurrent = false)
                    if (center != Offset.Unspecified) view.transform(center, event.calculatePan(), event.calculateZoom())
                }
                if (pinching) event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }) {
        if (view.size.width > 0 && view.size.height > 0) {
            LazyColumn(state = view.list, modifier = Modifier.fillMaxSize()) {
                items(count = document.pageCount, key = { it }) { index ->
                    PdfPage(document, view, title, index, night)
                }
            }
            if (!focus && view.count > 1 && view.list.isScrollInProgress) {
                val tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                Canvas(Modifier.align(Alignment.CenterStart).fillMaxHeight().padding(vertical = 50.dp).width(3.dp)) {
                    val first = view.list.layoutInfo.visibleItemsInfo.firstOrNull() ?: return@Canvas
                    val fraction = (first.index - first.offset.toFloat() / first.size.coerceAtLeast(1)) / (view.count - 1)
                    val thumb = minOf(size.height, 24.dp.toPx())
                    drawRoundRect(tint, Offset(0f, (size.height - thumb) * fraction.coerceIn(0f, 1f)),
                        Size(size.width, thumb), CornerRadius(size.width / 2f))
                }
            }
        }
    }
}

@Composable
private fun PdfPage(document: ReaderDocument, view: ReaderViewport, title: String, index: Int, night: Boolean) {
    var page by remember(document, index) { mutableStateOf<RenderedPage?>(null) }
    var failed by remember(document, index) { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    // A second resolution tier improves enlarged text without rasterizing every pinch frame.
    val target = (view.size.width * if (view.zoom >= 1.5f) 2f else 1.15f).roundToInt().coerceIn(640, 2304)
    val bucket = ((target + 127) / 128) * 128
    LaunchedEffect(document, index, bucket, retry) {
        failed = false
        try {
            val rendered = document.page(index, bucket)
            view.noteAspect(index, rendered.aspect)
            page = rendered
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = true
        }
    }
    val layout = view.layout(index)
    val description = stringResource(R.string.reader_page_description, title, (index + 1).arabicDigits(), view.count.arabicDigits())
    Box(
        modifier = Modifier.fillMaxWidth().height(with(LocalDensity.current) { layout.slotHeight.toDp() })
            .clipToBounds().draggable(
                state = rememberDraggableState { dx -> view.dragHorizontally(index, dx) },
                orientation = Orientation.Horizontal,
                enabled = layout.width > view.size.width + 1,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val image = remember(page) { page?.bitmap?.asImageBitmap() }
        when {
            image != null -> Canvas(Modifier.fillMaxSize().semantics { contentDescription = description }) {
                drawImage(
                    image = image,
                    dstOffset = IntOffset(layout.left(view.size.width, view.panFraction).roundToInt(), layout.top.roundToInt()),
                    dstSize = IntSize(layout.width.roundToInt().coerceAtLeast(1), layout.height.roundToInt().coerceAtLeast(1)),
                    colorFilter = if (night) nightFilter else null,
                    filterQuality = FilterQuality.Medium,
                )
            }
            failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.reader_page_error))
                TextButton(onClick = { retry++ }) { Text(stringResource(R.string.reader_retry)) }
            }
            else -> CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
        }
    }
}
