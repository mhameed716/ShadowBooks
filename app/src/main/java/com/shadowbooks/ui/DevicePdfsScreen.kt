package com.shadowbooks.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shadowbooks.R
import com.shadowbooks.data.BookVolume
import com.shadowbooks.data.DevicePdfEntry
import com.shadowbooks.data.DevicePdfLibrary
import com.shadowbooks.data.Favorites
import com.shadowbooks.store.StoreCovers
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale

private val DevicePdfNight = Color(0xFF071326)
private val DevicePdfCardColor = Color(0xFF101E32)
private val DevicePdfCyan = Color(0xFF65D4FA)
private val DevicePdfFont = FontFamily(
    Font(R.font.tajawal_regular),
    Font(R.font.tajawal_extrabold, FontWeight.ExtraBold),
)

@Composable
fun DevicePdfsScreen(
    onLibrary: () -> Unit,
    onStudyResources: () -> Unit,
    onTimetable: () -> Unit,
    onStore: () -> Unit,
    onOpen: (BookVolume) -> Unit,
) {
    val context = LocalContext.current
    var entries by remember(context) { mutableStateOf(DevicePdfLibrary.load(context)) }
    var showSettings by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<DevicePdfEntry?>(null) }
    LaunchedEffect(Unit) { Favorites.load(context) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { picked ->
        if (picked.isNotEmpty()) {
            val accepted = mutableListOf<DevicePdfEntry>()
            var invalidFiles = 0
            var permissionFailures = 0
            picked.forEach { uri ->
                val name = DevicePdfLibrary.displayName(context, uri) ?: uri.lastPathSegment.orEmpty()
                val mimeType = runCatching { context.contentResolver.getType(uri) }.getOrNull()
                if (!DevicePdfLibrary.looksLikePdf(mimeType, name)) {
                    invalidFiles++
                } else {
                    try {
                        // ACTION_OPEN_DOCUMENT grants access only to the documents the user selected.
                        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        accepted += DevicePdfEntry(uri.toString(), name.ifBlank { context.getString(R.string.device_pdf_unnamed) })
                    } catch (_: Exception) {
                        permissionFailures++
                    }
                }
            }
            val updated = (entries + accepted).distinctBy(DevicePdfEntry::uri)
            val added = updated.size - entries.size
            if (added > 0) {
                DevicePdfLibrary.save(context, updated)
                entries = updated
            }
            when {
                added > 0 -> Toast.makeText(context, context.getString(R.string.device_pdf_added, added), Toast.LENGTH_SHORT).show()
                permissionFailures > 0 -> Toast.makeText(context, R.string.device_pdf_access_failed, Toast.LENGTH_LONG).show()
                invalidFiles > 0 -> Toast.makeText(context, R.string.device_pdf_choose_pdf, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(Modifier.fillMaxSize().background(DevicePdfNight).safeDrawingPadding()) {
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 13.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(R.drawable.library_wordmark),
                        contentDescription = "SHADOWBOOKS",
                        modifier = Modifier.width(152.dp).height(30.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    SpinningGearButton(onClick = { showSettings = true })
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.device_pdf_title),
                    color = Color.White,
                    fontFamily = DevicePdfFont,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 25.sp,
                )
                Text(
                    stringResource(R.string.device_pdf_subtitle),
                    color = DevicePdfCyan,
                    fontFamily = DevicePdfFont,
                    fontSize = 14.sp,
                )
            }

            Button(
                onClick = { picker.launch(arrayOf("application/pdf")) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF123E59),
                    contentColor = DevicePdfCyan,
                ),
                contentPadding = PaddingValues(horizontal = 14.dp),
            ) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(21.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.device_pdf_pick), fontFamily = DevicePdfFont, fontWeight = FontWeight.Bold)
            }

            Text(
                stringResource(R.string.device_pdf_permission_note),
                modifier = Modifier.padding(top = 9.dp, bottom = 12.dp),
                color = Color(0xFFB9C6D8),
                fontFamily = DevicePdfFont,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )

            if (entries.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF12253B)),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Description, contentDescription = null, tint = DevicePdfCyan, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(9.dp))
                        Text(
                            stringResource(R.string.device_pdf_empty),
                            color = Color(0xFFE0E9F4),
                            fontFamily = DevicePdfFont,
                            fontSize = 13.sp,
                        )
                    }
                }
            } else {
                Text(
                    stringResource(R.string.device_pdf_count, entries.size),
                    modifier = Modifier.padding(bottom = 8.dp),
                    color = DevicePdfCyan,
                    fontFamily = DevicePdfFont,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                val sortedEntries = remember(entries, Favorites.keys) {
                    entries.sortedByDescending { Favorites.isFavorite("device:${it.uri}") }
                }
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                    contentPadding = PaddingValues(bottom = 14.dp),
                ) {
                    items(sortedEntries.chunked(2), key = { row -> row.first().uri }) { row ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            row.forEach { entry ->
                                Box(Modifier.weight(1f)) {
                                    DevicePdfCard(
                                        entry = entry,
                                        onOpen = { onOpen(entry.asVolume()) },
                                        onRemove = { removeTarget = entry },
                                    )
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        HomeBottomNavigation(
            selected = HomeTab.DevicePdfs,
            onLibrary = onLibrary,
            onStudyResources = onStudyResources,
            onTimetable = onTimetable,
            onDevicePdfs = {},
            onStore = onStore,
        )
    }

    removeTarget?.let { entry ->
        ConfirmTwiceDialog(
            what = entry.displayName,
            note = "سيُزال من قائمة ملفات الهاتف فقط؛ الملف نفسه يبقى في هاتفك.",
            confirmLabel = "نعم، أزِل",
            onDismiss = { removeTarget = null },
            onConfirmed = {
                removeTarget = null
                val updated = entries.filterNot { it.uri == entry.uri }
                DevicePdfLibrary.save(context, updated)
                entries = updated
                runCatching {
                    context.contentResolver.releasePersistableUriPermission(
                        Uri.parse(entry.uri),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            },
        )
    }

    if (showSettings) {
        SettingsSheet(onDismiss = { showSettings = false })
    }
}

@Composable
private fun DevicePdfCard(entry: DevicePdfEntry, onOpen: () -> Unit, onRemove: () -> Unit) {
    val context = LocalContext.current
    val favoriteKey = "device:${entry.uri}"
    val favorite = Favorites.isFavorite(favoriteKey)
    var cover by remember(entry.uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(entry.uri) {
        cover = StoreCovers.loadUri(context, Uri.parse(entry.uri), entry.readerKey)
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = DevicePdfCardColor),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(1.45f).background(Color(0xFF1B2C47)),
                contentAlignment = Alignment.Center,
            ) {
                val bitmap = cover
                if (bitmap != null) {
                    Image(
                        bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        Icons.Filled.MenuBook,
                        contentDescription = null,
                        tint = DevicePdfCyan,
                        modifier = Modifier.size(30.dp),
                    )
                }
                IconButton(
                    onClick = { Favorites.toggle(context, favoriteKey) },
                    modifier = Modifier.align(Alignment.TopEnd).size(34.dp),
                ) {
                    Icon(
                        if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = if (favorite) "إزالة من المفضلة" else "إضافة إلى المفضلة",
                        tint = if (favorite) Color(0xFFFFC86B) else Color.White,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
            Column(Modifier.fillMaxWidth().padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 4.dp)) {
                Text(
                    entry.displayName,
                    color = Color.White,
                    fontFamily = DevicePdfFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.device_pdf_open_hint),
                        color = Color(0xFF9EB2C9),
                        fontFamily = DevicePdfFont,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onRemove) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = stringResource(R.string.device_pdf_remove, entry.displayName),
                            tint = Color(0xFFE7A0A0),
                        )
                    }
                }
            }
        }
    }
}
