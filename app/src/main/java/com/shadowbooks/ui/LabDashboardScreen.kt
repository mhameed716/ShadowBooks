package com.shadowbooks.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shadowbooks.data.LabCatalog
import com.shadowbooks.data.LabSubject
import com.shadowbooks.data.LabSubjectId
import com.shadowbooks.data.formatExperimentCount
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun LabDashboardScreen(
    onSelectSubject: (LabSubject) -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "lab_ambient")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A1020)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val topLeftGlow = Offset(size.width * 0.16f, size.height * 0.08f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF236B91).copy(alpha = 0.25f), Color.Transparent),
                    center = topLeftGlow,
                    radius = size.minDimension * 0.55f,
                ),
                center = topLeftGlow,
                radius = size.minDimension * 0.55f,
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SHADOWBOOKS",
                        color = Color(0xFF75D8FA),
                        fontSize = 11.sp,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "مختبر التجارب",
                        color = Color(0xFFF4F7FC),
                        fontSize = 25.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "تجارب تفاعلية • الصف الثالث الثانوي",
                        color = Color(0xFF9BAEC4),
                        fontSize = 12.sp,
                    )
                }

                Spacer(Modifier.width(12.dp))
                LabHeaderEmblem(phase = pulse)
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = 20.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(
                    items = LabCatalog.subjects,
                    key = { it.id.name },
                ) { subject ->
                    LabSubjectCard(
                        subject = subject,
                        phase = pulse,
                        onClick = { onSelectSubject(subject) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LabHeaderEmblem(phase: Float) {
    val badgeShape = RoundedCornerShape(22.dp)
    Box(
        modifier = Modifier
            .size(76.dp)
            .shadow(16.dp, badgeShape)
            .clip(badgeShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF1A4F6B), Color(0xFF102037)),
                ),
            )
            .border(
                width = 1.2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF76E1FF).copy(alpha = 0.6f),
                        Color(0xFF2B6789).copy(alpha = 0.35f),
                    ),
                ),
                shape = badgeShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cyan = Color(0xFF65D4FA)

            rotate(degrees = -22f, pivot = center) {
                drawOval(
                    color = cyan.copy(alpha = 0.28f),
                    topLeft = Offset(w * 0.06f, h * 0.28f),
                    size = Size(w * 0.88f, h * 0.44f),
                    style = Stroke(width = 1.3.dp.toPx()),
                )
            }

            // Flask silhouette
            val flask = Path().apply {
                moveTo(w * 0.40f, h * 0.24f)
                lineTo(w * 0.54f, h * 0.24f)
                lineTo(w * 0.54f, h * 0.38f)
                lineTo(w * 0.70f, h * 0.70f)
                quadraticTo(w * 0.72f, h * 0.76f, w * 0.65f, h * 0.76f)
                lineTo(w * 0.29f, h * 0.76f)
                quadraticTo(w * 0.22f, h * 0.76f, w * 0.24f, h * 0.70f)
                lineTo(w * 0.40f, h * 0.38f)
                close()
            }
            drawPath(
                path = flask,
                brush = Brush.verticalGradient(
                    colors = listOf(cyan.copy(alpha = 0.16f), cyan.copy(alpha = 0.48f)),
                ),
            )
            drawPath(
                path = flask,
                color = Color(0xFF8BE4FF),
                style = Stroke(width = 1.8.dp.toPx(), join = StrokeJoin.Round),
            )

            // Neck rim
            drawLine(
                color = Color(0xFFC2F2FF),
                start = Offset(w * 0.37f, h * 0.24f),
                end = Offset(w * 0.57f, h * 0.24f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )

            // Liquid surface wave
            val liquid = Path().apply {
                moveTo(w * 0.31f, h * 0.59f)
                lineTo(w * 0.63f, h * 0.59f)
                lineTo(w * 0.69f, h * 0.71f)
                quadraticTo(w * 0.70f, h * 0.75f, w * 0.64f, h * 0.75f)
                lineTo(w * 0.30f, h * 0.75f)
                quadraticTo(w * 0.24f, h * 0.75f, w * 0.25f, h * 0.71f)
                close()
            }
            drawPath(path = liquid, color = cyan.copy(alpha = 0.55f))

            // Electric spark beside flask
            val bolt = Path().apply {
                moveTo(w * 0.74f, h * 0.34f)
                lineTo(w * 0.67f, h * 0.47f)
                lineTo(w * 0.73f, h * 0.47f)
                lineTo(w * 0.69f, h * 0.59f)
                lineTo(w * 0.81f, h * 0.43f)
                lineTo(w * 0.74f, h * 0.43f)
                close()
            }
            drawPath(path = bolt, color = Color(0xFF9DF0FF))

            // Animated rising bubble
            val bubbleOffset = (phase * 14f) % 14f
            drawCircle(
                color = Color(0xFFC8F5FF).copy(alpha = 0.85f),
                radius = 2.dp.toPx(),
                center = Offset(w * 0.46f, h * 0.52f - bubbleOffset),
            )
            drawCircle(
                color = Color(0xFF72D9FF).copy(alpha = 0.75f),
                radius = 1.6.dp.toPx(),
                center = Offset(w * 0.50f, h * 0.20f - (bubbleOffset * 0.5f)),
            )
        }
    }
}

