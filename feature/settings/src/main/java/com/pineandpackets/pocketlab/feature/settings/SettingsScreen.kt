package com.pineandpackets.pocketlab.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel? = null
) {
    val settings = viewModel?.settings?.collectAsState()?.value ?: AppSettings()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            PrivacyAndStorageSection(
                settings = settings,
                onRetentionModeChanged = { viewModel?.updateRetentionMode(it) },
                onReportRetentionDaysChanged = { viewModel?.updateReportRetentionDays(it) },
                onRedactSecretsChanged = { viewModel?.updateRedactSecrets(it) },
                onRedactIocChanged = { viewModel?.updateRedactIoc(it) },
                onIncludeFilenameChanged = { viewModel?.updateIncludeFilename(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            AnalysisSection(
                settings = settings,
                onAnalysisProfileChanged = { viewModel?.updateAnalysisProfile(it) },
                onNativeAnalysisChanged = { viewModel?.updateNativeAnalysis(it) },
                onDeepDexAnalysisChanged = { viewModel?.updateDeepDexAnalysis(it) },
                onIocExtractionChanged = { viewModel?.updateIocExtraction(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            AppearanceSection(
                settings = settings,
                onThemeModeChanged = { viewModel?.updateThemeMode(it) },
                onReduceMotionChanged = { viewModel?.updateReduceMotion(it) }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun PrivacyAndStorageSection(
    settings: AppSettings,
    onRetentionModeChanged: (Int) -> Unit,
    onReportRetentionDaysChanged: (Int) -> Unit,
    onRedactSecretsChanged: (Boolean) -> Unit,
    onRedactIocChanged: (Boolean) -> Unit,
    onIncludeFilenameChanged: (Boolean) -> Unit
) {
    SectionHeader("Privacy & Storage")

    var showRetentionDialog by remember { mutableStateOf(false) }
    var showReportRetentionDialog by remember { mutableStateOf(false) }

    val retentionLabels = listOf("Temporary", "Session only", "Retain sample", "Auto 1 day", "Auto 7 days", "Auto 30 days")
    val reportRetentionLabels = listOf("7 days", "30 days", "90 days", "Never delete")

    SettingsClickItem(
        title = "Sample retention",
        subtitle = retentionLabels.getOrElse(settings.retentionMode) { "Temporary" }
    ) { showRetentionDialog = true }

    SettingsClickItem(
        title = "Report retention",
        subtitle = reportRetentionLabels.getOrElse(
            when (settings.reportRetentionDays) {
                7 -> 0
                30 -> 1
                90 -> 2
                else -> 3
            }
        ) { "30 days" }
    ) { showReportRetentionDialog = true }

    SettingsSwitchItem(
        title = "Redact possible secrets",
        subtitle = "Hide API keys and tokens in exports",
        checked = settings.redactSecrets,
        onCheckedChange = onRedactSecretsChanged
    )

    SettingsSwitchItem(
        title = "Redact IOC values",
        subtitle = "Defang URLs and domains in exports",
        checked = settings.redactIocValues,
        onCheckedChange = onRedactIocChanged
    )

    SettingsSwitchItem(
        title = "Include source filename",
        subtitle = "Show original filename in reports",
        checked = settings.includeFilename,
        onCheckedChange = onIncludeFilenameChanged
    )

    if (showRetentionDialog) {
        SingleChoiceDialog(
            title = "Sample retention",
            options = retentionLabels,
            selectedIndex = settings.retentionMode,
            onSelected = {
                onRetentionModeChanged(it)
                showRetentionDialog = false
            },
            onDismiss = { showRetentionDialog = false }
        )
    }

    if (showReportRetentionDialog) {
        val daysOptions = listOf(7, 30, 90, 0)
        SingleChoiceDialog(
            title = "Report retention",
            options = reportRetentionLabels,
            selectedIndex = when (settings.reportRetentionDays) {
                7 -> 0
                30 -> 1
                90 -> 2
                else -> 3
            },
            onSelected = {
                onReportRetentionDaysChanged(daysOptions[it])
                showReportRetentionDialog = false
            },
            onDismiss = { showReportRetentionDialog = false }
        )
    }
}

@Composable
private fun AnalysisSection(
    settings: AppSettings,
    onAnalysisProfileChanged: (Int) -> Unit,
    onNativeAnalysisChanged: (Boolean) -> Unit,
    onDeepDexAnalysisChanged: (Boolean) -> Unit,
    onIocExtractionChanged: (Boolean) -> Unit
) {
    SectionHeader("Analysis")

    var showProfileDialog by remember { mutableStateOf(false) }
    val profileLabels = listOf("Standard", "Advanced")

    SettingsClickItem(
        title = "Analysis profile",
        subtitle = profileLabels.getOrElse(settings.analysisProfile) { "Standard" }
    ) { showProfileDialog = true }

    SettingsSwitchItem(
        title = "Native library analysis",
        subtitle = "Inspect ELF headers and symbols",
        checked = settings.nativeAnalysisEnabled,
        onCheckedChange = onNativeAnalysisChanged
    )

    SettingsSwitchItem(
        title = "Deep DEX analysis",
        subtitle = "Full instruction decoding and constant propagation",
        checked = settings.deepDexAnalysisEnabled,
        onCheckedChange = onDeepDexAnalysisChanged
    )

    SettingsSwitchItem(
        title = "IOC extraction",
        subtitle = "Extract URLs, domains, and IP addresses",
        checked = settings.iocExtractionEnabled,
        onCheckedChange = onIocExtractionChanged
    )

    if (showProfileDialog) {
        SingleChoiceDialog(
            title = "Analysis profile",
            options = profileLabels,
            selectedIndex = settings.analysisProfile,
            onSelected = {
                onAnalysisProfileChanged(it)
                showProfileDialog = false
            },
            onDismiss = { showProfileDialog = false }
        )
    }
}

@Composable
private fun AppearanceSection(
    settings: AppSettings,
    onThemeModeChanged: (Int) -> Unit,
    onReduceMotionChanged: (Boolean) -> Unit
) {
    SectionHeader("Appearance")

    var showThemeDialog by remember { mutableStateOf(false) }
    val themeLabels = listOf("System default", "Light", "Dark")

    SettingsClickItem(
        title = "Theme",
        subtitle = themeLabels.getOrElse(settings.themeMode) { "System default" }
    ) { showThemeDialog = true }

    SettingsSwitchItem(
        title = "Reduce motion",
        subtitle = "Minimize animations",
        checked = settings.reduceMotion,
        onCheckedChange = onReduceMotionChanged
    )

    if (showThemeDialog) {
        SingleChoiceDialog(
            title = "Theme",
            options = themeLabels,
            selectedIndex = settings.themeMode,
            onSelected = {
                onThemeModeChanged(it)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }
}

@Composable
private fun SettingsSwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsClickItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SingleChoiceDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { index, label ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(index) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (index == selectedIndex) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
