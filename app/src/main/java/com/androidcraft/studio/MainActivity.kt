package com.androidcraft.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.androidcraft.studio.ui.AppRoot
import com.androidcraft.studio.ui.theme.AndroidCraftTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AndroidCraftTheme {
                AppRoot()
            }
        }
    }
}
