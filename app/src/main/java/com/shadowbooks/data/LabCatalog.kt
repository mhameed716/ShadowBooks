package com.shadowbooks.data

import androidx.compose.ui.graphics.Color

enum class LabSubjectId {
    CHEMISTRY,
    PHYSICS,
    BIOLOGY,
    MATHEMATICS,
}

data class LabExperiment(
    val id: String,
    val orderInBook: Int,
    val title: String,
    val unitTitle: String = "",
    val summary: String = "",
)

data class LabSubject(
    val id: LabSubjectId,
    val title: String,
    val accentColor: Color,
    val cardTop: Color,
    val cardBottom: Color,
    val experiments: List<LabExperiment> = emptyList(),
)

object LabCatalog {
    val subjects = listOf(
        LabSubject(
            id = LabSubjectId.CHEMISTRY,
            title = "مختبر الكيمياء",
            accentColor = Color(0xFF38E2B5),
            cardTop = Color(0xFF102A2F),
            cardBottom = Color(0xFF0C1826),
            experiments = listOf(
                LabExperiment(
                    id = "chem_periodic_table",
                    orderInBook = 1,
                    title = "الجدول الدوري التفاعلي للعناصر",
                    unitTitle = "الوحدة الأولى: العناصر الانتقالية (ص 11–15)",
                    summary = "استعراض جميع العناصر الـ 118 بشكلها الحقيقي ولونها وكتلتها وتوزيعها الإلكتروني وتفاعلاتها بالصوت.",
                ),
            ),
        ),
        LabSubject(
            id = LabSubjectId.PHYSICS,
            title = "مختبر الفيزياء",
            accentColor = Color(0xFF65D4FA),
            cardTop = Color(0xFF10253D),
            cardBottom = Color(0xFF0B1628),
        ),
        LabSubject(
            id = LabSubjectId.BIOLOGY,
            title = "مختبر الأحياء",
            accentColor = Color(0xFF5CE68D),
            cardTop = Color(0xFF112C24),
            cardBottom = Color(0xFF0B1724),
        ),
        LabSubject(
            id = LabSubjectId.MATHEMATICS,
            title = "مختبر الرياضيات",
            accentColor = Color(0xFFFFC86B),
            cardTop = Color(0xFF2B2316),
            cardBottom = Color(0xFF0E1626),
        ),
    )

    fun findSubject(id: LabSubjectId): LabSubject =
        subjects.firstOrNull { it.id == id } ?: subjects.first()
}

fun formatExperimentCount(count: Int): String = when {
    count <= 0 -> "0 تجارب"
    count == 1 -> "تجربة واحدة"
    count == 2 -> "تجربتان"
    count in 3..10 -> "$count تجارب"
    else -> "$count تجربة"
}
