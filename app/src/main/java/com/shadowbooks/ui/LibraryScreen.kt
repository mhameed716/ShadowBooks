package com.shadowbooks.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shadowbooks.data.Book
import com.shadowbooks.data.BookCatalog

@Composable
fun LibraryScreen(onSelectBook: (Book) -> Unit) {
    val shelves = BookCatalog.books.chunked(3)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A1020))
            .systemBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SHADOWBOOKS",
                    color = Color(0xFF75D8FA),
                    fontSize = 10.sp,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "كتاب الطالب",
                    color = Color(0xFFF4F7FC),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF1B5574), Color(0xFF15223A)))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.MenuBook,
                    contentDescription = "ShadowBooks",
                    tint = Color(0xFFB9ECFF),
                    modifier = Modifier.size(27.dp),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("مناهج الصف الثالث ثانوي", color = Color(0xFF9BAEC4), fontSize = 13.sp)
            Surface(
                color = Color(0xFF17273A),
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    text = "13 كتاباً",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = Color(0xFF83DFFF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 2.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(shelves) { index, shelfBooks ->
                ShelfRow(
                    shelfNumber = index + 1,
                    books = shelfBooks,
                    onBookClick = onSelectBook,
                )
            }
        }
    }
}

@Composable
private fun ShelfRow(
    shelfNumber: Int,
    books: List<Book>,
    onBookClick: (Book) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(218.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF352719), Color(0xFF241A13), Color(0xFF392719)),
                ),
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            for (line in 1..5) {
                val y = size.height * line / 6f
                drawLine(
                    color = Color.White.copy(alpha = 0.035f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, Color(0xFFCAA16B).copy(alpha = 0.08f), Color.Transparent),
                ),
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 14.dp, top = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(5.dp).background(Color(0xFFDCAD68), CircleShape))
            Spacer(Modifier.width(7.dp))
            Text("الرف $shelfNumber", color = Color(0xFFB99465), fontSize = 10.sp)
        }

        if (books.size == 1) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 23.dp),
                contentAlignment = Alignment.Center,
            ) {
                BookCover(
                    book = books.first(),
                    modifier = Modifier.width(112.dp),
                    onClick = onBookClick,
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(start = 10.dp, end = 10.dp, bottom = 23.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                books.forEach { book ->
                    BookCover(
                        book = book,
                        modifier = Modifier.weight(1f),
                        onClick = onBookClick,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(19.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF9A6A3B), Color(0xFF724725), Color(0xFF4B2F1C)),
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(19.dp)
                .align(Alignment.BottomCenter)
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(2) {
                Box(
                    Modifier
                        .size(4.dp)
                        .background(Color(0xFFD0A46D), CircleShape),
                )
            }
        }
    }
}

@Composable
private fun BookCover(
    book: Book,
    modifier: Modifier = Modifier,
    onClick: (Book) -> Unit,
) {
    val shape = RoundedCornerShape(topStart = 5.dp, topEnd = 10.dp, bottomStart = 5.dp, bottomEnd = 5.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(154.dp)
            .shadow(8.dp, shape)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(book.coverTop, book.coverBottom)))
            .border(1.dp, Color.White.copy(alpha = 0.22f), shape)
            .clickable { onClick(book) },
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(5.dp)
                .background(Color.Black.copy(alpha = 0.16f)),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = book.number.toString().padStart(2, '0'),
                    color = book.accent,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "YEMEN",
                    color = Color.White.copy(alpha = 0.68f),
                    fontSize = 6.sp,
                    letterSpacing = 0.8.sp,
                )
            }
            Icon(
                imageVector = Icons.Filled.MenuBook,
                contentDescription = null,
                tint = book.accent,
                modifier = Modifier
                    .padding(top = 5.dp)
                    .size(20.dp),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = book.title,
                    color = Color.White,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "الصف الثالث الثانوي",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 7.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
