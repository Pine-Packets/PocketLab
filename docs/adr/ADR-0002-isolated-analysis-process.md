# ADR-0002: Isolated Analysis Process

## Status

Accepted (implementation planned for Phase 9)

## Context

PocketLab parses potentially malicious files including APKs, DEX files, archives, and other formats. Parser vulnerabilities are a common attack vector for malware.

Key concerns:
- Parser bugs could compromise the main application
- Malicious files could exploit parsing libraries
- Need to contain potential parser compromises
- Android process isolation capabilities

## Decision

The analysis engine will run in a separate Android process declared with `android:isolatedProcess="true"`. This process will have:

- No Android permissions
- No network capability
- No direct access to app database or case storage
- Read-only file descriptor access to samples
- Bounded output through IPC

The main application process will communicate with the isolated analyzer through AIDL/Binder IPC.

## Consequences

### Positive

1. **Containment**: Parser compromises cannot directly access main app data
2. **No permissions**: Isolated process cannot access network, storage, etc.
3. **Crash isolation**: Parser crashes don't crash the main app
4. **Resource control**: Can enforce memory and CPU limits per process
5. **Clean separation**: Clear trust boundary between UI and parsing

### Negative

1. **IPC complexity**: Need to design versioned IPC protocol
2. **Performance overhead**: Process boundary adds latency
3. **Debugging difficulty**: Harder to debug cross-process issues
4. **Android limitations**: Isolated processes have restrictions
5. **Development complexity**: Two-process architecture is more complex

## Implementation Notes

### IPC Protocol

- Use AIDL for type-safe interface
- Pass read-only ParcelFileDescriptor for sample access
- Stream results through bounded callbacks
- Version the interface for forward compatibility

### Isolated Process Restrictions

- Cannot access app's Room database
- Cannot access shared preferences
- Cannot access network (no permission)
- Cannot access external storage
- Cannot access content providers (unless granted)
- Can receive read-only file descriptors
- Can send results through IPC

### Fallback Plan

If isolated process proves too restrictive or complex:
- Use regular separate process with minimal permissions
- Implement strict SELinux policy
- Add additional validation at IPC boundary
- Document limitations clearly

## Security Impact

**Positive**: Significantly reduces impact of parser vulnerabilities. Even if a parser is compromised, the attacker cannot access main app data, network, or persistent storage.

## Play Policy Impact

**Neutral**: Isolated processes are a standard Android security feature. No policy concerns.

## Alternatives Considered

1. **In-process parsing with strict validation**: Rejected due to insufficient containment
2. **Native code with sandboxing**: Rejected due to complexity and native attack surface
3. **WebAssembly sandbox**: Rejected due to Android compatibility and performance
4. **Regular separate process**: Acceptable fallback if isolated process too restrictive

## Migration/Rollback

Initial implementation may use regular separate process. Can migrate to isolated process later if needed.

If isolated process proves unworkable, document limitations and add additional validation at IPC boundary.
