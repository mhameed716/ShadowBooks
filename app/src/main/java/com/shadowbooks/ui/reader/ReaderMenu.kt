package com.shadowbooks.ui.reader

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.shadowbooks.R

@Composable
internal fun ReaderMenu(
    modifier: Modifier,
    night: Boolean,
    fit: PageFit,
    calculatorOpen: Boolean,
    onCalculator: () -> Unit,
    onNight: () -> Unit,
    onFit: (PageFit) -> Unit,
    onBookmarks: () -> Unit,
    onFocus: () -> Unit,
    onOpen: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var fitChoices by remember { mutableStateOf(false) }
    Box(modifier) {
        ReaderIcon(Icons.Filled.MoreVert, R.string.reader_features, onClick = {
            onOpen()
            fitChoices = false
            expanded = true
        })
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(208.dp),
            shape = RoundedCornerShape(12.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            border = BorderStroke(1.dp, readerCyan.copy(alpha = 0.32f)),
        ) {
            if (fitChoices) {
                PageFit.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(stringResource(mode.label())) },
                        trailingIcon = { if (mode == fit) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = { onFit(mode); expanded = false },
                    )
                }
            } else {
                Feature(R.string.reader_night, Icons.Filled.NightsStay, checked = night, onClick = { onNight(); expanded = false })
                HorizontalDivider()
                Feature(R.string.calc_name, ImageVector.vectorResource(R.drawable.reader_calculator), detail = stringResource(R.string.calc_detail),
                    active = calculatorOpen, onClick = { expanded = false; onCalculator() })
                HorizontalDivider()
                Feature(R.string.reader_page_view, Icons.Filled.AspectRatio, detail = stringResource(fit.label()), onClick = { fitChoices = true })
                HorizontalDivider()
                Feature(R.string.reader_bookmarks, Icons.Filled.BookmarkBorder, onClick = { expanded = false; onBookmarks() })
                HorizontalDivider()
                Feature(R.string.reader_focus, Icons.Filled.Fullscreen, checked = false, onClick = { expanded = false; onFocus() })
            }
        }
    }
}

@StringRes
private fun PageFit.label(): Int = if (this == PageFit.WholePage) R.string.reader_fit_page else R.string.reader_fit_width

@Composable
private fun Feature(@StringRes label: Int, icon: ImageVector, detail: String? = null, checked: Boolean? = null, active: Boolean = false, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Column {
                Text(stringResource(label))
                if (detail != null) Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        modifier = when {
            checked != null -> Modifier.semantics { role = Role.Switch; toggleableState = ToggleableState(checked) }
            active -> Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)).semantics { selected = true }
            else -> Modifier
        },
        leadingIcon = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        trailingIcon = {
            if (checked != null) Switch(checked = checked, onCheckedChange = null)
            else if (active) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
            else Icon(Icons.Filled.ChevronLeft, null)
        },
        onClick = onClick,
    )
}
