package com.bniladridas.diff

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bniladridas.diff.ui.screens.DiffApp
import com.bniladridas.diff.ui.theme.DiffTheme

class MainActivity : ComponentActivity() {
    private var authCallbackUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authCallbackUri = intent?.data
        enableEdgeToEdge()
        setContent {
            DiffTheme {
                DiffApp(
                    authCallbackUri = authCallbackUri,
                    onAuthCallbackConsumed = { authCallbackUri = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        authCallbackUri = intent.data
    }
}
