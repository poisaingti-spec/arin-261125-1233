package com.aibuilt.arin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aibuilt.arin.ui.ArinApp
import com.aibuilt.arin.ui.theme.ArinTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ArinTheme {
                ArinApp()
            }
        }
    }
}
