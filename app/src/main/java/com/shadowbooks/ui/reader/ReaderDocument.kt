package com.shadowbooks.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.LruCache
import com.shadowbooks.BuildConfig
import com.shadowbooks.data.BookVolume
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class RenderedPage(val bitmap: Bitmap, val aspect: Float)
private data class PageKey(val index: Int, val width: Int, val arabic: Boolean)
private data class CompanionPdf(val book: PreparedPupilBook, val renderer: PdfRenderer)

/** A single bitmap budget for both views of a volume. PdfRenderer permits only one open page at a time. */
internal class ReaderDocument private constructor(
    private val original: PdfRenderer,
    private val companionPdf: CompanionPdf?,
    val pageCount: Int,
    val firstAspect: Float,
) {
    private val lock = Mutex()
    @Volatile private var closed = false
    private val cache = object : LruCache<PageKey, RenderedPage>(
        (Runtime.getRuntime().maxMemory() / 8).coerceIn(4L shl 20, 20L shl 20).toInt(),
    ) {
        override fun sizeOf(key: PageKey, value: RenderedPage) = value.bitmap.byteCount
    }

    fun arabicText(index: Int): String? = companionPdf?.book?.text(index)

    fun toggleArabic(index: Int, pages: IntArray): IntArray = companionPdf?.book?.toggle(index, pages) ?: pages

    suspend fun page(index: Int, targetWidth: Int, arabic: Boolean = false): RenderedPage = withContext(Dispatchers.IO) {
        lock.withLock {
            // A page requested while the reader is closing is a normal cancellation,
            // not a page failure: never flash the per-page error tile for it.
            if (closed) throw CancellationException("ReaderDocument closed")
            val translated = arabic && arabicText(index) != null
            val renderer = if (translated) checkNotNull(companionPdf).renderer else original
            val key = PageKey(index, targetWidth, translated)
            cache.get(key) ?: renderer.openPage(index).use { page ->
                val w = page.width.toFloat()
                val h = page.height.toFloat()
                require(w > 0 && h > 0)
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
            cache.evictAll() // Compose may still be drawing these bitmaps. Never recycle them here.
            try { companionPdf?.renderer?.close() } finally { original.close() }
        }
    }

    companion object {
        private fun cacheAsset(context: Context, dir: File, asset: String): File {
            require(!asset.startsWith('/'))
            val relative = asset.removePrefix("books/")
            require(relative.isNotBlank() && relative.split('/').all { it.isNotBlank() && it != "." && it != ".." })
            val file = File(dir, relative)
            file.parentFile?.mkdirs()
            if (file.length() == 0L) {
                val tmp = File.createTempFile("book-", ".part", checkNotNull(file.parentFile))
                try {
                    context.assets.open(asset).use { input -> tmp.outputStream().use { input.copyTo(it) } }
                    if (!tmp.renameTo(file) && file.length() == 0L) throw IOException("Cannot cache PDF")
                } finally { tmp.delete() }
            }
            return file
        }

        private fun renderer(file: File): PdfRenderer {
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            return try { PdfRenderer(fd) } catch (e: Exception) { fd.close(); throw e }
        }

        private fun renderer(context: Context, uri: Uri): PdfRenderer {
            val fd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw FileNotFoundException("Unable to open document: $uri")
            return try { PdfRenderer(fd) } catch (e: Exception) { fd.close(); throw e }
        }

        private fun translation(context: Context, dir: File, original: File, count: Int): CompanionPdf? {
            // Failure of an optional companion must never prevent reading the original.
            return try {
                val index = context.assets.open(PreparedPupilBook.INDEX).bufferedReader(Charsets.UTF_8).use {
                    JSONObject(it.readText())
                }
                require(index.getInt("version") == 1)
                val pages = index.getJSONArray("pages")
                val book = PreparedPupilBook(
                    index.getString("sourceSha256"),
                    index.getString("arabicSha256"),
                    List(pages.length()) { i -> if (pages.isNull(i)) null else pages.get(i) as String },
                )
                if (!book.matches(PreparedPupilBook.digest(original), count)) return null
                val file = cacheAsset(context, dir, PreparedPupilBook.PDF)
                if (PreparedPupilBook.digest(file) != book.arabicSha256) {
                    file.delete() // Retry the installed asset rather than a bad cache entry next time.
                    return null
                }
                val pdf = renderer(file)
                if (pdf.pageCount == count) CompanionPdf(book, pdf) else {
                    pdf.close()
                    null
                }
            } catch (e: Exception) {
                Log.w("ReaderDocument", "Prepared Arabic edition is unavailable", e)
                null
            }
        }

        // The packaged pupil edition may change while its catalogue filename stays the same.
        // Keep at most one installation's pair on disk; never touch other volumes or bookmarks.
        @Suppress("DEPRECATION")
        private fun pupilCache(context: Context, root: File): File {
            val stamp = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
            val dir = File(root, "pupil-$stamp")
            if (!dir.isDirectory) {
                dir.mkdirs()
                root.listFiles { f -> f.isDirectory && f != dir && f.name.matches(Regex("pupil-[0-9]+")) }
                    ?.forEach { it.deleteRecursively() }
                File(root, PreparedPupilBook.SOURCE).delete() // Legacy unversioned cache, if present.
            }
            return dir
        }

        /** Blocking; called on IO. User-picked documents use their persisted SAF URI; packaged books stay assets. */
        fun open(context: Context, volume: BookVolume): ReaderDocument {
            val trial = BuildConfig.BUILD_TYPE == "trial"
            val documentUri = volume.contentUri?.let { Uri.parse(it) }
            val assetName = if (trial && documentUri == null) "reader-sample.pdf" else volume.fileName
            val candidate = documentUri == null && PreparedPupilBook.isCandidate(volume.fileName, trial)
            val root = File(context.cacheDir, "shadowbooks-pdf")
            val dir = if (candidate) pupilCache(context, root) else root.apply { mkdirs() }
            val file = if (documentUri == null) cacheAsset(context, dir, "books/$assetName") else null
            val pdf = if (documentUri == null) renderer(checkNotNull(file)) else renderer(context, documentUri)
            return try {
                val count = pdf.pageCount
                require(count > 0)
                val aspect = try { pdf.openPage(0).use { it.width.toFloat() / it.height } } catch (e: Exception) { 0.707f }
                ReaderDocument(pdf, if (candidate) translation(context, dir, checkNotNull(file), count) else null, count, aspect)
            } catch (e: Exception) { pdf.close(); throw e }
        }
    }
}
