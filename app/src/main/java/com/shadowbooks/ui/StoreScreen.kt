package com.shadowbooks.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shadowbooks.R
import com.shadowbooks.data.Book
import com.shadowbooks.data.BookCatalog
import com.shadowbooks.data.BookVolume
import com.shadowbooks.data.StudyMaterialCatalog
import com.shadowbooks.data.StudyMaterialFile
import com.shadowbooks.data.StudyMaterialKind
import com.shadowbooks.store.LibraryPack
import com.shadowbooks.store.PackImportRequest
import com.shadowbooks.store.StoreContent
import com.shadowbooks.store.StoreDownloadState
import com.shadowbooks.store.StoreDownloader
import com.shadowbooks.store.StoreEntry
import java.io.File
import java.util.Locale
import kotlinx.coroutines.launch

private val StoreNight = Color(0xFF071326)
private val StoreCard = Color(0xFF101E32)
private val StoreCyan = Color(0xFF65D4FA)
private val StoreMuted = Color(0xFF9DADC0)
private val StoreFont = FontFamily(
    Font(R.font.tajawal_regular),
    Font(R.font.tajawal_extrabold, FontWeight.ExtraBold),
)

internal const val STORE_SECTION_LIBRARY = 0
internal const val STORE_SECTION_SUMMARY = 1
internal const val STORE_SECTION_EXAM = 2

private data class StoreCategory(
    val number: Int,
    val title: String,
    val files: List<StudyMaterialFile>,
)

