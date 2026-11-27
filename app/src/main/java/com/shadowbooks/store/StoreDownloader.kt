package com.shadowbooks.store

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

sealed interface StoreDownloadState {
    data object Idle : StoreDownloadState
    data class Running(val done: Long, val total: Long) : StoreDownloadState
    data class Paused(val done: Long, val total: Long) : StoreDownloadState
    data class Failed(val message: String) : StoreDownloadState
}

/**
 * Fast, resumable downloads for store content. Each file is split into
 * segments that are fetched in parallel (several HTTP Range connections at
 * once, like the standalone downloader app), every request starting from the
 * permanent release URL so it receives a fresh signed URL. Per-segment
 * progress is persisted beside the partial file, so pauses, failures and
 * process death resume exactly where they stopped. The assembled file is
 * SHA-256 verified and only then moved to its catalog destination.
 */
object StoreDownloader {
    private const val DOWNLOAD_BASE =
        "https://github.com/mhameed716/ShadowBooks/releases/download/store/"
    private const val CHUNK_SIZE = 1024 * 1024
    private const val SEGMENT_SIZE = 5L * 1024 * 1024
    private const val MAX_PARALLEL_SEGMENTS = 4
    private const val MAX_CHUNK_ATTEMPTS = 6

    val states = mutableStateMapOf<String, StoreDownloadState>()

    private val executor = Executors.newFixedThreadPool(2)
    private val active = ConcurrentHashMap.newKeySet<String>()
    private val pauseFlags = ConcurrentHashMap<String, AtomicBoolean>()
    private val cancelled = ConcurrentHashMap.newKeySet<String>()
    private val connections = ConcurrentHashMap<String, MutableSet<HttpURLConnection>>()

    @Volatile
    var onAllIdle: (() -> Unit)? = null

    /** The catalog names the exact release asset (pure ASCII — GitHub rewrites
     * uploaded names containing Arabic/spaces into dotted ones, so deriving a
     * name from the destination made every Arabic file 404). The legacy
     * derivation remains only as a fallback for catalogs cached before the
     * "asset" field existed, where Latin-named textbooks still resolve. */
    fun assetUrl(entry: StoreEntry): String =
        if (entry.asset.isNotBlank()) {
            DOWNLOAD_BASE + entry.asset
        } else {
            DOWNLOAD_BASE + URLEncoder.encode(entry.dest.replace("/", "__"), "UTF-8")
                .replace("+", "%20")
        }

