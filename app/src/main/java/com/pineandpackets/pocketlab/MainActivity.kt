package com.pineandpackets.pocketlab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pineandpackets.pocketlab.ui.PocketLabApp
import com.pineandpackets.pocketlab.ui.theme.PocketLabTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PocketLabTheme {
                PocketLabApp()
            }
        }
    }
}
