package com.shadowbooks.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
// BoxWithConstraints hosts the floating pill; drag state lives in the layer.
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shadowbooks.R
import com.shadowbooks.data.LastReadStore
import kotlin.math.roundToInt

private val FabFont = FontFamily(
    Font(R.font.tajawal_regular),
    Font(R.font.tajawal_extrabold, FontWeight.ExtraBold),
)
private val FabBlue = Color(0xFF123E59)
private val FabCyan = Color(0xFF65D4FA)

/**
 * «متابعة القراءة» as a floating pill the student can drag to any corner,
 * top or bottom. It hovers over every screen except ملفات الهاتف (device
 * PDFs), and tapping it reopens the last book at the exact saved page.
 */
@Composable
fun ContinueReadingLayer(onContinue: () -> Unit) {
    val context = LocalContext.current
    LastReadStore.peek(context)
    val lastRead = LastReadStore.lastRead
    val density = LocalDensity.current
    var fabWidthPx by remember { mutableStateOf(0) }
    var fabHeightPx by remember { mutableStateOf(0) }
    // Resting fraction of the available travel: 0 = start/top, 1 = end/bottom.
    // Remembered across screens so the pill stays where the student left it.
    var offsetXFraction by rememberSaveable { mutableFloatStateOf(0f) }
    var offsetYFraction by rememberSaveable { mutableFloatStateOf(0f) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var dragMoved by remember { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (lastRead == null) return@BoxWithConstraints
        val marginPx = with(density) { 12.dp.toPx() }
        val travelX = (maxWidth - with(density) { fabWidthPx.toDp() }).coerceAtLeast(0.dp)
        val travelY = (maxHeight - with(density) { fabHeightPx.toDp() }).coerceAtLeast(0.dp)
        Column(
            Modifier.offset {
                val maxX = (with(density) { travelX.toPx() } - marginPx * 2).coerceAtLeast(0f)
                val maxY = (with(density) { travelY.toPx() } - marginPx * 2).coerceAtLeast(0f)
                IntOffset(
                    (marginPx + offsetXFraction * maxX + dragX).roundToInt().coerceAtLeast(0),
                    (marginPx + offsetYFraction * maxY + dragY).roundToInt().coerceAtLeast(0),
                )
            }
                .onSizeChanged { size -> fabWidthPx = size.width; fabHeightPx = size.height }
                // Free drag to any corner (top/bottom, either side). A plain
                // tap still counts as a click: only real movement drags.
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { dragMoved = false; dragX = 0f; dragY = 0f },
                        onDragEnd = {
                            val w = (size.width).toFloat()
                            val h = (size.height).toFloat()
                            val maxX = (constraints.maxWidth - w - marginPx * 2).coerceAtLeast(1f)
                val maxY = (constraints.maxHeight - h - marginPx * 2).coerceAtLeast(1f)
                            offsetXFraction = (offsetXFraction + dragX / maxX).coerceIn(0f, 1f)
                            offsetYFraction = (offsetYFraction + dragY / maxY).coerceIn(0f, 1f)
                            dragX = 0f
                            dragY = 0f
                        },
                        onDragCancel = { dragX = 0f; dragY = 0f },
                    ) { change, dragAmount ->
                        change.consume()
                        dragX += dragAmount.x
                        dragY += dragAmount.y
                        dragMoved = true
                    }
                }
                .shadow(8.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(FabBlue)
                .border(1.dp, FabCyan.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                .clickable(role = Role.Button, onClickLabel = "متابعة القراءة") {
                    // A drag must not also resume reading on release.
                    val wasDrag = dragMoved
                    dragMoved = false
                    if (!wasDrag) onContinue()
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Icon(
                Icons.Filled.MenuBook,
                contentDescription = null,
                tint = FabCyan,
                modifier = Modifier.size(20.dp),
            )
            Text(
                "متابعة القراءة",
                color = FabCyan,
                fontFamily = FabFont,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp,
            )
            Text(
                buildString {
                    append(lastRead.title)
                    append(" · صفحة ${lastRead.page + 1}")
                },
                color = Color(0xFFB9C6D8),
                fontFamily = FabFont,
                fontSize = 10.sp,
                maxLines = 1,
                modifier = Modifier.width(110.dp),
            )
        }
    }
}
