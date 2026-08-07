# Google Play Reviewer Instructions

This document provides step-by-step instructions for Google Play reviewers to test PocketLab.

## App Overview

PocketLab is a local-first, offline Android application for static malware triage. It analyzes APK files, archives, and other supported formats without installing, executing, or transmitting the files.

**Key features:**
- Static analysis only (no execution)
- Local processing (no network access)
- No account required
- Privacy-focused (no data collection)

## Testing the App

### Step 1: Install the App

Install PocketLab from the Google Play Store. The app requires Android 10 (API 29) or higher.

### Step 2: Launch the App

Open PocketLab from the app drawer. You will see the onboarding screens explaining:
1. What the app does (static analysis)
2. Privacy information (local processing, no uploads)
3. Limitations (static analysis cannot prove safety)

Tap "Continue" to proceed to the main screen.

### Step 3: Analyze the Demo APK

PocketLab includes an inert demonstration APK for testing purposes.

1. From the home screen, tap **"Analyze a file"**
2. Navigate to the app's internal storage or use the file picker
3. Select the demo APK (filename: `demo_fixture.apk`)
4. You will see the intake confirmation screen showing:
   - File name
   - File size
   - Detected file type
   - Privacy statement
5. Tap **"Start Local Analysis"**

### Step 4: Observe Analysis Progress

The analysis progress screen shows:
- Current stage (file type detection, archive analysis, APK analysis, etc.)
- Progress indicator
- Elapsed time
- Cancel button

Wait for the analysis to complete (typically 5-15 seconds for the demo fixture).

### Step 5: Review the Report

After analysis completes, you will see the Simple Report with:
- **Risk band**: "No major concerns observed" (for the demo fixture)
- **Confidence**: High
- **Completeness**: 100%
- **Top findings**: Expected findings for the demo fixture
- **Permissions**: List of declared permissions
- **Components**: Activities, services, receivers, providers
- **Network indicators**: Any URLs or domains found
- **Limitations**: Static analysis limitations

### Step 6: Explore Analyst Mode

Tap the "Analyst" tab to see detailed technical information:
- Package metadata
- Signing certificates
- Permission details
- Component details
- DEX file information
- Extracted indicators
- Full finding details with evidence

### Step 7: Test Export Functionality

1. Tap the export icon (share icon)
2. Choose a format:
   - JSON (canonical report)
   - Markdown (human-readable)
   - HTML (formatted report)
   - CSV (indicators only)
3. Choose a destination (Google Drive, email, etc.)
4. Verify the exported file contains the expected data

### Step 8: Test Deletion

1. Return to the home screen
2. Tap the "Cases" tab
3. Long-press the demo case
4. Tap "Delete"
5. Confirm deletion
6. Verify the case is removed from the list

## Expected Findings for Demo Fixture

The demo fixture (`demo_fixture.apk`) is an inert APK with deliberately visible but harmless patterns:

### Expected Findings

1. **Debuggable flag**: The demo APK has `android:debuggable="true"`
   - Severity: High
   - This is intentional for testing purposes

2. **Dangerous permissions**: The demo APK declares several permissions
   - `android.permission.READ_CONTACTS` (Medium)
   - `android.permission.ACCESS_FINE_LOCATION` (Medium)
   - These are declared but not used

3. **Exported components**: The demo APK has exported components
   - One exported activity (Low severity)
   - One exported service (Medium severity)

4. **Cleartext traffic**: The demo APK allows cleartext traffic
   - `android:usesCleartextTraffic="true"` (Medium severity)

### Expected Metadata

- **Package name**: `com.example.demofixture`
- **Version**: 1.0.0
- **Min SDK**: 29
- **Target SDK**: 36
- **Signing**: Debug certificate (self-signed)

## Important Notes for Reviewers

### No Execution

**PocketLab never executes or installs analyzed files.** The app only reads file bytes and performs static analysis. You can verify this by:

1. Checking the app's permissions (no `REQUEST_INSTALL_PACKAGES`)
2. Reviewing the source code (no `PackageInstaller` or `startActivity` calls for analyzed files)
3. Observing that analyzed files remain unchanged after analysis

### No Network Access

**PocketLab has no internet permission.** The app cannot transmit data to external servers. You can verify this by:

1. Checking the app's permissions (no `INTERNET` permission)
2. Reviewing the AndroidManifest.xml
3. Observing that analysis works in airplane mode

### Privacy

**PocketLab does not collect or transmit user data.** All processing occurs locally. You can verify this by:

1. Reviewing the privacy policy (PRIVACY.md)
2. Checking the Data Safety form
3. Observing that no analytics or crash reporting SDKs are present

### Security

**PocketLab follows security best practices:**

1. **Process isolation**: File parsing occurs in a separate process with no permissions
2. **Encrypted storage**: Reports are encrypted using Android Keystore
3. **Input validation**: Strict limits on file sizes, entry counts, and recursion
4. **Path traversal prevention**: Archive entries are validated and normalized

## Troubleshooting

### Analysis fails or hangs

- Ensure the file is not corrupted
- Try a smaller file (demo fixture is ~100KB)
- Check that the device has sufficient storage
- Restart the app and try again

### Export fails

- Ensure you have granted permission to the destination app
- Try a different export format
- Check available storage space

### App crashes

- Check device compatibility (requires Android 10+)
- Ensure sufficient memory is available
- Report the crash through the app's diagnostic export

## Additional Resources

- **Source code**: https://github.com/Pine-Packets/PocketLab
- **Privacy policy**: https://github.com/Pine-Packets/PocketLab/blob/main/PRIVACY.md
- **Security policy**: https://github.com/Pine-Packets/PocketLab/blob/main/SECURITY.md

## Contact

For questions or issues during review:

**Pine and Packets LLC**  
Email: 310212849+Pine-Packets@users.noreply.github.com  
Website: https://github.com/Pine-Packets/PocketLab

## Test Account

**No account required.** PocketLab works without registration, login, or authentication.
