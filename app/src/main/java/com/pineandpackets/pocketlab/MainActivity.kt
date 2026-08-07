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
        val importUris: List<String> = runCatching {
            val uris = intent?.getStringArrayListExtra(EXTRA_IMPORT_URIS)
            val single = intent?.getStringExtra(EXTRA_IMPORT_URI)
            when {
                !uris.isNullOrEmpty() -> uris
                !single.isNullOrEmpty() -> listOf(single)
                else -> emptyList()
            }
        }.getOrDefault(emptyList())

        setContent {
            PocketLabTheme {
                PocketLabApp(initialUris = importUris)
            }
        }
    }

    companion object {
        const val EXTRA_IMPORT_URIS = "import_uris"
        const val EXTRA_IMPORT_URI = "import_uri"
    }
}
