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
 * Exported intake activity for receiving shared files via ACTION_SEND.
 * Validates the incoming intent and presents a confirmation screen before staging.
 * 
 * Security: This is a hostile-input boundary. All data from external apps is treated as untrusted.
 */
class ShareIntakeActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val uri = validateIntent(intent)
        if (uri == null) {
            finish()
            return
        }
        
        setContent {
            PocketLabTheme {
                ShareIntakeScreen(
                    uri = uri,
                    mimeType = intent.type,
                    onConfirm = {
                        navigateToAnalysis(uri)
                        finish()
                    },
                    onCancel = {
                        finish()
                    }
                )
            }
        }
    }
    
    private fun validateIntent(intent: Intent): Uri? {
        if (intent.action != Intent.ACTION_SEND) {
            return null
        }
        
        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            ?: intent.data
            ?: return null
        
        val scheme = uri.scheme
        if (scheme != "content" && scheme != "file") {
            return null
        }
        
        if (scheme == "file") {
            return null
        }
        
        val mimeType = intent.type
        val supportedMimeTypes = setOf(
            "application/vnd.android.package-archive",
            "application/zip",
            "application/x-dex"
        )
        
        if (mimeType != null && mimeType !in supportedMimeTypes) {
            return null
        }
        
        return uri
    }
    
    private fun navigateToAnalysis(uri: Uri) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("import_uri", uri.toString())
            putExtra("import_mime_type", this@ShareIntakeActivity.intent.type)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }
}

@Composable
fun ShareIntakeScreen(
    uri: Uri,
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
                text = "Analyze Shared File",
                style = MaterialTheme.typography.headlineMedium
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "File URI: ${uri.lastPathSegment ?: "unknown"}",
                style = MaterialTheme.typography.bodyMedium
            )
            
            if (mimeType != null) {
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
                        text = "This file will be copied into private app storage and analyzed locally. " +
                               "It will not be installed, executed, or uploaded anywhere.",
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
