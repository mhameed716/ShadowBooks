package com.shadowbooks.ui.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.shadowbooks.R

@Composable
internal fun PageTranslationButton(translated: Boolean, page: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(if (translated) R.string.translate_show_english else R.string.translate_show_arabic,
        (page + 1).arabicDigits())
    val scheme = MaterialTheme.colorScheme
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp).semantics { contentDescription = description },
        shape = CircleShape, border = BorderStroke(1.dp, scheme.primary.copy(alpha = .55f)),
        colors = ButtonDefaults.textButtonColors(containerColor = Color.Transparent, contentColor = scheme.primary),
        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 2.dp)) {
        Icon(Icons.Filled.Translate, null, Modifier.size(20.dp))
        Spacer(Modifier.width(5.dp))
        Text(stringResource(if (translated) R.string.translate_original else R.string.translate_button),
            style = MaterialTheme.typography.labelLarge.copy(shadow = Shadow(color = scheme.surface, blurRadius = 3f)))
    }
}
