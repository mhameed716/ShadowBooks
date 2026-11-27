package com.shadowbooks.store

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cover thumbnails for store cards — the real first page of each PDF.
 *
 * Order of sources: a downloaded file renders its own first page locally
 * (cached as a small JPEG afterwards); before download, the publisher's
 * tiny pre-rendered cover is fetched once and cached on disk. Books bundled
 * inside the APK are only rendered from a local copy when small (the two
 * guides) — large bundled books use the publisher cover instead of copying
 * hundreds of megabytes around. Anything unavailable shows a placeholder.
 */
object StoreCovers {
    private const val COVER_BASE =
        "https://github.com/mhameed716/ShadowBooks/releases/download/store/"
    private const val COVER_WIDTH = 360
    private const val MAX_BUNDLED_RENDER_BYTES = 5_000_000L

    private val memory = LruCache<String, Bitmap>(48)

    fun peek(dest: String): Bitmap? = memory.get(dest)

    suspend fun load(context: Context, entry: StoreEntry): Bitmap? =
        withContext(Dispatchers.IO) {
            memory.get(entry.dest)?.let { return@withContext it }
            val bitmap = renderLocal(context, entry)
                ?: decodeCached(context, entry)
                ?: downloadCover(context, entry)
            if (bitmap != null) memory.put(entry.dest, bitmap)
            bitmap
        }

    private fun renderLocal(context: Context, entry: StoreEntry): Bitmap? {
        val local = StoreContent.localFile(context, entry.dest)
        if (local.isFile) {
            return renderFirstPage(local)?.also { cacheJpeg(context, entry, it) }
        }
        if (entry.size in 1..MAX_BUNDLED_RENDER_BYTES &&
            StoreContent.isBundled(context, entry.dest)
        ) {
            val temp = File(context.cacheDir, "cover-source.pdf")
            return try {
                context.assets.open(entry.dest).use { input ->
                    FileOutputStream(temp).use { output -> input.copyTo(output) }
                }
                renderFirstPage(temp)?.also { cacheJpeg(context, entry, it) }
            } catch (e: Exception) {
                null
            } finally {
                temp.delete()
            }
        }
        return null
    }

    private fun renderFirstPage(pdf: File): Bitmap? = try {
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                if (renderer.pageCount < 1) {
                    null
                } else {
                    renderer.openPage(0).use { page ->
                        val height = (
                            COVER_WIDTH * page.height / maxOf(page.width, 1)
                            ).coerceIn(1, 720)
                        val bitmap = Bitmap.createBitmap(
                            COVER_WIDTH,
                            height,
                            Bitmap.Config.ARGB_8888,
                        )
                        Canvas(bitmap).drawColor(Color.WHITE)
                        page.render(
                            bitmap,
                            null,
                            null,
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                        )
                        bitmap
                    }
                }
            }
        }
    } catch (e: Exception) {
        null
    }

    private fun decodeCached(context: Context, entry: StoreEntry): Bitmap? {
        val file = cacheFile(context, entry)
        if (!file.isFile) return null
        return try {
            BitmapFactory.decodeFile(file.path)
        } catch (e: Exception) {
            null
        }
    }

    private fun downloadCover(context: Context, entry: StoreEntry): Bitmap? {
        if (entry.cover.isBlank()) return null
        return try {
            val bytes = fetchBytes(COVER_BASE + entry.cover)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return null
            val file = cacheFile(context, entry)
            file.outputStream().use { it.write(bytes) }
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    private fun cacheJpeg(context: Context, entry: StoreEntry, bitmap: Bitmap) {
        try {
            cacheFile(context, entry).outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, output)
            }
        } catch (e: Exception) {
            // A missing cache only means the cover renders again next time.
        }
    }

    private fun cacheFile(context: Context, entry: StoreEntry): File {
        val dir = File(context.filesDir, "store/covers").apply { mkdirs() }
        val key = entry.asset.ifBlank { entry.dest }.removeSuffix(".pdf")
        return File(dir, "$key.jpg")
    }

    /** GET with manual redirect handling (release URLs redirect to signed hosts). */
    private fun fetchBytes(initialUrl: String): ByteArray {
        var currentUrl = initialUrl
        for (redirect in 0 until 7) {
            val connection = URL(currentUrl).openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 20_000
                connection.readTimeout = 30_000
                connection.setRequestProperty("User-Agent", "ShadowBooks-Store/1.0")
                val code = connection.responseCode
                if (code in intArrayOf(301, 302, 303, 307, 308)) {
                    val location = connection.getHeaderField("Location")
                    if (location.isNullOrBlank()) throw IllegalStateException("HTTP $code")
                    currentUrl = URL(URL(currentUrl), location).toString()
                    continue
                }
                if (code !in 200..299) throw IllegalStateException("HTTP $code")
                return connection.inputStream.use { it.readBytes() }
            } finally {
                connection.disconnect()
            }
        }
        throw IllegalStateException("Too many redirects")
    }
}
