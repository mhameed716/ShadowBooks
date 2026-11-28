package com.shadowbooks.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.shadowbooks.data.LastReadStore
import kotlin.math.roundToInt

/** Small translucent circle; the invisible 48dp touch target stays accessible. */
@Composable
fun ContinueReadingLayer(onContinue: () -> Unit) {
    val context = LocalContext.current
    LastReadStore.peek(context)
    val lastRead = LastReadStore.lastRead ?: return
    val density = LocalDensity.current
    var xFraction by rememberSaveable { mutableFloatStateOf(1f) }
    var yFraction by rememberSaveable { mutableFloatStateOf(0.5f) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val maxX = with(density) { (maxWidth - 48.dp).coerceAtLeast(0.dp).toPx() }
        val maxY = with(density) { (maxHeight - 48.dp).coerceAtLeast(0.dp).toPx() }
        val label = "متابعة قراءة ${lastRead.title}، صفحة ${lastRead.page + 1}"
        Box(
            modifier = Modifier
                // Physical coordinates keep dragging natural in RTL as well as LTR.
                .absoluteOffset {
                    IntOffset(
                        (xFraction * maxX + dragX).coerceIn(0f, maxX).roundToInt(),
                        (yFraction * maxY + dragY).coerceIn(0f, maxY).roundToInt(),
                    )
                }
                .size(48.dp)
                .pointerInput(maxX, maxY) {
                    detectDragGestures(
                        onDragEnd = {
                            if (maxX > 0f) xFraction = (xFraction + dragX / maxX).coerceIn(0f, 1f)
                            if (maxY > 0f) yFraction = (yFraction + dragY / maxY).coerceIn(0f, 1f)
                            dragX = 0f
                            dragY = 0f
                        },
                        onDragCancel = { dragX = 0f; dragY = 0f },
                    ) { change, amount ->
                        // Consuming the drag cancels clickable's tap, not the next real tap.
                        change.consume()
                        dragX = (dragX + amount.x).coerceIn(-xFraction * maxX, (1f - xFraction) * maxX)
                        dragY = (dragY + amount.y).coerceIn(-yFraction * maxY, (1f - yFraction) * maxY)
                    }
                }
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = label, onClick = onContinue),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF123E59).copy(alpha = 0.18f))
                    .border(1.dp, Color(0xFF65D4FA).copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.MenuBook,
                    contentDescription = label,
                    tint = Color(0xFF3383A5).copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
