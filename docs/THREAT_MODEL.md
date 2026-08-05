# PocketLab Threat Model

## Protected Assets

1. **User-selected samples**: APK files, archives, and other imported files
2. **Extracted content**: Parsed structures, strings, indicators
3. **Analysis reports**: Findings, evidence, and interpretations
4. **User privacy**: Device integrity and data confidentiality
5. **App integrity**: Signing keys and rule-pack authenticity

## Adversaries

1. **Malicious file authors**: Craft files to exploit parsers or escape boundaries
2. **Malicious archives**: ZIP bombs, path traversal, decompression attacks
3. **Hostile content providers**: Lie about metadata, block reads, change content
4. **Local attackers**: Access backups, shared exports, or device storage
5. **Compromised dependencies**: Supply chain attacks via libraries

## Trust Boundaries

### 1. External Input Boundary

**Threat**: Hostile content URIs, filenames, MIME types, and metadata

**Mitigations**:
- Validate all URIs and reject unsafe schemes
- Never trust reported size or filename
- Bound all reads with explicit limits
- Escape all metadata for display
- Copy to private storage before deep analysis

### 2. Archive Boundary

**Threat**: ZIP bombs, path traversal, excessive nesting, duplicate entries

**Mitigations**:
- Preflight central directory before extraction
- Validate and normalize all paths
- Enforce entry count, size, and ratio limits
- Reject absolute paths and traversal sequences
- Map entries to random internal names
- Track compression ratio during decompression

### 3. Parser Boundary

**Threat**: Malformed structures, integer overflows, buffer overflows

**Mitigations**:
- Validate all structure offsets and bounds
- Use checked arithmetic for size calculations
- Enforce per-parser quotas
- Isolate parsers in separate process (planned)
- Fuzz test all parsers
- Fail safely with incomplete-analysis results

### 4. Report Boundary

**Threat**: Report injection, HTML/Markdown/CSV formula injection

**Mitigations**:
- Escape all sample-controlled text
- Defang URLs and indicators by default
- No active JavaScript in HTML exports
- Strict Content Security Policy
- Validate CSV cells for formula prefixes

### 5. Storage Boundary

**Threat**: Backup exposure, unauthorized access to samples

**Mitigations**:
- Store samples in noBackupFilesDir
- Exclude case data from backups
- Encrypt reports and retained samples
- Delete temporary files after analysis
- Use app-private storage only

## Attack Scenarios

### A-001: ZIP Path Traversal

**Attack**: Archive contains entry with path `../../etc/passwd`

**Mitigation**: Path normalization rejects absolute paths and traversal sequences. Entries mapped to random names.

### A-002: ZIP Bomb

**Attack**: Highly compressed archive expands to exhaust storage

**Mitigation**: Preflight checks compression ratio. Runtime counters abort if limits exceeded.

### A-003: Malformed DEX

**Attack**: DEX file with invalid offsets causes parser crash

**Mitigation**: Bounds checking on all offsets. Checked arithmetic. Safe failure with error code.

### A-004: Report Injection

**Attack**: Filename contains HTML/JavaScript to execute in report viewer

**Mitigation**: All text escaped. No active script in HTML exports. CSP prevents inline script.

### A-005: Content Provider Lies

**Attack**: Provider reports 1KB size but actually returns 1GB

**Mitigation**: Never trust reported size. Hard byte limit enforced during copy. Cancellation closes descriptors.

### A-006: Archive Password Brute Force

**Attack**: Repeated password attempts to exhaust battery

**Mitigation**: Limited password attempts. No automatic brute force. User must explicitly request.

### A-007: Nested Archive Recursion

**Attack**: Archives within archives to exhaust resources

**Mitigation**: Maximum nesting depth of 2. Global quotas apply across all levels.

### A-008: Unicode Confusion

**Attack**: Bidirectional text or zero-width characters in filenames

**Mitigation**: Control characters revealed in analyst mode. Display length capped. Raw name preserved as escaped metadata.

## Security Testing Requirements

1. **Malformed input tests**: Every parser tested with invalid structures
2. **Boundary tests**: Path traversal, absolute paths, duplicate entries
3. **Resource exhaustion tests**: Large files, deep nesting, high ratios
4. **Injection tests**: HTML, Markdown, CSV, JavaScript payloads
5. **Cancellation tests**: Abort at every stage, verify cleanup
6. **Crash recovery tests**: Process death, corrupt output, partial results
7. **Fuzz testing**: All parsers fuzzed with mutation-based inputs

## Residual Risks

1. **Parser vulnerabilities**: Mitigated by isolation and fuzzing, but not eliminated
2. **Zero-day exploits**: Unknown vulnerabilities in dependencies
3. **Side-channel attacks**: Timing or power analysis (out of scope for MVP)
4. **User error**: Exporting sensitive reports to untrusted destinations
5. **Device compromise**: Rooted device or malicious app with elevated privileges

## Security Review Checklist

- [ ] No network permission in analysis path
- [ ] No sample execution or installation
- [ ] All input bounded and validated
- [ ] All paths normalized and checked
- [ ] All quotas enforced
- [ ] All text escaped for output formats
- [ ] Backup exclusion configured
- [ ] Encryption implemented for reports
- [ ] Process isolation for parsers (future)
- [ ] Fuzz testing coverage for all parsers
- [ ] No forbidden APIs or permissions
- [ ] No logging of sensitive data
