package com.shadowbooks.store

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** One file resting in the trash: where it belongs, and when it was deleted. */
data class TrashItem(
    val id: String,
    val dest: String,
    val size: Long,
    val deletedAt: Long,
) {
    val fileName: String get() = dest.substringAfterLast('/')
}

/**
 * سلة المحذوفات: deleting a downloaded file moves it here instead of erasing
 * it, so a mistaken tap (guarded by two confirmations) is always undoable.
 * Items can be restored to their exact catalog destination or deleted
 * forever, and anything older than a month is purged automatically when the
 * app starts — the monthly auto-empty the settings promise.
 */
object TrashBin {
    private const val RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000

    /** Bumped on every change so the settings UI refreshes. */
    var version by mutableIntStateOf(0)
        private set

    fun items(context: Context): List<TrashItem> =
        readIndex(context).sortedByDescending(TrashItem::deletedAt)

    fun totalBytes(context: Context): Long = items(context).sumOf(TrashItem::size)

    fun count(context: Context): Int = readIndex(context).size

    /** Moves a downloaded file into the trash. Returns false if it was not there. */
    fun moveToTrash(context: Context, entry: StoreEntry): Boolean {
        val source = StoreContent.localFile(context, entry.dest)
        if (!source.isFile) return false
        val deletedAt = System.currentTimeMillis()
        val id = "$deletedAt" + "__" + entry.dest.replace('/', '_')
        val target = File(trashDir(context), id)
        val moved = source.renameTo(target) || run {
            try {
                source.copyTo(target, overwrite = true)
                source.delete()
                true
            } catch (e: Exception) {
                false
            }
        }
        if (!moved) return false
        File(source.path + ".part").delete()
        File(source.path + ".parts").delete()
        val index = readIndex(context).toMutableList()
        index.removeAll { it.dest == entry.dest }
        index += TrashItem(id, entry.dest, target.length(), deletedAt)
        writeIndex(context, index)
        StoreContent.detach(context, entry)
        version++
        return true
    }

    /** Puts a trashed file back at its catalog destination. */
    fun restore(context: Context, item: TrashItem): Boolean {
        val source = File(trashDir(context), item.id)
        if (!source.isFile) {
            removeFromIndex(context, item)
            return false
        }
        val target = StoreContent.localFile(context, item.dest)
        target.parentFile?.mkdirs()
        if (target.exists()) target.delete()
        val moved = source.renameTo(target) || run {
            try {
                source.copyTo(target, overwrite = true)
                source.delete()
                true
            } catch (e: Exception) {
                false
            }
        }
        if (!moved) return false
        val rev = StoreContent.cachedCatalog(context)
            .firstOrNull { it.dest == item.dest }
            ?.rev ?: 1
        StoreContent.markInstalled(
            context,
            StoreEntry(item.dest, target.length(), "", rev),
        )
        removeFromIndex(context, item)
        version++
        return true
    }

    fun deleteForever(context: Context, item: TrashItem) {
        File(trashDir(context), item.id).delete()
        removeFromIndex(context, item)
        version++
    }

    fun emptyAll(context: Context) {
        readIndex(context).forEach { File(trashDir(context), it.id).delete() }
        writeIndex(context, emptyList())
        version++
    }

    /** Monthly auto-empty, run at app start: purge anything older than 30 days. */
    fun purgeExpired(context: Context) {
        val cutoff = System.currentTimeMillis() - RETENTION_MILLIS
        val index = readIndex(context)
        val expired = index.filter { it.deletedAt < cutoff }
        if (expired.isEmpty()) return
        expired.forEach { File(trashDir(context), it.id).delete() }
        writeIndex(context, index - expired.toSet())
        version++
    }

    // ------------------------------------------------------------ internals

    private fun trashDir(context: Context): File =
        File(context.filesDir, "trash").apply { mkdirs() }

    private fun indexFile(context: Context): File = File(trashDir(context), "index.json")

    private fun removeFromIndex(context: Context, item: TrashItem) {
        writeIndex(context, readIndex(context).filterNot { it.id == item.id })
    }

    private fun readIndex(context: Context): List<TrashItem> {
        val file = indexFile(context)
        if (!file.isFile) return emptyList()
        return try {
            val array = JSONObject(file.readText(Charsets.UTF_8)).optJSONArray("items")
                ?: JSONArray()
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    val dest = item.optString("dest")
                    if (id.isNotBlank() && dest.startsWith("books/")) {
                        add(
                            TrashItem(
                                id = id,
                                dest = dest,
                                size = item.optLong("size", 0L),
                                deletedAt = item.optLong("deletedAt", 0L),
                            ),
                        )
                    }
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeIndex(context: Context, items: List<TrashItem>) {
        try {
            val array = JSONArray()
            items.forEach { item ->
                array.put(
                    JSONObject()
                        .put("id", item.id)
                        .put("dest", item.dest)
                        .put("size", item.size)
                        .put("deletedAt", item.deletedAt),
                )
            }
            indexFile(context).writeText(
                JSONObject().put("items", array).toString(),
                Charsets.UTF_8,
            )
        } catch (e: Exception) {
            // A lost index orphans trashed bytes, never live content.
        }
    }
}