@Composable
fun StoreScreen(
    section: Int,
    onSectionChange: (Int) -> Unit,
    onLibrary: () -> Unit,
    onStudyResources: () -> Unit,
    onTimetable: () -> Unit,
    onDevicePdfs: () -> Unit,
    onOpenBook: (BookVolume) -> Unit,
    onOpenStudy: (BookVolume) -> Unit,
) {
    val context = LocalContext.current
    // Subscribing to this snapshot state refreshes the lists as downloads finish.
    val installedVersion = StoreContent.installedVersion
    var catalog by remember { mutableStateOf(StoreContent.cachedCatalog(context)) }
    var refreshing by remember { mutableStateOf(false) }
    var refreshFailed by remember { mutableStateOf(false) }
    var downloadedMode by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableIntStateOf(0) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    fun enqueue(entry: StoreEntry) {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        StoreDownloader.enqueue(context, entry)
    }

    LaunchedEffect(refreshTick) {
        refreshing = true
        StoreContent.refreshCatalog(context)
            .onSuccess { catalog = it }
            .onFailure { refreshFailed = true }
        refreshing = false
    }

    // Library-pack export/import ("send my downloaded files" to a friend).
    val scope = rememberCoroutineScope()
    var packBusy by remember { mutableStateOf(false) }
    var packProgress by remember { mutableFloatStateOf(0f) }
    var packDialog by remember { mutableStateOf<String?>(null) }
    var exportedPack by remember { mutableStateOf<File?>(null) }

    fun sharePack(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND)
            .setType("application/zip")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "إرسال ملفات ShadowBooks"))
    }

    fun startImport(uri: Uri) {
        if (packBusy) return
        packBusy = true
        packProgress = 0f
        scope.launch {
            try {
                val result = LibraryPack.import(context, uri) { packProgress = it }
                packDialog = buildString {
                    append("تم الاستيراد: ${result.imported} ملف جديد")
                    if (result.skipped > 0) append("، و${result.skipped} موجودة لديك أصلًا")
                    if (result.failed > 0) append("، وتعذّر استيراد ${result.failed} ملف")
                    append(". تجدها الآن في أماكنها بالمكتبة والملخصات والاختبارات.")
                }
            } catch (e: Exception) {
                packDialog = e.message ?: "تعذّر استيراد الحزمة."
            } finally {
                packBusy = false
            }
        }
    }

    fun startExport() {
        if (packBusy) return
        packBusy = true
        packProgress = 0f
        scope.launch {
            try {
                val result = LibraryPack.export(context) { packProgress = it }
                exportedPack = result.file
                packDialog = "تم تجهيز حزمة مكتبتك: ${result.count} ملف " +
                    "(${formatStoreSize(result.bytes)})." +
                    if (result.savedToDownloads) {
                        " حُفظت نسخة منها في مجلد التنزيلات باسم " +
                            "${LibraryPack.PACK_FILE_NAME} لإعادة إرسالها في أي وقت."
                    } else {
                        ""
                    }
            } catch (e: Exception) {
                packDialog = e.message ?: "تعذّر تجهيز الحزمة."
            } finally {
                packBusy = false
            }
        }
    }

    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) startImport(uri) }

    // A pack opened from a file manager ("open with ShadowBooks") lands here.
    val pendingPackUri = PackImportRequest.pendingUri
    LaunchedEffect(pendingPackUri) {
        if (pendingPackUri != null) {
            PackImportRequest.pendingUri = null
            downloadedMode = true
            startImport(Uri.parse(pendingPackUri))
        }
    }

    // On-disk files (including freshly imported ones) must list even before
    // the catalog is known; the catalog overrides them whenever available.
    val diskEntries = remember(installedVersion) { StoreContent.downloadedEntries(context) }
    val entriesByDest = remember(catalog, diskEntries) {
        buildMap {
            diskEntries.forEach { put(it.dest, it) }
            catalog.forEach { put(it.dest, it) }
        }
    }

    fun studyMaterials(kind: StudyMaterialKind): Map<Int, List<StudyMaterialFile>> {
        val prefix = "books/study/${kind.folder}/"
        val names = entriesByDest.keys
            .filter { it.startsWith(prefix) }
            .map { it.removePrefix(prefix) }
            .filter { '/' !in it }
        val files = StudyMaterialCatalog.fromAssetNames(kind, names)
        val golden = if (kind == StudyMaterialKind.Summary) {
            val goldenPrefix = "books/${StudyMaterialCatalog.GOLDEN_ENGLISH_PATH}/"
            val units = StudyMaterialCatalog.goldenEnglishUnits(
                entriesByDest.keys
                    .filter { it.startsWith(goldenPrefix) }
                    .map { it.removePrefix(goldenPrefix) },
            )
            listOfNotNull(StudyMaterialCatalog.goldenEnglishGroup(units))
        } else {
            emptyList()
        }
        return (files + golden).groupBy(StudyMaterialFile::bookNumber)
    }

    fun studyCategories(kind: StudyMaterialKind): List<StoreCategory> {
        val bySubject = studyMaterials(kind)
        fun single(number: Int): StoreCategory? =
            BookCatalog.books.firstOrNull { it.number == number }?.let { book ->
                StoreCategory(book.number, book.title, bySubject[book.number].orEmpty())
            }
        val quran = BookCatalog.books.first { it.number == 1 }
        val grouped = listOf(
            StoreCategory(quran.number, quran.title, bySubject[quran.number].orEmpty()),
        ) + if (kind == StudyMaterialKind.Exam) listOf(
            StoreCategory(
                StudyMaterialCatalog.GENERAL_ISLAMIC_NUMBER,
                "الإسلامية",
                StudyMaterialCatalog.islamicGroupFiles(bySubject),
            ),
            StoreCategory(6, "اللغة العربية", StudyMaterialCatalog.arabicGroupFiles(bySubject)),
        ) else emptyList()
        val singles = listOf(9, 11, 12, 10, 13).mapNotNull { single(it) }
        return if (kind == StudyMaterialKind.Exam) {
            listOf(
                StoreCategory(
                    StudyMaterialCatalog.MINISTRY_MODELS_GUIDE_NUMBER,
                    "كيف تستفيد من النماذج الوزارية؟",
                    bySubject[StudyMaterialCatalog.MINISTRY_MODELS_GUIDE_NUMBER].orEmpty(),
                ),
                StoreCategory(
                    StudyMaterialCatalog.MODEL_VS_QUESTION_BANK_NUMBER,
                    "الفرق بين النموذج وبنك الأسئلة",
                    bySubject[StudyMaterialCatalog.MODEL_VS_QUESTION_BANK_NUMBER].orEmpty(),
                ),
            ) + grouped + singles
        } else {
            grouped + singles
        }
    }

    fun materialEntry(material: StudyMaterialFile): StoreEntry? =
        entriesByDest["books/${material.assetPath}"]

    fun goldenUnits(): List<StudyMaterialFile> {
        val goldenPrefix = "books/${StudyMaterialCatalog.GOLDEN_ENGLISH_PATH}/"
        return StudyMaterialCatalog.goldenEnglishUnits(
            entriesByDest.keys
                .filter { it.startsWith(goldenPrefix) }
                .map { it.removePrefix(goldenPrefix) },
        )
    }

    Column(Modifier.fillMaxSize().background(StoreNight).safeDrawingPadding()) {
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "المتجر",
                    color = Color.White,
                    fontFamily = StoreFont,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 25.sp,
                    modifier = Modifier.weight(1f),
                )
                if (refreshing) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = StoreCyan)
                } else {
                    IconButton(onClick = { refreshTick++ }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "تحديث", tint = StoreCyan)
                    }
                }
            }

            // Available / Downloaded toggle.
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StoreChip("المتوفرة", !downloadedMode, Modifier.weight(1f)) { downloadedMode = false }
                StoreChip("المحمّلة", downloadedMode, Modifier.weight(1f)) { downloadedMode = true }
            }
            // Section toggle.
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StoreChip("المكتبة", section == STORE_SECTION_LIBRARY, Modifier.weight(1f)) {
                    onSectionChange(STORE_SECTION_LIBRARY)
                }
                StoreChip("الملخصات", section == STORE_SECTION_SUMMARY, Modifier.weight(1f)) {
                    onSectionChange(STORE_SECTION_SUMMARY)
                }
                StoreChip("الاختبارات", section == STORE_SECTION_EXAM, Modifier.weight(1f)) {
                    onSectionChange(STORE_SECTION_EXAM)
                }
            }

            if (downloadedMode) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StoreChip("إرسال ملفاتي", false, Modifier.weight(1f)) {
                        if (!packBusy) startExport()
                    }
                    StoreChip("استيراد ملفات", false, Modifier.weight(1f)) {
                        if (!packBusy) importPicker.launch(arrayOf("*/*"))
                    }
                }
            }

            if (packBusy) {
                AlertDialog(
                    onDismissRequest = {},
                    containerColor = StoreCard,
                    title = {
                        Text(
                            "جارٍ العمل على الحزمة...",
                            color = Color.White,
                            fontFamily = StoreFont,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    text = {
                        Column {
                            LinearProgressIndicator(
                                progress = { packProgress },
                                color = StoreCyan,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                "${(packProgress * 100).toInt()}٪",
                                color = StoreMuted,
                                fontFamily = StoreFont,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    },
                    confirmButton = {},
                )
            }

            packDialog?.let { message ->
                AlertDialog(
                    onDismissRequest = { packDialog = null; exportedPack = null },
                    containerColor = StoreCard,
                    title = {
                        Text(
                            "مكتبة ShadowBooks",
                            color = Color.White,
                            fontFamily = StoreFont,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    text = {
                        Text(
                            message,
                            color = StoreMuted,
                            fontFamily = StoreFont,
                            fontSize = 14.sp,
                        )
                    },
                    confirmButton = {
                        if (exportedPack != null) {
                            TextButton(onClick = {
                                val file = exportedPack
                                packDialog = null
                                exportedPack = null
                                if (file != null) sharePack(file)
                            }) {
                                Text("إرسال الآن", color = StoreCyan, fontFamily = StoreFont)
                            }
                        }
                        TextButton(onClick = { packDialog = null; exportedPack = null }) {
                            Text("حسنًا", color = StoreCyan, fontFamily = StoreFont)
                        }
                    },
                )
            }

            if (catalog.isEmpty() && diskEntries.isEmpty() && !refreshing) {
                Text(
                    if (refreshFailed) "تعذر تحديث القائمة. تحقق من الاتصال ثم أعد المحاولة."
                    else "لا توجد ملفات متاحة بعد.",
                    color = StoreMuted,
                    fontFamily = StoreFont,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }

            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(9.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                when (section) {
                    STORE_SECTION_LIBRARY -> {
                        items(BookCatalog.books, key = Book::number) { book ->
                            val volumes = book.volumes.ifEmpty {
                                listOf(BookVolume(book.title, book.fileName))
                            }
                            val rows = volumes.mapNotNull { volume ->
                                entriesByDest["books/${volume.fileName}"]?.let { volume to it }
                            }
                            val visible = if (downloadedMode) {
                                rows.filter { StoreContent.isInstalled(context, it.second) }
                            } else {
                                rows
                            }
                            if (visible.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    StoreHeader(book.title)
                                    visible.forEach { (volume, entry) ->
                                        StoreEntryRow(
                                            entry = entry,
                                            title = volume.title,
                                            downloadedMode = downloadedMode,
                                            onOpen = { onOpenBook(volume) },
                                            onDownload = { enqueue(entry) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        val kind = if (section == STORE_SECTION_SUMMARY) {
                            StudyMaterialKind.Summary
                        } else {
                            StudyMaterialKind.Exam
                        }
                        // Sections = subject categories, plus the golden-English
                        // units as their own section inside the summaries.
                        data class StudySection(
                            val key: String,
                            val title: String,
                            val rows: List<Pair<StudyMaterialFile, StoreEntry>>,
                        )
                        val sections = buildList {
                            studyCategories(kind).forEach { category ->
                                val rows = category.files
                                    .filter { it.assetPath != StudyMaterialCatalog.GOLDEN_ENGLISH_PATH }
                                    .mapNotNull { material ->
                                        materialEntry(material)?.let { material to it }
                                    }
                                add(StudySection("cat-${category.number}", category.title, rows))
                            }
                            if (kind == StudyMaterialKind.Summary) {
                                val rows = goldenUnits().mapNotNull { unit ->
                                    materialEntry(unit)?.let { unit to it }
                                }
                                if (rows.isNotEmpty()) {
                                    add(
                                        StudySection(
                                            "golden",
                                            StudyMaterialCatalog.GOLDEN_ENGLISH_TITLE,
                                            rows,
                                        ),
                                    )
                                }
                            }
                        }
                        items(sections, key = { it.key }) { studySection ->
                            val visible = if (downloadedMode) {
                                studySection.rows.filter {
                                    StoreContent.isInstalled(context, it.second)
                                }
                            } else {
                                studySection.rows
                            }
                            if (visible.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    StoreHeader(studySection.title)
                                    visible.forEach { (material, entry) ->
                                        StoreEntryRow(
                                            entry = entry,
                                            title = material.title,
                                            downloadedMode = downloadedMode,
                                            onOpen = {
                                                onOpenStudy(
                                                    BookVolume(
                                                        material.title,
                                                        material.assetPath,
                                                        kind.label,
                                                    ),
                                                )
                                            },
                                            onDownload = { enqueue(entry) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        HomeBottomNavigation(
            selected = HomeTab.Store,
            onLibrary = onLibrary,
            onStudyResources = onStudyResources,
            onTimetable = onTimetable,
            onDevicePdfs = onDevicePdfs,
            onStore = {},
        )
    }
}

@Composable
private fun StoreHeader(title: String) {
    Text(
        title,
        color = StoreCyan,
        fontFamily = StoreFont,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun StoreChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier.height(42.dp).clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color(0xFF123E59) else StoreCard)
            .border(
                1.dp,
                if (selected) StoreCyan.copy(alpha = .8f) else Color(0xFF30445D),
                RoundedCornerShape(14.dp),
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = if (selected) StoreCyan else Color(0xFFB9C6D8),
            fontFamily = StoreFont,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun StoreEntryRow(
    entry: StoreEntry,
    title: String,
    downloadedMode: Boolean,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
) {
    val context = LocalContext.current
    val installedVersion = StoreContent.installedVersion
    val state = StoreDownloader.states[entry.dest]
    val installed = StoreContent.isInstalled(context, entry)
    val update = StoreContent.hasUpdate(context, entry)
    val partial = if (!installed && state !is StoreDownloadState.Running) {
        StoreDownloader.partialBytes(context, entry)
    } else {
        0L
    }
    Card(
        modifier = Modifier.fillMaxWidth()
            .clickable(enabled = installed, role = Role.Button, onClick = onOpen),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = StoreCard),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = Color.White,
                    fontFamily = StoreFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                val detail = when {
                    state is StoreDownloadState.Running ->
                        "${formatStoreSize(state.done)} من ${formatStoreSize(state.total)}"
                    state is StoreDownloadState.Failed -> state.message
                    installed && update -> "يتوفر تحديث · ${formatStoreSize(entry.size)}"
                    installed -> formatStoreSize(entry.size)
                    partial > 0L -> "محفوظ ${formatStoreSize(partial)} من ${formatStoreSize(entry.size)}"
                    else -> formatStoreSize(entry.size)
                }
                Text(
                    detail,
                    color = if (state is StoreDownloadState.Failed) Color(0xFFFF9D9D) else StoreMuted,
                    fontFamily = StoreFont,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (state is StoreDownloadState.Running) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = {
                            if (state.total > 0) {
                                (state.done.toFloat() / state.total).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color = StoreCyan,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            when {
                state is StoreDownloadState.Running -> IconButton(
                    onClick = { StoreDownloader.pause(entry.dest) },
                ) {
                    Icon(Icons.Filled.Pause, contentDescription = "إيقاف مؤقت", tint = StoreCyan)
                }
                installed && !update -> TextButton(onClick = onOpen) {
                    Text("فتح", color = StoreCyan, fontFamily = StoreFont)
                }
                else -> TextButton(onClick = onDownload) {
                    Icon(
                        Icons.Filled.Download,
                        contentDescription = null,
                        tint = StoreCyan,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        when {
                            update -> "تحديث"
                            partial > 0L || state is StoreDownloadState.Paused -> "استئناف"
                            state is StoreDownloadState.Failed -> "إعادة المحاولة"
                            else -> "تنزيل"
                        },
                        color = StoreCyan,
                        fontFamily = StoreFont,
                        fontSize = 13.sp,
                    )
                }
            }
            if (downloadedMode && installed) {
                IconButton(onClick = {
                    StoreDownloader.cancel(entry.dest)
                    StoreContent.deleteLocal(context, entry)
                }) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = "حذف",
                        tint = Color(0xFFFF9D9D),
                    )
                }
            }
        }
    }
}

private fun formatStoreSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes بايت"
    val megabytes = bytes / 1_048_576.0
    return if (megabytes < 1024.0) {
        String.format(Locale.US, "%.1f ميجا", megabytes)
    } else {
        String.format(Locale.US, "%.2f جيجا", megabytes / 1024.0)
    }
}
