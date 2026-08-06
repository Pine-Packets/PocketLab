package com.pineandpackets.pocketlab.feature.report

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pineandpackets.pocketlab.core.model.*

@Composable
fun SimpleReportView(
    report: AnalysisReport,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            RiskBanner(report.summary)
        }
        
        item {
            FileSummaryCard(report)
        }
        
        if (report.findings.isNotEmpty()) {
            item {
                Text(
                    text = "Top Findings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            
            items(report.findings.take(5)) { finding ->
                FindingCard(finding)
            }
        }
        
        report.apk?.let { apk ->
            item {
                CapabilitiesCard(apk)
            }
        }
        
        item {
            RecommendationCard(report.summary)
        }
        
        item {
            PrivacyStatementCard()
        }
        
        item {
            LimitationsCard(report.limitations)
        }
    }
}

@Composable
private fun RiskBanner(summary: ReportSummary) {
    val (backgroundColor, textColor, icon) = when (summary.riskBand) {
        RiskBand.NO_MAJOR_CONCERNS -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "✓"
        )
        RiskBand.REVIEW_RECOMMENDED -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            "⚠"
        )
        RiskBand.SUSPICIOUS_CAPABILITIES -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "⚠"
        )
        RiskBand.HIGH_RISK_INDICATORS -> Triple(
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.onError,
            "⚠"
        )
        RiskBand.ANALYSIS_INCOMPLETE -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "?"
        )
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.displayLarge,
                color = textColor
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = when (summary.riskBand) {
                    RiskBand.NO_MAJOR_CONCERNS -> "No Major Concerns Observed"
                    RiskBand.REVIEW_RECOMMENDED -> "Review Recommended"
                    RiskBand.SUSPICIOUS_CAPABILITIES -> "Suspicious Capabilities Observed"
                    RiskBand.HIGH_RISK_INDICATORS -> "High-Risk Indicators Observed"
                    RiskBand.ANALYSIS_INCOMPLETE -> "Analysis Incomplete"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Confidence: ${summary.confidence.name.lowercase().replaceFirstChar { it.uppercase() }}",
                style = MaterialTheme.typography.bodyMedium,
                color = textColor
            )
            
            Text(
                text = "Completeness: ${(summary.completeness * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = textColor
            )
        }
    }
}

@Composable
private fun FileSummaryCard(report: AnalysisReport) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "What This File Is",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            InfoRow("Filename", report.source.displayName)
            report.apk?.packageName?.let { InfoRow("Package", it) }
            report.apk?.versionName?.let { InfoRow("Version", it) }
            InfoRow("Size", formatFileSize(report.source.sizeActual))
            InfoRow("SHA-256", report.source.sha256.take(16) + "...")
        }
    }
}

@Composable
private fun FindingCard(finding: Finding) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (finding.severity) {
                Severity.CRITICAL, Severity.HIGH -> MaterialTheme.colorScheme.errorContainer
                Severity.MEDIUM -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = finding.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                
                Text(
                    text = finding.severity.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = finding.simpleExplanation,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun CapabilitiesCard(apk: ApkInfo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "What The App Can Do",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (apk.permissions.isNotEmpty()) {
                Text(
                    text = "Permissions: ${apk.permissions.size}",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                apk.permissions.take(5).forEach { perm ->
                    Text(
                        text = "• ${perm.name.substringAfterLast('.')}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                
                if (apk.permissions.size > 5) {
                    Text(
                        text = "... and ${apk.permissions.size - 5} more",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            if (apk.components.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Components: ${apk.components.size}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun RecommendationCard(summary: ReportSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Recommended Action",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = when (summary.riskBand) {
                    RiskBand.HIGH_RISK_INDICATORS -> "Do not install this file unless its publisher and purpose can be independently verified."
                    RiskBand.SUSPICIOUS_CAPABILITIES -> "Proceed with caution. Verify the source and purpose before installing."
                    RiskBand.REVIEW_RECOMMENDED -> "Review the findings below and verify the source before proceeding."
                    RiskBand.NO_MAJOR_CONCERNS -> "No major concerns observed, but static analysis cannot guarantee safety."
                    RiskBand.ANALYSIS_INCOMPLETE -> "Analysis was incomplete. Do not rely on these results for security decisions."
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun PrivacyStatementCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Privacy Statement",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "✓ Analyzed locally on your device",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "✓ No execution or installation",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "✓ No automatic uploads",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun LimitationsCard(limitations: List<String>) {
    if (limitations.isEmpty()) return
    
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Limitations",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            limitations.take(3).forEach { limitation ->
                Text(
                    text = "• $limitation",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatFileSize(bytes: Long?): String {
    if (bytes == null) return "Unknown"
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}
