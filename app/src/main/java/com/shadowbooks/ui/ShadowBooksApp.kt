package com.shadowbooks.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shadowbooks.data.Book
import com.shadowbooks.data.BookCatalog
import com.shadowbooks.data.BookVolume
import com.shadowbooks.data.LabCatalog
import com.shadowbooks.data.LabSubjectId

private enum class AppDestination { Welcome, Main, VolumeChooser, Reader, LabSubject }

private enum class MainTab { Library, Lab }

@Composable
fun ShadowBooksApp() {
    var destination by remember { mutableStateOf(AppDestination.Welcome) }
    var activeTab by remember { mutableStateOf(MainTab.Library) }
    var selectedBook by remember { mutableStateOf(BookCatalog.books.first()) }
    var selectedVolume by remember {
        mutableStateOf(BookVolume(selectedBook.title, selectedBook.fileName))
    }
    var selectedLabSubjectId by remember { mutableStateOf(LabSubjectId.CHEMISTRY) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        BackHandler(enabled = destination != AppDestination.Welcome) {
            when (destination) {
                AppDestination.Reader -> {
                    destination = if (selectedBook.volumes.size > 1) {
                        AppDestination.VolumeChooser
                    } else {
                        AppDestination.Main
                    }
                }
                AppDestination.VolumeChooser -> destination = AppDestination.Main
                AppDestination.LabSubject -> destination = AppDestination.Main
                AppDestination.Main -> {
                    if (activeTab == MainTab.Lab) {
                        activeTab = MainTab.Library
                    } else {
                        destination = AppDestination.Welcome
                    }
                }
                AppDestination.Welcome -> Unit
            }
        }

        when (destination) {
            AppDestination.Welcome -> WelcomeScreen {
                destination = AppDestination.Main
            }

            AppDestination.Main -> MainShell(
                activeTab = activeTab,
                onSelectTab = { activeTab = it },
                onSelectBook = { book: Book ->
                    selectedBook = book
                    val volumes = book.volumes.ifEmpty { listOf(BookVolume(book.title, book.fileName)) }
                    if (volumes.size > 1) {
                        destination = AppDestination.VolumeChooser
                    } else {
                        selectedVolume = volumes.first()
                        destination = AppDestination.Reader
                    }
                },
                onSelectLabSubject = { subject ->
                    selectedLabSubjectId = subject.id
                    destination = AppDestination.LabSubject
                },
            )

            AppDestination.VolumeChooser -> VolumeChooserScreen(
                book = selectedBook,
                onBack = { destination = AppDestination.Main },
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
                        AppDestination.Main
                    }
                },
            )

            AppDestination.LabSubject -> LabSubjectScreen(
                subject = LabCatalog.findSubject(selectedLabSubjectId),
                onBack = { destination = AppDestination.Main },
                onSelectExperiment = { _ ->
                    // Interactive experiment screens will be wired here step-by-step
                },
            )
        }
    }
}

@Composable
private fun MainShell(
    activeTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    onSelectBook: (Book) -> Unit,
    onSelectLabSubject: (com.shadowbooks.data.LabSubject) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A1020))
            .systemBarsPadding(),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when (activeTab) {
                MainTab.Library -> LibraryScreen(
                    applySystemBarsPadding = false,
                    onSelectBook = onSelectBook,
                )
                MainTab.Lab -> LabDashboardScreen(
                    onSelectSubject = onSelectLabSubject,
                )
            }
        }

        ShadowBooksBottomBar(
            activeTab = activeTab,
            onSelectTab = onSelectTab,
        )
    }
}

@Composable
private fun ShadowBooksBottomBar(
    activeTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F182A)),
    ) {
        HorizontalDivider(
            thickness = 1.dp,
            color = Color.White.copy(alpha = 0.08f),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            // In RTL: first item appears on the RIGHT ("المكتبة"), second on the LEFT ("مختبر التجارب")
            BottomNavItem(
                label = "المكتبة",
                icon = Icons.Filled.MenuBook,
                selected = activeTab == MainTab.Library,
                modifier = Modifier.weight(1f),
                onClick = { onSelectTab(MainTab.Library) },
            )
            BottomNavItem(
                label = "مختبر التجارب",
                icon = Icons.Filled.Science,
                selected = activeTab == MainTab.Lab,
                modifier = Modifier.weight(1f),
                onClick = { onSelectTab(MainTab.Lab) },
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val activeColor = Color(0xFF65D4FA)
    val inactiveColor = Color(0xFF8396AD)
    val tint = if (selected) activeColor else inactiveColor
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .height(64.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (selected) {
                        Brush.radialGradient(
                            colors = listOf(activeColor.copy(alpha = 0.24f), Color.Transparent),
                        )
                    } else {
                        Brush.radialGradient(colors = listOf(Color.Transparent, Color.Transparent))
                    },
                )
                .padding(horizontal = 18.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
        Spacer(Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .width(if (selected) 26.dp else 0.dp)
                .height(2.5.dp)
                .clip(RoundedCornerShape(50))
                .background(if (selected) activeColor else Color.Transparent),
        )
    }
}
