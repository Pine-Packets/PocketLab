package com.pineandpackets.pocketlab

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pineandpackets.pocketlab.ui.theme.PocketLabTheme

/**
 * Exported intake activity for receiving shared files via ACTION_SEND and ACTION_SEND_MULTIPLE.
 * Validates the incoming intent and presents a confirmation screen before staging.
 * Never auto-starts analysis.
 *
 * Security: This is a hostile-input boundary. All data from external apps is treated as untrusted.
 */
class ShareIntakeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uris = validateIntent(intent)
        if (uris == null || uris.isEmpty()) {
            finish()
            return
        }

        setContent {
            PocketLabTheme {
                ShareIntakeScreen(
                    uris = uris,
                    mimeType = intent.type,
                    onConfirm = {
                        navigateToIntake(uris)
                        finish()
                    },
                    onCancel = {
                        finish()
                    }
                )
            }
        }
    }

    private fun validateIntent(intent: Intent): List<Uri>? {
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM, Uri::class.java)
                    ?: intent.data
                    ?: return null
                if (isSupportedUri(uri) && isSupportedMime(intent.type)) listOf(uri) else null
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val items = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    ?: return null
                if (items.isEmpty() || items.size > MAX_INTAKE_ITEMS) return null
                if (!isSupportedMime(intent.type)) return null
                val validated = items.mapNotNull { uri ->
                    if (isSupportedUri(uri)) uri else null
                }
                if (validated.size != items.size) null else validated
            }
            else -> null
        }
    }

    private fun isSupportedUri(uri: Uri): Boolean {
        if (uri.scheme != "content") return false
        return true
    }

    private fun isSupportedMime(mimeType: String?): Boolean {
        if (mimeType == null) return true
        return mimeType in SUPPORTED_MIME_TYPES
    }

    private fun navigateToIntake(uris: List<Uri>) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putStringArrayListExtra("import_uris", ArrayList(uris.map { it.toString() }))
            putExtra("import_mime_type", this@ShareIntakeActivity.intent.type)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    companion object {
        private val SUPPORTED_MIME_TYPES = setOf(
            "application/vnd.android.package-archive",
            "application/zip",
            "application/x-dex"
        )
        private const val MAX_INTAKE_ITEMS = 10
    }
}

@Composable
fun ShareIntakeScreen(
    uris: List<Uri>,
    mimeType: String?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Analyze Shared Files",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = if (uris.size == 1) "1 file selected" else "${uris.size} files selected",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    uris.forEach { uri ->
                        Text(
                            text = "• ${uri.lastPathSegment ?: "unknown"}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (mimeType != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Type: $mimeType",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Privacy Notice",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "These files will be copied into private app storage and analyzed locally. " +
                               "They will not be installed, executed, or uploaded anywhere.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Start Analysis")
                }
            }
        }
    }
}
