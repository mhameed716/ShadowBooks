package com.shadowbooks.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shadowbooks.R
import com.shadowbooks.data.BookCatalog
import com.shadowbooks.data.BookVolume
import com.shadowbooks.data.StudyMaterialCatalog
import com.shadowbooks.data.StudyMaterialFile
import com.shadowbooks.data.StudyMaterialKind

private val StudyNight = Color(0xFF071326)
private val StudyCard = Color(0xFF101E32)
private val StudyCyan = Color(0xFF65D4FA)
private val StudyGold = Color(0xFFFFC86B)
private val StudyFont = FontFamily(Font(R.font.tajawal_regular), Font(R.font.tajawal_extrabold, FontWeight.ExtraBold))
private val SubjectTileColors = listOf(
    Color(0xFF765521), Color(0xFF137495), Color(0xFF147B69), Color(0xFF488A3D), Color(0xFF8C6430),
)

private data class StudyResourceCategory(
    val number: Int,
    val title: String,
    val files: List<StudyMaterialFile>,
)

@Composable
fun StudyResourcesScreen(
    onLibrary: () -> Unit,
    onTimetable: () -> Unit,
    onDevicePdfs: () -> Unit,
    onOpen: (BookVolume) -> Unit,
) {
    val context = LocalContext.current
    val goldenUnits = remember(context) {
        StudyMaterialCatalog.goldenEnglishUnits(
            context.assets.list("books/${StudyMaterialCatalog.GOLDEN_ENGLISH_PATH}")?.toList().orEmpty(),
        )
    }
    val materials = remember(context, goldenUnits) {
        StudyMaterialKind.values().associateWith { kind ->
            val files = StudyMaterialCatalog.fromAssetNames(
                kind,
                context.assets.list("books/study/${kind.folder}")?.toList().orEmpty(),
            )
            val group = if (kind == StudyMaterialKind.Summary) StudyMaterialCatalog.goldenEnglishGroup(goldenUnits) else null
            (files + listOfNotNull(group)).groupBy(StudyMaterialFile::bookNumber)
        }
    }
    var selectedKind by rememberSaveable { mutableStateOf(StudyMaterialKind.Exam.name) }
    val kind = StudyMaterialKind.values().firstOrNull { it.name == selectedKind } ?: StudyMaterialKind.Exam
    val bySubject = materials[kind].orEmpty()
    val visibleSubjects = remember(kind, bySubject) {
        if (kind == StudyMaterialKind.Exam) {
            val quranBook = BookCatalog.books.first { it.number == 1 }
            listOf(
                StudyResourceCategory(
                    number = StudyMaterialCatalog.GENERAL_ISLAMIC_NUMBER,
                    title = "الإسلامية",
                    files = StudyMaterialCatalog.islamicGroupFiles(bySubject),
                ),
                StudyResourceCategory(
                    number = quranBook.number,
                    title = quranBook.title,
                    files = bySubject[quranBook.number].orEmpty(),
                ),
                StudyResourceCategory(
                    number = 6,
                    title = "اللغة العربية",
                    files = StudyMaterialCatalog.arabicGroupFiles(bySubject),
                ),
            ) + BookCatalog.books
                .filterNot {
                    it.number == quranBook.number ||
                        StudyMaterialCatalog.isIslamicBook(it.number) ||
                        StudyMaterialCatalog.isArabicBook(it.number)
                }
                .map { book -> StudyResourceCategory(book.number, book.title, bySubject[book.number].orEmpty()) }
        } else {
            BookCatalog.books.map { book ->
                StudyResourceCategory(book.number, book.title, bySubject[book.number].orEmpty())
            }
        }
    }
    var chooseForCategory by remember { mutableStateOf<StudyResourceCategory?>(null) }
    var choosingGoldenUnits by remember { mutableStateOf(false) }
    val openMaterial: (StudyMaterialFile, String) -> Unit = { file, categoryTitle ->
        if (file.assetPath == StudyMaterialCatalog.GOLDEN_ENGLISH_PATH) choosingGoldenUnits = true
        else onOpen(file.asVolume(categoryTitle))
    }
    val categories = listOf(StudyMaterialKind.Exam, StudyMaterialKind.Summary)

    Column(Modifier.fillMaxSize().background(StudyNight).safeDrawingPadding()) {
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 13.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Image(
                    painter = painterResource(R.drawable.library_wordmark),
                    contentDescription = "SHADOWBOOKS",
                    modifier = Modifier.width(152.dp).height(30.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "الملخصات والاختبارات",
                    color = Color.White,
                    fontFamily = StudyFont,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 25.sp,
                )
                Text("مراجعة المواد", color = StudyCyan, fontFamily = StudyFont, fontSize = 14.sp)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                categories.forEach { option ->
                    val selected = option == kind
                    Row(
                        modifier = Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (selected) Color(0xFF123E59) else Color(0xFF101E32))
                            .border(1.dp, if (selected) StudyCyan.copy(alpha = .8f) else Color(0xFF30445D), RoundedCornerShape(16.dp))
                            .clickable(role = Role.Button, onClick = { selectedKind = option.name })
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Description,
                            contentDescription = null,
                            tint = if (selected) StudyCyan else Color(0xFF8DA2BB),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            option.label,
                            color = if (selected) StudyCyan else Color(0xFFB9C6D8),
                            fontFamily = StudyFont,
                            fontSize = 14.sp,
                        )
                    }
                }
            }

            val count = bySubject.values.sumOf { it.size }
            if (count == 0) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF12253B)),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Description, contentDescription = null, tint = StudyGold, modifier = Modifier.size(21.dp))
                        Spacer(Modifier.width(9.dp))
                        Text(
                            "ستظهر الملفات هنا بعد إضافتها.",
                            color = Color(0xFFE0E9F4),
                            fontFamily = StudyFont,
                            fontSize = 13.sp,
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(9.dp),
                contentPadding = PaddingValues(top = 3.dp, bottom = 16.dp),
            ) {
                items(visibleSubjects, key = StudyResourceCategory::number) { subject ->
                    val files = subject.files
                    StudySubjectCard(subject.title, subject.number, kind, files) {
                        when (files.size) {
                            0 -> Unit
                            1 -> openMaterial(files.single(), subject.title)
                            else -> chooseForCategory = subject
                        }
                    }
                }
            }
        }
        HomeBottomNavigation(
            selected = HomeTab.StudyResources,
            onLibrary = onLibrary,
            onStudyResources = {},
            onTimetable = onTimetable,
            onDevicePdfs = onDevicePdfs,
        )
    }

    chooseForCategory?.let { category ->
        AlertDialog(
            onDismissRequest = { chooseForCategory = null },
            containerColor = Color(0xFF13233A),
            title = { Text(category.title, color = Color.White, fontFamily = StudyFont, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    category.files.forEach { file ->
                        TextButton(onClick = { chooseForCategory = null; openMaterial(file, category.title) }) {
                            Icon(Icons.Filled.Description, contentDescription = null, tint = StudyCyan, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(file.title, color = Color.White, fontFamily = StudyFont, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { chooseForCategory = null }) { Text("إغلاق", color = StudyCyan, fontFamily = StudyFont) } },
        )
    }

    if (choosingGoldenUnits) {
        AlertDialog(
            onDismissRequest = { choosingGoldenUnits = false },
            containerColor = Color(0xFF13233A),
            title = { Text(StudyMaterialCatalog.GOLDEN_ENGLISH_TITLE, color = Color.White, fontFamily = StudyFont, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    goldenUnits.forEach { unit ->
                        TextButton(onClick = { choosingGoldenUnits = false; onOpen(unit.asVolume(StudyMaterialCatalog.GOLDEN_ENGLISH_TITLE)) }) {
                            Icon(Icons.Filled.Description, contentDescription = null, tint = StudyCyan, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(unit.title, color = Color.White, fontFamily = StudyFont)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosingGoldenUnits = false }) { Text("إغلاق", color = StudyCyan, fontFamily = StudyFont) } },
        )
    }
}

@Composable
private fun StudySubjectCard(
    title: String,
    subjectNumber: Int,
    kind: StudyMaterialKind,
    files: List<StudyMaterialFile>,
    onClick: () -> Unit,
) {
    val enabled = files.isNotEmpty()
    val tileColor = SubjectTileColors[Math.floorMod(subjectNumber - 1, SubjectTileColors.size)]
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = StudyCard),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // RTL: arrow at the right, text in the middle, and a generic document tile at the left.
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = if (enabled) "فتح ${kind.label} مادة $title" else null,
                tint = if (enabled) StudyCyan else Color(0xFF71839A),
                modifier = Modifier.size(21.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontFamily = StudyFont, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(kind.label, color = StudyCyan, fontFamily = StudyFont, fontSize = 11.sp)
                Text(
                    if (enabled) "${files.size} ملف جاهز" else "بانتظار الملف",
                    color = if (enabled) Color(0xFF91E6C2) else Color(0xFF9DADC0),
                    fontFamily = StudyFont,
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)).background(tileColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Description, contentDescription = null, tint = Color.White, modifier = Modifier.size(27.dp))
            }
        }
    }
}

private fun StudyMaterialFile.asVolume(categoryTitle: String) =
    BookVolume("$categoryTitle — $title", assetPath, "${kind.label} · $categoryTitle")
