package com.shadowbooks.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class RenderedPage(val bitmap: Bitmap, val aspect: Float)

/** One renderer per open volume. PdfRenderer permits only one open page at a time. */
internal class ReaderDocument private constructor(
    private val renderer: PdfRenderer,
    val pageCount: Int,
    val firstAspect: Float,
) {
    private val lock = Mutex()
    private var closed = false
    private val cache = object : LruCache<Pair<Int, Int>, RenderedPage>(
        (Runtime.getRuntime().maxMemory() / 8).coerceIn(4L shl 20, 20L shl 20).toInt(),
    ) {
        override fun sizeOf(key: Pair<Int, Int>, value: RenderedPage) = value.bitmap.byteCount
    }

    suspend fun page(index: Int, targetWidth: Int): RenderedPage = withContext(Dispatchers.IO) {
        lock.withLock {
            check(!closed)
            val key = index to targetWidth
            cache.get(key) ?: renderer.openPage(index).use { page ->
                val w = page.width.toFloat()
                val h = page.height.toFloat()
                require(w > 0 && h > 0)
                // Bound both the allocation and texture dimensions, including on unusually tall sheets.
                val scale = minOf(targetWidth / w, 3072f / maxOf(w, h), sqrt(2_500_000f / (w * h)))
                val bitmap = Bitmap.createBitmap(
                    (w * scale).roundToInt().coerceAtLeast(1),
                    (h * scale).roundToInt().coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888,
                )
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                RenderedPage(bitmap, w / h).also { cache.put(key, it) }
            }
        }
    }

    suspend fun close() {
        lock.withLock {
            if (closed) return@withLock
            closed = true
            cache.evictAll() // Never recycle a bitmap that Compose may still be drawing.
            renderer.close() // PdfRenderer owns its ParcelFileDescriptor.
        }
    }

    companion object {
        /** Blocking; call on IO. Publish the cached PDF only after the asset was copied in full. */
        fun open(context: Context, fileName: String): ReaderDocument {
            val dir = File(context.cacheDir, "shadowbooks-pdf").apply { mkdirs() }
            val file = File(dir, fileName)
            if (file.length() == 0L) {
                val tmp = File.createTempFile("book-", ".part", dir)
                try {
                    context.assets.open("books/$fileName").use { input ->
                        tmp.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (!tmp.renameTo(file) && file.length() == 0L) throw IOException("Cannot cache PDF")
                } finally {
                    tmp.delete()
                }
            }
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val pdf = try {
                PdfRenderer(fd)
            } catch (e: Exception) {
                fd.close()
                throw e
            }
            return try {
                require(pdf.pageCount > 0)
                // Estimate placeholders from page one. A damaged page is an item-level error;
                // it should not prevent navigation to the rest of the volume.
                val aspect = try {
                    pdf.openPage(0).use { it.width.toFloat() / it.height }
                } catch (e: Exception) {
                    0.707f
                }
                ReaderDocument(pdf, pdf.pageCount, aspect)
            } catch (e: Exception) {
                pdf.close()
                throw e
            }
        }
    }
}
