package com.shadowbooks.data

import android.content.Context
import android.net.Uri
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

    fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst()) cursor.getString(column)?.trim()?.takeIf(String::isNotEmpty) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf(String::isNotBlank)

    fun looksLikePdf(mimeType: String?, name: String): Boolean =
        mimeType.equals("application/pdf", ignoreCase = true) || name.endsWith(".pdf", ignoreCase = true)
}
