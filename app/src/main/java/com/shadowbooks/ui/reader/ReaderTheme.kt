package com.shadowbooks.ui.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.shadowbooks.R

private val ink = Color(0xFF12243A)
internal val readerCyan = Color(0xFF10C8E7)
private val paperNight = Color(0xFF0A162A)
private val day = lightColorScheme(
    primary = Color(0xFF006F83), onPrimary = Color.White,
    surface = Color.White, onSurface = ink, background = Color.White, onBackground = ink,
    surfaceContainer = Color.White, onSurfaceVariant = Color(0xFF4E6272),
)
private val night = darkColorScheme(
    primary = Color(0xFF6CE2F4), surface = Color(0xFF13243A), onSurface = Color(0xFFF5EFE9),
    surfaceContainer = Color(0xFF13243A), background = paperNight, onBackground = Color(0xFFF5EFE9),
)
private val arabicFont = FontFamily(Font(R.font.tajawal_regular), Font(R.font.tajawal_extrabold, FontWeight.ExtraBold))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderTheme(nightMode: Boolean, content: @Composable () -> Unit) {
    ReaderSystemBars()
    val base = MaterialTheme.typography
    val type = remember(base) {
        base.copy(
            bodyLarge = base.bodyLarge.copy(fontFamily = arabicFont), bodyMedium = base.bodyMedium.copy(fontFamily = arabicFont),
            labelLarge = base.labelLarge.copy(fontFamily = arabicFont), labelSmall = base.labelSmall.copy(fontFamily = arabicFont),
            titleLarge = base.titleLarge.copy(fontFamily = arabicFont),
        )
    }
    MaterialTheme(colorScheme = if (nightMode) night else day, typography = type) {
        CompositionLocalProvider(
            LocalLayoutDirection provides LayoutDirection.Rtl,
            LocalRippleConfiguration provides remember { RippleConfiguration(color = readerCyan) },
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, content = content)
        }
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

@Composable
private fun ReaderSystemBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.activity()?.window
        val insets = ViewCompat.getRootWindowInsets(view)
        if (window == null || insets == null) return@DisposableEffect onDispose { }
        val controller = WindowCompat.getInsetsController(window, view)
        val status = WindowInsetsCompat.Type.statusBars()
        val navigation = WindowInsetsCompat.Type.navigationBars()
        val wasStatusVisible = insets.isVisible(status)
        val wasNavigationVisible = insets.isVisible(navigation)
        val oldBehavior = controller.systemBarsBehavior
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(status or navigation)
        onDispose {
            controller.systemBarsBehavior = oldBehavior
            if (wasStatusVisible) controller.show(status) else controller.hide(status)
            if (wasNavigationVisible) controller.show(navigation) else controller.hide(navigation)
        }
    }
}
