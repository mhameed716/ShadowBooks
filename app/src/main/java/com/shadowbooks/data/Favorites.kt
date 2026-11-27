package com.shadowbooks.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Pinned favourites ("مفضّلتي"): store files keyed by their catalog
 * destination ("books/..."), so a favourite summary or book floats to the
 * top of its section everywhere it is listed.
 */
object Favorites {
    private const val PREFS = "favorites"
    private const val KEY = "keys"

    var keys by mutableStateOf<Set<String>>(emptySet())
        private set

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        keys = try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(KEY, emptySet())
                ?.toSet()
                ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    fun isFavorite(dest: String): Boolean = dest in keys

    fun toggle(context: Context, dest: String) {
        load(context)
        keys = if (dest in keys) keys - dest else keys + dest
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(KEY, keys)
                .apply()
        } catch (e: Exception) {
            // Losing a favourite is cosmetic; never crash over it.
        }
    }
}
