# PocketLab Security Policy

This document describes the security practices, vulnerability reporting process, and security architecture of PocketLab.

## Security Architecture

### Core Principles

1. **Never execute analyzed files**: PocketLab performs static analysis only. Files are never installed, launched, or executed.
2. **Local-first processing**: All analysis occurs on the device. No data is transmitted to external servers.
3. **Minimal permissions**: The app requests no dangerous permissions and has no network access.
4. **Defense in depth**: Multiple layers of security controls protect against parser vulnerabilities and malicious input.
5. **Treat all input as hostile**: Every byte from analyzed files is considered potentially malicious.

### Process Isolation

The analysis engine runs in a separate Android process with:
- `android:isolatedProcess="true"` - No Android permissions
- `android:process=":analyzer"` - Separate process namespace
- `android:exported="false"` - Not accessible to other apps
- No network capability
- No direct access to app database or case storage

Communication occurs through AIDL/Binder IPC with:
- Read-only file descriptors for sample access
- Bounded output through callbacks
- Explicit budget enforcement
- Crash detection and recovery

### Input Validation

All input is validated and bounded:

**File intake:**
- Size limits (512 MB default, configurable)
- Magic byte detection (not extension-based)
- MIME type validation
- Path traversal prevention

**Archive analysis:**
- Entry count limits (5,000 max)
- Total expanded size limits (1 GB max)
- Per-entry size limits (256 MB max)
- Compression ratio limits (100:1 max)
- Nesting depth limits (2 max)
- Path normalization and validation

**DEX analysis:**
- String count limits (100,000 max)
- Method count limits (100,000 max)
- Class count limits (50,000 max)
- Header validation
- Bounds checking

**ELF analysis:**
- Section count limits
- Symbol count limits
- Header validation
- Architecture detection

### Quota Enforcement

Every analysis stage enforces explicit quotas:

```kotlin
object AnalysisLimits {
    const val MAX_INPUT_SIZE_BYTES: Long = 512L * 1024 * 1024 // 512 MB
    const val MAX_ARCHIVE_ENTRIES: Int = 5000
    const val MAX_ARCHIVE_EXPANDED_BYTES: Long = 1024L * 1024 * 1024 // 1 GB
    const val MAX_SINGLE_ENTRY_BYTES: Long = 256L * 1024 * 1024 // 256 MB
    const val MAX_COMPRESSION_RATIO: Double = 100.0
    const val MAX_NESTING_DEPTH: Int = 2
    const val MAX_STRING_COUNT: Int = 100_000
    const val MAX_METHOD_COUNT: Int = 100_000
    const val MAX_CLASS_COUNT: Int = 50_000
    const val MAX_ANALYSIS_DURATION_MS: Long = 10 * 60 * 1000 // 10 minutes
}
```

Quotas prevent:
- Denial of service through resource exhaustion
- Infinite loops or excessive recursion
- Memory exhaustion
- Storage exhaustion
- CPU exhaustion

### Encryption

Sensitive data is encrypted at rest:

- **Report encryption**: AES-256-GCM
- **Key management**: Android Keystore
- **Nonce uniqueness**: Unique nonce per encryption
- **No plaintext storage**: Sensitive data never stored in plaintext

### Path Traversal Prevention

Archive entries are validated to prevent path traversal:

1. Reject NUL characters
2. Reject absolute paths
3. Reject Windows drive paths and UNC paths
4. Normalize `.` and `..` segments
5. Reject paths escaping the virtual archive root
6. Map to random internal extraction names
7. Verify canonical destination remains under workspace

### Report Injection Prevention

All sample-controlled text is escaped in reports:

- **HTML export**: HTML entity encoding
- **Markdown export**: Special character escaping
- **CSV export**: Formula injection prevention (prefix with `'`)
- **JSON export**: Proper JSON encoding

No active JavaScript, remote resources, or executable content in exports.

### Forbidden APIs

The following APIs are forbidden and tested for:

- `Runtime.exec()` - No command execution
- `ProcessBuilder` - No process creation
- `PackageInstaller` - No package installation
- `startActivity()` for analyzed files - No file execution
- `DexClassLoader` for analyzed files - No dynamic code loading
- `WebView` for analyzed content - No web rendering

Automated tests verify these APIs are not present in the codebase.

## Vulnerability Disclosure

### Reporting a Vulnerability

