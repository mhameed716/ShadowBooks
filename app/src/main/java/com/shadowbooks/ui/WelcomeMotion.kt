package com.shadowbooks.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.imageResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.shadowbooks.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/** One clock for both halves of the welcome illustration. No timers on other screens or in the background. */
@Composable
internal fun rememberWelcomePhase(): State<Float> {
    val rest = remember { mutableFloatStateOf(0f) }
    val state by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    if (!state.isAtLeast(Lifecycle.State.RESUMED) || LocalInspectionMode.current) return rest
    // InfiniteTransition follows the system animator duration scale. Both endpoints are the still artwork.
    return rememberInfiniteTransition(label = "Welcome sky").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 6000, easing = LinearEasing)),
        label = "Background drift",
    )
}

internal enum class WelcomePlane(@DrawableRes val texture: Int, val width: Float, val height: Float) {
    Sky(R.drawable.welcome_sky, 953f, 672f),
    Night(R.drawable.welcome_night, 536f, 551f),
}

/** Decorative only: layout, accessibility and touch handling stay with the original foreground. */
@Composable
internal fun WelcomeBackdrop(plane: WelcomePlane, phase: State<Float>, modifier: Modifier) {
    val texture = ImageBitmap.imageResource(plane.texture).asAndroidBitmap()
    val atlas = if (plane == WelcomePlane.Sky) ImageBitmap.imageResource(R.drawable.welcome_motes).asAndroidBitmap() else null
    val flow = remember(plane) { BlueFlow(plane) }
    Canvas(modifier) {
        // Read the animated state in draw, not composition or measurement of the entry screen.
        drawIntoCanvas { flow.draw(it.nativeCanvas, size.width, size.height, texture, atlas, phase.value) }
    }
}

private fun ramp(start: Float, end: Float, value: Float): Float {
    val t = ((value - start) / (end - start)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** A small, reusable texture mesh: drift the existing light rays and bend the bottom blue bands.
 * All displacements are prepared once. There is no video player, bitmap allocation or decoding per frame. */
private class BlueFlow(private val plane: WelcomePlane) {
    private val cols = 24
    private val rows = 16
    private val origin = FloatArray((cols + 1) * (rows + 1) * 2)
    private val drift = FloatArray(origin.size)
    private val sway = FloatArray(origin.size)
    private val vertices = FloatArray(origin.size)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val src = Rect()
    private val dst = RectF()

    init {
        for (row in 0..rows) for (col in 0..cols) {
            val i = (row * (cols + 1) + col) * 2
            val u = col.toFloat() / cols
            val v = row.toFloat() / rows
            val arch = sin(PI * u).toFloat()
            origin[i] = plane.width * u
            origin[i + 1] = plane.height * v
            if (plane == WelcomePlane.Sky) {
                // Sweep the light inwards from the upper right, without moving the crest or book.
                val inward = 1f - u - (1f - u).pow(1f / 1.47f)
                val depth = v * 2.29f
                val ray = exp(-depth * depth) * ramp(0.46f, 0.92f, u + inward * 0.5f)
                val seam = 1f - ramp(0.90f, 1f, v)
                drift[i] = plane.width * seam * (inward * ray - 0.004f * arch * (1f - ray))
                sway[i] = plane.width * seam * (-0.0064f * arch * (1f - ray))
                drift[i + 1] = plane.height * seam * (0.093f * v * ray - 0.0043f * ramp(0f, 0.11f, v) * (1f - ray))
                sway[i + 1] = plane.height * seam * (-0.038f * v * ray - 0.0065f * ramp(0f, 0.11f, v) * (1f - ray))
            } else {
                val lights = ramp(0.07f, 0.43f, v) * (1f - ramp(0.66f, 0.81f, v))
                val wave = ramp(0.80f, 0.86f, v)
                val sides = ramp(0f, 0.055f, u) * ramp(0f, 0.055f, 1f - u)
                drift[i] = plane.width * sides * (-0.014f * lights + 0.016f * arch * wave)
                sway[i] = plane.width * sides * (0.007f * lights - 0.010f * arch * wave)
                drift[i + 1] = plane.height * (-0.018f * lights + (0.011f + 0.0053f * arch) * wave)
                sway[i + 1] = plane.height * (0.0035f * lights - 0.0045f * cos(3f * u) * wave)
            }
        }
    }

    fun draw(canvas: AndroidCanvas, width: Float, height: Float, texture: Bitmap, atlas: Bitmap?, progress: Float) {
        if (width <= 0f || height <= 0f) return
        val angle = if (progress > 0f && progress < 1f) progress * 2.0 * PI else 0.0
        val eased = angle - 0.17 * sin(angle)
        val q = ((1.0 - cos(eased)) * 0.5).toFloat()
        val s = sin(eased).toFloat()
        for (i in vertices.indices) vertices[i] = origin[i] + q * drift[i] + s * sway[i]
        // The lower curve can move down beyond the frame, but must never uncover a strip at the bottom.
        for (i in (vertices.size - (cols + 1) * 2 + 1) until vertices.size step 2) {
            vertices[i] = vertices[i].coerceAtLeast(plane.height)
        }
        val save = canvas.save()
        try {
            canvas.scale(width / plane.width, height / plane.height)
            canvas.clipRect(0f, 0f, plane.width, plane.height)
            canvas.drawBitmapMesh(texture, cols, rows, vertices, 0, null, 0, paint)
            if (atlas != null) for (index in originalMotes.indices) {
                val mote = originalMotes[index]
                val x = mote.x + q * mote.dx + s * mote.bendX
                val y = mote.y + q * mote.dy + s * mote.bendY
                val left = index % 4 * TILE
                val top = index / 4 * TILE
                src.set(left, top, left + TILE, top + TILE)
                dst.set(x - TILE / 2f, y - TILE / 2f, x + TILE / 2f, y + TILE / 2f)
                canvas.drawBitmap(atlas, src, dst, paint)
            }
        } finally {
            canvas.restoreToCount(save)
        }
    }
}

// Actual lights lifted from the 953 × 672 header, in atlas order; no new stars or random spawning.
private const val TILE = 56
private data class Mote(val x: Float, val y: Float, val dx: Float, val dy: Float, val bendX: Float, val bendY: Float)
private val originalMotes = arrayOf(
    Mote(809f, 133f, -10.79f, -17.96f, 6.70f, 3.77f),
    Mote(880f, 175f, -19.72f, -18.69f, 8.69f, 4.89f),
    Mote(729f, 206f, -3.73f, -32.08f, 10.33f, 5.81f),
    Mote(585f, 213f, -13.33f, -9.85f, 5.30f, 2.98f),
    Mote(764f, 252f, -7.29f, -20.03f, 6.82f, 3.84f),
    Mote(55f, 276f, -16.26f, -13.47f, 6.75f, 3.80f),
    Mote(139f, 316f, -2.44f, -23.20f, 7.46f, 4.20f),
    Mote(246f, 314f, -9.34f, -7.63f, 3.86f, 2.17f),
    Mote(188f, 419f, -6.39f, -13.66f, 4.82f, 2.71f),
    Mote(845f, 434f, -2.72f, -23.78f, 7.65f, 4.31f),
    Mote(765f, 490f, -12.50f, -11.67f, 5.47f, 3.08f),
    Mote(126f, 560f, -14.42f, -24.56f, 9.11f, 5.13f),
    Mote(699f, 604f, -6.91f, -4.80f, 2.69f, 1.52f),
    Mote(722f, 556f, -1.02f, -7.08f, 2.29f, 1.29f),
)
