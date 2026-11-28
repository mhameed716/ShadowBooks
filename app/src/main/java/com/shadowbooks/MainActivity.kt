package com.shadowbooks

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.shadowbooks.store.PackImportRequest
import com.shadowbooks.ui.ShadowBooksApp
import com.shadowbooks.ui.theme.ShadowBooksTheme

class MainActivity : ComponentActivity() {
    private val firstRunPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(10, 16, 32)
        window.navigationBarColor = android.graphics.Color.rgb(10, 16, 32)
        askFirstRunPermissions()
        handlePackIntent(intent)
        setContent {
            ShadowBooksTheme {
                ShadowBooksApp(onExit = { finishAndRemoveTask() })
            }
        }
    }

    /**
     * First install/run: explicitly ask for file access (so «ملفات الهاتف»
     * can list the phone's PDFs) and, on Android 13+, notification access
     * (so the study alarm can ring). Asked once; never nagged again.
     */
    private fun askFirstRunPermissions() {
        val prefs = getSharedPreferences("first_run_permissions", MODE_PRIVATE)
        if (prefs.getBoolean("asked", false)) return
        prefs.edit().putBoolean("asked", true).apply()
        val wanted = mutableListOf<String>()
        if (Build.VERSION.SDK_INT <= 32 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            wanted += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            wanted += Manifest.permission.POST_NOTIFICATIONS
        }
        if (wanted.isNotEmpty()) firstRunPermissions.launch(wanted.toTypedArray())
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