    fun partialBytes(context: Context, entry: StoreEntry): Long {
        val part = partFile(context, entry)
        if (!part.isFile) return 0L
        val progress = readProgress(context, entry)
        return progress?.totalDone() ?: part.length().coerceAtMost(entry.size)
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
            connections[dest]?.forEach { it.disconnect() }
        } catch (e: Exception) {
            // The workers notice the pause flag on their next read regardless.
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

    // ------------------------------------------------------------ internals

    private fun partFile(context: Context, entry: StoreEntry): File =
        File(StoreContent.localFile(context, entry.dest).path + ".part")

    private fun progressFile(context: Context, entry: StoreEntry): File =
        File(StoreContent.localFile(context, entry.dest).path + ".parts")

    private class SegmentProgress(
        val size: Long,
        val segmentCount: Int,
        val done: LongArray,
    ) {
        fun segmentStart(index: Int): Long = index * SEGMENT_SIZE
        fun segmentEnd(index: Int): Long = minOf(size, (index + 1) * SEGMENT_SIZE)
        fun segmentLength(index: Int): Long = segmentEnd(index) - segmentStart(index)
        fun totalDone(): Long = done.sum()
        fun isComplete(): Boolean =
            (0 until segmentCount).all { done[it] >= segmentLength(it) }
    }

    private fun runDownload(context: Context, entry: StoreEntry) {
        val dest = entry.dest
        try {
            val target = StoreContent.localFile(context, dest)
            val part = partFile(context, entry)
            part.parentFile?.mkdirs()
            val progress = prepareProgress(context, entry, part)
            val totalDone = AtomicLong(progress.totalDone())
            val failure = AtomicReference<IOException?>(null)
            val stateLock = Any()

            val pending = (0 until progress.segmentCount)
                .filter { progress.done[it] < progress.segmentLength(it) }
            if (pending.isNotEmpty()) {
                val pool = Executors.newFixedThreadPool(
                    minOf(MAX_PARALLEL_SEGMENTS, pending.size),
                )
                try {
                    val futures = pending.map { index ->
                        pool.submit {
                            downloadSegment(
                                context, entry, part, progress, index,
                                totalDone, failure, stateLock,
                            )
                        }
                    }
                    futures.forEach { it.get() }
                } finally {
                    pool.shutdownNow()
                }
            }

            checkStopped(entry)
            failure.get()?.let { throw it }
            if (!progress.isComplete() || part.length() != entry.size) {
                throw IOException("حجم الملف غير مكتمل.")
            }
            if (!sha256(part).equals(entry.sha256, ignoreCase = true)) {
                deletePartial(context, entry)
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
            progressFile(context, entry).delete()
            StoreContent.markInstalled(context, entry)
            states.remove(dest)
        } catch (e: PausedDownload) {
            if (cancelled.contains(dest)) {
                deletePartial(context, entry)
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

    /**
     * Opens (or resumes) the partial file with its segment map. A partial left
     * by the pre-parallel downloader holds a valid sequential prefix, which is
     * honoured by crediting the segments it already covers; the final SHA-256
     * check makes any mistake here harmless.
     */
    private fun prepareProgress(
        context: Context,
        entry: StoreEntry,
        part: File,
    ): SegmentProgress {
        val segmentCount = maxOf(1, ((entry.size + SEGMENT_SIZE - 1) / SEGMENT_SIZE).toInt())
        val saved = readProgress(context, entry)
        if (saved != null && part.isFile && part.length() == entry.size) return saved

        val done = LongArray(segmentCount)
        if (saved == null && part.isFile && part.length() in 1 until entry.size) {
            var credited = part.length()
            var index = 0
            while (credited > 0 && index < segmentCount) {
                val take = minOf(credited, SEGMENT_SIZE)
                done[index] = take
                credited -= take
                index++
            }
        }
        if (part.exists()) part.delete()
        RandomAccessFile(part, "rw").use { it.setLength(entry.size) }
        val progress = SegmentProgress(entry.size, segmentCount, done)
        persistProgress(context, entry, progress)
        return progress
    }

    private fun readProgress(context: Context, entry: StoreEntry): SegmentProgress? {
        val file = progressFile(context, entry)
        if (!file.isFile) return null
        return try {
            val properties = Properties()
            file.inputStream().use { properties.load(it) }
            val size = properties.getProperty("size")?.toLongOrNull() ?: return null
            val sha = properties.getProperty("sha256").orEmpty()
            if (size != entry.size) return null
            if (sha.isNotBlank() && entry.sha256.isNotBlank() &&
                !sha.equals(entry.sha256, ignoreCase = true)
            ) {
                return null
            }
            val count = properties.getProperty("segments")?.toIntOrNull() ?: return null
            if (count <= 0 || count > 512) return null
            val raw = LongArray(count) { index ->
                (properties.getProperty("seg.$index.done")?.toLongOrNull() ?: 0L)
                    .coerceAtLeast(0L)
            }
            val progress = SegmentProgress(size, count, raw)
            for (index in 0 until count) {
                raw[index] = raw[index].coerceAtMost(progress.segmentLength(index))
            }
            progress
        } catch (e: Exception) {
            null
        }
    }

    private fun persistProgress(context: Context, entry: StoreEntry, progress: SegmentProgress) {
        try {
            val properties = Properties()
            properties.setProperty("size", progress.size.toString())
            properties.setProperty("sha256", entry.sha256)
            properties.setProperty("segments", progress.segmentCount.toString())
            for (index in 0 until progress.segmentCount) {
                properties.setProperty("seg.$index.done", progress.done[index].toString())
            }
            val file = progressFile(context, entry)
            file.parentFile?.mkdirs()
            val temp = File(file.path + ".tmp")
            temp.outputStream().use { properties.store(it, null) }
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
        } catch (e: Exception) {
            // Losing the resume map only restarts this file's segments; the
            // download itself continues and the final SHA-256 still guards it.
        }
    }

    private fun deletePartial(context: Context, entry: StoreEntry) {
        partFile(context, entry).delete()
        progressFile(context, entry).delete()
    }

    private fun downloadSegment(
        context: Context,
        entry: StoreEntry,
        part: File,
        progress: SegmentProgress,
        index: Int,
        totalDone: AtomicLong,
        failure: AtomicReference<IOException?>,
        stateLock: Any,
    ) {
        try {
            var position = progress.segmentStart(index) + progress.done[index]
            val segmentEnd = progress.segmentEnd(index)
            RandomAccessFile(part, "rw").use { output ->
                while (position < segmentEnd) {
                    checkStopped(entry)
                    if (failure.get() != null) return
                    val end = minOf(segmentEnd - 1, position + CHUNK_SIZE - 1)
                    val chunk = downloadRangeWithRetries(entry, position, end)
                    checkStopped(entry)
                    output.seek(position)
                    output.write(chunk)
                    position += chunk.size
                    val doneNow = totalDone.addAndGet(chunk.size.toLong())
                    synchronized(stateLock) {
                        progress.done[index] = position - progress.segmentStart(index)
                        persistProgress(context, entry, progress)
                    }
                    states[entry.dest] = StoreDownloadState.Running(doneNow, entry.size)
                }
            }
        } catch (e: PausedDownload) {
            // The enclosing runDownload renders the paused state.
        } catch (e: IOException) {
            failure.compareAndSet(null, e)
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
            val connection = URL(currentUrl).openConnection() as HttpURLConnection
            register(entry.dest, connection)
            try {
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
                unregister(entry.dest, connection)
                connection.disconnect()
            }
        }
        throw IOException("عمليات إعادة توجيه كثيرة أثناء التنزيل.")
    }

    private fun register(dest: String, connection: HttpURLConnection) {
        connections.computeIfAbsent(dest) {
            ConcurrentHashMap.newKeySet()
        }.add(connection)
    }

    private fun unregister(dest: String, connection: HttpURLConnection) {
        connections[dest]?.remove(connection)
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
