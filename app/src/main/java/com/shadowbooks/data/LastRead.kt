package com.shadowbooks.data

import android.content.Context
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

    fun save(context: Context, lastRead: LastRead) {
        try {
            val json = JSONObject()
                .put("fileName", lastRead.fileName)
                .put("title", lastRead.title)
                .put("description", lastRead.description)
                .put("contentUri", lastRead.contentUri)
                .put("origin", lastRead.origin)
                .put("bookNumber", lastRead.bookNumber)
                .put("page", lastRead.page)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, json.toString())
                .apply()
        } catch (e: Exception) {
            // The shortcut is a convenience; losing it is harmless.
        }
    }

    fun updatePage(context: Context, page: Int) {
        val current = load(context) ?: return
        if (current.page != page) save(context, current.copy(page = page))
    }
}
