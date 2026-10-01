package com.shadowbooks.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.shadowbooks.data.Book
import com.shadowbooks.data.BookCatalog
import com.shadowbooks.data.BookVolume

private enum class AppDestination { Welcome, Library, VolumeChooser, Reader }

@Composable
fun ShadowBooksApp() {
    var destination by remember { mutableStateOf(AppDestination.Welcome) }
    var selectedBook by remember { mutableStateOf(BookCatalog.books.first()) }
    var selectedVolume by remember {
        mutableStateOf(BookVolume(selectedBook.title, selectedBook.fileName))
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        BackHandler(enabled = destination != AppDestination.Welcome) {
            destination = when (destination) {
                AppDestination.Reader -> {
                    if (selectedBook.volumes.size > 1) AppDestination.VolumeChooser else AppDestination.Library
                }
                AppDestination.VolumeChooser -> AppDestination.Library
                AppDestination.Library -> AppDestination.Welcome
                AppDestination.Welcome -> AppDestination.Welcome
            }
        }

        when (destination) {
            AppDestination.Welcome -> WelcomeScreen {
                destination = AppDestination.Library
            }
            AppDestination.Library -> LibraryScreen { book: Book ->
                selectedBook = book
                val volumes = book.volumes.ifEmpty { listOf(BookVolume(book.title, book.fileName)) }
                if (volumes.size > 1) {
                    destination = AppDestination.VolumeChooser
                } else {
                    selectedVolume = volumes.first()
                    destination = AppDestination.Reader
                }
            }
            AppDestination.VolumeChooser -> VolumeChooserScreen(
                book = selectedBook,
                onBack = { destination = AppDestination.Library },
                onSelectVolume = { volume ->
                    selectedVolume = volume
                    destination = AppDestination.Reader
                },
            )
            AppDestination.Reader -> PdfReaderScreen(
                volume = selectedVolume,
                onBack = {
                    destination = if (selectedBook.volumes.size > 1) {
                        AppDestination.VolumeChooser
                    } else {
                        AppDestination.Library
                    }
                },
            )
        }
    }
}