@Composable
private fun LabSubjectCard(
    subject: LabSubject,
    phase: Float,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    val accent = subject.accentColor
    val countLabel = formatExperimentCount(subject.experiments.size)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(118.dp)
            .shadow(12.dp, shape)
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(subject.cardTop, subject.cardBottom),
                ),
            )
            .border(
                width = 1.2.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.62f),
                        accent.copy(alpha = 0.24f),
                        accent.copy(alpha = 0.45f),
                    ),
                ),
                shape = shape,
            )
            .clickable(onClick = onClick),
    ) {
        // Subtle thematic background pattern
        Canvas(Modifier.fillMaxSize()) {
            drawSubjectBackgroundPattern(subject.id, accent, phase)
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Right side in RTL: Custom lively illustration
            SubjectIllustration(
                subjectId = subject.id,
                accent = accent,
                phase = phase,
                modifier = Modifier.size(86.dp),
            )

            Spacer(Modifier.width(14.dp))

            // Middle: Title + Experiment count pill
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = subject.title,
                    color = Color(0xFFF6FAFF),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = accent.copy(alpha = 0.20f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.border(
                        width = 0.9.dp,
                        color = accent.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(50),
                    ),
                ) {
                    Text(
                        text = countLabel,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        color = accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Left side in RTL: Interactive chevron arrow
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.ChevronLeft,
                    contentDescription = "فتح ${subject.title}",
                    tint = accent,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun SubjectIllustration(
    subjectId: LabSubjectId,
    accent: Color,
    phase: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.24f), Color.Transparent),
                center = center,
                radius = size.minDimension * 0.55f,
            ),
            center = center,
            radius = size.minDimension * 0.55f,
        )

        when (subjectId) {
            LabSubjectId.CHEMISTRY -> drawChemistryArtwork(accent, phase)
            LabSubjectId.PHYSICS -> drawPhysicsArtwork(accent, phase)
            LabSubjectId.BIOLOGY -> drawBiologyArtwork(accent, phase)
            LabSubjectId.MATHEMATICS -> drawMathArtwork(accent, phase)
        }
    }
}

