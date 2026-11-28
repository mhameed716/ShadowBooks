package com.shadowbooks.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/** The last volume opened in the reader, for the "متابعة القراءة" shortcut. */
data class LastRead(
    val fileName: String,
    val title: String,
    val description: String,
    val contentUri: String,
    /** library | resources | store | device */
    val origin: String,
    val bookNumber: Int,
    val page: Int,
)

object LastReadStore {
    private const val PREFS = "reading_last"
    private const val KEY = "last"

    /** Observable mirror so the floating «متابعة القراءة» pill redraws live. */
    var lastRead by mutableStateOf<LastRead?>(null)
        private set

    private var loaded = false

    /** Warms the observable mirror once; afterwards it tracks save/update. */
    fun peek(context: Context): LastRead? {
        if (!loaded) {
            loaded = true
            lastRead = load(context)
        }
        return lastRead
    }

    fun load(context: Context): LastRead? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
            ?: return null
        return try {
            val json = JSONObject(raw)
            val fileName = json.optString("fileName")
            if (fileName.isBlank()) return null
            LastRead(
                fileName = fileName,
                title = json.optString("title"),
                description = json.optString("description"),
                contentUri = json.optString("contentUri"),
                origin = json.optString("origin", "resources"),
                bookNumber = json.optInt("bookNumber", 0),
                page = json.optInt("page", 0),
            )
        } catch (e: Exception) {
            null
        }
    }

    fun save(context: Context, record: LastRead) {
        try {
            val json = JSONObject()
                .put("fileName", record.fileName)
                .put("title", record.title)
                .put("description", record.description)
                .put("contentUri", record.contentUri)
                .put("origin", record.origin)
                .put("bookNumber", record.bookNumber)
                .put("page", record.page)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, json.toString())
                .apply()
            lastRead = record
        } catch (e: Exception) {
            // The shortcut is a convenience; losing it is harmless.
        }
    }

    fun updatePage(context: Context, page: Int) {
        val current = lastRead ?: load(context) ?: return
        if (current.page != page) save(context, current.copy(page = page))
    }
}
