# Google Play Data Safety Form

This document provides the answers for the Google Play Console Data Safety form.

## Data collection and security

### Does your app collect or share any user data?

**No**

PocketLab does not collect or share any user data. All processing occurs locally on the device.

### Are all data types encrypted in transit?

**Not applicable**

PocketLab does not transmit any data over the network. The app does not declare the INTERNET permission.

### Can users request that their data be deleted?

**Yes**

Users can delete individual cases or all cases at any time through the app's interface. Deletion is immediate and permanent.

## Data types

### File content

**Not collected**

PocketLab processes files that users explicitly select, but does not collect or transmit this data. All file processing occurs locally on the device.

### App activity

**Not collected**

PocketLab does not collect app activity, usage statistics, or analytics.

### Crash logs

**Not collected**

PocketLab does not automatically collect or transmit crash logs. Users can manually export local crash diagnostics if they choose.

### Diagnostics

**Not collected**

PocketLab does not collect diagnostic data or telemetry.

### Other in-app messages

**Not collected**

PocketLab does not collect or transmit in-app messages or communications.

### Purchase history

**Not collected**

PocketLab does not have in-app purchases or collect purchase history.

### Name

**Not collected**

PocketLab does not collect user names.

### Email address

**Not collected**

PocketLab does not collect email addresses.

### User IDs

**Not collected**

PocketLab does not collect or assign user IDs.

### Address

**Not collected**

PocketLab does not collect physical addresses.

### Phone number

**Not collected**

PocketLab does not collect phone numbers.

### Other info

**Not collected**

PocketLab does not collect any other personal information.

### Contacts

**Not collected**

PocketLab does not access or collect contacts.

### Messages

**Not collected**

PocketLab does not access or collect messages (SMS, email, etc.).

### Photos

**Not collected**

PocketLab does not access or collect photos.

### Videos

**Not collected**

PocketLab does not access or collect videos.

### Voice or sound recordings

**Not collected**

PocketLab does not access or collect audio recordings.

### Music files

**Not collected**

PocketLab does not access or collect music files.

### Other audio files

**Not collected**

PocketLab does not access or collect other audio files.

### Documents or files

**Processed but not collected**

PocketLab processes files that users explicitly select for analysis. These files are processed locally and are not collected, transmitted, or shared. Users control which files are selected and can delete them at any time.

### Calendar events

**Not collected**

PocketLab does not access or collect calendar events.

### Location

**Not collected**

PocketLab does not collect location data.

### Device or other IDs

**Not collected**

PocketLab does not collect device identifiers (IMEI, serial number, advertising ID, etc.).

### Approximate location

**Not collected**

PocketLab does not collect approximate location.

### Precise location

**Not collected**

PocketLab does not collect precise location.

### App info and performance

**Not collected**

PocketLab does not collect app performance data or diagnostics.

### Other app performance data

**Not collected**

PocketLab does not collect other app performance data.

### Other in-app messages

**Not collected**

PocketLab does not collect other in-app messages.

## Data handling

### Do you have a privacy policy?

**Yes**

Privacy policy URL: https://github.com/Pine-Packets/PocketLab/blob/main/PRIVACY.md

The privacy policy is also accessible within the app through the Settings → About screen.

### Is your app designed for children?

**No**

PocketLab is a security analysis tool intended for security professionals, developers, and technical users. It is not designed for children.

## Data sharing

### Do you share any user data with third parties?

**No**

PocketLab does not share any user data with third parties. All processing occurs locally on the device.

### Do you share data with other apps?

**No**

PocketLab does not share data with other apps. Users can export reports through Android's share sheet, but this requires explicit user action and the user controls the destination.

## Specific declarations

### File and media access

**No**

PocketLab does not request broad file or media access. The app uses Android's Storage Access Framework to access only files that users explicitly select.

### Installed apps

**No**

PocketLab does not enumerate or access installed applications. The app analyzes files that users explicitly select, not installed apps.

### Background location

**No**

PocketLab does not collect location data in the background or foreground.

### Foreground location

**No**

PocketLab does not collect location data.

### Camera

**No**

PocketLab does not access the camera.

### Microphone

**No**

PocketLab does not access the microphone.

### Contacts

**No**

PocketLab does not access contacts.

### Phone

**No**

PocketLab does not access phone functionality.

### SMS

**No**

PocketLab does not access SMS messages.

### Calendar

**No**

PocketLab does not access calendar data.

### Body sensors

**No**

PocketLab does not access body sensors.

### Physical activity

**No**

PocketLab does not access physical activity data.

## Security practices

### Is data encrypted in transit?

**Not applicable**

PocketLab does not transmit data over the network.

### Can users request data deletion?

**Yes**

Users can delete individual cases or all cases at any time.

### Is data exposed in the app's privacy policy?

**Yes**

The privacy policy clearly explains what data is processed and how it is handled.

### Do you follow security best practices?

**Yes**

PocketLab follows security best practices:

- Process isolation for file parsing
- Encrypted storage for sensitive data
- No network access
- Minimal permissions
- Input validation and quota enforcement
- Path traversal prevention
- Regular security testing

## Additional notes

### Security app disclosure

PocketLab is a security analysis application. It processes files that users explicitly select to identify potential security risks. The app:

- Does not execute or install analyzed files
- Does not automatically upload or transmit files
- Processes all data locally on the device
- Provides static analysis only (no dynamic analysis or detonation)
- Clearly states that analysis results are indicators, not guarantees

### Static analysis limitations

PocketLab performs static analysis only. It cannot:

- Prove that a file is safe or malicious
- Detect all types of malware
- Replace professional security analysis
- Guarantee the absence of threats

Users are advised to exercise caution with files from untrusted sources and to consult security professionals for critical decisions.

## Reviewer instructions

For Google Play reviewers:

1. **No account required**: The app works without registration or login
2. **Demo fixture**: An inert demonstration APK is included for testing
3. **No execution**: Analyzed files are never executed or installed
4. **Local processing**: All analysis occurs on the device
5. **No network**: The app has no internet permission
6. **Privacy**: See PRIVACY.md for detailed privacy information
7. **Security**: See SECURITY.md for security practices
8. **Source code**: The app is open source at https://github.com/Pine-Packets/PocketLab

## Contact

For questions about data handling:

**Pine and Packets LLC**  
Email: privacy@pineandpackets.com  
Website: https://pineandpackets.com
