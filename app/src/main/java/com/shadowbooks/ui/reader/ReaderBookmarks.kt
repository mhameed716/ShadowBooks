package com.shadowbooks.ui.reader

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shadowbooks.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

private data class PageMark(val page: Int, val name: String)

/** The asset file name, not the display title, identifies a volume (including multi-part books). */
private class BookmarkStore(context: Context, private val fileName: String) {
    private val prefs = context.getSharedPreferences("reader_bookmarks", Context.MODE_PRIVATE)

    fun load(pageCount: Int): List<PageMark> {
        val array = try { JSONArray(prefs.getString(fileName, null) ?: "[]") } catch (e: JSONException) { return emptyList() }
        return buildList<PageMark> {
            for (i in 0 until array.length()) {
                val entry = array.optJSONObject(i) ?: continue
                val page = entry.optInt("page")
                val name = entry.optString("name").trim()
                if (page in 1..pageCount && name.isNotEmpty()) add(PageMark(page, name))
            }
        }.distinctBy { it.page }.sortedBy { it.page }
    }

    fun write(marks: List<PageMark>) {
        val json = JSONArray()
        marks.forEach { json.put(JSONObject().put("page", it.page).put("name", it.name)) }
        prefs.edit().putString(fileName, json.toString()).apply()
    }
}

@Composable
internal fun ReaderBookmarks(
    fileName: String,
    page: Int,
    count: Int,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember(fileName, context) { BookmarkStore(context.applicationContext, fileName) }
    var marks by remember(store) { mutableStateOf<List<PageMark>?>(null) }
    var name by remember(store, page) { mutableStateOf("") }
    val defaultName = stringResource(R.string.reader_page, page.arabicDigits())
    LaunchedEffect(store, page) {
        val loaded = withContext(Dispatchers.IO) { store.load(count) }
        marks = loaded
        name = loaded.find { it.page == page }?.name.orEmpty()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_bookmarks)) },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                item {
                    Text(defaultName, color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(
                        value = name, onValueChange = { name = it.take(64) }, enabled = marks != null,
                        label = { Text(stringResource(R.string.reader_mark_name)) },
                        placeholder = { Text(defaultName) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (marks?.isEmpty() == true) item {
                    Text(stringResource(R.string.reader_no_marks), modifier = Modifier.padding(vertical = 16.dp))
                }
                items(marks.orEmpty(), key = { it.page }) { mark ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            Modifier.weight(1f).clickable { onJump(mark.page - 1); onDismiss() }.padding(vertical = 8.dp),
                        ) {
                            Text(mark.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(stringResource(R.string.reader_page, mark.page.arabicDigits()), style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton(onClick = {
                            val remaining = marks.orEmpty().filterNot { it.page == mark.page }
                            store.write(remaining)
                            marks = remaining
                        }) {
                            Icon(Icons.Filled.DeleteOutline, stringResource(R.string.reader_remove_mark, mark.name))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = marks != null, onClick = {
                val updated = marks.orEmpty().filterNot { it.page == page } + PageMark(page, name.trim().ifEmpty { defaultName })
                store.write(updated.sortedBy { it.page })
                Toast.makeText(context, R.string.reader_mark_saved, Toast.LENGTH_SHORT).show()
                onDismiss()
            }) { Text(stringResource(R.string.reader_save_mark)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.reader_close)) } },
    )
}
