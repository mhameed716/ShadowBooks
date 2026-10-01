package com.shadowbooks.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shadowbooks.data.BookVolume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

@Composable
fun PdfReaderScreen(volume: BookVolume, onBack: () -> Unit) {
    val context = LocalContext.current
    val holderState = remember { mutableStateOf<PdfDocumentHolder?>(null) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var pageIndex by remember { mutableIntStateOf(0) }
    var pageInput by remember { mutableStateOf("1") }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val keyboardController = LocalSoftwareKeyboardController.current

    DisposableEffect(Unit) {
        onDispose {
            holderState.value?.close()
            holderState.value = null
        }
    }

    LaunchedEffect(volume.fileName) {
        holderState.value?.close()
        holderState.value = null
        loading = true
        errorMessage = null
        pageCount = 0
        pageIndex = 0
        pageBitmap = null
        try {
            val opened = withContext(Dispatchers.IO) {
                openPdfAsset(context, volume.fileName)
            }
            holderState.value = opened
            pageCount = opened.pageCount
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (missing: FileNotFoundException) {
            errorMessage = "ملف PDF لهذا الكتاب لم يُضف إلى المشروع بعد."
        } catch (exception: Exception) {
            errorMessage = "تعذّر فتح ملف PDF. تأكد من سلامة الملف ثم أعد المحاولة."
        } finally {
            loading = false
        }
    }

    LaunchedEffect(holderState.value, pageIndex) {
        val current = holderState.value ?: return@LaunchedEffect
        if (pageIndex !in 0 until current.pageCount) return@LaunchedEffect
        pageBitmap = null
        errorMessage = null
        try {
            pageBitmap = withContext(Dispatchers.IO) {
                current.renderPage(pageIndex)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            errorMessage = "تعذّر عرض هذه الصفحة من ملف PDF."
        }
    }

    LaunchedEffect(pageIndex) {
        pageInput = (pageIndex + 1).toString()
        zoom = 1f
        pan = Offset.Zero
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        zoom = (zoom * zoomChange).coerceIn(1f, 4f)
        pan = if (zoom <= 1f) Offset.Zero else pan + panChange
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090E18))
            .systemBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF111A2A))
                .padding(horizontal = 9.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "العودة إلى الرفوف",
                    tint = Color(0xFF83DFFF),
                    modifier = Modifier.size(19.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text("الرفوف", color = Color(0xFF83DFFF), fontSize = 12.sp)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp, end = 8.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    text = volume.title,
                    color = Color(0xFFF3F7FC),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text("قارئ PDF", color = Color(0xFF9AADC3), fontSize = 10.sp)
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF151B25)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                loading -> CircularProgressIndicator(color = Color(0xFF65D4FA))
                pageBitmap != null -> Image(
                    bitmap = pageBitmap!!.asImageBitmap(),
                    contentDescription = "صفحة ${pageIndex + 1} من ${volume.title}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .transformable(transformState)
                        .graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            translationX = pan.x
                            translationY = pan.y
                        },
                )
                else -> PdfEmptyState(
                    message = errorMessage ?: "جارٍ تجهيز ملف الكتاب...",
                    loading = false,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF111A2A))
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            zoom = (zoom / 1.25f).coerceAtLeast(1f)
                            if (zoom <= 1f) pan = Offset.Zero
                        },
                        enabled = pageBitmap != null,
                    ) {
                        Icon(Icons.Filled.ZoomOut, contentDescription = "تصغير", tint = Color(0xFFDCE7F3))
                    }
                    Text(
                        text = "${(zoom * 100).toInt()}٪",
                        color = Color(0xFFB7C6D8),
                        fontSize = 11.sp,
                    )
                    IconButton(
                        onClick = { zoom = (zoom * 1.25f).coerceAtMost(4f) },
                        enabled = pageBitmap != null,
                    ) {
                        Icon(Icons.Filled.ZoomIn, contentDescription = "تكبير", tint = Color(0xFFDCE7F3))
                    }
                    TextButton(
                        onClick = {
                            zoom = 1f
                            pan = Offset.Zero
                        },
                        enabled = pageBitmap != null,
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("ملاءمة", fontSize = 11.sp)
                    }
                }
                Text(
                    text = if (pageCount > 0) "$pageCount صفحة" else "PDF",
                    color = Color(0xFF93A5BC),
                    fontSize = 11.sp,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { pageIndex = (pageIndex + 1).coerceAtMost(pageCount - 1) },
                    enabled = pageCount > 0 && pageIndex < pageCount - 1,
                ) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "الصفحة التالية")
                }
                OutlinedTextField(
                    value = pageInput,
                    onValueChange = { value -> pageInput = value.filter { it.isDigit() }.take(5) },
                    modifier = Modifier.width(76.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = TextStyle(textAlign = TextAlign.Center, fontSize = 14.sp),
                )
                Text(
                    text = "/ ${if (pageCount > 0) pageCount else "—"}",
                    modifier = Modifier.padding(horizontal = 8.dp),
                    color = Color(0xFFB7C6D8),
                    fontSize = 13.sp,
                )
                Button(
                    onClick = {
                        val requested = parsePageNumber(pageInput)
                        if (requested != null && pageCount > 0) {
                            pageIndex = (requested - 1).coerceIn(0, pageCount - 1)
                            keyboardController?.hide()
                        }
                    },
                    enabled = pageCount > 0,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 9.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF65D4FA),
                        contentColor = Color(0xFF071522),
                    ),
                ) {
                    Text("انتقال", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(
                    onClick = { pageIndex = (pageIndex - 1).coerceAtLeast(0) },
                    enabled = pageCount > 0 && pageIndex > 0,
                ) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "الصفحة السابقة")
                }
            }
        }
    }
}

