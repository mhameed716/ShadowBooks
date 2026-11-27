package com.shadowbooks

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.shadowbooks.store.PackImportRequest
import com.shadowbooks.ui.ShadowBooksApp
import com.shadowbooks.ui.theme.ShadowBooksTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(10, 16, 32)
        window.navigationBarColor = android.graphics.Color.rgb(10, 16, 32)
        handlePackIntent(intent)
        setContent {
            ShadowBooksTheme {
                ShadowBooksApp(onExit = { finishAndRemoveTask() })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePackIntent(intent)
    }

    /**
     * A library pack ("ShadowBooks-Library.zip") opened from a file manager or
     * shared into the app is handed to the store screen for verified import.
     */
    private fun handlePackIntent(intent: Intent?) {
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
            }
            else -> null
        }
        if (uri != null) PackImportRequest.pendingUri = uri.toString()
    }
}
