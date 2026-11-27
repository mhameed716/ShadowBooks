package com.shadowbooks.store

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Holds a library-pack Uri handed to the app from outside ("open with
 * ShadowBooks" / share target) until the store screen consumes it.
 */
object PackImportRequest {
    var pendingUri by mutableStateOf<String?>(null)
}

/**
 * "Send my downloaded files": everything the store filed under the content
 * root is packed into ONE zip (dest paths preserved, PDFs stored uncompressed)
 * plus a manifest carrying each file's destination, size, SHA-256 and revision.
 * A friend imports the pack in the store: every file is hash-verified and
 * filed at exactly its catalog destination, so it shows up in the library,
 * summaries and exams as if downloaded — and the rest can be topped up from
 * the store as usual. Works fully offline on both sides.
 */
object LibraryPack {
    const val PACK_FILE_NAME = "ShadowBooks-Library.zip"
    private const val MANIFEST_NAME = "shadowbooks-pack.json"
    private const val BUFFER = 256 * 1024

    class PackException(message: String) : Exception(message)

    data class ExportResult(
        val file: File,
        val savedToDownloads: Boolean,
        val count: Int,
        val bytes: Long,
    )

    data class ImportResult(
        val imported: Int,
        val skipped: Int,
        val failed: Int,
    )

    private data class PackFile(
        val dest: String,
        val size: Long,
        val sha256: String,
        val rev: Int,
    )

    // ---------------------------------------------------------------- export

    suspend fun export(
        context: Context,
        onProgress: (Float) -> Unit,
    ): ExportResult = withContext(Dispatchers.IO) {
        val entries = StoreContent.downloadedEntries(context)
        if (entries.isEmpty()) throw PackException("لا توجد ملفات منزّلة بعد.")
        val total = entries.sumOf(StoreEntry::size).coerceAtLeast(1L)
        var written = 0L
        val packFiles = mutableListOf<PackFile>()

        val shareDir = File(context.cacheDir, "share").apply { mkdirs() }
        val outFile = File(shareDir, PACK_FILE_NAME)
        outFile.delete()
        ZipOutputStream(BufferedOutputStream(FileOutputStream(outFile))).use { zip ->
            zip.setLevel(Deflater.NO_COMPRESSION)
            val buffer = ByteArray(BUFFER)
            for (entry in entries) {
                val source = StoreContent.localFile(context, entry.dest)
                if (!source.isFile) continue
                val digest = MessageDigest.getInstance("SHA-256")
                zip.putNextEntry(ZipEntry(entry.dest))
                BufferedInputStream(FileInputStream(source)).use { input ->
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        zip.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        written += count
                    }
                }
                zip.closeEntry()
                packFiles += PackFile(
                    dest = entry.dest,
                    size = source.length(),
                    sha256 = digest.digest().toHex(),
                    rev = maxOf(entry.rev, 1),
                )
                onProgress((written.toFloat() / total).coerceIn(0f, 1f))
            }
            val manifest = JSONObject()
                .put("app", "ShadowBooks")
                .put("kind", "library-pack")
                .put("version", 1)
                .put("files", JSONArray().apply {
                    packFiles.forEach { file ->
                        put(
                            JSONObject()
                                .put("dest", file.dest)
                                .put("size", file.size)
                                .put("sha256", file.sha256)
                                .put("rev", file.rev),
                        )
                    }
                })
            zip.putNextEntry(ZipEntry(MANIFEST_NAME))
            zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        if (packFiles.isEmpty()) {
            outFile.delete()
            throw PackException("لا توجد ملفات منزّلة بعد.")
        }
        val saved = copyToDownloads(context, outFile)
        ExportResult(outFile, saved, packFiles.size, written)
    }

    /** Best-effort public copy, so the pack can also be sent later from Downloads. */
    private fun copyToDownloads(context: Context, file: File): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, PACK_FILE_NAME)
            put(MediaStore.Downloads.MIME_TYPE, "application/zip")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        var uri: Uri? = null
        return try {
            uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return false
            resolver.openOutputStream(uri)?.use { output ->
                BufferedInputStream(FileInputStream(file)).use { input ->
                    input.copyTo(output, BUFFER)
                }
            } ?: return false
            true
        } catch (e: Exception) {
            uri?.let { runCatching { resolver.delete(it, null, null) } }
            false
        }
    }

    // ---------------------------------------------------------------- import

    suspend fun import(
        context: Context,
        uri: Uri,
        onProgress: (Float) -> Unit,
    ): ImportResult = withContext(Dispatchers.IO) {
        val importDir = File(context.cacheDir, "import").apply { mkdirs() }
        val temp = File(importDir, "pack-${System.currentTimeMillis()}.zip")
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw PackException("تعذر فتح الملف المختار.")
            BufferedOutputStream(FileOutputStream(temp)).use { output ->
                input.use { it.copyTo(output, BUFFER) }
            }
            ZipFile(temp).use { zip ->
                val manifestEntry = zip.getEntry(MANIFEST_NAME)
                    ?: throw PackException("هذه ليست حزمة مكتبة ShadowBooks صالحة.")
                val manifest = JSONObject(
                    zip.getInputStream(manifestEntry).bufferedReader(Charsets.UTF_8)
                        .use { it.readText() },
                )
                if (manifest.optString("app") != "ShadowBooks" ||
                    manifest.optString("kind") != "library-pack"
                ) {
                    throw PackException("هذه ليست حزمة مكتبة ShadowBooks صالحة.")
                }
                val filesJson: JSONArray = manifest.optJSONArray("files") ?: JSONArray()
                val packFiles = buildList {
                    for (i in 0 until filesJson.length()) {
                        val item = filesJson.optJSONObject(i) ?: continue
                        val dest = item.optString("dest")
                        val size = item.optLong("size", -1L)
                        val sha = item.optString("sha256")
                        if (isValidDest(dest) && size > 0 && sha.length == 64) {
                            add(PackFile(dest, size, sha, item.optInt("rev", 1)))
                        }
                    }
                }
                if (packFiles.isEmpty()) {
                    throw PackException("الحزمة لا تحتوي ملفات صالحة.")
                }
                val total = packFiles.sumOf(PackFile::size).coerceAtLeast(1L)
                var processed = 0L
                var imported = 0
                var skipped = 0
                var failed = 0
                val buffer = ByteArray(BUFFER)
                for (packFile in packFiles) {
                    val target = StoreContent.localFile(context, packFile.dest)
                    val alreadyGood = target.isFile && target.length() == packFile.size &&
                        sha256Hex(target) == packFile.sha256
                    if (alreadyGood) {
                        StoreContent.markInstalled(
                            context,
                            StoreEntry(packFile.dest, packFile.size, packFile.sha256, packFile.rev),
                        )
                        skipped++
                        processed += packFile.size
                        onProgress((processed.toFloat() / total).coerceIn(0f, 1f))
                        continue
                    }
                    val zipEntry = zip.getEntry(packFile.dest)
                    if (zipEntry == null) {
                        failed++
                        processed += packFile.size
                        continue
                    }
                    target.parentFile?.mkdirs()
                    val partial = File(target.path + ".importing")
                    val digest = MessageDigest.getInstance("SHA-256")
                    var bytes = 0L
                    BufferedOutputStream(FileOutputStream(partial)).use { output ->
                        zip.getInputStream(zipEntry).use { entryInput ->
                            while (true) {
                                val count = entryInput.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                digest.update(buffer, 0, count)
                                bytes += count
                            }
                        }
                    }
                    if (bytes != packFile.size || digest.digest().toHex() != packFile.sha256) {
                        partial.delete()
                        failed++
                    } else {
                        if (target.exists()) target.delete()
                        if (!partial.renameTo(target)) {
                            partial.copyTo(target, overwrite = true)
                            partial.delete()
                        }
                        StoreContent.markInstalled(
                            context,
                            StoreEntry(packFile.dest, packFile.size, packFile.sha256, packFile.rev),
                        )
                        imported++
                    }
                    processed += packFile.size
                    onProgress((processed.toFloat() / total).coerceIn(0f, 1f))
                }
                ImportResult(imported, skipped, failed)
            }
        } finally {
            temp.delete()
        }
    }

    private fun isValidDest(dest: String): Boolean =
        dest.startsWith("books/") && dest.endsWith(".pdf") &&
            !dest.contains("..") && !dest.contains('\\') && !dest.startsWith("/")

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        BufferedInputStream(FileInputStream(file)).use { input ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }
}
