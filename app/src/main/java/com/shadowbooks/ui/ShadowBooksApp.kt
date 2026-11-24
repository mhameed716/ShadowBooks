package com.shadowbooks.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.rememberLazyListState
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

private enum class AppDestination { Welcome, Library, Timetable, StudyResources, VolumeChooser, Reader }

private fun Book.availableVolumes() = volumes.ifEmpty { listOf(BookVolume(title, fileName)) }

@Composable
fun ShadowBooksApp(onExit: () -> Unit) {
    // A fresh launch opens the welcome screen; the versioned key discards the old library-first saved route.
    // Preserve navigation in Android's restored task; timetable entries live in preferences.
    var destination by rememberSaveable(key = "app_destination_welcome_v1") { mutableStateOf(AppDestination.Welcome) }
    var bookNumber by rememberSaveable { mutableIntStateOf(0) }
    var volumeFile by rememberSaveable { mutableStateOf("") }
    var readerTitle by rememberSaveable { mutableStateOf("") }
    var readerDescription by rememberSaveable { mutableStateOf("") }
    var readerFromResources by rememberSaveable { mutableStateOf(false) }
    val libraryScroll = rememberLazyListState()
    val book = BookCatalog.books.firstOrNull { it.number == bookNumber }
    val bookVolume = book?.availableVolumes()?.find { it.fileName == volumeFile }
    val readerVolume = when {
        destination != AppDestination.Reader -> null
        readerFromResources && volumeFile.isNotBlank() -> BookVolume(readerTitle, volumeFile, readerDescription)
        else -> bookVolume
    }
    val screen = when {
        destination == AppDestination.Reader && readerVolume == null -> AppDestination.Library
        destination == AppDestination.VolumeChooser && book == null -> AppDestination.Library
        else -> destination
    }
    val back: () -> Unit = {
        when (screen) {
            AppDestination.Reader -> destination = if (readerFromResources) AppDestination.StudyResources else if (book != null && book.volumes.size > 1) {
                AppDestination.VolumeChooser
            } else AppDestination.Library
            AppDestination.StudyResources -> destination = AppDestination.Library
            AppDestination.VolumeChooser -> destination = AppDestination.Library
            AppDestination.Timetable -> destination = AppDestination.Library
            AppDestination.Library -> destination = AppDestination.Welcome
            AppDestination.Welcome -> onExit()
        }
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        BackHandler(onBack = back)
        when (screen) {
            AppDestination.Welcome -> WelcomeScreen { destination = AppDestination.Library }
            AppDestination.Library -> LibraryScreen(
                state = libraryScroll,
                onSelectBook = { selected ->
                    bookNumber = selected.number
                    readerFromResources = false
                    val volumes = selected.availableVolumes()
                    volumeFile = volumes.first().fileName
                    destination = if (volumes.size > 1) AppDestination.VolumeChooser else AppDestination.Reader
                },
                onOpenStudyResources = { destination = AppDestination.StudyResources },
                onOpenTimetable = { destination = AppDestination.Timetable },
            )
            AppDestination.Timetable -> TimetableScreen(
                onLibrary = { destination = AppDestination.Library },
                onStudyResources = { destination = AppDestination.StudyResources },
            )
            AppDestination.StudyResources -> StudyResourcesScreen(
                onLibrary = { destination = AppDestination.Library },
                onTimetable = { destination = AppDestination.Timetable },
                onOpen = { selected ->
                    readerFromResources = true
                    volumeFile = selected.fileName
                    readerTitle = selected.title
                    readerDescription = selected.description
                    destination = AppDestination.Reader
                },
            )
            AppDestination.VolumeChooser -> VolumeChooserScreen(
                book = checkNotNull(book), onBack = back,
                onSelectVolume = { selected ->
                    readerFromResources = false
                    volumeFile = selected.fileName
                    destination = AppDestination.Reader
                },
            )
            AppDestination.Reader -> PdfReaderScreen(volume = checkNotNull(readerVolume), onBack = back)
        }
    }
}
