package com.shadowbooks.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shadowbooks.R
import com.shadowbooks.data.BookVolume
import com.shadowbooks.ui.reader.ReaderBookmarks
import com.shadowbooks.ui.reader.ReaderDocument
import com.shadowbooks.ui.reader.ReaderIcon
import com.shadowbooks.ui.reader.ReaderMenu
import com.shadowbooks.ui.reader.ReaderNavigation
import com.shadowbooks.ui.reader.ReaderPages
import com.shadowbooks.ui.reader.ReaderTheme
import com.shadowbooks.ui.reader.ReaderViewport
import com.shadowbooks.ui.reader.arabicDigits
import java.io.FileNotFoundException
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun PdfReaderScreen(volume: BookVolume, onBack: () -> Unit) {
    key(volume.fileName) {
        val context = LocalContext.current.applicationContext
        var night by rememberSaveable { mutableStateOf(false) }
        var retry by remember { mutableIntStateOf(0) }
        var document by remember(retry) { mutableStateOf<ReaderDocument?>(null) }
        var error by remember(retry) { mutableStateOf<Int?>(null) }
        LaunchedEffect(volume.fileName, retry) {
            var owned: ReaderDocument? = null
            try {
                // Assign inside IO so cancellation on the return to Main cannot leak a renderer.
                withContext(Dispatchers.IO) { owned = ReaderDocument.open(context, volume.fileName) }
                document = checkNotNull(owned)
                awaitCancellation()
            } catch (e: CancellationException) {
                throw e
            } catch (e: FileNotFoundException) {
                error = R.string.reader_missing
            } catch (e: Exception) {
                error = R.string.reader_open_error
            } finally {
                withContext(NonCancellable + Dispatchers.IO) { owned?.close() }
            }
        }
        ReaderTheme(night) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                val ready = document
                if (ready != null) {
                    OpenBook(ready, volume, night, onNight = { night = !night }, onBack = onBack)
                } else {
                    Column(
                        Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (error == null) CircularProgressIndicator()
                        Text(stringResource(error ?: R.string.reader_loading))
                        if (error != null) TextButton(onClick = { retry++ }) { Text(stringResource(R.string.reader_retry)) }
                    }
                    ReaderIcon(Icons.AutoMirrored.Filled.ArrowBack, R.string.reader_back, Modifier.align(Alignment.TopStart), onClick = onBack)
                }
            }
        }
    }
}

@Composable
private fun OpenBook(document: ReaderDocument, volume: BookVolume, night: Boolean, onNight: () -> Unit, onBack: () -> Unit) {
    val list = rememberLazyListState()
    val view = remember(document, list) { ReaderViewport(list, document.pageCount, document.firstAspect) }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    var focused by rememberSaveable { mutableStateOf(false) }
    var bookmarkPage by rememberSaveable { mutableStateOf<Int?>(null) }
    var lastZoom by remember { mutableFloatStateOf(view.zoom) }
    var showZoom by remember { mutableStateOf(false) }
    LaunchedEffect(view.zoom) {
        if (lastZoom != view.zoom) {
            lastZoom = view.zoom
            showZoom = true
            delay(850)
            showZoom = false
        }
    }
    BackHandler(enabled = focused) { focused = false }
    Box(Modifier.fillMaxSize()) {
        ReaderPages(document, view, volume.title, night, focused, onReveal = { focused = false })
        if (!focused) {
            // In this RTL overlay Start is physical right, End is physical left.
            ReaderIcon(Icons.AutoMirrored.Filled.ArrowBack, R.string.reader_back, Modifier.align(Alignment.TopStart), onClick = onBack)
            ReaderMenu(
                modifier = Modifier.align(Alignment.TopEnd), night = night, fit = view.fit,
                onNight = onNight, onFit = view::setPageFit,
                onBookmarks = { bookmarkPage = view.currentIndex + 1 },
                onFocus = {
                    focused = true
                    Toast.makeText(context, R.string.reader_focus_hint, Toast.LENGTH_SHORT).show()
                },
                onOpen = { focusManager.clearFocus() },
            )
            ReaderNavigation(view, Modifier.align(Alignment.BottomCenter))
            if (showZoom) Text(
                stringResource(R.string.reader_zoom_percent, (view.zoom * 100).roundToInt().arabicDigits()),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
                color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge,
            )
        }
        bookmarkPage?.let { page ->
            ReaderBookmarks(volume.fileName, page, document.pageCount, onJump = view::jump, onDismiss = { bookmarkPage = null })
        }
    }
}
