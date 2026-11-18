package com.shadowbooks.ui

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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shadowbooks.data.LabExperiment
import com.shadowbooks.data.LabSubject
import com.shadowbooks.data.formatExperimentCount

@Composable
fun LabSubjectScreen(
    subject: LabSubject,
    onBack: () -> Unit,
    onSelectExperiment: (LabExperiment) -> Unit,
) {
    val accent = subject.accentColor

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A1020))
            .systemBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        TextButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = "رجوع إلى مختبر التجارب",
                tint = accent,
                modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text("مختبر التجارب", color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subject.title,
                    color = Color(0xFFF4F7FC),
                    fontSize = 25.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "التجارب التفاعلية مرتبة حسب الكتاب المدرسي",
                    color = Color(0xFF9BAEC4),
                    fontSize = 13.sp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Surface(
                color = accent.copy(alpha = 0.16f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.border(
                    width = 1.dp,
                    color = accent.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(50),
                ),
            ) {
                Text(
                    text = formatExperimentCount(subject.experiments.size),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        if (subject.experiments.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(subject.cardTop, subject.cardBottom),
                            ),
                        )
                        .border(
                            width = 1.dp,
                            color = accent.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(22.dp),
                        )
                        .padding(horizontal = 24.dp, vertical = 34.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(accent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Science,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "لا توجد تجارب مضافة بعد",
                        color = Color(0xFFF4F7FC),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "سيتم إضافة تجارب ${subject.title} التفاعلية هنا بالترتيب المعتمد في المنهج المدرسي خطوةً بخطوة.",
                        color = Color(0xFF9BAEC4),
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = subject.experiments,
                    key = { it.id },
                ) { experiment ->
                    ExperimentRow(
                        experiment = experiment,
                        accent = accent,
                        cardTop = subject.cardTop,
                        cardBottom = subject.cardBottom,
                        onClick = { onSelectExperiment(experiment) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ExperimentRow(
    experiment: LabExperiment,
    accent: Color,
    cardTop: Color,
    cardBottom: Color,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(cardTop, cardBottom)))
            .border(1.dp, accent.copy(alpha = 0.35f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = experiment.orderInBook.toString().padStart(2, '0'),
                color = accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = experiment.title,
                color = Color(0xFFF3F7FC),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (experiment.unitTitle.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = experiment.unitTitle,
                    color = Color(0xFF9BAEC4),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            imageVector = Icons.Filled.ChevronLeft,
            contentDescription = "فتح ${experiment.title}",
            tint = accent,
            modifier = Modifier.size(23.dp),
        )
    }
}
