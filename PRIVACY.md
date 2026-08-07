# PocketLab Privacy Policy

**Last Updated:** August 5, 2026  
**Organization:** Pine and Packets LLC

## Overview

PocketLab is a local-first, offline Android application for static malware triage. This privacy policy explains how PocketLab handles your data and what information is collected, processed, or transmitted.

## Core Privacy Principles

1. **Local-First Processing**: All analysis occurs on your device. No files, samples, or analysis results are transmitted to external servers.
2. **No Network Access**: The application does not declare the INTERNET permission and cannot access the internet.
3. **No Account Required**: PocketLab does not require user accounts, registration, or authentication.
4. **No Analytics or Telemetry**: The application does not collect usage statistics, analytics, or telemetry data.
5. **No Advertising**: PocketLab contains no advertising SDKs or ad tracking.

## What Data PocketLab Processes

### Files You Select

When you use PocketLab, you explicitly select files for analysis. PocketLab processes:

- **File content**: The bytes of files you select (APKs, ZIPs, DEX files, etc.)
- **File metadata**: Filename, size, and file type as reported by your device
- **Analysis results**: Extracted indicators, findings, and report data

**Important**: PocketLab never automatically scans files on your device. You must explicitly select each file for analysis.

### Where Data is Stored

All processed data is stored locally on your device:

- **Temporary workspace**: Files are copied to app-private storage during analysis
- **Encrypted reports**: Analysis reports are encrypted using Android Keystore
- **Case database**: Case metadata is stored in a local Room database

**Storage location**: `noBackupFilesDir/cases/<uuid>/`

**Backup exclusion**: All case data is excluded from Android backup to prevent sensitive samples from being backed up to cloud services.

### What Data is NOT Collected

PocketLab does **not** collect or transmit:

- Personal information (name, email, phone number, etc.)
- Device identifiers (IMEI, serial number, advertising ID, etc.)
- Location data
- Contacts or address book information
- Installed application list
- Network information or IP addresses
- Usage analytics or telemetry
- Crash reports (unless you manually export them)
- Files unless you explicitly select them

## Data Retention

### Default Retention Policy

- **Temporary samples**: Deleted after analysis completes (default)
- **Analysis reports**: Retained for 30 days, then automatically deleted
- **Encrypted reports**: Retained until you manually delete them

### User-Controlled Retention

You can configure retention settings in the app:

- **Session only**: Delete everything when app closes
- **Temporary**: Delete samples after analysis, keep reports for 30 days
- **Retain sample**: Keep encrypted samples (advanced users only)
- **Auto-delete**: Configure custom retention periods (1, 7, or 30 days)

### Manual Deletion

You can delete individual cases or all cases at any time:

- **Individual case**: Open the case and tap "Delete"
- **All cases**: Settings → Privacy & Storage → "Delete all cases now"

Deletion is immediate and permanent. Deleted data cannot be recovered.

## Permissions

PocketLab requests minimal permissions:

### Declared Permissions

- **None**: PocketLab does not declare any dangerous permissions

### Not Declared

PocketLab explicitly does **not** request:

- `INTERNET` - No network access
- `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` - Uses Storage Access Framework
- `READ_MEDIA_*` - No direct media access
- `CAMERA` - No camera access
- `LOCATION` - No location access
- `CONTACTS` - No contact access
- `ACCESSIBILITY_SERVICE` - No accessibility service
- `REQUEST_INSTALL_PACKAGES` - Never installs analyzed files
- `QUERY_ALL_PACKAGES` - Does not enumerate installed apps

### File Access Model

PocketLab uses Android's Storage Access Framework (SAF):

- `ACTION_OPEN_DOCUMENT` - You select files through the system file picker
- `ACTION_CREATE_DOCUMENT` - You choose where to export reports
- Files are accessed only with your explicit selection
- No broad storage access is requested or required

## Third-Party Services

PocketLab does **not** integrate with:

- Analytics services (Google Analytics, Firebase Analytics, etc.)
- Crash reporting services (Crashlytics, Sentry, etc.)
- Advertising networks (AdMob, Facebook Ads, etc.)
- Cloud services (Firebase, AWS, Azure, etc.)
- Social media SDKs
- Payment processors

## Security Measures

### Encryption

- **Report encryption**: Analysis reports are encrypted using AES-256-GCM
- **Key management**: Encryption keys are protected by Android Keystore
- **No plaintext storage**: Sensitive data is never stored in plaintext

### Process Isolation

- **Isolated analysis**: File parsing occurs in a separate Android process
- **No permissions**: The isolated process has no Android permissions
- **No network**: The isolated process cannot access the network
- **Crash containment**: Parser crashes do not affect the main application

### Input Validation

- **Path traversal prevention**: Archive entries are validated and normalized
- **Quota enforcement**: Strict limits on file sizes, entry counts, and recursion
- **Magic byte detection**: File types are detected by content, not extension
- **Malformed input handling**: Parser failures produce controlled errors, not crashes

## Children's Privacy

PocketLab does not knowingly collect data from children under 13. The application is a security analysis tool intended for security professionals, developers, and technical users.

## Your Rights

### Data Access

You can view all data PocketLab has processed:

- Open the app to see your case list
- View individual case reports
- Export reports in JSON, Markdown, HTML, or CSV formats

### Data Deletion

You can delete your data at any time:

- Delete individual cases
- Delete all cases
- Uninstall the app (removes all app data)

### Data Portability

You can export your data:

- Export reports in standard formats (JSON, Markdown, HTML, CSV)
- Copy hashes and indicators to clipboard
- Share reports through Android's share sheet

## Changes to This Policy

We may update this privacy policy to reflect changes in the application or legal requirements. Updates will be:

- Documented in the app's "About" section
- Noted with a "Last Updated" date
- Available in the app and on our website

## Contact Information

For privacy questions or concerns:

**Pine and Packets LLC**  
Email: 310212849+Pine-Packets@users.noreply.github.com  
Website: https://github.com/Pine-Packets/PocketLab

## Open Source

PocketLab is open source software. You can review the source code to verify the privacy practices described in this policy:

**Repository**: https://github.com/Pine-Packets/PocketLab

## Limitations of Static Analysis

**Important**: PocketLab performs static analysis only. It does not:

- Execute or install analyzed files
- Guarantee that a file is safe or malicious
- Replace professional security analysis
- Provide real-time protection

Static analysis identifies capabilities and suspicious patterns, but cannot prove that code executed or guarantee a file is safe. Always exercise caution with files from untrusted sources.

## Summary

PocketLab is designed with privacy as a core principle:

✅ All processing occurs locally on your device  
✅ No internet access or network transmission  
✅ No account or registration required  
✅ No analytics, telemetry, or advertising  
✅ Minimal permissions (none declared)  
✅ Encrypted storage for sensitive data  
✅ User-controlled data retention  
✅ Immediate and permanent deletion  
✅ Open source for transparency  

Your files and analysis results remain on your device and under your control.