private fun DrawScope.drawSubjectBackgroundPattern(
    subjectId: LabSubjectId,
    accent: Color,
    phase: Float,
) {
    val w = size.width
    val h = size.height
    when (subjectId) {
        LabSubjectId.CHEMISTRY -> {
            // Faint molecular hexagons
            val hexCenters = listOf(
                Offset(w * 0.42f, h * 0.28f),
                Offset(w * 0.50f, h * 0.68f),
                Offset(w * 0.34f, h * 0.72f),
            )
            hexCenters.forEach { c ->
                val r = 16.dp.toPx()
                val hex = Path().apply {
                    for (i in 0 until 6) {
                        val angle = (i * 60.0) * PI / 180.0
                        val px = c.x + (r * cos(angle)).toFloat()
                        val py = c.y + (r * sin(angle)).toFloat()
                        if (i == 0) moveTo(px, py) else lineTo(px, py)
                    }
                    close()
                }
                drawPath(hex, color = accent.copy(alpha = 0.08f), style = Stroke(1.dp.toPx()))
            }
        }

        LabSubjectId.PHYSICS -> {
            // Flowing subtle wave curves
            for (waveIdx in 0..2) {
                val path = Path()
                val baseY = h * (0.35f + waveIdx * 0.16f)
                val amp = (8f + waveIdx * 3f).dp.toPx()
                for (step in 0..32) {
                    val t = step / 32f
                    val x = w * (0.15f + t * 0.75f)
                    val y = baseY + sin(t * 2f * PI.toFloat() + phase * 2f * PI.toFloat() + waveIdx) * amp
                    if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color = accent.copy(alpha = 0.10f), style = Stroke(1.1.dp.toPx()))
            }
        }

        LabSubjectId.BIOLOGY -> {
            // Faint cellular rings
            val cells = listOf(
                Offset(w * 0.40f, h * 0.25f) to 18.dp.toPx(),
                Offset(w * 0.48f, h * 0.75f) to 14.dp.toPx(),
                Offset(w * 0.28f, h * 0.62f) to 11.dp.toPx(),
            )
            cells.forEach { (c, r) ->
                drawCircle(accent.copy(alpha = 0.08f), radius = r, center = c, style = Stroke(1.dp.toPx()))
                drawCircle(accent.copy(alpha = 0.07f), radius = r * 0.35f, center = c)
            }
        }

        LabSubjectId.MATHEMATICS -> {
            // Subtle coordinate grid
            val cols = 6
            val rows = 4
            for (i in 1 until cols) {
                val x = w * (0.20f + i * 0.12f)
                drawLine(accent.copy(alpha = 0.07f), Offset(x, 0f), Offset(x, h), strokeWidth = 0.8.dp.toPx())
            }
            for (j in 1 until rows) {
                val y = h * (j.toFloat() / rows)
                drawLine(accent.copy(alpha = 0.07f), Offset(w * 0.2f, y), Offset(w * 0.92f, y), strokeWidth = 0.8.dp.toPx())
            }
        }
    }
}

private fun DrawScope.drawChemistryArtwork(accent: Color, phase: Float) {
    val w = size.width
    val h = size.height

    // Orbiting atom rings
    rotate(degrees = 28f, pivot = Offset(w * 0.5f, h * 0.56f)) {
        drawOval(
            color = accent.copy(alpha = 0.45f),
            topLeft = Offset(w * 0.08f, h * 0.34f),
            size = Size(w * 0.84f, h * 0.44f),
            style = Stroke(width = 1.4.dp.toPx()),
        )
    }
    rotate(degrees = -28f, pivot = Offset(w * 0.5f, h * 0.56f)) {
        drawOval(
            color = accent.copy(alpha = 0.45f),
            topLeft = Offset(w * 0.08f, h * 0.34f),
            size = Size(w * 0.84f, h * 0.44f),
            style = Stroke(width = 1.4.dp.toPx()),
        )
    }

    // Orbiting electron dots
    val angle = phase * 2f * PI.toFloat()
    val ex = w * 0.5f + cos(angle) * w * 0.36f
    val ey = h * 0.56f + sin(angle) * h * 0.18f
    drawCircle(Color(0xFFB5FFEB), radius = 3.dp.toPx(), center = Offset(ex, ey))

    // Erlenmeyer flask
    val flask = Path().apply {
        moveTo(w * 0.42f, h * 0.20f)
        lineTo(w * 0.58f, h * 0.20f)
        lineTo(w * 0.58f, h * 0.37f)
        lineTo(w * 0.77f, h * 0.75f)
        quadraticTo(w * 0.79f, h * 0.83f, w * 0.70f, h * 0.83f)
        lineTo(w * 0.30f, h * 0.83f)
        quadraticTo(w * 0.21f, h * 0.83f, w * 0.23f, h * 0.75f)
        lineTo(w * 0.42f, h * 0.37f)
        close()
    }
    drawPath(
        flask,
        brush = Brush.verticalGradient(
            colors = listOf(accent.copy(alpha = 0.14f), accent.copy(alpha = 0.40f)),
        ),
    )

    val liquid = Path().apply {
        moveTo(w * 0.32f, h * 0.58f)
        lineTo(w * 0.68f, h * 0.58f)
        lineTo(w * 0.75f, h * 0.76f)
        quadraticTo(w * 0.77f, h * 0.82f, w * 0.69f, h * 0.82f)
        lineTo(w * 0.31f, h * 0.82f)
        quadraticTo(w * 0.23f, h * 0.82f, w * 0.25f, h * 0.76f)
        close()
    }
    drawPath(liquid, color = accent.copy(alpha = 0.75f))
    drawPath(flask, color = Color(0xFFA7FFE6), style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round))
    drawLine(
        color = Color(0xFFD6FFF4),
        start = Offset(w * 0.38f, h * 0.20f),
        end = Offset(w * 0.62f, h * 0.20f),
        strokeWidth = 2.4.dp.toPx(),
        cap = StrokeCap.Round,
    )

    // Rising bubbles
    val bShift = (phase * 16f) % 16f
    drawCircle(Color(0xFFD9FFF5), radius = 2.4.dp.toPx(), center = Offset(w * 0.48f, h * 0.72f - bShift))
    drawCircle(Color(0xFFA7FFE6), radius = 2.dp.toPx(), center = Offset(w * 0.53f, h * 0.15f - bShift * 0.35f))
}

