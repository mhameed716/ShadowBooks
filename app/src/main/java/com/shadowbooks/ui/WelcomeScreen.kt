package com.shadowbooks.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
private val WhatsGreen = Color(0xFF25D366)

// Channel gate (method "ج"): the code is posted inside the WhatsApp channel only. It is kept
// out of every string resource and label so it never appears on screen; typing it once unlocks
// the app on this device.
private const val ChannelUrl = "https://whatsapp.com/channel/0029Vb8xx5LCsU9RQXEXTS1t"
private const val ChannelCode = "SHADOW2027"
private const val GatePreferences = "welcome_gate"
private const val GateUnlockedKey = "channel_unlocked"

@Composable
fun WelcomeScreen(onEnter: () -> Unit) {
    val phase = rememberWelcomePhase()
    val context = LocalContext.current
    var showGate by rememberSaveable { mutableStateOf(false) }
    fun gateUnlocked() = context.getSharedPreferences(GatePreferences, Context.MODE_PRIVATE)
        .getBoolean(GateUnlockedKey, false)
    // The welcome "دخول" button never enters directly on a locked device: it raises the
    // channel gate first. After one correct code the device stays unlocked.
    val enter: () -> Unit = {
        if (gateUnlocked()) onEnter() else showGate = true
    }
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
            WelcomeDetails(panelWidth, bodyMinHeight, phase, enter)
        }
    }
    if (showGate) {
        ChannelGateDialog(
            onDismiss = { showGate = false },
            onUnlocked = {
                context.getSharedPreferences(GatePreferences, Context.MODE_PRIVATE)
                    .edit().putBoolean(GateUnlockedKey, true).apply()
                showGate = false
                onEnter()
            },
        )
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

/** Popup raised by the welcome "دخول" button: follow the channel, copy its posted code, enter. */
@Composable
private fun ChannelGateDialog(onDismiss: () -> Unit, onUnlocked: () -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val openChannel = {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(ChannelUrl)).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        Unit
    }
    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxWidth().padding(top = 42.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF062453), RoundedCornerShape(26.dp))
                    .border(1.dp, Gold.copy(alpha = 0.75f), RoundedCornerShape(26.dp))
                    .padding(start = 20.dp, end = 20.dp, top = 60.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.welcome_gate_title),
                    color = Color.White, fontFamily = Tajawal, fontWeight = FontWeight.ExtraBold,
                    fontSize = 25.sp, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.welcome_gate_subtitle),
                    color = SeaGlass, fontFamily = Tajawal, fontSize = 15.sp, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.take(32); error = false },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = error,
                    shape = RoundedCornerShape(24.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        capitalization = KeyboardCapitalization.Characters,
                    ),
                    textStyle = TextStyle(
                        fontSize = 18.sp, letterSpacing = 2.sp, textAlign = TextAlign.Center,
                        textDirection = TextDirection.Ltr,
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Cyan,
                        unfocusedBorderColor = SeaGlass.copy(alpha = 0.55f),
                        errorBorderColor = Color(0xFFFF8A80),
                        focusedLabelColor = Cyan,
                        unfocusedLabelColor = SeaGlass,
                        cursorColor = Cyan,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF041A3D),
                        unfocusedContainerColor = Color(0xFF041A3D),
                    ),
                )
                if (error) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.welcome_gate_error),
                        color = Color(0xFFFFB4A8), fontFamily = Tajawal, fontSize = 13.5.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // RTL order: the first child sits at the right — دخول right, تابع left.
                    Button(
                        onClick = { if (code.trim().uppercase() == ChannelCode) onUnlocked() else error = true },
                        modifier = Modifier.weight(1f).heightIn(min = 50.dp).background(EnterBrush, CircleShape),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, Color(0xFF48EAFF)),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Navy),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            stringResource(R.string.welcome_enter),
                            fontFamily = Tajawal, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp,
                        )
                    }
                    Button(
                        onClick = openChannel,
                        modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = WhatsGreen, contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Image(
                            painter = painterResource(R.drawable.gate_whatsapp),
                            contentDescription = null,
                            modifier = Modifier.size(21.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.welcome_gate_follow),
                            fontFamily = Tajawal, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp,
                        )
                    }
                }
            }
            GateBadge(Modifier.align(Alignment.TopCenter).offset(y = (-48).dp).size(96.dp))
        }
    }
}

