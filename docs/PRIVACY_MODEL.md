# PocketLab Privacy Model

## Core Privacy Principles

1. **Local-first**: All analysis occurs on-device
2. **No automatic uploads**: Samples and reports never leave device without explicit user action
3. **No analytics**: No telemetry, crash reporting, or usage tracking in MVP
4. **No advertising**: No ad SDKs or tracking
5. **Minimal permissions**: Only permissions necessary for core functionality
6. **User control**: Clear deletion controls and retention policies

## Data Collection

### What We Collect

**Nothing automatically.**

The application does not collect, transmit, or store any data externally. All processing occurs locally on the user's device.

### What the User Provides

- **Selected files**: User explicitly chooses files to analyze
- **Analysis settings**: User preferences for analysis behavior
- **Retention preferences**: How long to keep cases and reports

## Data Storage

### Local Storage

- **Samples**: Stored in app-private `noBackupFilesDir`
- **Reports**: Encrypted with AES-256-GCM, stored in app-private storage
- **Case metadata**: Room database in app-private storage
- **Settings**: DataStore preferences in app-private storage

### Backup Exclusion

All case data, samples, and reports are excluded from Android backup:
- `allowBackup="false"` in manifest
- Data extraction rules exclude all domains
- Samples stored in `noBackupFilesDir`

### Encryption

- Reports encrypted with per-installation AES-256 key
- Key wrapped by Android Keystore
- Unique nonces for each encryption operation
- No encryption for samples by default (deleted after analysis)

## Data Retention

### Default Retention Policy

- **Samples**: Deleted after report generation (configurable)
- **Extracted content**: Deleted immediately after analysis
- **Reports**: Retained for 30 days by default (configurable)
- **Case metadata**: Retained until user deletes case

### Retention Modes

1. **Temporary** (default): Delete sample after report, keep report 30 days
2. **Session only**: Delete everything when app closes
3. **Retain sample**: Keep encrypted sample (advanced, opt-in with warning)
4. **Auto-delete**: Configurable 1, 7, or 30 day automatic deletion

### User Controls

- Delete individual cases
- Delete all cases
- Configure default retention period
- Configure sample retention preference

## Network Access

### MVP Network Access

**None.**

The application has no `INTERNET` permission. No data leaves the device.

### Future Optional Network Features

If implemented in future versions:
- Hash-only reputation lookups (opt-in)
- User-supplied API keys for external services (opt-in)
- Signed rule-pack updates (opt-in)

All network features will require:
- Explicit user enablement
- Clear disclosure of transmitted data
- Preview before transmission
- No automatic sample upload
- Privacy policy update
- Data Safety form update

## Permissions

### Requested Permissions

**None in MVP.**

The application uses only:
- Storage Access Framework (no storage permissions needed)
- No dangerous permissions
- No special permissions

### Future Permissions

Any additional permissions require:
- Architecture Decision Record
- Threat model update
- Play Store policy review
- Automated manifest tests
- Clear user benefit documentation

## Third-Party Services

### MVP Third-Party Services

**None.**

- No analytics SDKs
- No advertising SDKs
- No crash reporting SDKs
- No network libraries in analysis path

### Dependencies

All dependencies are:
- Open source with permissive licenses
- Reviewed for security and maintenance
- Pinned to specific versions
- Free of known vulnerabilities where possible

## User Rights

### Access

Users can view all data stored by the app through the case management interface.

### Deletion

Users can delete:
- Individual cases
- All cases at once
- Exported reports

### Portability

Users can export:
- Reports in multiple formats (JSON, Markdown, HTML, CSV)
- Indicators of Compromise
- Case metadata

## Privacy Policy Requirements

The public privacy policy must explain:
- What files the user selects
- That analysis is local
- Where temporary files are stored
- Retention defaults and controls
- Report contents and export options
- Deletion behavior
- No network transmission in MVP
- Future optional network features
- Contact information for Pine and Packets LLC

## Google Play Data Safety

### Data Collection

- **No data collected**

### Data Shared

- **No data shared**

### Data Stored

- **No data stored externally**

### Security Practices

- Data encrypted in transit: N/A (no network)
- Data encrypted at rest: Yes (reports)
- Data can be deleted: Yes (user controls)
- Independent security review: Planned

## Privacy Testing

1. **Network prohibition test**: Verify no `INTERNET` permission
2. **Backup exclusion test**: Verify no data in backups
3. **Deletion test**: Verify complete case deletion
4. **Encryption test**: Verify report encryption
5. **Logging test**: Verify no sensitive data in logs
6. **Export test**: Verify user-controlled export only

## Compliance

- **GDPR**: Compliant (no data collection)
- **CCPA**: Compliant (no data collection)
- **Play Store Policy**: Compliant (accurate Data Safety)
- **App Privacy Labels**: Accurate (no data collection)

## Contact

Privacy concerns or questions:
- Email: 310212849+Pine-Packets@users.noreply.github.com
