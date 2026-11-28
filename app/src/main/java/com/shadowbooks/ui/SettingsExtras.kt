package com.shadowbooks.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.shadowbooks.R
import com.shadowbooks.data.BookCatalog
import com.shadowbooks.data.BookVolume
import com.shadowbooks.data.StudyMaterialKind
import com.shadowbooks.store.LibraryPack
import com.shadowbooks.store.StoreContent
import com.shadowbooks.store.StoreCovers
import com.shadowbooks.store.StoreEntry
import com.shadowbooks.store.TrashBin
import java.io.File
import java.util.Locale
import kotlinx.coroutines.launch

private val ExtraCard = Color(0xFF13233A)
private val ExtraPanel = Color(0xFF101E32)
private val ExtraCyan = Color(0xFF65D4FA)
private val ExtraGold = Color(0xFFFFC86B)
private val ExtraMint = Color(0xFF91E6C2)
private val ExtraMuted = Color(0xFF9DADC0)
private val ExtraFont = FontFamily(
    Font(R.font.tajawal_regular),
    Font(R.font.tajawal_extrabold, FontWeight.ExtraBold),
)

/** A downloaded file the student tapped inside the settings sheets; the app
 *  root watches this and routes it into the reader, from any screen. */
object SettingsOpenRequest {
    var pending by mutableStateOf<PendingDownloadedOpen?>(null)

    data class PendingDownloadedOpen(val volume: BookVolume, val fromResources: Boolean)
}

/**
 * «التنزيل من غير قيود»: one tap takes the student straight to the phone's
 * own switch — battery exemption first, so downloads run without problems.
 */
fun openUnrestrictedDownload(context: Context) {
    val intents = listOf(
        Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:" + context.packageName),
        ),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:" + context.packageName),
        ),
    )
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: Exception) {
            // Try the next door until the phone opens one.
        }
    }
}

/**
 * The downloaded-files shelf that used to live behind the store's «المحمّلة»
 * tab — now a bright little room inside the settings, right below the trash.
 */
@Composable
fun DownloadedFilesSheet(onDismiss: () -> Unit, onOpenFile: () -> Unit) {
    val context = LocalContext.current
    val installedVersion = StoreContent.installedVersion
    val trashVersion = TrashBin.version
    var entries by remember(installedVersion, trashVersion) {
        mutableStateOf(StoreContent.downloadedEntries(context))
    }
    var deleteTarget by remember { mutableStateOf<StoreEntry?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(20.dp))
                .background(ExtraCard)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF0E5E78), Color(0xFF147B69), Color(0xFF765521)),
                        ),
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Column {
                    Text(
                        "الملفات المحمّلة",
                        color = Color.White,
                        fontFamily = ExtraFont,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                    )
                    Text(
                        if (entries.isEmpty()) "لا ملفات منزّلة بعد" else "${entries.size} ملف على هذا الهاتف",
                        color = Color(0xFFE8F6FF),
                        fontFamily = ExtraFont,
                        fontSize = 12.sp,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            if (entries.isEmpty()) {
                Text(
                    "نزّل أي كتاب أو ملف من المتجر وسيظهر هنا لفتحه أو حذفه في أي وقت.",
                    color = ExtraMuted,
                    fontFamily = ExtraFont,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                Column(
                    Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    entries.forEach { entry ->
                        DownloadedFileRow(
                            entry = entry,
                            onDelete = { deleteTarget = entry },
                            onOpen = onOpenFile,
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text("إغلاق", color = ExtraCyan, fontFamily = ExtraFont, fontSize = 13.sp)
                }
            }
        }
    }

    deleteTarget?.let { entry ->
        ConfirmTwiceDialog(
            what = entry.fileName.removeSuffix(".pdf").replace('_', ' '),
            note = "سينتقل إلى سلة المحذوفات في الإعدادات، ويمكنك استرجاعه منها خلال شهر.",
            onDismiss = { deleteTarget = null },
            onConfirmed = {
                deleteTarget = null
                TrashBin.moveToTrash(context, entry)
                entries = StoreContent.downloadedEntries(context)
            },
        )
    }
}

@Composable
private fun DownloadedFileRow(entry: StoreEntry, onDelete: () -> Unit, onOpen: () -> Unit) {
    val context = LocalContext.current
    var cover by remember(entry.dest) { mutableStateOf(StoreCovers.peek(entry.dest)) }
    LaunchedEffect(entry.dest) {
        if (cover == null) cover = StoreCovers.loadForDest(context, entry.dest)
    }
    val title = remember(entry.dest) { downloadedTitle(entry) }
    val sectionLabel = when {
        entry.dest.startsWith("books/study/summaries/") -> "ملخصات"
        entry.dest.startsWith("books/study/exams/") -> "اختبارات"
        else -> "المكتبة"
    }
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ExtraPanel)
            .clickable {
                val isStudy = entry.dest.startsWith("books/study/")
                val kind = if (entry.dest.contains("/summaries/")) {
                    StudyMaterialKind.Summary
                } else {
                    StudyMaterialKind.Exam
                }
                SettingsOpenRequest.pending = SettingsOpenRequest.PendingDownloadedOpen(
                    volume = BookVolume(
                        title = title,
                        fileName = entry.dest.removePrefix("books/"),
                        description = if (isStudy) kind.label else "",
                    ),
                    fromResources = isStudy,
                )
                onOpen()
            }
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(width = 44.dp, height = 56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1B2C47)),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = cover
            if (bitmap != null) {
                Image(
                    bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(Icons.Filled.MenuBook, contentDescription = null, tint = ExtraMuted, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = Color.White,
                fontFamily = ExtraFont,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "$sectionLabel · ${formatExtraSize(entry.size)}",
                color = if (sectionLabel == "المكتبة") ExtraCyan else ExtraMint,
                fontFamily = ExtraFont,
                fontSize = 11.sp,
            )
        }
        Text(
            "فتح",
            color = ExtraGold,
            fontFamily = ExtraFont,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.DeleteOutline, contentDescription = "حذف", tint = Color(0xFFFF9D9D), modifier = Modifier.size(19.dp))
        }
    }
}

