package com.shadowbooks.store

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

sealed interface StoreDownloadState {
    data object Idle : StoreDownloadState
    data class Running(val done: Long, val total: Long) : StoreDownloadState
    data class Paused(val done: Long, val total: Long) : StoreDownloadState
    data class Failed(val message: String) : StoreDownloadState
}

/**
 * Chunked, resumable downloads for store content. Each file arrives in 1 MB
 * HTTP Range chunks (every chunk starts from the permanent release URL, so it
 * receives a fresh signed URL), is SHA-256 verified, and only then moved to
 * its catalog destination. Partial bytes survive pauses, failures and process
 * death in a sibling ".part" file.
 */
object StoreDownloader {
    private const val DOWNLOAD_BASE =
        "https://github.com/mhameed716/ShadowBooks/releases/download/store/"
    private const val CHUNK_SIZE = 1024 * 1024
    private const val MAX_CHUNK_ATTEMPTS = 6

    val states = mutableStateMapOf<String, StoreDownloadState>()

    private val executor = Executors.newFixedThreadPool(2)
    private val active = ConcurrentHashMap.newKeySet<String>()
    private val pauseFlags = ConcurrentHashMap<String, AtomicBoolean>()
    private val cancelled = ConcurrentHashMap.newKeySet<String>()
    private val connections = ConcurrentHashMap<String, HttpURLConnection>()

    @Volatile
    var onAllIdle: (() -> Unit)? = null

    /** Release asset names are the destination path with '/' replaced by '__'
     * (see scripts/publish_store.sh). */
    fun assetUrl(entry: StoreEntry): String =
        DOWNLOAD_BASE + URLEncoder.encode(entry.dest.replace("/", "__"), "UTF-8")
            .replace("+", "%20")

    fun partialBytes(context: Context, entry: StoreEntry): Long {
        val part = File(StoreContent.localFile(context, entry.dest).path + ".part")
        return if (part.isFile) part.length() else 0L
    }

    fun enqueue(context: Context, entry: StoreEntry) {
        if (!active.add(entry.dest)) return
        cancelled.remove(entry.dest)
        pauseFlags[entry.dest] = AtomicBoolean(false)
        states[entry.dest] = StoreDownloadState.Running(partialBytes(context, entry), entry.size)
        StoreDownloadService.start(context)
        val appContext = context.applicationContext
        executor.execute { runDownload(appContext, entry) }
    }

    fun pause(dest: String) {
        pauseFlags[dest]?.set(true)
        try {
            connections[dest]?.disconnect()
        } catch (e: Exception) {
            // The worker notices the pause flag on its next read regardless.
        }
    }

    fun cancel(dest: String) {
        cancelled.add(dest)
        pause(dest)
    }

    fun forget(dest: String) {
        states.remove(dest)
    }

    fun activeCount(): Int = active.size

