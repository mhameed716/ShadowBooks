package com.shadowbooks.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shadowbooks.R

@Composable
fun WelcomeScreen(onEnter: () -> Unit) {
    val goldAccent = Color(0xFFF0D284)
    val goldText = Color(0xFFE7C779)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF121E35), Color(0xFF0A1020), Color(0xFF070C17)),
                ),
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val topGlow = Offset(size.width * 0.82f, size.height * 0.16f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF236B91).copy(alpha = 0.26f), Color.Transparent),
                    center = topGlow,
                    radius = size.minDimension * 0.72f,
                ),
                center = topGlow,
                radius = size.minDimension * 0.72f,
            )

            val centerGlow = Offset(size.width * 0.5f, size.height * 0.34f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF1C5B7E).copy(alpha = 0.18f), Color.Transparent),
                    center = centerGlow,
                    radius = size.minDimension * 0.45f,
                ),
                center = centerGlow,
                radius = size.minDimension * 0.45f,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 22.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top bar fixed in physical LTR so Yemen emblem is always top-left
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    YemenEmblemHeader(goldText = goldText)

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MenuBook,
                            contentDescription = null,
                            tint = Color(0xFF72D9FF),
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "SHADOWBOOKS",
                            color = Color(0xFFEAF7FF),
                            fontSize = 13.sp,
                            letterSpacing = 2.2.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }

                    // Invisible balance spacer on the physical top-right for clean centering
                    Spacer(Modifier.width(32.dp))
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                OpenBookBadge()
                Spacer(Modifier.height(22.dp))

                Text(
                    text = "منهج الصف الثالث الثانوي",
                    color = Color(0xFFF7FAFF),
                    fontSize = 27.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))

                Text(
                    text = "مكتبتك المدرسية الرقمية",
                    color = Color(0xFFC3D5EA),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))

                GraduationBanner(
                    text = "مشروع تخرج: الدفعه التي لا تنسى",
                    accentColor = goldAccent,
                )
                Spacer(Modifier.height(5.dp))

                Text(
                    text = "\" للعام الدراسي 2027 \"",
                    color = Color(0xFFE3ECF7),
                    fontSize = 13.sp,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(14.dp))

                Text(
                    text = "اتمنى التوفيق للجميع، شكراً",
                    color = Color(0xFFEBF2FA),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = onEnter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .shadow(12.dp, RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF65D4FA),
                        contentColor = Color(0xFF081421),
                    ),
                ) {
                    Text("دخول", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(14.dp))
                Text("إعداد: محمد حجر", color = Color(0xFFAAB9CC), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun YemenEmblemHeader(goldText: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .width(86.dp)
                .height(52.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFE7C779).copy(alpha = 0.16f), Color.Transparent),
                        center = center,
                        radius = size.maxDimension * 0.55f,
                    ),
                    center = center,
                    radius = size.maxDimension * 0.55f,
                )
            }
            Image(
                painter = painterResource(id = R.drawable.yemen_emblem),
                contentDescription = "شعار الجمهورية اليمنية",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = "الجمهورية اليمنية",
            color = goldText,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun GraduationBanner(
    text: String,
    accentColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        OrnamentWing(
            accentColor = accentColor,
            flip = false,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = text,
            color = accentColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        OrnamentWing(
            accentColor = accentColor,
            flip = true,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun OrnamentWing(
    accentColor: Color,
    flip: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier.height(18.dp),
    ) {
        if (size.width < 12.dp.toPx()) return@Canvas
        val midY = size.height / 2f
        val lineStartX = if (flip) size.width else 0f
        val flourishX = if (flip) 10.dp.toPx().coerceAtMost(size.width * 0.55f) else (size.width - 10.dp.toPx()).coerceAtLeast(size.width * 0.45f)

        drawLine(
            brush = Brush.horizontalGradient(
                colors = if (flip) {
                    listOf(accentColor.copy(alpha = 0.85f), Color.Transparent)
                } else {
                    listOf(Color.Transparent, accentColor.copy(alpha = 0.85f))
                },
            ),
            start = Offset(if (flip) flourishX else lineStartX, midY),
            end = Offset(if (flip) lineStartX else flourishX, midY),
            strokeWidth = 1.1.dp.toPx(),
            cap = StrokeCap.Round,
        )

        val dir = if (flip) -1f else 1f
        val d = 4.5.dp.toPx()
        val diamond = Path().apply {
            moveTo(flourishX, midY - d * 0.7f)
            lineTo(flourishX + dir * d, midY)
            lineTo(flourishX, midY + d * 0.7f)
            lineTo(flourishX - dir * d * 0.7f, midY)
            close()
        }
        drawPath(
            path = diamond,
            color = accentColor,
            style = Stroke(width = 1.2.dp.toPx()),
        )
    }
}

@Composable
private fun OpenBookBadge() {
    val badgeShape = RoundedCornerShape(36.dp)
    Box(
        modifier = Modifier
            .size(144.dp)
            .shadow(24.dp, badgeShape)
            .background(
                Brush.linearGradient(listOf(Color(0xFF1B5B7B), Color(0xFF11233D), Color(0xFF0C1528))),
                badgeShape,
            )
            .border(
                width = 1.2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF7AE0FF).copy(alpha = 0.55f),
                        Color(0xFF286084).copy(alpha = 0.3f),
                        Color(0xFF65D4FA).copy(alpha = 0.4f),
                    ),
                ),
                shape = badgeShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(114.dp)
                .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.MenuBook,
                contentDescription = null,
                tint = Color(0xFFE9F9FF),
                modifier = Modifier.size(76.dp),
            )
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(11.dp)
                        .size(28.dp)
                        .background(Color(0xFF65D4FA), RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("S", color = Color(0xFF0A1725), fontSize = 14.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}
