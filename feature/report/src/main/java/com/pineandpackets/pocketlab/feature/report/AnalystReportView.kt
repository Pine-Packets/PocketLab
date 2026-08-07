package com.pineandpackets.pocketlab.feature.report

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pineandpackets.pocketlab.core.crypto.AnalystNote
import com.pineandpackets.pocketlab.core.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalystReportView(
    report: AnalysisReport,
    notes: List<AnalystNote>,
    onAddNote: (String) -> Unit,
    onDeleteNote: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedSection by remember { mutableStateOf("overview") }
    
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            AnalystSectionCard(
                title = "Executive Summary",
                sectionKey = "overview",
                expanded = expandedSection == "overview",
                onToggle = { expandedSection = if (expandedSection == "overview") "" else "overview" }
            ) {
                ExecutiveSummaryContent(report)
            }
        }
        
        item {
            AnalystSectionCard(
                title = "File Metadata",
                sectionKey = "metadata",
                expanded = expandedSection == "metadata",
                onToggle = { expandedSection = if (expandedSection == "metadata") "" else "metadata" }
            ) {
                FileMetadataContent(report)
            }
        }
        
        report.archive?.let { archive ->
            item {
                AnalystSectionCard(
                    title = "Archive Info",
                    sectionKey = "archive",
                    expanded = expandedSection == "archive",
                    onToggle = { expandedSection = if (expandedSection == "archive") "" else "archive" }
                ) {
                    ArchiveInfoContent(archive)
                }
            }
        }
        
        report.apk?.let { apk ->
            item {
                AnalystSectionCard(
                    title = "APK Package Info",
                    sectionKey = "apk",
                    expanded = expandedSection == "apk",
                    onToggle = { expandedSection = if (expandedSection == "apk") "" else "apk" }
                ) {
                    ApkInfoContent(apk)
                }
            }
            
            item {
                AnalystSectionCard(
                    title = "Permissions (${apk.permissions.size})",
                    sectionKey = "permissions",
                    expanded = expandedSection == "permissions",
                    onToggle = { expandedSection = if (expandedSection == "permissions") "" else "permissions" }
                ) {
                    PermissionsContent(apk.permissions)
                }
            }
            
            item {
                AnalystSectionCard(
                    title = "Components (${apk.components.size})",
                    sectionKey = "components",
                    expanded = expandedSection == "components",
                    onToggle = { expandedSection = if (expandedSection == "components") "" else "components" }
                ) {
                    ComponentsContent(apk.components)
                }
            }
            
            apk.signingInfo?.let { signingInfo ->
                item {
                    AnalystSectionCard(
                        title = "Signing Info",
                        sectionKey = "signing",
                        expanded = expandedSection == "signing",
                        onToggle = { expandedSection = if (expandedSection == "signing") "" else "signing" }
                    ) {
                        SigningInfoContent(signingInfo)
                    }
                }
            }
        }
        
        if (report.dex.isNotEmpty()) {
            item {
                AnalystSectionCard(
                    title = "DEX Files (${report.dex.size})",
                    sectionKey = "dex",
                    expanded = expandedSection == "dex",
                    onToggle = { expandedSection = if (expandedSection == "dex") "" else "dex" }
                ) {
                    DexContent(report.dex)
                }
            }
        }
        
        if (report.indicators.isNotEmpty()) {
            item {
                AnalystSectionCard(
                    title = "Indicators (${report.indicators.size})",
                    sectionKey = "indicators",
                    expanded = expandedSection == "indicators",
                    onToggle = { expandedSection = if (expandedSection == "indicators") "" else "indicators" }
                ) {
                    IndicatorsContent(report.indicators)
                }
            }
        }
        
        item {
            AnalystSectionCard(
                title = "Findings (${report.findings.size})",
                sectionKey = "findings",
                expanded = expandedSection == "findings",
                onToggle = { expandedSection = if (expandedSection == "findings") "" else "findings" }
            ) {
                FindingsContent(report.findings)
            }
        }

        item {
            AnalystSectionCard(
                title = "Analyst Notes (${notes.size})",
                sectionKey = "notes",
                expanded = expandedSection == "notes",
                onToggle = { expandedSection = if (expandedSection == "notes") "" else "notes" }
            ) {
                AnalystNotesContent(
                    notes = notes,
                    onAddNote = onAddNote,
                    onDeleteNote = onDeleteNote
                )
            }
        }

        if (report.errors.isNotEmpty()) {
            item {
                AnalystSectionCard(
                    title = "Errors (${report.errors.size})",
                    sectionKey = "errors",
                    expanded = expandedSection == "errors",
                    onToggle = { expandedSection = if (expandedSection == "errors") "" else "errors" }
                ) {
                    ErrorsContent(report.errors)
                }
            }
        }
    }
}

