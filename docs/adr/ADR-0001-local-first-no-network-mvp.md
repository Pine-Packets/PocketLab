# ADR-0001: Local-First No-Network MVP

## Status

Accepted

## Context

PocketLab is a static malware triage application that analyzes potentially malicious files. Users will import suspicious APKs, archives, and other files for analysis.

Key concerns:
- User privacy and data protection
- Preventing accidental sample exfiltration
- Play Store policy compliance for security applications
- Building user trust
- Simplifying initial development and testing

## Decision

The MVP will have **no network access** whatsoever. The application will not declare the `INTERNET` permission, will not include any network libraries in the analysis path, and will not transmit any data externally.

All analysis, storage, and reporting will occur locally on the device.

## Consequences

### Positive

1. **Strong privacy guarantee**: No sample data can leave the device accidentally
2. **Simplified threat model**: No network-based attack vectors
3. **Play Store compliance**: Clear offline positioning, simpler Data Safety
4. **User trust**: Easy to explain and verify
5. **Reduced dependencies**: No network libraries to maintain or secure
6. **Offline operation**: Works in air-gapped environments
7. **Simpler testing**: No network mocking or integration tests needed

### Negative

1. **No reputation lookups**: Cannot check hashes against online databases
2. **No automatic updates**: Rule packs must ship with app releases
3. **No cloud sync**: Reports and cases are device-local only
4. **Limited threat intelligence**: No access to live threat feeds

## Mitigations

1. **Future optional network features**: Can be added later with explicit user opt-in
2. **Hash-only lookups**: Future feature could send only SHA-256 hashes, not samples
3. **User-supplied API keys**: Future feature for VirusTotal or enterprise APIs
4. **Signed rule updates**: Future feature for over-the-air rule pack updates

## Security Impact

**Positive**: Eliminates entire class of network-based attacks and data exfiltration risks.

## Play Policy Impact

**Positive**: Simplifies Play Store review, Data Safety form, and privacy policy. Accurate "offline" claim.

## Alternatives Considered

1. **Network access for hash lookups only**: Rejected for MVP due to privacy complexity
2. **Optional network access**: Rejected for MVP to keep initial release simple
3. **Cloud-based analysis**: Rejected as it violates core local-first principle

## Migration/Rollback

Not applicable. This is a foundational architectural decision for the MVP.

Future network features will require:
- Separate ADR
- Privacy model update
- Threat model update
- Data Safety form update
- Explicit user opt-in
- Clear disclosure of transmitted data
