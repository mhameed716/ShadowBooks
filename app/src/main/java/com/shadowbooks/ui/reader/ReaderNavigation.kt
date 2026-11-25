package com.shadowbooks.ui.reader

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shadowbooks.R

@Composable
internal fun ReaderIcon(
    icon: ImageVector, @StringRes label: Int, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick, modifier = modifier, enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(contentColor = readerCyan),
    ) {
        Icon(icon, stringResource(label), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f))
    }
}

@Composable
internal fun ReaderNavigation(view: ReaderViewport, modifier: Modifier = Modifier) {
    val focus = LocalFocusManager.current
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        val fontScale = LocalDensity.current.fontScale
        val compact = maxWidth < 304.dp || (maxWidth < 480.dp && fontScale > 1.5f)
        val hideTotal = maxWidth < 338.dp || fontScale > 1.35f
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // ReaderTheme is explicitly RTL: the first three are the physical RIGHT cluster,
            // with Next between (+) and (−). Previous stays at the far LEFT.
            ReaderIcon(Icons.Filled.Add, R.string.reader_zoom_in, enabled = view.zoom < 4f) {
                focus.clearFocus(); view.zoomBy(1.25f)
            }
            PageAction(next = true, compact = compact, enabled = view.currentIndex < view.count - 1) {
                focus.clearFocus(); view.jump(view.currentIndex + 1)
            }
            ReaderIcon(Icons.Filled.Remove, R.string.reader_zoom_out, enabled = view.zoom > 1f) {
                focus.clearFocus(); view.zoomBy(1f / 1.25f)
            }
            PageInput(view, hideTotal, Modifier.weight(1f))
            PageAction(next = false, compact = compact, enabled = view.currentIndex > 0) {
                focus.clearFocus(); view.jump(view.currentIndex - 1)
            }
        }
    }
}

@Composable
private fun PageAction(next: Boolean, compact: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val label = if (next) R.string.reader_next else R.string.reader_previous
    val chevron = if (next) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess
    if (compact) {
        ReaderIcon(chevron, label, enabled = enabled, onClick = onClick)
    } else {
        val ink = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f)
        TextButton(
            onClick = onClick, enabled = enabled, contentPadding = PaddingValues(horizontal = 4.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = readerCyan),
        ) {
            if (!next) Text(stringResource(label), color = ink)
            Icon(chevron, null, Modifier.size(20.dp), tint = ink)
            if (next) Text(stringResource(label), color = ink)
        }
    }
}

@Composable
private fun PageInput(view: ReaderViewport, hideTotal: Boolean, modifier: Modifier) {
    val current = (view.currentIndex + 1).arabicDigits()
    val fieldLabel = stringResource(R.string.reader_page_number)
    val invalid = stringResource(R.string.reader_invalid_page, view.count.arabicDigits())
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(TextFieldValue(current)) }
    val submit: () -> Unit = {
        val index = pageIndexForInput(draft.text, view.count)
        if (index == null) {
            Toast.makeText(context, invalid, Toast.LENGTH_SHORT).show()
            draft = draft.copy(selection = TextRange(0, draft.text.length))
        } else {
            view.jump(index)
            focus.clearFocus()
            keyboard?.hide()
        }
    }
    Row(modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = if (editing) draft else TextFieldValue(current),
            onValueChange = { if (it.text.length <= 7 && it.text.all(Char::isDigit)) draft = it },
            modifier = Modifier.weight(1f, fill = false).width(58.dp).onFocusChanged {
                if (it.isFocused && !editing) draft = TextFieldValue(current, TextRange(0, current.length))
                editing = it.isFocused
            }.semantics { contentDescription = fieldLabel },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(readerCyan),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { submit() }),
            interactionSource = remember { MutableInteractionSource() },
            decorationBox = { input ->
                Row(
                    Modifier.heightIn(min = 48.dp).drawBehind {
                        val y = size.height - 7.dp.toPx()
                        drawLine(readerCyan, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { input() }
                    if (!editing) Icon(Icons.Filled.Edit, null, modifier = Modifier.size(13.dp))
                }
            },
        )
        if (editing) ReaderIcon(Icons.Filled.Check, R.string.reader_go, onClick = submit)
        else if (!hideTotal) Text(
            stringResource(R.string.reader_total, view.count.arabicDigits()),
            modifier = Modifier.padding(horizontal = 3.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1,
        )
    }
}
