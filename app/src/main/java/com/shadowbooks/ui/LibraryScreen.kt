package com.shadowbooks.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shadowbooks.R
import com.shadowbooks.data.Book
import com.shadowbooks.data.BookCatalog
import java.text.NumberFormat
import java.util.Locale

private val LibraryNight = Color(0xFF02142B)
private val LibraryFont = FontFamily(Font(R.font.tajawal_regular), Font(R.font.tajawal_extrabold, FontWeight.ExtraBold))
private val ShelfTextures = intArrayOf(
    R.drawable.library_shelf_1, R.drawable.library_shelf_2, R.drawable.library_shelf_3,
    R.drawable.library_shelf_4, R.drawable.library_shelf_5,
)

// Coordinates in the approved artwork. See scripts/prepare_library_art.py. Only the
// decorative wall and plank go in a shelf texture; the covers are separate controls.
private const val BoardWidth = 952f
private const val CoverWidth = 282f
private const val CoverGap = 14f
private const val CoverTop = 15f
private val Painter.artAspect: Float get() = intrinsicSize.width / intrinsicSize.height

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    state: LazyListState = rememberLazyListState(),
    onSelectBook: (Book) -> Unit,
    onOpenStudyResources: () -> Unit,
    onOpenTimetable: () -> Unit,
) {
    val shelves = remember { BookCatalog.books.chunked(3) }
    val count = remember {
        NumberFormat.getIntegerInstance(Locale.forLanguageTag("ar-YE-u-nu-arab")).format(BookCatalog.books.size.toLong())
    }
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(LibraryNight).safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        // Base shelf sizing on the original full safe area, not the height left after adding
        // navigation. This keeps the library artwork at its existing scale in landscape too.
        val width = minOf(maxWidth, 600.dp, (maxHeight * 1.3f).coerceAtLeast(320.dp))
        val unit = width / BoardWidth
        // Keep the heading while browsing on a phone, but let it scroll out of the way
        // in a short window or at large accessibility font sizes.
        val pinHeader = maxHeight >= 400.dp && LocalDensity.current.fontScale <= 1.35f
        Column(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                LazyColumn(state = state, modifier = Modifier.width(width).fillMaxHeight()) {
                    if (pinHeader) {
                        stickyHeader(key = "library-header") { LibraryHeader(unit, count) }
                    } else {
                        item(key = "library-header") { LibraryHeader(unit, count) }
                    }
                    itemsIndexed(shelves, key = { _, books -> books.first().number }) { index, books ->
                        ShelfRow(unit, ShelfTextures[index % ShelfTextures.size], books, onSelectBook)
                    }
                }
            }
            HomeBottomNavigation(
                selected = HomeTab.Library,
                onLibrary = {},
                onStudyResources = onOpenStudyResources,
                onTimetable = onOpenTimetable,
            )
        }
    }
}

@Composable
private fun LibraryHeader(unit: Dp, count: String) {
    Box(Modifier.fillMaxWidth().heightIn(min = unit * 223).background(LibraryNight)) {
        Image(
            painterResource(R.drawable.library_header_sky), contentDescription = null,
            modifier = Modifier.matchParentSize(), contentScale = ContentScale.FillBounds,
        )
        Row(Modifier.fillMaxWidth().padding(start = unit * 52, end = unit * 51, top = unit * 20, bottom = unit * 3)) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                val logo = painterResource(R.drawable.library_wordmark)
                Image(
                    logo, contentDescription = stringResource(R.string.library_brand),
                    modifier = Modifier.padding(start = unit * 5).width(unit * 273).aspectRatio(logo.artAspect),
                )
                Spacer(Modifier.height(unit * 26))
                Text(
                    stringResource(R.string.library_title),
                    modifier = Modifier.semantics { heading() }, color = Color(0xFFF5F6F7),
                    fontFamily = LibraryFont, fontWeight = FontWeight.ExtraBold,
                    fontSize = (unit.value * 77).sp, lineHeight = (unit.value * 86).sp, letterSpacing = 0.sp,
                )
                Text(
                    stringResource(R.string.library_subtitle), color = Color(0xFF78E6F4), fontFamily = LibraryFont,
                    fontSize = (unit.value * 31).coerceAtLeast(12f).sp,
                    lineHeight = (unit.value * 39).coerceAtLeast(16f).sp, letterSpacing = 0.sp,
                )
            }
            Spacer(Modifier.width(unit * 16))
            Box(
                modifier = Modifier.padding(top = unit * 47).widthIn(min = unit * 182, max = unit * 285)
                    .heightIn(min = unit * 64)
                    .border((unit * 1.8f).coerceAtLeast(0.7.dp), Color(0xFF06B8F4), CircleShape)
                    .padding(horizontal = unit * 10, vertical = unit * 3),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.library_subject_count, count), color = Color.White,
                    fontFamily = LibraryFont, fontSize = (unit.value * 36).coerceAtLeast(12f).sp,
                    lineHeight = (unit.value * 43).coerceAtLeast(15f).sp, letterSpacing = 0.sp, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ShelfRow(unit: Dp, @DrawableRes texture: Int, books: List<Book>, onSelectBook: (Book) -> Unit) {
    val shelf = painterResource(texture)
    Box(Modifier.fillMaxWidth().aspectRatio(shelf.artAspect)) {
        Image(shelf, contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.FillBounds)
        // The app's RTL direction places the first subject on the right. A short final
        // row is centred as a group, without stretching English to fill empty slots.
        Row(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = unit * CoverTop),
            horizontalArrangement = Arrangement.spacedBy(unit * CoverGap),
        ) {
            books.forEach { book ->
                val cover = painterResource(book.coverRes)
                val action = if (book.volumes.size > 1) R.string.library_choose_volume else R.string.library_open_book
                Image(
                    painter = cover, contentDescription = book.title, contentScale = ContentScale.FillBounds,
                    modifier = Modifier.width(unit * CoverWidth).aspectRatio(cover.artAspect).clipToBounds()
                        .clickable(role = Role.Button, onClickLabel = stringResource(action)) { onSelectBook(book) },
                )
            }
        }
    }
}