private fun DrawScope.drawPhysicsArtwork(accent: Color, phase: Float) {
    val w = size.width
    val h = size.height

    // Circuit branches on left & right
    val leftNodes = listOf(Offset(w * 0.16f, h * 0.30f), Offset(w * 0.12f, h * 0.50f), Offset(w * 0.18f, h * 0.70f))
    val rightNodes = listOf(Offset(w * 0.84f, h * 0.30f), Offset(w * 0.88f, h * 0.50f), Offset(w * 0.82f, h * 0.70f))

    leftNodes.forEach { node ->
        drawLine(accent.copy(alpha = 0.6f), Offset(w * 0.38f, h * 0.50f), node, strokeWidth = 1.5.dp.toPx())
        drawCircle(accent, radius = 2.6.dp.toPx(), center = node)
    }
    rightNodes.forEach { node ->
        drawLine(accent.copy(alpha = 0.6f), Offset(w * 0.62f, h * 0.50f), node, strokeWidth = 1.5.dp.toPx())
        drawCircle(accent, radius = 2.6.dp.toPx(), center = node)
    }

    // Glowing electric lightning bolt
    val bolt = Path().apply {
        moveTo(w * 0.56f, h * 0.12f)
        lineTo(w * 0.32f, h * 0.53f)
        lineTo(w * 0.49f, h * 0.53f)
        lineTo(w * 0.42f, h * 0.88f)
        lineTo(w * 0.69f, h * 0.45f)
        lineTo(w * 0.52f, h * 0.45f)
        close()
    }
    val glowAlpha = 0.25f + 0.15f * sin(phase * 2f * PI.toFloat())
    drawPath(bolt, color = accent.copy(alpha = glowAlpha), style = Stroke(width = 7.dp.toPx(), join = StrokeJoin.Round))
    drawPath(
        bolt,
        brush = Brush.verticalGradient(colors = listOf(Color(0xFFC5F2FF), accent)),
    )
    drawPath(bolt, color = Color.White.copy(alpha = 0.85f), style = Stroke(width = 1.6.dp.toPx(), join = StrokeJoin.Round))
}

