# Test Corpus Policy

This document defines the policy for test fixtures used in PocketLab development and testing.

## Principles

1. **No real malware**: The public repository must never contain real malware samples
2. **Synthetic fixtures**: Use intentionally crafted, inert test files
3. **Documented hashes**: Record hashes and expected results for all fixtures
4. **Reproducible**: Fixtures should be reproducible from source when possible
5. **Safe by design**: Fixtures must not execute, install, or cause harm

## Categories

### Synthetic Fixtures (Public Repository)

Located in `test-corpus/synthetic/`

These are intentionally crafted files with specific properties for testing:

- **Valid APKs**: Minimal APKs with known properties
- **Malformed files**: Intentionally broken files to test error handling
- **Edge cases**: Files with unusual properties (large sizes, deep nesting, etc.)
- **Demo fixtures**: Inert files for demonstrating app functionality

**Requirements:**
- Must be inert and non-executable
- Must not contain real malware or malicious code
- Must be documented with expected behavior
- Must have recorded hashes (SHA-256, SHA-1, MD5)

### Malformed Fixtures (Public Repository)

Located in `test-corpus/malformed/`

These are intentionally broken files to test parser robustness:

- Truncated files
- Invalid headers
- Corrupted structures
- Path traversal attempts
- Quota-exceeding files

**Requirements:**
- Must not crash the application
- Must produce controlled error messages
- Must not escape workspace
- Must be documented with expected error codes

### Metadata Files (Public Repository)

Located in `test-corpus/metadata/`

These contain expected results and metadata for fixtures:

- Expected analysis results (JSON)
- Expected findings
- Expected indicators
- Hash records

**Requirements:**
- Must match current engine version
- Must be updated when engine behavior changes
- Must be versioned with the engine

### Controlled Research Corpus (Private)

**Not in public repository**

This contains legally obtained real malware samples for advanced testing:

- Access controlled
- Password protected
- Hash indexed
- Never synchronized to personal cloud
- Analyzed only on designated test devices

**Requirements:**
- Legal acquisition and authorization
- Strict access controls
- Never included in Play Store artifacts
- Documented chain of custody

## Demo Fixture Specification

The demo fixture (`demo_fixture.apk`) is an inert APK used for:
- Google Play reviewer testing
- User demonstrations
- Integration testing

### Required Properties

**Package metadata:**
- Package name: `com.example.demofixture`
- Version: 1.0.0
- Min SDK: 29
- Target SDK: 36

**Manifest flags:**
- `android:debuggable="true"` (intentional for testing)
- `android:allowBackup="false"`
- `android:usesCleartextTraffic="true"` (intentional for testing)

**Permissions:**
- `android.permission.READ_CONTACTS` (declared, not used)
- `android.permission.ACCESS_FINE_LOCATION` (declared, not used)

**Components:**
- One exported activity (low risk)
- One exported service (medium risk)
- One non-exported receiver

**Signing:**
- Debug certificate (self-signed)
- v1 signature scheme

**Code:**
- Minimal DEX with harmless code
- No actual malicious behavior
- No network calls
- No file system access

**Size:**
- < 1 MB total
- < 100 KB DEX

### Expected Findings

The demo fixture should produce these findings:

1. **MANIFEST-DEBUGGABLE**: Application is debuggable (High severity)
2. **PERM-READ_CONTACTS**: Dangerous permission declared (Medium severity)
3. **PERM-ACCESS_FINE_LOCATION**: Dangerous permission declared (Medium severity)
4. **COMP-EXPORTED-ACTIVITY**: Exported activity detected (Low severity)
5. **COMP-EXPORTED-SERVICE**: Exported service detected (Medium severity)
6. **MANIFEST-CLEARTEXT**: Cleartext traffic permitted (Medium severity)

**Risk band**: "Review recommended" (due to multiple findings)

### Hashes

After creating the demo fixture, record:
- SHA-256
- SHA-1
- MD5
- File size

Store in `test-corpus/metadata/demo_fixture.json`

## Creating New Fixtures

### Synthetic APK

1. Create a minimal Android project
2. Configure the desired properties in AndroidManifest.xml
3. Build a debug APK
4. Verify the APK is inert and non-executable
5. Record hashes and expected results
6. Add to `test-corpus/synthetic/`
7. Document in this file

### Malformed File

1. Take a valid fixture
2. Intentionally corrupt it (truncate, modify headers, etc.)
3. Verify it produces controlled errors
4. Verify it does not crash or escape workspace
5. Add to `test-corpus/malformed/`
6. Document expected error code

### Metadata File

1. Analyze the fixture with the current engine
2. Export the canonical JSON report
3. Remove timestamps and non-deterministic fields
4. Save to `test-corpus/metadata/<fixture_name>.json`
5. Update when engine behavior changes

## Testing with Fixtures

### Unit Tests

Use fixtures in unit tests to verify:
- Parser correctness
- Error handling
- Quota enforcement
- Security controls

### Integration Tests

Use fixtures in integration tests to verify:
- End-to-end analysis
- Report generation
- Export functionality

### Regression Tests

Use fixtures in regression tests to verify:
- Engine behavior consistency
- Report schema stability
- Finding accuracy

## Maintenance

### Regular Review

- Review fixtures quarterly
- Remove obsolete fixtures
- Update metadata for engine changes
- Verify hashes match expected values

### Version Control

- Track fixture changes in git
- Document breaking changes
- Update metadata with engine version
- Maintain backward compatibility where possible

### Security Review

- Verify no real malware in public repository
- Verify fixtures are truly inert
- Verify no sensitive data in fixtures
- Scan for accidental secrets or credentials

## Contact

For questions about test corpus policy:

**Pine and Packets LLC**  
Email: 310212849+Pine-Packets@users.noreply.github.com  
Website: https://github.com/Pine-Packets/PocketLab
