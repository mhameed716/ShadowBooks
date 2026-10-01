package com.shadowbooks

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.shadowbooks.ui.ShadowBooksApp
import com.shadowbooks.ui.theme.ShadowBooksTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(10, 16, 32)
        window.navigationBarColor = android.graphics.Color.rgb(10, 16, 32)
        setContent {
            ShadowBooksTheme {
                ShadowBooksApp()
            }
        }
    }
}