private fun downloadedTitle(entry: StoreEntry): String {
    BookCatalog.books.forEach { book ->
        val volumes = book.volumes.ifEmpty { listOf(BookVolume(book.title, book.fileName)) }
        volumes.firstOrNull { "books/${it.fileName}" == entry.dest }?.let { return it.title }
        if ("books/${book.fileName}" == entry.dest) return book.title
    }
    return entry.fileName.removeSuffix(".pdf").replace('_', ' ')
}

/**
 * «إرسال واستيراد الملفات» — its own settings room, separate from the
 * downloaded shelf: send your downloaded files to a friend, or import his.
 */
@Composable
fun FileTransferSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var result by remember { mutableStateOf<String?>(null) }
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
        if (busy) return
        busy = true
        progress = 0f
        scope.launch {
            try {
                val imported = LibraryPack.import(context, uri) { progress = it }
                result = buildString {
                    append("تم الاستيراد: ${imported.imported} ملف جديد")
                    if (imported.skipped > 0) append("، و${imported.skipped} موجودة لديك أصلًا")
                    if (imported.failed > 0) append("، وتعذّر استيراد ${imported.failed} ملف")
                    append(". تجدها الآن في أماكنها بالمكتبة والملخصات والاختبارات.")
                }
            } catch (e: Exception) {
                result = e.message ?: "تعذّر استيراد الحزمة."
            } finally {
                busy = false
            }
        }
    }

    fun startExport() {
        if (busy) return
        busy = true
        progress = 0f
        scope.launch {
            try {
                val packed = LibraryPack.export(context) { progress = it }
                exportedPack = packed.file
                result = "تم تجهيز حزمة مكتبتك: ${packed.count} ملف " +
                    "(${formatExtraSize(packed.bytes)})." +
                    if (packed.savedToDownloads) {
                        " حُفظت نسخة منها في مجلد التنزيلات باسم " +
                            "${LibraryPack.PACK_FILE_NAME} لإعادة إرسالها في أي وقت."
                    } else {
                        ""
                    }
            } catch (e: Exception) {
                result = e.message ?: "تعذّر تجهيز الحزمة."
            } finally {
                busy = false
            }
        }
    }

    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) startImport(uri) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(20.dp))
                .background(ExtraCard)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                "إرسال واستيراد الملفات",
                color = Color.White,
                fontFamily = ExtraFont,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 17.sp,
            )
            Text(
                "شارك ملفاتك المنزّلة مع زميل، أو أضف ملفاته إلى جهازك — بلا إنترنت.",
                color = ExtraMuted,
                fontFamily = ExtraFont,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(12.dp))
            TransferAction(
                title = "إرسال",
                detail = "تجهيز ملفاتي المنزّلة في حزمة واحدة وإرسالها",
                color = ExtraCyan,
            ) { startExport() }
            Spacer(Modifier.height(8.dp))
            TransferAction(
                title = "استيراد",
                detail = "اختيار حزمة ملفات وصلتك وإضافتها إلى مكتبتي",
                color = ExtraMint,
            ) { importPicker.launch(arrayOf("*/*")) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text("إغلاق", color = ExtraCyan, fontFamily = ExtraFont, fontSize = 13.sp)
                }
            }
        }
    }

    if (busy) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = ExtraCard,
            title = {
                Text("جارٍ العمل على الحزمة...", color = Color.White, fontFamily = ExtraFont, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    LinearProgressIndicator(
                        progress = { progress },
                        color = ExtraCyan,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "${(progress * 100).toInt()}٪",
                        color = ExtraMuted,
                        fontFamily = ExtraFont,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {},
        )
    }

    result?.let { message ->
        AlertDialog(
            onDismissRequest = { result = null; exportedPack = null },
            containerColor = ExtraCard,
            title = {
                Text("مكتبة ShadowBooks", color = Color.White, fontFamily = ExtraFont, fontWeight = FontWeight.Bold)
            },
            text = { Text(message, color = ExtraMuted, fontFamily = ExtraFont, fontSize = 14.sp) },
            confirmButton = {
                if (exportedPack != null) {
                    TextButton(onClick = {
                        val file = exportedPack
                        result = null
                        exportedPack = null
                        if (file != null) sharePack(file)
                    }) {
                        Text("إرسال الآن", color = ExtraCyan, fontFamily = ExtraFont)
                    }
                }
                TextButton(onClick = { result = null; exportedPack = null }) {
                    Text("حسنًا", color = ExtraCyan, fontFamily = ExtraFont)
                }
            },
        )
    }
}

@Composable
private fun TransferAction(title: String, detail: String, color: Color, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ExtraPanel)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Text(title, color = color, fontFamily = ExtraFont, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        Text(detail, color = ExtraMuted, fontFamily = ExtraFont, fontSize = 12.sp)
    }
}

private fun formatExtraSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes بايت"
    val megabytes = bytes / 1_048_576.0
    return if (megabytes < 1024.0) {
        String.format(Locale.US, "%.1f ميجا", megabytes)
    } else {
        String.format(Locale.US, "%.2f جيجا", megabytes / 1024.0)
    }
}