If you discover a security vulnerability in PocketLab, please report it responsibly:

**Email**: security@pineandpackets.com  
**PGP Key**: Available on request

Please include:
- Description of the vulnerability
- Steps to reproduce
- Potential impact
- Suggested fix (if available)

### Response Timeline

- **Acknowledgment**: Within 48 hours
- **Initial assessment**: Within 7 days
- **Fix deployment**: Within 30 days for critical issues
- **Public disclosure**: After fix is deployed and users have had time to update

### Responsible Disclosure

We ask that you:
- Do not publicly disclose the vulnerability before we have had time to address it
- Do not attempt to exploit the vulnerability beyond what is necessary to demonstrate it
- Do not access or modify other users' data
- Do not perform denial of service attacks

We commit to:
- Acknowledging your report promptly
- Working to fix the vulnerability quickly
- Crediting you in the security advisory (unless you prefer anonymity)
- Not taking legal action against researchers who follow responsible disclosure practices

## Security Testing

### Automated Tests

PocketLab includes extensive security tests:

- **Manifest policy tests**: Verify no dangerous permissions
- **Forbidden API tests**: Verify no execution APIs
- **Path traversal tests**: Verify traversal attempts are blocked
- **Quota enforcement tests**: Verify limits are enforced
- **Report injection tests**: Verify escaping works correctly
- **Isolation boundary tests**: Verify process isolation properties

### Fuzzing

Parser fuzzing targets:
- File type detector
- Archive parser
- DEX parser
- ELF parser
- IOC extractor

Fuzzing is performed using:
- JVM fuzzing tools
- Property-based testing (Kotest)
- Malformed input corpus

### Penetration Testing

Before each major release:
- Review threat model
- Test isolation boundary
- Verify quota enforcement
- Test crash recovery
- Verify no data leakage
- Test report injection

## Security Updates

### Dependency Management

- All dependencies have pinned versions
- Dependencies are monitored for CVEs
- Security patches are applied promptly
- Dependency verification is enabled

### Update Process

1. Security issue identified
2. Fix developed and tested
3. Security review completed
4. Release created with security patch
5. Users notified through release notes
6. Vulnerability disclosed (if appropriate)

## Known Limitations

### Static Analysis Limitations

PocketLab performs static analysis only. It cannot:
- Prove that a file is safe or malicious
- Detect all types of malware
- Detect runtime behavior
- Detect obfuscated or packed malware with high confidence
- Replace professional security analysis

### Parser Limitations

Some file formats have limited support:
- Password-protected archives: Detection only, not full decryption
- Advanced DEX analysis: Basic header parsing only
- Native libraries: Header and symbol analysis only
- Obfuscated code: Limited deobfuscation capability

These limitations are clearly documented in reports.

## Security Contacts

**Security team**: security@pineandpackets.com  
**General support**: support@pineandpackets.com  
**Website**: https://pineandpackets.com

## Open Source

PocketLab is open source. You can review the security implementation:

**Repository**: https://github.com/Pine-Packets/PocketLab

We welcome security reviews and contributions from the community.

## Compliance

### Google Play Policy

PocketLab complies with Google Play policies:
- No dangerous permissions
- No network access without disclosure
- Accurate privacy policy
- Complete Data Safety form
- No malware distribution
- No execution of analyzed files

### Industry Standards

PocketLab follows security best practices:
- OWASP Mobile Security Guidelines
- Android Security Best Practices
- Secure Software Development Framework (SSDF)
- NIST Cybersecurity Framework

## Incident Response

### Security Incident

In the event of a security incident:

1. **Detection**: Identify and assess the incident
2. **Containment**: Isolate affected systems
3. **Investigation**: Determine root cause and impact
4. **Remediation**: Fix the vulnerability
5. **Recovery**: Restore normal operations
6. **Communication**: Notify affected users
7. **Post-mortem**: Document lessons learned

### User Notification

If a security incident affects users:
- Notify users promptly
- Explain the impact
- Provide remediation steps
- Offer support

## Continuous Improvement

Security is an ongoing process. We:
- Regularly review and update security controls
- Monitor for new threats and vulnerabilities
- Incorporate feedback from security researchers
- Update documentation and training
- Conduct regular security audits

## Acknowledgments

We thank the security research community for their contributions to making PocketLab more secure.

---

**Last Updated**: August 5, 2026  
**Version**: 1.0.0
