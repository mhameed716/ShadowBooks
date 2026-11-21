package com.shadowbooks.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.shadowbooks.data.Book
import com.shadowbooks.data.BookCatalog
import com.shadowbooks.data.BookVolume

private enum class AppDestination { Welcome, Library, VolumeChooser, Reader }

private fun Book.availableVolumes() = volumes.ifEmpty { listOf(BookVolume(title, fileName)) }

@Composable
fun ShadowBooksApp(onExit: () -> Unit) {
    // Task saved state, NOT preferences: Android can recreate a backgrounded task, but a new
    // task after full exit must not resurrect the old session. Persist only stable catalog IDs.
    var destination by rememberSaveable { mutableStateOf(AppDestination.Welcome) }
    var bookNumber by rememberSaveable { mutableIntStateOf(0) }
    var volumeFile by rememberSaveable { mutableStateOf("") }
    val book = BookCatalog.books.firstOrNull { it.number == bookNumber }
    val volume = book?.availableVolumes()?.find { it.fileName == volumeFile }
    val screen = when {
        destination == AppDestination.Reader && volume == null -> AppDestination.Library
        destination == AppDestination.VolumeChooser && book == null -> AppDestination.Library
        else -> destination
    }
    val back: () -> Unit = {
        when (screen) {
            AppDestination.Reader -> destination = if (book != null && book.volumes.size > 1) {
                AppDestination.VolumeChooser
            } else AppDestination.Library
            AppDestination.VolumeChooser -> destination = AppDestination.Library
            AppDestination.Library -> destination = AppDestination.Welcome
            AppDestination.Welcome -> onExit()
        }
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        BackHandler(onBack = back)
        when (screen) {
            AppDestination.Welcome -> WelcomeScreen { destination = AppDestination.Library }
            AppDestination.Library -> LibraryScreen { selected ->
                bookNumber = selected.number
                val volumes = selected.availableVolumes()
                volumeFile = volumes.first().fileName
                destination = if (volumes.size > 1) AppDestination.VolumeChooser else AppDestination.Reader
            }
            AppDestination.VolumeChooser -> VolumeChooserScreen(
                book = checkNotNull(book), onBack = back,
                onSelectVolume = { selected ->
                    volumeFile = selected.fileName
                    destination = AppDestination.Reader
                },
            )
            AppDestination.Reader -> PdfReaderScreen(volume = checkNotNull(volume), onBack = back)
        }
    }
}