@Composable
private fun PdfEmptyState(message: String, loading: Boolean) {
    Column(
        modifier = Modifier.padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.PictureAsPdf,
            contentDescription = null,
            tint = Color(0xFF68CFF2),
            modifier = Modifier.size(54.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = if (loading) "جارٍ تحميل الكتاب" else "الكتاب غير متاح حالياً",
            color = Color(0xFFF0F5FA),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            color = Color(0xFF9AADC3),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp,
        )
    }
}

private fun openPdfAsset(context: Context, fileName: String): PdfDocumentHolder {
    val folder = File(context.cacheDir, "shadowbooks-pdf").apply { mkdirs() }
    val cachedFile = File(folder, fileName)
    if (!cachedFile.exists() || cachedFile.length() == 0L) {
        context.assets.open("books/$fileName").use { input ->
            cachedFile.outputStream().use { output -> input.copyTo(output) }
        }
    }

    val descriptor = ParcelFileDescriptor.open(cachedFile, ParcelFileDescriptor.MODE_READ_ONLY)
    return try {
        PdfDocumentHolder(PdfRenderer(descriptor), descriptor)
    } catch (exception: Exception) {
        descriptor.close()
        throw exception
    }
}

private class PdfDocumentHolder(
    private val renderer: PdfRenderer,
    private val descriptor: ParcelFileDescriptor,
) {
    val pageCount: Int
        get() = renderer.pageCount

    @Synchronized
    fun renderPage(index: Int): Bitmap {
        val page = renderer.openPage(index)
        try {
            val bitmapWidth = 1600
            val bitmapHeight = (page.height * bitmapWidth.toFloat() / page.width).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(
                bitmap,
                Rect(0, 0, bitmapWidth, bitmapHeight),
                null,
                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
            )
            return bitmap
        } finally {
            page.close()
        }
    }

    @Synchronized
    fun close() {
        try {
            renderer.close()
        } finally {
            descriptor.close()
        }
    }
}

private fun parsePageNumber(value: String): Int? {
    val arabicDigits = "٠١٢٣٤٥٦٧٨٩"
    val persianDigits = "۰۱۲۳۴۵۶۷۸۹"
    val latin = buildString {
        value.forEach { character ->
            val arabicIndex = arabicDigits.indexOf(character)
            val persianIndex = persianDigits.indexOf(character)
            when {
                arabicIndex >= 0 -> append(('0'.code + arabicIndex).toChar())
                persianIndex >= 0 -> append(('0'.code + persianIndex).toChar())
                else -> append(character)
            }
        }
    }
    return latin.toIntOrNull()
}