private fun DrawScope.drawBiologyArtwork(accent: Color, phase: Float) {
    val w = size.width
    val h = size.height

    // DNA double helix on the left side of the icon
    val helixX = w * 0.24f
    val strand1 = Path()
    val strand2 = Path()
    for (i in 0..20) {
        val t = i / 20f
        val y = h * (0.20f + t * 0.60f)
        val dx = sin(t * 2.5f * PI.toFloat() + phase * 2f * PI.toFloat()) * w * 0.08f
        if (i == 0) {
            strand1.moveTo(helixX + dx, y)
            strand2.moveTo(helixX - dx, y)
        } else {
            strand1.lineTo(helixX + dx, y)
            strand2.lineTo(helixX - dx, y)
        }
        if (i % 4 == 2) {
            drawLine(
                color = accent.copy(alpha = 0.55f),
                start = Offset(helixX + dx, y),
                end = Offset(helixX - dx, y),
                strokeWidth = 1.3.dp.toPx(),
            )
        }
    }
    drawPath(strand1, color = Color(0xFFB5FFCE), style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
    drawPath(strand2, color = accent, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))

    // Microscope on center-right
    // Base
    drawRoundRect(
        color = accent.copy(alpha = 0.75f),
        topLeft = Offset(w * 0.44f, h * 0.77f),
        size = Size(w * 0.40f, h * 0.08f),
        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
    )
    // C-Arm
    val arm = Path().apply {
        moveTo(w * 0.68f, h * 0.77f)
        quadraticTo(w * 0.86f, h * 0.52f, w * 0.64f, h * 0.34f)
    }
    drawPath(arm, color = Color(0xFFA2FFC1), style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))

    // Stage
    drawLine(
        color = Color(0xFFD1FFE0),
        start = Offset(w * 0.44f, h * 0.62f),
        end = Offset(w * 0.69f, h * 0.62f),
        strokeWidth = 2.6.dp.toPx(),
        cap = StrokeCap.Round,
    )

    // Eyepiece tube
    rotate(degrees = -28f, pivot = Offset(w * 0.58f, h * 0.36f)) {
        drawRoundRect(
            brush = Brush.verticalGradient(colors = listOf(Color(0xFFD5FFE3), accent)),
            topLeft = Offset(w * 0.52f, h * 0.18f),
            size = Size(w * 0.12f, h * 0.34f),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
        )
    }

    // Small glowing cell
    drawCircle(accent.copy(alpha = 0.25f), radius = 7.dp.toPx(), center = Offset(w * 0.82f, h * 0.26f))
    drawCircle(Color(0xFFC5FFD9), radius = 7.dp.toPx(), center = Offset(w * 0.82f, h * 0.26f), style = Stroke(1.2.dp.toPx()))
    drawCircle(accent, radius = 2.5.dp.toPx(), center = Offset(w * 0.82f, h * 0.26f))
}

private fun DrawScope.drawMathArtwork(accent: Color, phase: Float) {
    val w = size.width
    val h = size.height

    // Coordinate axes
    drawLine(accent.copy(alpha = 0.65f), Offset(w * 0.50f, h * 0.12f), Offset(w * 0.50f, h * 0.88f), strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round)
    drawLine(accent.copy(alpha = 0.65f), Offset(w * 0.10f, h * 0.52f), Offset(w * 0.90f, h * 0.52f), strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round)

    // Animated golden sine curve
    val sine = Path()
    for (i in 0..36) {
        val t = i / 36f
        val x = w * (0.12f + t * 0.76f)
        val y = h * 0.52f - sin(t * 2f * PI.toFloat() + phase * 2f * PI.toFloat()) * h * 0.20f
        if (i == 0) sine.moveTo(x, y) else sine.lineTo(x, y)
    }
    drawPath(sine, color = Color(0xFFFFE5AE), style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round))

    // Compass on bottom-left
    drawCircle(accent, radius = 2.4.dp.toPx(), center = Offset(w * 0.26f, h * 0.58f))
    drawLine(Color(0xFFFFE09E), Offset(w * 0.26f, h * 0.58f), Offset(w * 0.17f, h * 0.84f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
    drawLine(Color(0xFFFFE09E), Offset(w * 0.26f, h * 0.58f), Offset(w * 0.35f, h * 0.84f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)

    // 3D wireframe pyramid on bottom-right
    val apex = Offset(w * 0.74f, h * 0.50f)
    val baseLeft = Offset(w * 0.59f, h * 0.78f)
    val baseFront = Offset(w * 0.73f, h * 0.85f)
    val baseRight = Offset(w * 0.88f, h * 0.76f)

    val pyramidFill = Path().apply {
        moveTo(apex.x, apex.y)
        lineTo(baseLeft.x, baseLeft.y)
        lineTo(baseFront.x, baseFront.y)
        lineTo(baseRight.x, baseRight.y)
        close()
    }
    drawPath(pyramidFill, color = accent.copy(alpha = 0.26f))
    drawLine(Color(0xFFFFE5AE), apex, baseLeft, strokeWidth = 1.5.dp.toPx())
    drawLine(Color(0xFFFFE5AE), apex, baseFront, strokeWidth = 1.5.dp.toPx())
    drawLine(Color(0xFFFFE5AE), apex, baseRight, strokeWidth = 1.5.dp.toPx())
    drawLine(Color(0xFFFFE5AE), baseLeft, baseFront, strokeWidth = 1.5.dp.toPx())
    drawLine(Color(0xFFFFE5AE), baseFront, baseRight, strokeWidth = 1.5.dp.toPx())
}
