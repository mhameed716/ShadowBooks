package com.shadowbooks.store

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import com.shadowbooks.data.Book
import com.shadowbooks.data.BookVolume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * One downloadable content file. [dest] is the destination decided on GitHub
 * (the store catalog): the same relative path the file used to occupy inside
 * the APK's assets, e.g. "books/mathematics.pdf" or
 * "books/study/summaries/13-…/01-….pdf". Downloaded bytes are filed under
 * exactly that destination in the app's private content directory, so library
 * shelves, study sections, bookmarks and reading positions keep working
 * unchanged.
 */
data class StoreEntry(
    val dest: String,
    val size: Long,
    val sha256: String,
    val rev: Int,
    /** Release asset name (pure ASCII, decided by the catalog). GitHub
     * rewrites uploaded names with Arabic/spaces into dotted ones, so the app
     * must ask for exactly this name and never derive one from [dest]. */
    val asset: String = "",
) {
    val fileName: String get() = dest.substringAfterLast('/')
    val isBook: Boolean get() = dest.startsWith("books/") && !dest.startsWith("books/study/")
    val isStudy: Boolean get() = dest.startsWith("books/study/")
}

object StoreContent {
    const val CATALOG_URL =
        "https://github.com/mhameed716/ShadowBooks/releases/download/store/catalog.json"

    /** Bumped whenever a file is installed or deleted, so lists can refresh. */
    var installedVersion by mutableIntStateOf(0)
        private set

    private const val REGISTRY_NAME = "installed.json"
    private var registryCache: MutableMap<String, Int>? = null

    fun contentRoot(context: Context): File = File(context.filesDir, "content")

    fun localFile(context: Context, dest: String): File = File(contentRoot(context), dest)

    fun isInstalled(context: Context, entry: StoreEntry): Boolean {
        val file = localFile(context, entry.dest)
        return file.isFile && file.length() == entry.size
    }

    fun installedRev(context: Context, dest: String): Int =
        registry(context)[dest] ?: 0

    fun hasUpdate(context: Context, entry: StoreEntry): Boolean =
        isInstalled(context, entry) && installedRev(context, entry.dest) < entry.rev

    /** Names directly inside a downloaded content directory (mirrors AssetManager.list). */
    fun localFileNames(context: Context, destDir: String): List<String> =
        localFile(context, destDir).list()?.toList().orEmpty()

    fun isVolumeDownloaded(context: Context, fileName: String): Boolean =
        localFile(context, "books/$fileName").isFile

    fun isBookDownloaded(context: Context, book: Book): Boolean {
        val volumes = book.volumes.ifEmpty { listOf(BookVolume(book.title, book.fileName)) }
        return volumes.all { isVolumeDownloaded(context, it.fileName) }
    }

    fun downloadedBytes(context: Context): Long =
        contentRoot(context).walkTopDown()
            .filter { it.isFile && !it.name.endsWith(".part") }
            .sumOf { it.length() }

    /**
     * Every content file currently on disk, as catalog-shaped entries. Files
     * that arrived via a library pack may have no catalog row yet (offline
     * first launch), so their SHA is blank and the revision falls back to the
     * install registry; the real catalog overrides them whenever it is known.
     */
    fun downloadedEntries(context: Context): List<StoreEntry> {
        val root = contentRoot(context)
        if (!root.isDirectory) return emptyList()
        return root.walkTopDown()
            .filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".importing") }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .filter { it.startsWith("books/") && it.endsWith(".pdf") }
            .sorted()
            .map { dest ->
                StoreEntry(
                    dest = dest,
                    size = localFile(context, dest).length(),
                    sha256 = "",
                    rev = installedRev(context, dest),
                )
            }
            .toList()
    }

    internal fun markInstalled(context: Context, entry: StoreEntry) {
        val registry = registry(context)
        registry[entry.dest] = entry.rev
        saveRegistry(context, registry)
        installedVersion++
    }

    fun deleteLocal(context: Context, entry: StoreEntry) {
        localFile(context, entry.dest).delete()
        File(localFile(context, entry.dest).path + ".part").delete()
        File(localFile(context, entry.dest).path + ".parts").delete()
        val registry = registry(context)
        if (registry.remove(entry.dest) != null) saveRegistry(context, registry)
        StoreDownloader.forget(entry.dest)
        installedVersion++
    }

    // ------------------------------------------------------------ catalog

    fun cachedCatalog(context: Context): List<StoreEntry> {
        val cache = catalogCacheFile(context)
        if (!cache.isFile) return emptyList()
        return try {
            parseCatalog(cache.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun refreshCatalog(context: Context): Result<List<StoreEntry>> =
        withContext(Dispatchers.IO) {
            try {
                val text = fetchText(CATALOG_URL)
                val entries = parseCatalog(text)
                val cache = catalogCacheFile(context)
                cache.parentFile?.mkdirs()
                val temp = File(cache.path + ".tmp")
                temp.writeText(text, Charsets.UTF_8)
                if (!temp.renameTo(cache)) {
                    cache.writeText(text, Charsets.UTF_8)
                    temp.delete()
                }
                Result.success(entries)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun parseCatalog(text: String): List<StoreEntry> {
        val root = JSONObject(text)
        val array: JSONArray = root.optJSONArray("entries") ?: JSONArray()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val dest = item.optString("dest")
                val size = item.optLong("size", -1L)
                val sha = item.optString("sha256")
                val rev = item.optInt("rev", 1)
                val asset = item.optString("asset")
                if (dest.startsWith("books/") && size > 0 && sha.length == 64) {
                    add(StoreEntry(dest, size, sha, rev, asset))
                }
            }
        }
    }

    /** GET with manual redirect handling (release URLs redirect to signed hosts). */
    private fun fetchText(initialUrl: String): String {
        var currentUrl = initialUrl
        for (redirect in 0 until 7) {
            val connection = URL(currentUrl).openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 30_000
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
                return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }
        throw IllegalStateException("Too many redirects")
    }

    private fun catalogCacheFile(context: Context): File =
        File(context.filesDir, "store/catalog.json")

    // ------------------------------------------------------------ registry

    @Synchronized
    private fun registry(context: Context): MutableMap<String, Int> {
        registryCache?.let { return it }
        val file = registryFile(context)
        val map = mutableMapOf<String, Int>()
        if (file.isFile) {
            try {
                val json = JSONObject(file.readText(Charsets.UTF_8))
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map[key] = json.optInt(key, 0)
                }
            } catch (e: Exception) {
                // A corrupt registry only loses revision numbers; files remain.
            }
        }
        registryCache = map
        return map
    }

    @Synchronized
    private fun saveRegistry(context: Context, registry: Map<String, Int>) {
        try {
            val json = JSONObject()
            registry.forEach { (dest, rev) -> json.put(dest, rev) }
            val file = registryFile(context)
            file.parentFile?.mkdirs()
            file.writeText(json.toString(), Charsets.UTF_8)
        } catch (e: Exception) {
            // Non-fatal: revision tracking retries on the next install.
        }
    }

    private fun registryFile(context: Context): File =
        File(context.filesDir, "store/$REGISTRY_NAME")
}
