package com.shadowbooks.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shadowbooks.R

private const val HeroAspect = 953f / 672f
private val Navy = Color(0xFF02132D)
private val Gold = Color(0xFFFFD263)
private val Cyan = Color(0xFF00CBFF)
private val SeaGlass = Color(0xFF88F0F8)
private val GoldRuleBrush = Brush.horizontalGradient(listOf(Gold.copy(alpha = 0.16f), Gold, Gold.copy(alpha = 0.16f)))
private val EnterBrush = Brush.verticalGradient(listOf(Color(0xFF19E4F6), Color(0xFF00C8F3), Color(0xFF00ACF2)))
private val Tajawal = FontFamily(
    Font(R.font.tajawal_regular, FontWeight.Normal),
    Font(R.font.tajawal_extrabold, FontWeight.ExtraBold),
)

@Composable
fun WelcomeScreen(onEnter: () -> Unit) {
    val phase = rememberWelcomePhase()
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(Navy).safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        // Avoid a screen-sized illustration in short landscape or split-screen windows.
        val panelWidth = minOf(maxWidth, 520.dp, (maxHeight * 0.7f).coerceAtLeast(340.dp))
        val bodyMinHeight = (maxHeight - panelWidth / HeroAspect).coerceAtLeast(0.dp)
        Column(
            modifier = Modifier.width(panelWidth).fillMaxHeight().verticalScroll(rememberScrollState()),
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(HeroAspect)) {
                WelcomeBackdrop(WelcomePlane.Sky, phase, Modifier.matchParentSize())
                // Original emblem, wordmark and neon book: fixed above the moving blue sky.
                Image(
                    painter = painterResource(R.drawable.welcome_hero),
                    contentDescription = stringResource(R.string.welcome_identity),
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.matchParentSize(),
                )
            }
            WelcomeDetails(panelWidth, bodyMinHeight, phase, onEnter)
        }
    }
}

@Composable
private fun WelcomeDetails(width: Dp, minHeight: Dp, phase: State<Float>, onEnter: () -> Unit) {
    val scale = (width.value / 390f).coerceIn(0.80f, 1.30f)
    Box(Modifier.fillMaxWidth()) {
        WelcomeBackdrop(WelcomePlane.Night, phase, Modifier.matchParentSize())
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .padding(top = 2.dp, bottom = 28.dp * scale),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.padding(horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                WelcomeLabel(
                    R.string.welcome_title, (29 * scale).sp, Color.White, FontWeight.ExtraBold,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(3.dp * scale))
                WelcomeLabel(R.string.welcome_subtitle, (25 * scale).sp, SeaGlass)
            }
            Column(Modifier.padding(top = 14.dp * scale), horizontalAlignment = Alignment.CenterHorizontally) {
                RuledLine(Modifier.fillMaxWidth(), labelMaxWidth = width * 0.84f) {
                    WelcomeLabel(R.string.welcome_project, (20 * scale).sp, Gold, FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(8.dp * scale))
                RuledLine(Modifier.fillMaxWidth(0.85f), labelMaxWidth = width * 0.60f, diamonds = true) {
                    WelcomeLabel(R.string.welcome_year, (18 * scale).sp, Color(0xFFCDDFEE), style = FontStyle.Italic)
                }
            }
            WelcomeLabel(
                R.string.welcome_wish, (21 * scale).sp, Color.White, FontWeight.ExtraBold,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 18.dp * scale),
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 30.dp * scale),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EnterButton(scale, onEnter)
                Spacer(Modifier.height(16.dp * scale))
                WelcomeLabel(R.string.welcome_credit, (17 * scale).sp, Color(0xFFB7CADB))
            }
        }
    }
}

@Composable
private fun WelcomeLabel(
    @StringRes label: Int,
    size: TextUnit,
    color: Color,
    weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
    style: FontStyle = FontStyle.Normal,
) {
    Text(
        text = stringResource(label),
        modifier = modifier,
        color = color,
        fontFamily = Tajawal,
        fontSize = size,
        lineHeight = size * 1.35f,
        letterSpacing = 0.sp,
        fontWeight = weight,
        fontStyle = style,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun RuledLine(
    modifier: Modifier,
    labelMaxWidth: Dp,
    diamonds: Boolean = false,
    label: @Composable () -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GoldRule(Modifier.weight(1f), if (diamonds) Alignment.CenterStart else null)
        Box(Modifier.widthIn(max = labelMaxWidth)) { label() }
        GoldRule(Modifier.weight(1f), if (diamonds) Alignment.CenterEnd else null)
    }
}

@Composable
private fun GoldRule(modifier: Modifier, diamond: Alignment?) {
    Box(modifier.height(12.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(GoldRuleBrush))
        if (diamond != null) {
            Box(Modifier.align(diamond).size(8.dp).rotate(45f).background(Gold))
        }
    }
}

@Composable
private fun EnterButton(scale: Float, onEnter: () -> Unit) {
    Button(
        onClick = onEnter,
        modifier = Modifier
            .fillMaxWidth(0.89f)
            .heightIn(min = (55.dp * scale).coerceAtLeast(48.dp))
            .drawBehind {
                // Paint outside the pill, not over its text or Material press indication.
                for (layer in 12 downTo 1) {
                    val spread = 14.dp.toPx() * layer / 12f
                    drawRoundRect(
                        color = Cyan.copy(alpha = 0.012f + 0.002f * (12 - layer)),
                        topLeft = Offset(-spread, -spread),
                        size = Size(size.width + spread * 2, size.height + spread * 2),
                        cornerRadius = CornerRadius(size.height / 2 + spread),
                    )
                }
            }
            .background(EnterBrush, CircleShape),
        shape = CircleShape,
        border = BorderStroke(1.dp, Color(0xFF48EAFF)),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Navy),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
    ) {
        WelcomeLabel(R.string.welcome_enter, (28 * scale).sp, Navy, FontWeight.ExtraBold)
    }
}
