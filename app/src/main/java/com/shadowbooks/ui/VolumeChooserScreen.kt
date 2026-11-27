package com.shadowbooks.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shadowbooks.data.Book
import com.shadowbooks.data.BookVolume
import com.shadowbooks.store.StoreContent
import com.shadowbooks.store.StoreDownloadState
import com.shadowbooks.store.StoreDownloader
import com.shadowbooks.store.StoreEntry

@Composable
fun VolumeChooserScreen(
    book: Book,
    onBack: () -> Unit,
    onSelectVolume: (BookVolume) -> Unit,
    onOpenStore: () -> Unit,
) {
    val context = LocalContext.current
    // Store catalog snapshot for per-volume sizes; refreshes as downloads land.
    val installedVersion = StoreContent.installedVersion
    val catalog = remember(context, installedVersion) { StoreContent.cachedCatalog(context) }
    val downloadStates = StoreDownloader.states
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
                contentDescription = "رجوع",
                tint = Color(0xFF83DFFF),
                modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text("الرفوف", color = Color(0xFF83DFFF), fontSize = 12.sp)
        }

        Spacer(Modifier.height(22.dp))
        Text(
            text = book.title,
            color = Color(0xFFF4F7FC),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(7.dp))
        Text(
            text = "اختر الجزء أو النسخة التي تريد فتحها",
            color = Color(0xFF9BAEC4),
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(24.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            book.volumes.forEach { volume ->
                val entry = catalog.firstOrNull { it.dest == "books/${volume.fileName}" }
                val downloaded = StoreContent.isVolumeDownloaded(context, volume.fileName)
                val state = downloadStates[entry?.dest]
                VolumeOption(
                    volume = volume,
                    downloaded = downloaded,
                    entry = entry,
                    state = state,
                    onClick = {
                        when {
                            downloaded -> onSelectVolume(volume)
                            entry != null -> StoreDownloader.enqueue(context, entry)
                            else -> onOpenStore()
                        }
                    },
                    onPause = { entry?.let { StoreDownloader.pause(it.dest) } },
                )
            }
        }

        Spacer(Modifier.weight(1f))
        Text(
            text = "يمكن قراءة الكتب المحفوظة داخل التطبيق دون اتصال بالإنترنت.",
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            color = Color(0xFF71849A),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun VolumeOption(
    volume: BookVolume,
    downloaded: Boolean,
    entry: StoreEntry?,
    state: StoreDownloadState?,
    onClick: () -> Unit,
    onPause: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Color(0xFF1B3450), Color(0xFF152236))))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Color(0xFF214766)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.MenuBook,
                contentDescription = null,
                tint = Color(0xFF9DE6FF),
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = volume.title,
                color = Color(0xFFF3F7FC),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = volume.description,
                color = Color(0xFF9BAEC4),
                fontSize = 11.sp,
            )
            if (!downloaded) {
                Spacer(Modifier.height(5.dp))
                Text(
                    text = when {
                        state is StoreDownloadState.Running && state.total > 0 ->
                            "جارٍ التنزيل ${state.done * 100 / state.total}٪"
                        state is StoreDownloadState.Failed -> state.message
                        entry != null -> "غير منزّل · اضغط للتنزيل"
                        else -> "من المتجر"
                    },
                    color = Color(0xFF65D4FA),
                    fontSize = 11.sp,
                )
            }
        }
        if (!downloaded && state is StoreDownloadState.Running) {
            TextButton(onClick = onPause) {
                Text("إيقاف", color = Color(0xFF65D4FA), fontSize = 12.sp)
            }
        }
        Icon(
            imageVector = if (downloaded) Icons.Filled.ChevronLeft else Icons.Filled.Download,
            contentDescription = "فتح ${volume.title}",
            tint = Color(0xFF83DFFF),
            modifier = Modifier.size(23.dp),
        )
    }
}
