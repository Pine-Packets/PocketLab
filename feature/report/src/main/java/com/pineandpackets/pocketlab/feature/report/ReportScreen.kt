package com.pineandpackets.pocketlab.feature.report

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pineandpackets.pocketlab.core.model.RiskBand

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    caseId: String,
    onNavigateBack: () -> Unit,
    viewModel: ReportViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(caseId) {
        viewModel.loadReport(caseId)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Report") },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text("Back")
                    }
                },
                actions = {
                    if (uiState.report != null) {
                        ViewModeToggle(
                            currentMode = uiState.viewMode,
                            onModeChanged = { viewModel.setViewMode(it) }
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    LoadingState()
                }
                
                uiState.error != null -> {
                    ErrorState(
                        error = uiState.error!!,
                        onRetry = { viewModel.loadReport(caseId) }
                    )
                }
                
                uiState.report != null -> {
                    when (uiState.viewMode) {
                        ReportViewMode.SIMPLE -> {
                            SimpleReportView(
                                report = uiState.report!!,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        ReportViewMode.ANALYST -> {
                            AnalystReportView(
                                report = uiState.report!!,
                                notes = uiState.notes,
                                onAddNote = { viewModel.addNote(caseId, it) },
                                onDeleteNote = { viewModel.deleteNote(caseId, it) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewModeToggle(
    currentMode: ReportViewMode,
    onModeChanged: (ReportViewMode) -> Unit
) {
    Row(
        modifier = Modifier.padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = currentMode == ReportViewMode.SIMPLE,
            onClick = { onModeChanged(ReportViewMode.SIMPLE) },
            label = { Text("Simple") }
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        FilterChip(
            selected = currentMode == ReportViewMode.ANALYST,
            onClick = { onModeChanged(ReportViewMode.ANALYST) },
            label = { Text("Analyst") }
        )
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text("Loading report...")
        }
    }
}

@Composable
private fun ErrorState(
    error: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "Error",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}
