package com.shadowbooks.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shadowbooks.R
import com.shadowbooks.data.BookVolume
import com.shadowbooks.data.Favorites
import com.shadowbooks.store.StoreCovers

private val SearchCard = Color(0xFF13233A)
private val SearchCyan = Color(0xFF65D4FA)
private val SearchMuted = Color(0xFF9DADC0)
private val SearchGold = Color(0xFFFFC86B)
private val SearchFont = FontFamily(
    Font(R.font.tajawal_regular),
    Font(R.font.tajawal_extrabold, FontWeight.ExtraBold),
)

/**
 * Global search: one box over books, summaries and exams. Favourites float
 * to the top of the results, and every hit wears its real PDF cover.
 */
@Composable
fun GlobalSearchDialog(
    onDismiss: () -> Unit,
    onOpen: (BookVolume, Boolean) -> Unit,
    onOpenStore: () -> Unit,
) {
    val context = LocalContext.current
    var index by remember { mutableStateOf<GlobalSearchIndex?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        Favorites.load(context)
        index = buildGlobalSearchIndex(context)
    }
    val results = remember(index, query, Favorites.keys) {
        val found = index?.query(query).orEmpty()
        found.sortedWith(
            compareByDescending<GlobalSearchHit> { Favorites.isFavorite(it.dest) }
                .thenBy { it.section }
                .thenBy { it.title },
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SearchCard,
        title = {
            Text(
                "بحث شامل",
                color = Color.White,
                fontFamily = SearchFont,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = {
                        Text(
                            "ابحث في الكتب والملخصات والاختبارات...",
                            fontFamily = SearchFont,
                            color = SearchMuted,
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = SearchCyan)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SearchCyan,
                        unfocusedBorderColor = Color(0xFF30445D),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = SearchCyan,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                when {
                    index == null -> Text(
                        "جارٍ تجهيز الفهرس...",
                        color = SearchMuted,
                        fontFamily = SearchFont,
                        fontSize = 13.sp,
                    )
                    query.trim().length < 2 -> Text(
                        "اكتب حرفين على الأقل. المفضّلة (★) تظهر أولًا دائمًا.",
                        color = SearchMuted,
                        fontFamily = SearchFont,
                        fontSize = 12.sp,
                    )
                    results.isEmpty() -> Text(
                        "لا نتائج مطابقة.",
                        color = SearchMuted,
                        fontFamily = SearchFont,
                        fontSize = 13.sp,
                    )
                    else -> Column(
                        Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        results.chunked(2).forEach { pair ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                pair.forEach { hit ->
                                    Box(Modifier.weight(1f)) {
                                        SearchHitCard(
                                            hit = hit,
                                            onOpen = onOpen,
                                            onOpenStore = onOpenStore,
                                        )
                                    }
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق", color = SearchCyan, fontFamily = SearchFont)
            }
        },
    )
}

@Composable
private fun SearchHitCard(
    hit: GlobalSearchHit,
    onOpen: (BookVolume, Boolean) -> Unit,
    onOpenStore: () -> Unit,
) {
    val context = LocalContext.current
    val favorite = Favorites.isFavorite(hit.dest)
    var cover by remember(hit.dest) { mutableStateOf(StoreCovers.peek(hit.dest)) }
    LaunchedEffect(hit.dest) {
        if (cover == null) cover = StoreCovers.loadForDest(context, hit.dest)
    }
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF101E32))
            .clickable {
                if (hit.available) onOpen(hit.volume, hit.fromResources) else onOpenStore()
            }
            .padding(8.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().height(110.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1B2C47)),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = cover
            if (bitmap != null) {
                Image(
                    bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(110.dp),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    Icons.Filled.MenuBook,
                    contentDescription = null,
                    tint = SearchMuted,
                    modifier = Modifier.size(30.dp),
                )
            }
            IconButton(
                onClick = { Favorites.toggle(context, hit.dest) },
                modifier = Modifier.align(Alignment.TopEnd).size(32.dp),
            ) {
                Icon(
                    if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (favorite) "إزالة من المفضلة" else "إضافة إلى المفضلة",
                    tint = if (favorite) SearchGold else Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            if (!hit.available) {
                Row(
                    Modifier.align(Alignment.BottomStart)
                        .background(Color(0xCC0B1B2E), RoundedCornerShape(50))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Download,
                        contentDescription = null,
                        tint = SearchCyan,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "من المتجر",
                        color = SearchCyan,
                        fontFamily = SearchFont,
                        fontSize = 10.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            hit.title,
            color = Color.White,
            fontFamily = SearchFont,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${hit.section} · ${hit.subject}",
            color = SearchMuted,
            fontFamily = SearchFont,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
