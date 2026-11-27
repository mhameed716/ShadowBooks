package com.shadowbooks.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.security.MessageDigest

data class DevicePdfEntry(val uri: String, val displayName: String) {
    /** Stable reader/bookmark key without putting a potentially long content URI in preferences. */
    val readerKey: String
        get() {
            val digest = MessageDigest.getInstance("SHA-256").digest(uri.toByteArray(Charsets.UTF_8))
            return "device-pdf-" + digest.take(16).joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        }

    fun asVolume() = BookVolume(
        title = displayName,
        fileName = readerKey,
        description = "ملف PDF من الهاتف",
        contentUri = uri,
    )
}

/** Keeps user-selected document URIs; Android's persisted SAF grants provide read access after relaunch. */
object DevicePdfLibrary {
    private const val PREFERENCES = "device_pdf_library"
    private const val KEY_ENTRIES = "entries_v1"

    fun load(context: Context): List<DevicePdfEntry> {
        val raw = context.applicationContext
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(KEY_ENTRIES, null) ?: return emptyList()
        val array = try {
            JSONArray(raw)
        } catch (_: JSONException) {
            return emptyList()
        }
        val readGrants = context.applicationContext.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .map { it.uri.toString() }
            .toSet()
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optJSONObject(index) ?: continue
                val uri = value.optString("uri").trim()
                val name = value.optString("name").trim()
                if (uri.startsWith("content://") && uri in readGrants && name.isNotEmpty()) {
                    add(DevicePdfEntry(uri, name))
                }
            }
        }.distinctBy(DevicePdfEntry::uri)
    }

    fun save(context: Context, entries: List<DevicePdfEntry>) {
        val array = JSONArray()
        entries.distinctBy(DevicePdfEntry::uri).forEach { entry ->
            array.put(JSONObject().put("uri", entry.uri).put("name", entry.displayName))
        }
        context.applicationContext
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ENTRIES, array.toString())
            .apply()
    }

    private const val KEY_HIDDEN = "hidden_discovered_v1"

    /** URIs of discovered files the student removed from the list (files stay on the phone). */
    fun loadHidden(context: Context): Set<String> =
        context.applicationContext
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getStringSet(KEY_HIDDEN, emptySet())
            ?.toSet()
            ?: emptySet()

    fun saveHidden(context: Context, hidden: Set<String>) {
        context.applicationContext
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_HIDDEN, hidden)
            .apply()
    }

    /**
     * Every PDF the media store knows about, so the screen can show the
     * phone's books without picking them one by one. Needs the storage
     * permission on older Android; returns what it may on newer versions.
     */
    fun discover(context: Context): List<DevicePdfEntry> = runCatching {
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DISPLAY_NAME)
        val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} = ?"
        val found = mutableListOf<DevicePdfEntry>()
        context.applicationContext.contentResolver.query(
            collection,
            projection,
            selection,
            arrayOf("application/pdf"),
            "${MediaStore.Files.FileColumns.DISPLAY_NAME} ASC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameCol)?.trim().orEmpty()
                if (name.isEmpty()) continue
                val uri = ContentUris.withAppendedId(collection, cursor.getLong(idCol))
                found += DevicePdfEntry(uri.toString(), name)
            }
        }
        found.distinctBy(DevicePdfEntry::uri)
    }.getOrDefault(emptyList())

    fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst()) cursor.getString(column)?.trim()?.takeIf(String::isNotEmpty) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf(String::isNotBlank)

    fun looksLikePdf(mimeType: String?, name: String): Boolean =
        mimeType.equals("application/pdf", ignoreCase = true) || name.endsWith(".pdf", ignoreCase = true)
}
