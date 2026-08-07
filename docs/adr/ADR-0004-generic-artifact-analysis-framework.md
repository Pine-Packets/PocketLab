# ADR-0004: Generic Artifact Analysis Framework

## Status

Accepted

## Context

PocketLab's analysis engine is hard-wired to the APK/DEX/archive pipeline. `AnalysisReport` ships `containers`, `files`, and `facts` as always-empty placeholders, detection dispatches on a single hard-coded `when` over `DetectedType`, there is no per-analyzer version tracking, and cancellation is a stub in the orchestrator.

The authoritative expansion plan (Phase 14) requires converting the pipeline into a general static-analysis platform that can handle arbitrary artifact types, nested artifacts, containers, recursive analysis, shared case-level budgets, layered detection, multi-analyzer fan-out (polyglots), partial analysis, parser-version tracking, and cross-artifact correlation. Future phases (15.1+) add one analyzer at a time (PDF, OOXML, OLE, images, email, ...), so the framework must let each new format be added without rewriting the product.

## Decision

Introduce a generic artifact framework in three layers:

1. **`core:model` — `ArtifactNode`, `AnalyzerInfo`, `AnalyzerUse`, `ArtifactMetadataEntry`, `ParserErrorRecord`.** Every analyzed input (root and nested/container child) is represented as a versioned `ArtifactNode` with stable id, parent id, container relation, original/sanitized display name, claimed MIME, detected type/subtype, size, SHA-256, metadata, indicators, findings, facts, children, parser errors, completeness, limitations, and analyzer attribution. Report schema bumps additively to `1.1.0` with new `artifacts` and `analyzerInfo` lists.

2. **`engine:api` — `CaseBudget`, `AnalysisCancellation`, `AnalysisContext`, `ArtifactAnalyzer`, `ArtifactRef`, `AnalyzerResult`, `ParsedChild`, `DetectionLayer`.** `CaseBudget` is a single shared, case-level counter set that is never reset for a child or nested container; all size summation uses checked arithmetic and overflow is a rejection condition. `AnalysisCancellation` provides thread-safe cooperative cancellation, and `AnalysisContext` adds an optional wall-clock deadline and per-operation budget. `ArtifactAnalyzer` is a pure engine interface (no Android framework types, no networking, no dynamic code loading) so analyzers run both in-process and in the isolated `:analyzer` process.

3. **`engine:orchestrator` — `AnalysisDispatcher`.** A registry-driven dispatcher that performs layered detection (content signature → structural → container characteristic → advisory MIME → advisory extension), fans out to every analyzer supporting the detected type (polyglot support), and recursively dispatches children under the same shared budget with a max nesting depth. Analyzer crashes are caught and recorded as `ParserErrorRecord`s that mark the node incomplete; quota exhaustion marks nodes incomplete while preserving collected evidence; cancellation and timeout propagate as `CancellationException` and are captured in `AnalysisOutcome`.

`AnalysisOrchestrator` wires the dispatcher around the existing pipeline: it merges the dispatcher's artifact tree and analyzer metadata into the final report and raises the report schema version to `1.1.0`.

## Consequences

### Positive

1. **Extensible**: each new format is a new `ArtifactAnalyzer` registered with the dispatcher; no pipeline rewrite.
2. **One shared budget**: hostile nested archives cannot obtain fresh allocation headroom per container.
3. **Honest partial analysis**: parser failures, quota hits, timeouts, and cancellation produce incomplete nodes with `ParserErrorRecord`s, never false-clean results.
4. **Attributable findings**: every node lists the analyzers (id + version) that inspected it.
5. **Layered detection**: magic/structural signals outrank advisory MIME/extension, and mismatches are surfaced.
6. **Deterministic**: artifact ids are the only random element; ordering is stable by analyzer registry order.
7. **No new permissions/exported components/networking**: framework is pure engine code.

### Negative

1. **Report size grows**: artifact trees add fields to the canonical report; must be bounded by report-size budget and truncation rules.
2. **Duplication risk**: legacy pipeline sections (containers/files/archive) coexist with the new artifact tree during migration.
3. **Migration effort**: golden reports and fixtures must be regenerated for schema `1.1.0`.

## Security Impact

**Positive**: shared budgets, cooperative cancellation, wall-clock deadlines, per-analyzer crash isolation, and layered content-first detection reduce parser-exploitation and resource-exhaustion risk. A parser failure marks the artifact incomplete instead of clean, satisfying the core "no false-clean" invariant.

**Negative**: any new analyzer is a new attack surface; each must follow the same discipline (bounded reads, fuzzing, quotas) before landing.

## Play Policy Impact

**None.** No new permissions, exported components, background work, or network access are introduced.

## Alternatives Considered

1. **Refactor the entire pipeline onto the dispatcher immediately**: Rejected. Too large a change for one increment and risks regressing the verified APK/DEX/archive path. The dispatcher runs alongside and enriches the report first; legacy stages migrate later.
2. **Per-container budget reset**: Rejected. A nested archive would gain fresh allocation headroom, violating the exhaustion defenses.
3. **Plugin-based analyzers loaded at runtime**: Rejected. Dynamic code loading is prohibited; analyzers are compiled-in and versioned.
4. **Single-process-only framework**: Rejected. The interfaces are framework-type-free so the isolated `:analyzer` process can host them.

## Migration/Rollback

- Report schema moves additively `1.0.0 → 1.1.0` (`artifacts`, `analyzerInfo` added with defaults). Old readers ignore the new fields; a downgrade drops them.
- Golden reports must be regenerated once; report-schema release tagged separately.
- The dispatcher path can be disabled by reverting the orchestrator merge without touching the legacy pipeline.
- New analyzers must each add analyzer metadata, quota tests, and fuzzing before being enabled.