/** Green-ringed chat badge with an open book, drawn in code after the agreed design. */
@Composable
private fun GateBadge(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.welcome_gate_badge)
    Canvas(modifier.semantics { contentDescription = description }) {
        val radius = size.minDimension / 2f
        val lightGreen = Color(0xFF8CF5B6)
        val neon = Color(0xFF35E0FF)
        // Soft green halo, then the glossy dark disc and its gradient ring.
        drawCircle(
            brush = Brush.radialGradient(listOf(WhatsGreen.copy(alpha = 0.45f), Color.Transparent)),
            radius = radius,
        )
        drawCircle(color = Color(0xFF031633), radius = radius * 0.94f)
        drawCircle(
            brush = Brush.sweepGradient(listOf(WhatsGreen, lightGreen, WhatsGreen)),
            radius = radius * 0.94f - size.minDimension * 0.025f,
            style = Stroke(width = size.minDimension * 0.05f),
        )
        // Chat-bubble tail at the lower left of the ring.
        val tail = Path().apply {
            moveTo(center.x - radius * 0.47f, center.y + radius * 0.41f)
            lineTo(center.x - radius * 0.75f, center.y + radius * 0.82f)
            lineTo(center.x - radius * 0.13f, center.y + radius * 0.66f)
            close()
        }
        drawPath(tail, WhatsGreen)
        // Two readers silhouetted behind the book.
        val silhouette = Color(0xFF14667F)
        for (hx in listOf(center.x - radius * 0.26f, center.x + radius * 0.26f)) {
            drawCircle(silhouette, radius = radius * 0.15f, center = Offset(hx, center.y - radius * 0.36f))
            drawArc(
                silhouette, 180f, 180f, true,
                topLeft = Offset(hx - radius * 0.27f, center.y - radius * 0.32f),
                size = Size(radius * 0.54f, radius * 0.46f),
            )
        }
        // Neon open book: teal pages with a bright outline around a centre spine.
        val bookWidth = radius * 1.08f
        val bookHeight = radius * 0.74f
        val top = center.y - bookHeight * 0.40f
        val leftPage = Path().apply {
            moveTo(center.x, top)
            cubicTo(center.x - bookWidth * 0.26f, top - bookHeight * 0.20f, center.x - bookWidth * 0.50f, top - bookHeight * 0.12f, center.x - bookWidth * 0.50f, top + bookHeight * 0.58f)
            cubicTo(center.x - bookWidth * 0.30f, top + bookHeight * 0.48f, center.x - bookWidth * 0.12f, top + bookHeight * 0.52f, center.x, top + bookHeight * 0.70f)
            close()
        }
        val rightPage = Path().apply {
            moveTo(center.x, top)
            cubicTo(center.x + bookWidth * 0.26f, top - bookHeight * 0.20f, center.x + bookWidth * 0.50f, top - bookHeight * 0.12f, center.x + bookWidth * 0.50f, top + bookHeight * 0.58f)
            cubicTo(center.x + bookWidth * 0.30f, top + bookHeight * 0.48f, center.x + bookWidth * 0.12f, top + bookHeight * 0.52f, center.x, top + bookHeight * 0.70f)
            close()
        }
        drawPath(leftPage, Color(0xFF0E5A7A))
        drawPath(rightPage, Color(0xFF12708F))
        val outline = Stroke(width = size.minDimension * 0.022f)
        drawPath(leftPage, neon, style = outline)
        drawPath(rightPage, neon, style = outline)
        drawLine(
            color = neon,
            start = Offset(center.x, top),
            end = Offset(center.x, top + bookHeight * 0.70f),
            strokeWidth = size.minDimension * 0.022f,
        )
    }
}
