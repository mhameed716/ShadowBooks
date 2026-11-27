package com.shadowbooks.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

/**
 * Deletion never happens on one tap: the student must confirm twice —
 * "هل تريد المسح؟" then "هل أنت متأكد؟" — exactly as demanded, so a stray
 * touch can never erase a book.
 */
@Composable
fun ConfirmTwiceDialog(
    what: String,
    note: String? = null,
    confirmLabel: String = "نعم، امسح",
    onDismiss: () -> Unit,
    onConfirmed: () -> Unit,
) {
    var step by remember { mutableStateOf(1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF13233A),
        title = {
            Text(
                if (step == 1) "هل تريد المسح؟" else "هل أنت متأكد؟",
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                (if (step == 1) "سيتم مسح: $what" else "هذا آخر تحذير قبل المسح: $what") +
                    (note?.let { "\n$it" } ?: ""),
                color = Color(0xFFB9C6D8),
            )
        },
        confirmButton = {
            if (step == 1) {
                TextButton(onClick = { step = 2 }) {
                    Text("متابعة", color = Color(0xFFFF9D9D))
                }
            } else {
                TextButton(onClick = onConfirmed) {
                    Text(confirmLabel, color = Color(0xFFFF9D9D))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = Color(0xFF65D4FA))
            }
        },
    )
}
