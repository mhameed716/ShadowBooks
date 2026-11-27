package com.shadowbooks.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.shadowbooks.R
import com.shadowbooks.calculator.CalculatorState
import com.shadowbooks.data.BookVolume
import com.shadowbooks.ui.reader.ReaderCalculator
import com.shadowbooks.ui.reader.calculatorSaver
import com.shadowbooks.ui.reader.ReaderBookmarks
import com.shadowbooks.ui.reader.ReaderDocument
import com.shadowbooks.ui.reader.ReaderIcon
import com.shadowbooks.ui.reader.ReaderMenu
import com.shadowbooks.ui.reader.ReaderNavigation
import com.shadowbooks.ui.reader.PageTranslationButton
import com.shadowbooks.ui.reader.ReaderPages
import com.shadowbooks.ui.reader.AN_NUR_AUDIO_ASSET
import com.shadowbooks.ui.reader.QuranAudioPlayer
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
fun PdfReaderScreen(
    volume: BookVolume,
    initialPage: Int = 0,
    onPageChanged: (Int) -> Unit = {},
    onBack: () -> Unit,
) {
    key(volume.fileName) {
        val context = LocalContext.current.applicationContext
        var night by rememberSaveable { mutableStateOf(false) }
        var retry by remember { mutableIntStateOf(0) }
        var document by remember(retry) { mutableStateOf<ReaderDocument?>(null) }
        var error by remember(retry) { mutableStateOf<Int?>(null) }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val session = rememberSaveableStateHolder()
        LaunchedEffect(lifecycle, volume.fileName, retry) {
            // Do not keep rendering or holding the PDF cache after Home / screen off.
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                error = null
                var owned: ReaderDocument? = null
                try {
                    // Assign inside IO so cancellation on the return to Main cannot leak a renderer.
                    withContext(Dispatchers.IO) { owned = ReaderDocument.open(context, volume) }
                    document = checkNotNull(owned)
                    awaitCancellation()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: FileNotFoundException) {
                    error = R.string.reader_missing
                } catch (e: Exception) {
                    error = R.string.reader_open_error
                } finally {
                    document = null
                    withContext(NonCancellable + Dispatchers.IO) { owned?.close() }
                }
            }
        }
        ReaderTheme(night) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                val ready = document
                if (ready != null) {
                    session.SaveableStateProvider(volume.fileName) {
                        OpenBook(ready, volume, night, onNight = { night = !night }, onBack = onBack)
                    }
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
private fun OpenBook(
    document: ReaderDocument,
    volume: BookVolume,
    night: Boolean,
    onNight: () -> Unit,
    onBack: () -> Unit,
    initialPage: Int = 0,
    onPageChanged: (Int) -> Unit = {},
) {
    val list = rememberLazyListState()
    val count = document.pageCount
    val aspect = document.firstAspect
    val saver = remember(list, count, aspect) { ReaderViewport.saver(list, count, aspect) }
    // A fresh renderer on foregrounding the same volume is not a new reading session.
    val view = rememberSaveable(volume.fileName, count, list, saver = saver) { ReaderViewport(list, count, aspect) }
    // «متابعة القراءة»: land on the remembered page once per opened document.
    LaunchedEffect(document) {
        if (initialPage in 1 until count) {
            try {
                view.jump(initialPage)
            } catch (e: Exception) {
                // A failed jump only means reading starts at the first page.
            }
        }
    }
    LaunchedEffect(view.currentIndex) { onPageChanged(view.currentIndex) }
    var arabicPages by rememberSaveable { mutableStateOf(intArrayOf()) }
    val pageIndex = view.currentIndex
    val hasArabic = document.arabicText(pageIndex) != null
    val readingView = LocalView.current
    LifecycleResumeEffect(readingView) {
        val previous = readingView.keepScreenOn
        readingView.keepScreenOn = true
        onPauseOrDispose { readingView.keepScreenOn = previous }
    }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val showQuranAudio = remember(context, volume.fileName) {
        volume.fileName == "quran-and-its-sciences.pdf" &&
            runCatching { context.assets.open(AN_NUR_AUDIO_ASSET).use { } }.isSuccess
    }
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
    var calculatorOpen by rememberSaveable { mutableStateOf(false) }
    var calculatorShare by rememberSaveable { mutableFloatStateOf(.45f) }
    val clipboard = LocalClipboardManager.current
    var calculator by rememberSaveable(stateSaver = calculatorSaver) { mutableStateOf(CalculatorState()) }
    val fold = { focusManager.clearFocus(); calculatorOpen = false; calculator = calculator.copy(menu = null) }
    BackHandler(enabled = focused || calculatorOpen) { if (calculatorOpen) fold() else focused = false }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val available = maxHeight
        val heightPx = with(LocalDensity.current) { available.toPx() }.coerceAtLeast(1f)
        val paneHeight = (available * calculatorShare).coerceIn(minOf(128.dp, available * .45f),
            available - minOf(120.dp, available * .45f))
        val resize: (Float) -> Unit = { delta -> calculatorShare = (calculatorShare + delta).coerceIn(.30f, .68f) }
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                // Keep the PDF and its LazyList in the same composition when folding/unfolding the tool.
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        ReaderPages(document, view, volume.title, night, focused, arabicPages, onReveal = { focused = false })
                        if (!focused) {
                            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), horizontalAlignment = Alignment.End) {
                                if (hasArabic) PageTranslationButton(pageIndex in arabicPages, pageIndex, onClick = {
                                    arabicPages = document.toggleArabic(pageIndex, arabicPages)
                                }, modifier = Modifier.padding(end = 6.dp, bottom = 2.dp))
                                if (!calculatorOpen) ReaderNavigation(view)
                            }
                        }
                    }
                    if (calculatorOpen && !focused) ReaderNavigation(view, Modifier.background(MaterialTheme.colorScheme.surface))
                }
                if (!focused) {
                    // In this RTL overlay Start is physical right, End is physical left.
                    ReaderIcon(Icons.AutoMirrored.Filled.ArrowBack, R.string.reader_back, Modifier.align(Alignment.TopStart),
                        onClick = if (calculatorOpen) fold else onBack)
                    ReaderMenu(
                        modifier = Modifier.align(Alignment.TopEnd), night = night, fit = view.fit,
                        calculatorOpen = calculatorOpen, onCalculator = { if (calculatorOpen) fold() else calculatorOpen = true },
                        onNight = onNight, onFit = view::setPageFit,
                        onBookmarks = { bookmarkPage = view.currentIndex + 1 },
                        onFocus = {
                            fold(); focused = true
                            Toast.makeText(context, R.string.reader_focus_hint, Toast.LENGTH_SHORT).show()
                        },
                        onOpen = { focusManager.clearFocus() },
                    )
                    if (showZoom) Text(
                        stringResource(R.string.reader_zoom_percent, (view.zoom * 100).roundToInt().arabicDigits()),
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
                        color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge,
                    )
                    if (showQuranAudio) {
                        QuranAudioPlayer(
                            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                                .padding(horizontal = 14.dp).padding(top = 52.dp),
                        )
                    }
                }
            }
            if (calculatorOpen) ReaderCalculator(
                calculator, onKey = { key ->
                    calculator = calculator.press(key, readClipboard = { clipboard.getText()?.text },
                        writeClipboard = { clipboard.setText(AnnotatedString(it)) })
                }, onState = { calculator = it },
                onResult = { request, result -> calculator = calculator.finishWork(request, result) }, onFold = fold,
                onDrag = { resize(-it / heightPx) }, onNudge = resize,
                modifier = Modifier.fillMaxWidth().height(paneHeight),
            )
        }
        bookmarkPage?.let { page ->
            ReaderBookmarks(volume.fileName, page, document.pageCount, onJump = view::jump, onDismiss = { bookmarkPage = null })
        }
    }
}
