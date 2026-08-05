# ADR-0003: Canonical Report JSON Schema

## Status

Accepted

## Context

PocketLab generates analysis reports containing findings, evidence, indicators, and metadata. Reports must be:

- Serializable and deserializable
- Versioned for forward compatibility
- Human-readable for debugging
- Machine-parseable for automation
- Suitable for multiple output formats (JSON, Markdown, HTML, CSV)

## Decision

The canonical report format will be **versioned JSON** using Kotlin serialization.

Key design principles:
1. JSON is the source of truth; other formats are projections
2. Schema versioned with semantic versioning
3. Backward-compatible changes only within major version
4. Facts and interpretations clearly separated
5. All evidence includes provenance
6. Incomplete analysis explicitly marked

## Schema Structure

```json
{
  "schemaVersion": "1.0.0",
  "reportId": "uuid",
  "caseId": "uuid",
  "createdAt": "ISO-8601",
  "engine": { ... },
  "source": { ... },
  "containers": [ ... ],
  "files": [ ... ],
  "apk": { ... },
  "dex": [ ... ],
  "indicators": [ ... ],
  "facts": [ ... ],
  "findings": [ ... ],
  "summary": { ... },
  "stageResults": [ ... ],
  "limitations": [ ... ],
  "errors": [ ... ],
  "integrity": { ... }
}
```

## Versioning Policy

- **Major version**: Breaking changes (incompatible schema)
- **Minor version**: Additive changes (new optional fields)
- **Patch version**: Clarifications and bug fixes

Migration strategy:
- Include schema version in every report
- Support reading previous minor versions
- Document breaking changes between major versions
- Provide migration tools where practical

## Consequences

### Positive

1. **Interoperability**: Standard format for tools and automation
2. **Determinism**: Same input produces same output
3. **Auditability**: Clear evidence provenance
4. **Extensibility**: Can add fields without breaking compatibility
5. **Multiple outputs**: JSON can be transformed to other formats
6. **Testing**: Can validate against schema and golden files

### Negative

1. **Verbosity**: JSON is more verbose than binary formats
2. **Parsing overhead**: JSON parsing slower than binary
3. **Schema evolution**: Must carefully manage compatibility
4. **Size limits**: Large reports may hit size limits

## Security Impact

**Neutral**: JSON is a well-understood format. Must escape all sample-controlled text to prevent injection.

## Play Policy Impact

**Neutral**: Report format is internal to the app.

## Alternatives Considered

1. **Protocol Buffers**: Rejected due to complexity and less human-readable
2. **Custom binary format**: Rejected due to debugging difficulty
3. **XML**: Rejected due to verbosity and parsing complexity
4. **YAML**: Rejected due to parsing ambiguity and security concerns

## Migration/Rollback

Schema changes will include:
- Version bump
- Migration guide
- Backward compatibility where possible
- Golden test updates
- Documentation updates
