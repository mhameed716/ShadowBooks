package com.shadowbooks.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WelcomeScreen(onEnter: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF121E35), Color(0xFF0A1020), Color(0xFF080D18)),
                ),
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val glowCenter = androidx.compose.ui.geometry.Offset(size.width * 0.82f, size.height * 0.2f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF236B91).copy(alpha = 0.22f), Color.Transparent),
                    center = glowCenter,
                    radius = size.minDimension * 0.72f,
                ),
                center = glowCenter,
                radius = size.minDimension * 0.72f,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 26.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
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
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                OpenBookBadge()
                Spacer(Modifier.height(30.dp))
                Text(
                    text = "مناهج الصف الثالث ثانوي",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color(0xFFF7FAFF),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "مكتبتك المدرسية الرقمية",
                    color = Color(0xFFB7C6DA),
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Box(Modifier.size(5.dp).background(Color(0xFF65D4FA), RoundedCornerShape(50)))
                    Spacer(Modifier.width(8.dp))
                    Text("13 كتاباً • المنهج اليمني", color = Color(0xFF8FA4BC), fontSize = 12.sp)
                }
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
private fun OpenBookBadge() {
    Box(
        modifier = Modifier
            .size(154.dp)
            .shadow(24.dp, RoundedCornerShape(42.dp))
            .background(
                Brush.linearGradient(listOf(Color(0xFF1E6485), Color(0xFF13253F), Color(0xFF0D172B))),
                RoundedCornerShape(42.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(122.dp)
                .background(Color.White.copy(alpha = 0.035f), RoundedCornerShape(34.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.MenuBook,
                contentDescription = null,
                tint = Color(0xFFE9F9FF),
                modifier = Modifier.size(82.dp),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(13.dp)
                .size(27.dp)
                .background(Color(0xFF65D4FA), RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("S", color = Color(0xFF0A1725), fontSize = 14.sp, fontWeight = FontWeight.Black)
        }
    }
}