@Composable
private fun AnalystSectionCard(
    title: String,
    sectionKey: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onToggle
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (expanded) "▼" else "▶",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            if (expanded) {
                HorizontalDivider()
                Box(modifier = Modifier.padding(16.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun ExecutiveSummaryContent(report: AnalysisReport) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SummaryRow("Risk Band", report.summary.riskBand.name)
        SummaryRow("Confidence", report.summary.confidence.name)
        SummaryRow("Completeness", "${(report.summary.completeness * 100).toInt()}%")
        SummaryRow("Findings", report.summary.findingCount.toString())
        report.summary.maxSeverity?.let {
            SummaryRow("Max Severity", it.name)
        }
        
        if (report.summary.topFindings.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Top Findings:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            report.summary.topFindings.forEach { finding ->
                Text(
                    text = "• $finding",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun FileMetadataContent(report: AnalysisReport) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MetadataRow("Display Name", report.source.displayName)
        report.source.mimeType?.let { MetadataRow("MIME Type", it) }
        report.source.sizeReported?.let { MetadataRow("Reported Size", formatBytes(it)) }
        report.source.sizeActual?.let { MetadataRow("Actual Size", formatBytes(it)) }
        
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Hashes:",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        MetadataRow("SHA-256", report.source.sha256)
        report.source.sha1?.let { MetadataRow("SHA-1", it) }
        report.source.md5?.let { MetadataRow("MD5", it) }
    }
}

@Composable
private fun ArchiveInfoContent(archive: ArchiveReportSection) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MetadataRow("Archive Type", archive.archiveType)
        MetadataRow("Encrypted", archive.encrypted.toString())
        MetadataRow("Entry Count", archive.entryCount.toString())
        MetadataRow("Compressed Size", formatBytes(archive.declaredCompressedSize))
        MetadataRow("Expanded Size (declared)", formatBytes(archive.declaredExpandedSize))
        MetadataRow("Expanded Size (observed)", formatBytes(archive.observedExpandedSize))
        MetadataRow("Max Observed Ratio", "${(archive.maxObservedRatio * 10).toInt() / 10.0}:1")
        MetadataRow("Nested Depth", archive.nestedDepth.toString())
        MetadataRow("Integrity", archive.integrityStatus.name)
        
        if (archive.suspiciousPaths.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Suspicious Paths (${archive.suspiciousPaths.size}):",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            archive.suspiciousPaths.take(10).forEach { path ->
                Text(
                    text = "• $path",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (archive.suspiciousPaths.size > 10) {
                Text(
                    text = "... and ${archive.suspiciousPaths.size - 10} more",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        if (archive.duplicateEntries.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Duplicate Entries (${archive.duplicateEntries.size}):",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary
            )
            archive.duplicateEntries.take(5).forEach { path ->
                Text(
                    text = "• $path",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        
        if (archive.unsupportedEntries.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Unsupported Entries (${archive.unsupportedEntries.size}):",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            archive.unsupportedEntries.take(10).forEach { path ->
                Text(
                    text = "• $path",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        if (archive.skippedChildren.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Skipped Entries (${archive.skippedChildren.size}):",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            archive.skippedChildren.take(10).forEach { skipped ->
                Text(
                    text = "• ${skipped.path} — ${skipped.reason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        if (archive.quotaEvents.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Quota Events (${archive.quotaEvents.size}):",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            archive.quotaEvents.take(10).forEach { event ->
                Text(
                    text = "• $event",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        
        if (archive.analyzedChildren.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Entries (${archive.analyzedChildren.size}):",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            archive.analyzedChildren.take(20).forEach { child ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = child.path,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = formatBytes(child.expandedSize),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (archive.analyzedChildren.size > 20) {
                Text(
                    text = "... and ${archive.analyzedChildren.size - 20} more entries",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        if (archive.textEntryInventory.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Notes / Text Entries (${archive.textEntryInventory.size}):",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            archive.textEntryInventory.take(20).forEach { entry ->
                Column {
                    Text(
                        text = entry.path,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Scanned ${formatBytes(entry.scannedBytes)} of ${formatBytes(entry.expandedSize)}" +
                            (entry.detectedCharset?.let { ", charset: $it" } ?: "") +
                            ", indicators: ${entry.indicators.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    entry.indicators.take(5).forEach { indicator ->
                        Text(
                            text = "  • ${indicator.type.name}: ${indicator.defangedValue}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (archive.textEntryInventory.size > 20) {
                Text(
                    text = "... and ${archive.textEntryInventory.size - 20} more text entries",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ApkInfoContent(apk: ApkInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        apk.packageName?.let { MetadataRow("Package", it) }
        apk.versionName?.let { MetadataRow("Version Name", it) }
        apk.versionCode?.let { MetadataRow("Version Code", it.toString()) }
        apk.minSdk?.let { MetadataRow("Min SDK", it.toString()) }
        apk.targetSdk?.let { MetadataRow("Target SDK", it.toString()) }
        apk.compileSdk?.let { MetadataRow("Compile SDK", it.toString()) }
        apk.applicationLabel?.let { MetadataRow("App Label", it) }
        
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Flags:",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        MetadataRow("Debuggable", apk.debuggable.toString())
        MetadataRow("Backup Allowed", apk.backupAllowed.toString())
        MetadataRow("Cleartext Traffic", apk.usesCleartextTraffic.toString())
    }
}

@Composable
private fun PermissionsContent(permissions: List<PermissionInfo>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        permissions.forEach { perm ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = perm.name.substringAfterLast('.'),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                perm.protectionLevel?.let { level ->
                    Text(
                        text = level,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ComponentsContent(components: List<ComponentInfo>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        components.forEach { comp ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = comp.name.substringAfterLast('.'),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = comp.type.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (comp.exported) {
                    Text(
                        text = "EXPORTED",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun SigningInfoContent(signingInfo: SigningInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MetadataRow("Verified", signingInfo.verified.toString())
        MetadataRow("Signers", signingInfo.signerCount.toString())
        MetadataRow("Schemes", signingInfo.signatureSchemes.joinToString(", "))
        MetadataRow("Has Lineage", signingInfo.hasLineage.toString())
        
        if (signingInfo.certificates.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Certificates:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            signingInfo.certificates.forEachIndexed { index, cert ->
                Text(
                    text = "Certificate ${index + 1}:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                MetadataRow("Subject", cert.subject)
                MetadataRow("Issuer", cert.issuer)
                MetadataRow("Algorithm", cert.algorithm)
                MetadataRow("Key Size", "${cert.keySize} bits")
                MetadataRow("Fingerprint", cert.fingerprint.take(20) + "...")
                MetadataRow("Self-Signed", cert.selfSigned.toString())
                MetadataRow("Valid From", cert.validFrom)
                MetadataRow("Valid To", cert.validTo)
            }
        }
        
        if (signingInfo.hasLineage && signingInfo.signingLineage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Signing Lineage:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            signingInfo.signingLineage.forEachIndexed { index, entry ->
                Text(
                    text = "Lineage Entry ${index + 1}:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                MetadataRow("Index", entry.index.toString())
                MetadataRow("Current Signer", entry.isCurrentSigner.toString())
                entry.rotationTarget?.let {
                    MetadataRow("Rotation Target", it.take(20) + "...")
                }
                entry.proofOfRotation?.let {
                    MetadataRow("Proof", it)
                }
                if (entry.certificate.fingerprint.isNotEmpty()) {
                    MetadataRow("Certificate", entry.certificate.fingerprint.take(20) + "...")
                }
            }
        }
    }
}

@Composable
private fun DexContent(dexFiles: List<DexInfo>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        dexFiles.forEach { dex ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = dex.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    MetadataRow("Version", dex.version)
                    MetadataRow("Classes", dex.classCount.toString())
                    MetadataRow("Methods", dex.methodCount.toString())
                    MetadataRow("Strings", dex.stringCount.toString())
                    MetadataRow("Size", formatBytes(dex.size))
                }
            }
        }
    }
}

@Composable
private fun IndicatorsContent(indicators: List<Indicator>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        indicators.take(50).forEach { indicator ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = indicator.type.name,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(80.dp)
                )
                Text(
                    text = indicator.defangedValue,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        if (indicators.size > 50) {
            Text(
                text = "... and ${indicators.size - 50} more",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FindingsContent(findings: List<Finding>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        findings.forEach { finding ->
            AnalystFindingCard(finding)
        }
    }
}

@Composable
private fun AnalystFindingCard(finding: Finding) {
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
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
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
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Severity: ${finding.severity.name}",
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = "Confidence: ${finding.confidence.name}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            
            Text(
                text = "Rule: ${finding.ruleId}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace
            )
            
            Text(
                text = finding.analystExplanation ?: finding.simpleExplanation,
                style = MaterialTheme.typography.bodySmall
            )
            
            if (finding.evidence.isNotEmpty()) {
                Text(
                    text = "Evidence (${finding.evidence.size}):",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                finding.evidence.take(3).forEach { evidence ->
                    val evidenceText = buildEvidenceText(evidence)
                    Text(
                        text = "• ${evidence.type.name}: $evidenceText",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            
            if (finding.limitations.isNotEmpty()) {
                Text(
                    text = "Limitations:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                finding.limitations.forEach { limitation ->
                    Text(
                        text = "• $limitation",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorsContent(errors: List<AnalysisError>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        errors.forEach { error ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = error.code,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = error.stage ?: "unknown",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = error.message,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}

private fun buildEvidenceText(evidence: Evidence): String {
    val parts = mutableListOf<String>()
    
    evidence.dexName?.let { parts.add(it) }
    evidence.className?.let { parts.add(it) }
    evidence.method?.let { parts.add(it) }
    evidence.offset?.let { parts.add("@0x${it.toString(16).uppercase()}") }
    evidence.excerpt?.let { parts.add(it.take(50)) }
    
    return if (parts.isNotEmpty()) {
        parts.joinToString(" | ")
    } else {
        "evidence"
    }
}

@Composable
private fun AnalystNotesContent(
    notes: List<AnalystNote>,
    onAddNote: (String) -> Unit,
    onDeleteNote: (String) -> Unit
) {
    var newNoteText by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = newNoteText,
            onValueChange = { newNoteText = it },
            label = { Text("Add analyst note") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 6
        )

        Button(
            onClick = {
                onAddNote(newNoteText)
                newNoteText = ""
            },
            enabled = newNoteText.isNotBlank(),
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Add Note")
        }

        if (notes.isEmpty()) {
            Text(
                text = "No analyst notes yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            notes.forEach { note ->
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = note.content,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = java.text.SimpleDateFormat(
                                    "yyyy-MM-dd HH:mm",
                                    java.util.Locale.getDefault()
                                ).format(java.util.Date(note.createdAt)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(onClick = { onDeleteNote(note.id) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete note"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