    private fun runDownload(context: Context, entry: StoreEntry) {
        val dest = entry.dest
        try {
            val target = StoreContent.localFile(context, dest)
            val part = File(target.path + ".part")
            part.parentFile?.mkdirs()
            var downloaded = if (part.isFile) part.length() else 0L
            if (downloaded > entry.size) {
                part.delete()
                downloaded = 0L
            }
            RandomAccessFile(part, "rw").use { output ->
                output.seek(downloaded)
                while (downloaded < entry.size) {
                    checkStopped(entry)
                    val end = minOf(entry.size - 1, downloaded + CHUNK_SIZE - 1L)
                    val chunk = downloadRangeWithRetries(entry, downloaded, end)
                    checkStopped(entry)
                    output.write(chunk)
                    downloaded += chunk.size
                    states[dest] = StoreDownloadState.Running(downloaded, entry.size)
                }
            }
            checkStopped(entry)
            if (part.length() != entry.size) {
                throw IOException("حجم الملف غير مكتمل.")
            }
            if (!sha256(part).equals(entry.sha256, ignoreCase = true)) {
                part.delete()
                states[dest] = StoreDownloadState.Failed(
                    "لم يطابق الملف بصمة السلامة، حُذف الجزء التالف. أعد المحاولة.",
                )
                return
            }
            target.parentFile?.mkdirs()
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) {
                throw IOException("تعذر حفظ الملف في مكانه.")
            }
            StoreContent.markInstalled(context, entry)
            states.remove(dest)
        } catch (e: PausedDownload) {
            if (cancelled.contains(dest)) {
                File(StoreContent.localFile(context, dest).path + ".part").delete()
                states.remove(dest)
            } else {
                states[dest] = StoreDownloadState.Paused(
                    partialBytes(context, entry), entry.size,
                )
            }
        } catch (e: Exception) {
            states[dest] = if (pauseFlags[dest]?.get() == true) {
                StoreDownloadState.Paused(partialBytes(context, entry), entry.size)
            } else {
                StoreDownloadState.Failed(
                    e.message?.takeIf { it.isNotBlank() } ?: "تعذر إكمال التنزيل.",
                )
            }
        } finally {
            connections.remove(dest)
            active.remove(dest)
            if (active.isEmpty()) onAllIdle?.invoke()
        }
    }

    private fun checkStopped(entry: StoreEntry) {
        if (cancelled.contains(entry.dest) || pauseFlags[entry.dest]?.get() == true ||
            Thread.currentThread().isInterrupted
        ) {
            throw PausedDownload()
        }
    }

    private fun downloadRangeWithRetries(
        entry: StoreEntry,
        start: Long,
        end: Long,
    ): ByteArray {
        var lastError: IOException? = null
        for (attempt in 1..MAX_CHUNK_ATTEMPTS) {
            checkStopped(entry)
            try {
                return downloadRangeOnce(entry, start, end)
            } catch (e: PausedDownload) {
                throw e
            } catch (e: IOException) {
                lastError = e
                if (attempt == MAX_CHUNK_ATTEMPTS) break
                val waitMillis = minOf(15_000L, attempt * 2_000L)
                var waited = 0L
                while (waited < waitMillis) {
                    checkStopped(entry)
                    Thread.sleep(minOf(250L, waitMillis - waited))
                    waited += 250L
                }
            }
        }
        throw lastError ?: IOException("تعذر تنزيل أحد أجزاء الملف.")
    }

    private fun downloadRangeOnce(entry: StoreEntry, start: Long, end: Long): ByteArray {
        var currentUrl = assetUrl(entry)
        val expected = (end - start + 1).toInt()
        for (redirect in 0 until 7) {
            var connection: HttpURLConnection? = null
            try {
                connection = URL(currentUrl).openConnection() as HttpURLConnection
                connections[entry.dest] = connection
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 30_000
                connection.readTimeout = 60_000
                connection.setRequestProperty("User-Agent", "ShadowBooks-Store/1.0")
                connection.setRequestProperty("Accept-Encoding", "identity")
                connection.setRequestProperty("Range", "bytes=$start-$end")
                val code = connection.responseCode
                if (code in intArrayOf(301, 302, 303, 307, 308)) {
                    val location = connection.getHeaderField("Location")
                    if (location.isNullOrBlank()) throw IOException("رابط تنزيل غير صالح.")
                    currentUrl = URL(URL(currentUrl), location).toString()
                    continue
                }
                if (code == 416) throw IOException("رفض الخادم استئناف هذا الجزء.")
                if (code != 206) throw IOException("رفض الخادم طلب الجزء (HTTP $code).")
                val contentRange = connection.getHeaderField("Content-Range")
                if (contentRange == null || !contentRange.startsWith("bytes $start-")) {
                    throw IOException("رجع جزء غير متوقع من الخادم.")
                }
                BufferedInputStream(connection.inputStream).use { input ->
                    val output = ByteArrayOutputStream(expected)
                    val buffer = ByteArray(64 * 1024)
                    var total = 0
                    while (total < expected) {
                        checkStopped(entry)
                        val count = input.read(buffer, 0, minOf(buffer.size, expected - total))
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        total += count
                    }
                    if (total != expected) throw IOException("انقطع تنزيل أحد الأجزاء.")
                    return output.toByteArray()
                }
            } finally {
                if (connection != null && connections[entry.dest] === connection) {
                    connections.remove(entry.dest)
                }
                connection?.disconnect()
            }
        }
        throw IOException("عمليات إعادة توجيه كثيرة أثناء التنزيل.")
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { String.format(Locale.US, "%02x", it) }
    }

    private class PausedDownload : Exception()
}
