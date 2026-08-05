package com.pineandpackets.pocketlab.feature.analysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AnalysisScreen(
    caseId: String,
    onReportReady: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Analyzing...",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            progress = { 0.0f }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Case ID: $caseId",
            style = MaterialTheme.typography.bodySmall
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Analysis will be implemented in Phase 2+",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onReportReady,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Skip to Report (Demo)")
        }
    }
}
