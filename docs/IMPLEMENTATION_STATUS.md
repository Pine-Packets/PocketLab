# PocketLab Implementation Status

**Last Updated:** 2026-08-07 (Phase 14 generic artifact framework)  
**Project:** PocketLab - Android Static Malware Analysis App  
**Organization:** Pine and Packets LLC

## Current Phase: Phase 10 (Password-Protected Archives and Package Sets) - IN PROGRESS

**Actual Progress:** The codebase is at Phase 10 of 13. Recent work (1) fixed the pipeline gap where raw APKs and case archives containing APKs were only treated as generic ZIP containers, so APK analysis stages (`apk_structure`, `apk`, `signing`, `dex`, `code_analysis`) never ran for them; (2) added `SplitApkSetBuilder` to bundle multiple staged APKs into a synthetic APKS container; (3) wired multi-file intake (`ACTION_SEND_MULTIPLE`, multi-file picker, URI-list navigation); (4) implemented the intake staging coordinator that stages URIs into a private case workspace and bundles multiple APKs into a case before analysis; (5) added case ZIP notes/text inventory (WF-004) with bounded text-entry scanning and IOC extraction with container provenance.

## Overall Progress

### Phase 0: Foundation and Architecture ✅ COMPLETE
- ✅ Repository initialized with Git
- ✅ GitHub repository created (Pine-Packets/PocketLab)
- ✅ Multi-module Gradle project structure
- ✅ Build logic and convention plugins
- ✅ Core dependencies configured (Compose, Room, Coroutines, etc.)
- ✅ Documentation structure established
- ✅ ADRs created (local-first, isolated process, canonical report)

### Phase 1: Application Foundation ✅ COMPLETE
- ✅ Room database with CaseDao and CaseRepository
- ✅ EncryptionManager with Android Keystore
- ✅ EncryptedReportStorage for secure report persistence
- ✅ CaseWorkspace and FileStager for secure file handling
- ✅ Compose UI screens (Home, Cases, Settings, About, Onboarding, Intake, Analysis, Report)
- ✅ Navigation with NavHost and bottom navigation
- ✅ Manifest policy tests (no dangerous permissions, backup disabled)
- ✅ Forbidden API tests (no execution APIs)
- ✅ Demo case with static fixture data (DemoFixtureGenerator)
- ✅ Settings screen with Privacy & Storage, Analysis, and Appearance sections
- ✅ SettingsRepository with DataStore preferences
- ✅ CaseCleanupWorker for retention-based case lifecycle management
- ✅ Settings wired to analysis pipeline via AnalysisConfig

### Phase 2: File Intake and Type Detection ✅ MOSTLY COMPLETE
- ✅ FileTypeDetector with magic byte detection
- ✅ Support for ZIP, APK, DEX, ELF, PE, PDF, OLE, GZIP, 7z, RAR
- ✅ Extension and MIME type validation
- ✅ Mismatch detection between magic bytes and extensions
- ✅ FileStager with bounded copy and hashing (SHA-256, SHA-1, MD5)
- ✅ File picker uses narrow MIME types (APK, ZIP, DEX)
- ✅ ACTION_SEND share target activity created
- ✅ ShareIntakeActivity with URI validation
- ✅ Available storage check before staging (200MB minimum)
- ✅ DeviceCapabilityProfiler with memory/storage/ABI profiling
- ✅ Device capability profiling wired to analysis limits via AnalysisClient
- ⚠️ Intake confirmation screen needs enhancement

### Phase 3: Archive Analysis ✅ MOSTLY COMPLETE
- ✅ ArchiveAnalyzer using Apache Commons Compress
- ✅ Path traversal detection and prevention
- ✅ Quota enforcement (entry count, size limits)
- ✅ Suspicious path detection
- ✅ Path normalization
- ✅ Checked arithmetic for size summations
- ✅ Encryption detection from ZIP entry flags
- ✅ Password attempt helper with "infected" shortcut
- ✅ Central directory validation (EOCD, Zip64) via ZipValidator
- ✅ Runtime decompression counters via DecompressionCounter
- ✅ Nested archive identification
- ✅ Selective extraction with randomized names via SelectiveExtractor
- ✅ Archive report section model (ArchiveReportSection)
- ✅ Password-protected ZIP support with encryption detection
- ❌ Archive report section in UI

### Phase 4: APK Analysis ✅ MOSTLY COMPLETE
- ✅ BinaryXmlParser for AXML format
- ✅ ApkAnalyzer using binary XML parser
- ✅ Permission extraction with protection levels
- ✅ Component analysis (activities, services, receivers, providers)
- ✅ Exported component detection
- ✅ Debuggable, backup, cleartext traffic flags
- ✅ resources.arsc parsing via ResourceTableParser (application labels resolved)
- ✅ Permission knowledge base with 50+ permissions
- ✅ Intent filter / deep link analysis
- ✅ APK file inventory with type detection
- ✅ APK structural validation via ApkStructureValidator
- ✅ APK structural validation integrated into pipeline

### Phase 5: Signing Analysis ✅ MOSTLY COMPLETE
- ✅ SigningAnalyzer for certificate extraction
- ✅ Signature scheme detection (v1, basic v2/v3)
- ✅ Certificate fingerprint calculation
- ✅ Self-signed certificate detection
- ✅ APK Signing Block detection
- ✅ Debug certificate detection
- ✅ AOSP apksig integration spike (ApksigVerifier wrapper)
- ✅ apksig confirmed working on Android runtime
- ✅ ApksigVerifier wired into AnalysisPipeline for cryptographic verification
- ✅ Signing lineage parsing implemented
- ✅ Signing lineage displayed in Analyst Report UI

### Phase 6: DEX Analysis ✅ MOSTLY COMPLETE
- ✅ DexAnalyzer for DEX file parsing
- ✅ Header validation and version detection
- ✅ Class, method, and string count extraction
- ✅ Quota enforcement for large DEX files
- ✅ Endian tag validation
- ✅ String table extraction with MUTF-8 decoding
- ✅ Method/field/class ID table parsing
- ✅ Code item parsing with instruction lists
- ✅ Basic Dalvik instruction decoding
- ✅ API reference indexes
- ✅ API capability map with 80+ Android API mappings
- ✅ Reflection and dynamic-loading detection via ReflectionDetector
- ✅ Bounded constant propagation for string reconstruction
- ❌ Full constant propagation across all opcodes
- ❌ Interprocedural analysis

### Phase 7: IOC Extraction and Rules Engine ✅ MOSTLY COMPLETE
- ✅ IocExtractor for URLs, domains, IPs, emails
- ✅ Defanging of indicators
- ✅ IP address classification
- ✅ RulesEngine with manifest rules
- ✅ Dangerous permission detection
- ✅ Exported component risk assessment
- ✅ Declarative rule interpreter (RuleInterpreter)
- ✅ Fact extraction system (FactExtractor)
- ✅ 8 default declarative rules in JSON format
- ✅ Nested condition support in rule interpreter
- ✅ Code analysis stage in pipeline with reflection detection
- ✅ Correlation rules combining manifest and code facts
- ❌ Full interprocedural correlation
- ❌ Taint tracking

### Phase 8: Reporting and Export ✅ COMPLETE
- ✅ ReportSerializer for JSON serialization
- ✅ ReportExporter with JSON, Markdown, HTML, and CSV formats
- ✅ HTML escaping to prevent XSS attacks
- ✅ Markdown escaping for special characters
- ✅ CSV export with formula injection protection
- ✅ AnalysisPipeline orchestrating full analysis workflow
- ✅ Simple Report UI view
- ✅ Analyst Report UI view
- ✅ View mode toggle (Simple/Analyst)
- ✅ Demo fixture generator
- ✅ Report redaction settings (secrets, IOCs, filename)
- ✅ Encrypted analyst notes merged at render time
- ✅ Golden report snapshots
- ⚠️ PDF export not implemented (deferred)

### Phase 9: Process Isolation ✅ IMPLEMENTED (not tested on device)
- ✅ AIDL interface (IAnalyzerService, IAnalyzerCallback) for IPC
- ✅ AnalyzerService declared with isolatedProcess="true" and process=":analyzer"
- ✅ Read-only ParcelFileDescriptor passing for sample input
- ✅ Engine-side budget enforcement from IsolatedAnalysisRequest
- ✅ AnalysisClient main-process wrapper implementing AnalysisEngine
- ✅ ProcessCrashDetector with state tracking and recovery limits
- ✅ CheckpointManager for crash recovery persistence
- ⚠️ Not tested on actual device

### Phase 10: Password-Protected Archives and Package Sets ✅ IN PROGRESS
- ✅ Password-protected ZIP support with encryption detection
- ✅ Apache Commons Compress integration for encrypted archives
- ✅ Password handling with fallback for non-encrypted archives
- ✅ Nested archive recursion under global budgets
- ✅ Global quota tracking across nested archives (entries, bytes, depth)
- ✅ Depth limit enforcement (MAX_NESTING_DEPTH = 2)
- ✅ Directory entries are not recursively analyzed
- ✅ APKS format detection and analysis
- ✅ XAPK format detection and analysis
- ✅ Manifest parsing for XAPK files
- ✅ Multi-APK analysis with result merging
- ✅ Signing certificate consistency checking across splits
- ✅ Permission and component merging from multiple APKs
- ✅ PackageSetAnalyzer wired into AnalysisPipeline (packageset stage)
- ✅ DEX analysis for APKs contained in package sets
- ✅ Signing verification for base APK in package sets
- ✅ Archive correlation report: max ratio, nested depth, unsupported entries, detected child types
- ✅ Pipeline handles raw APK files (detected as ZIP by magic) by running APK analysis stages on the container itself
- ✅ Pipeline extracts and analyzes APK entries inside case archives (ZIP containing .apk)
- ✅ Plain archives without APK content skip APK stages with a clear limitation
- ✅ SplitApkSetBuilder creates a synthetic APKS container from multiple staged APK files (base.apk + split_N.apk, STORED entries, BundleConfig.pb marker)
- ✅ ACTION_SEND_MULTIPLE share target: manifest intent filters, URI list validation (scheme, MIME, item-count cap), confirmation screen, multi-URI navigation
- ✅ Multi-file document picker (OpenMultipleDocuments) with list-based navigation route
- ✅ IntakeStagingCoordinator stages URIs into private case workspace; single file becomes original.bin, multiple files are bundled into a synthetic APKS (original.bin) via SplitApkSetBuilder
- ✅ IntakeViewModel drives multi-file intake state (load, confirm, staging, ready, error)
- ✅ CaseZipTextScanner inventories text/notes entries in case archives with bounded per-entry and total scan quotas; extracts indicators with container/entry provenance (WF-004)
- ✅ Archive report section records textEntryInventory with extracted indicators

### Phase 11-12: IN PROGRESS
- ✅ Native ELF analysis expansion: `ElfAnalyzer` rewritten to a bounded, read-only ELF parser that extracts sections (with names via `.shstrtab`), program headers, `.symtab`/`.dynsym` symbols, `DT_NEEDED` dynamic dependencies, and `Java_*` JNI exports
- ✅ Executable-writable segment detection (PT_LOAD with both PF_X and PF_W) as a native capability fact
- ✅ Strip-status detection (no `.symtab` symbols), architecture/ABI mapping (ARM/armeabi-v7a, AArch64/arm64-v8a, x86, x86_64), endianness, entry point, and size quota
- ✅ All counts capped (sections/program headers, symbols, dynamic entries, JNI exports, string length) with quota rejection for oversized files
- ✅ 5 additional ElfAnalyzer tests using a synthetic `Elf64Builder` fixture (dynamic dependencies, JNI exports, symbol extraction, executable-writable segment, R-X not flagged, truncated section table) plus a size-quota test
- ❌ Play Store release preparation

## Critical Gaps Summary

1. **Pipeline integration tests need enhancement** - Tests exist but use minimal fixtures

### Addressed This Session: Comprehensive Fuzzing

A deterministic, seed-fixed `FuzzHarness` (in `core:testing`) now exercises the hostile
parser boundary across the file-type detector, binary XML parser, resources.arsc parser,
DEX parser, ELF parser, the archive preflight/enumeration path, and the report renderers.
It runs each probe with a termination budget on a dedicated worker, classifies fatal
`Error`s (`OOM`, `StackOverflowError`, `NegativeArraySizeException`, `AssertionError`),
and re-runs pure probes to check determinism. The parser changes below were made after
the harness surfaced allocation risks in the hostile count/length fields of the binary
XML and resource-table parsers.

## Test Coverage

### Unit Tests
- ✅ AnalysisUtilsTest (overflow checking, safe analysis)
- ✅ HashUtilsTest (SHA-256, SHA-1, MD5)
- ✅ FileTypeDetectorTest (magic byte detection)
- ✅ RulesEngineTest (basic rules)
- ✅ IocExtractorTest (URL, domain, IP, email extraction)
- ✅ ReportExporterTest (JSON, Markdown, HTML, CSV export with escaping)
- ✅ ArchiveAnalyzerTest (path traversal, quota enforcement, decompression)
- ✅ SelectiveExtractorTest (selective extraction, randomized names, path traversal prevention)
- ✅ ManifestPolicyTest (permission validation, service isolation)
- ✅ ForbiddenApiTest (execution API detection)
- ✅ IsolationBoundaryTest (isolation boundary security)
- ✅ ProcessCrashDetectorTest (crash detection and recovery)
- ✅ CheckpointManagerTest (checkpoint persistence)
- ✅ IsolatedAnalysisRequestTest (request serialization and budgets)
- ✅ ElfAnalyzerTest (ELF header parsing, sections, program headers, symbols, dynamic dependencies, JNI exports, exec+write segment, strip status, size quota)
- ✅ BinaryXmlParserTest (binary XML parsing)
- ✅ ApkAnalyzerTest (APK analysis)
- ✅ ApksigVerifierTest (apksig integration verification)
- ✅ DexAnalyzerTest (DEX parsing)
- ✅ ConstantPropagatorTest (string reconstruction)
- ✅ RulesEngineTest with correlation rule tests
- ✅ ReportRedactorTest (redaction settings)
- ✅ GoldenReportTest (report snapshot regression)
- ✅ AnalysisConfigTest (config creation, device profile adaptation)
- ✅ AnalysisPipelineIntegrationTest (end-to-end pipeline with config, archive section, stage results, package set analysis)
- ✅ SplitApkSetBuilderTest (synthetic APKS construction, base/split naming, STORED preservation, size/count bounds, cleanup on failure)
- ✅ Pipeline regression tests: raw APK runs APK stages, case archive with contained APK extracts and analyzes, plain archive skips APK stages, split APK set produces package set report
- ✅ IntakeStagingCoordinatorTest (single-file staging, multi-file APKS bundling with scratch cleanup, empty input, staging failure cleanup, bundle failure cleanup)
- ✅ CaseZipTextScannerTest (indicator extraction with container provenance, binary guard, non-text extension skip, per-entry scan cap, empty result)
- ✅ Pipeline test: case archive notes inventory extracts indicators with provenance
- ✅ CaseCleanupWorkerTest (retention-based cleanup, scratch data cleanup)
- ✅ NestedArchiveTest (nested depth reporting, max compression ratio, unsupported entries)
- ✅ FileTypeDetectorFuzzTest (magic/extension/MIME hostile fuzzing)
- ✅ BinaryXmlParserFuzzTest + ResourceTableParserFuzzTest (AXML chunks, hostile counts/lengths)
- ✅ DexAnalyzerFuzzTest (random DEX with temp-file staging, excessive-count quota)
- ✅ ElfAnalyzerFuzzTest (random ELF64, hostile section count quota)
- ✅ ReportRendererFuzzTest (JSON/Markdown/HTML/CSV hostile string rendering, determinism)
- ✅ ArchiveAnalyzerFuzzTest (random ZIP bytes plus byte-flip/truncation mutants of a valid ZIP through ZipValidator preflight and enumeration)

### Test Results
- **Total Tests:** 525 test cases
- **Status:** All passing
- **Coverage:** Core functionality, security controls, parsers, redaction, correlation rules, golden reports, selective extraction, case cleanup, analysis config, pipeline integration, apksig verification, package sets, archive correlation, split APK set construction, raw-APK and case-archive pipeline dispatch, multi-file intake staging orchestration, case ZIP notes/text inventory, analysis view-model state orchestration, property-based archive path safety, property-based IOC extraction invariants, native ELF symbol/dependency/JNI/segment analysis, comprehensive deterministic fuzzing of file-type, binary XML, resource table, DEX, ELF, and report renderer boundaries
- **Missing:** Archive UI tests, signed APK fixtures for apksig

## Build Status

### Compilation
- ✅ All modules compile successfully
- ✅ No compilation errors
- ✅ Only deprecation warnings remain

### Tests
- ✅ All unit tests pass
- ✅ Build successful

## Known Limitations

### Current Limitations
1. **Archive Decryption**: Detection only, no actual decryption
2. **Code Analysis**: Basic instruction decoding, no full capability extraction
3. **Test Fixtures**: Placeholder binary XML data in tests

### Future Work
- Add comprehensive fuzzing tests
- Play Store release preparation
- Advanced code analysis (taint tracking, data flow)

## Security Features

### Implemented
1. **No Network Access**: INTERNET permission not declared
2. **No Dangerous Permissions**: No storage, camera, location, etc.
3. **Backup Disabled**: android:allowBackup="false"
4. **Encrypted Storage**: Reports encrypted with Android Keystore
5. **Path Traversal Prevention**: Archive entries validated and normalized
6. **Quota Enforcement**: Limits on file size, entry count, compression ratio
7. **XSS Prevention**: HTML and Markdown escaping in exports
8. **Process Isolation**: Analyzer runs in isolated process
9. **Checked Arithmetic**: Archive size calculations use overflow-safe math
10. **Honest Reporting**: Signature verification and encryption detection are honest
11. **ZIP Bomb Protection**: Runtime decompression counters and ratio checks
12. **Archive Validation**: Central directory and EOCD validation
13. **Selective Extraction**: Only specified entries extracted with randomized names
14. **Storage Pre-check**: Available storage validated before staging (200MB minimum)
15. **Retention Cleanup**: Automatic case cleanup based on retention policy
16. **Device Profiling**: Memory/storage/ABI profiling for adaptive limits
17. **Settings-Driven Analysis**: Configurable analysis profile, IOC extraction, deep DEX analysis
18. **Dynamic Limits**: Analysis limits adapt to device memory class and capabilities
19. **Stage Tracking**: Pipeline tracks stage timing, completeness, and confidence
20. **Archive Report Section**: Structured archive analysis results in canonical report
21. **Apksig Integration**: AOSP apksig library for cryptographic signature verification (v1/v2/v3 schemes)
22. **Signing Lineage**: Detection and display of certificate rotation/lineage information
23. **Password-Protected ZIP Support**: Encryption detection and password handling for encrypted archives
24. **Nested Archive Handling**: Recursive analysis with global quota tracking and depth limits to prevent resource exhaustion
25. **APKS/XAPK Package Set Analysis**: Detection and analysis of multi-APK package formats with result merging and consistency checking
26. **Package Set Pipeline Integration**: End-to-end APKS/XAPK analysis including contained DEX and base-APK signature verification
27. **Archive Correlation Metrics**: Report records max compression ratio, nested depth, unsupported entries, and detected child types
28. **Raw-APK and Case-Archive Dispatch**: Pipeline runs full APK analysis stages on raw APKs and on APK entries extracted from case archives, with provenance limitations
29. **Split APK Set Bundling**: SplitApkSetBuilder combines staged APKs into a bounded synthetic APKS container
30. **Multi-File Share Intake**: ACTION_SEND_MULTIPLE validated and confirmed before any staging; never auto-analyzes
31. **Multi-File Staging Coordinator**: Stages selected URIs into the private case workspace; single file becomes the case input, multiple files are bundled into a synthetic APKS before analysis
32. **Case ZIP Notes/Text Inventory**: Bounded streaming scan of text entries in case archives with indicator extraction and container/entry provenance (WF-004)
33. **Real Analysis Progress UI**: AnalysisScreen now runs the real analysis pipeline on staged cases through an AnalysisViewModel, streaming stage progress from the AnalysisOrchestrator, persisting the canonical report via EncryptedReportStorage, and updating the case index; includes a cancel path and error surfacing
34. **Centralized Archive Path Normalizer**: ArchivePathNormalizer centralizes traversal/absolute/drive/NUL rejection and normalization, shared by the archive analyzer, with rejection of empty and drive-prefixed normalized paths
35. **Property-Based Security Tests**: Seed-fixed kotest-property suites over arbitrary hostile inputs — archive paths never escape the workspace root or resolve outside it on disk, normalization is idempotent, rejected paths are suspicious or degenerate; IOC extraction is deterministic, deduplicated, substring-faithful, refang round-trip safe, scheme-defanged, and junk-only text never reports indicators
36. **Full Archive Report Rendering**: Analyst archive section now displays observed expanded size, max observed compression ratio, nested depth, unsupported entries, skipped entries with reasons, quota events, and the WF-004 case notes/text-entry inventory with scanned bytes, charset, and extracted indicator previews
37. **Native ELF Analysis**: Bounded read-only ELF parser extracting sections, program headers, symbols, dynamic dependencies, and JNI exports with executable-writable segment and strip-status detection; synthetic ELF64 fixture builder for parser tests
38. **Deterministic Parser Fuzzing**: Seed-fixed `FuzzHarness` in `core:testing` drives a bounded corpus into the file-type detector, binary XML, resource-table, DEX, ELF, and report renderer boundaries, asserting no fatal `Error` escapes, no non-termination, and no non-determinism; the binary XML and resource-table parsers now bound hostile count/length allocations
39. **Bounded Hostile Allocation in APK Parsers**: `BinaryXmlParser` and `ResourceTableParser` reject out-of-range string-pool count fields and cap UTF-8/UTF-16 string lengths against `AnalysisLimits` before allocating, closing potential OOM / `NegativeArraySizeException` vectors from hostile manifests and `resources.arsc`

## Repository

- **GitHub:** https://github.com/Pine-Packets/PocketLab
- **Branch:** main
- **Total Commits:** 25

## Next Steps

### Immediate (Next)
1. Begin Stage 15.3 (Legacy OLE/CFB): bounded compound-file directory parser for `.doc/.xls/.ppt/.rtf` — streams, VBA, embedded objects, ActiveX, external links, metadata, indicators; treat as hostile binary attack surface with extensive malformed fixtures + fuzz
2. Expand physical test corpus and instrumentation coverage (process isolation, cancellation, export surface)

### Short-term
1. Continue Phase 15 format stages (15.1 → 15.17), one analyzer per stage in `analyzerRegistry()`
2. Expand physical test corpus and instrumentation coverage (process isolation, cancellation, export surface)
3. Play Store release preparation

### Long-term
1. Advanced code analysis (streaming analysis, data flow)
2. Machine learning for malware classification

## Conclusion

PocketLab has made significant progress. The most recent session added:
- A deterministic, seed-fixed `FuzzHarness` in `core:testing` that drives hostile byte
  corpora (structured magic prefixes + random tails across many sizes) into the engine's
  hostile-input boundaries with a termination budget, fatal-`Error` classification,
  and a pure-probe determinism re-run. It is deliberately seed-fixed so any failure is
  reproducible, matching the "safe parser failure" definition.
- Fuzz suites for the file-type detector, binary XML parser, resources.arsc parser,
  DEX parser, ELF parser, archive preflight/enumeration, and the JSON/Markdown/HTML/CSV
  report renderers (13 new tests).
- A deterministic fuzz corpus for the archive path combining pure-random bytes with
  byte-flip and truncation mutants of a structurally valid ZIP, driving ZipValidator
  preflight and ArchiveAnalyzer enumeration.
- Bounded allocation in `BinaryXmlParser` and `ResourceTableParser`: previously the
  string-pool count and per-string length fields were used to size `IntArray`/`CharArray`/`ByteArray`
  allocations directly, permitting hostile headers to trigger `OutOfMemoryError` or
  `NegativeArraySizeException`. These are now bounded against `AnalysisLimits.MAX_STRING_COUNT`
  and `MAX_STRING_LENGTH`, matching the plan §32 rule "Do not allocate using hostile length
  fields before validation".
- All engine modules that exercise hostile parsers now depend on `:core:testing` for tests.

Total tests: 278, all passing (plus fuzz-driven allocation hardening).

The critical path forward is:
1. Extend fuzzing to the archive metadata path and grow the physical/instrumentation test base
2. Prepare for Play Store release

Estimated time to MVP: 2-3 weeks of full-time development.

## Repository housekeeping

A subsequent session cleaned up the repository and prepared it for public GitHub
presentation:

- Added `README.md` with a project overview, feature set, screenshots (SVG mockups of the
  actual UI in `docs/screens/`), build/install/test instructions, architecture overview,
  tech stack, security & privacy pointers, and license.
- Added `LICENSE` (Creative Commons Attribution-NonCommercial-NoDerivatives 4.0
  International), matching the requested non-commercial, attribution-required posture.
- Removed internal/agent-only artifacts no longer needed by the app:
  `POCKETLAB_ONESHOT_PROMPT.md`, `opencode.json`, the master development plan
  (`docs/ANDROID_STATIC_ANALYSIS_APP_DEVELOPMENT_PLAN.md`), `docs/GAP_ANALYSIS.md`, and
  `docs/SESSION_NOTES_2026-08-05.md`, and cleared their cross-references in `AGENTS.md`,
  `docs/IMPLEMENTATION_STATUS.md`, and `docs/PLAY_STORE_REVIEWER_INSTRUCTIONS.md`.
- Replaced placeholder `@pineandpackets.com` contact addresses (nonexistent domain) in
  `SECURITY.md`, `PRIVACY.md`, `docs/PLAY_STORE_DATA_SAFETY.md`,
  `docs/PLAY_STORE_REVIEWER_INSTRUCTIONS.md`, `docs/PRIVACY_MODEL.md`, and
  `test-corpus/README.md` with the GitHub noreply address
  `310212849+Pine-Packets@users.noreply.github.com`.

Work item: The development-plan document was removed because it was an internal build
specification. Future work should treat `docs/IMPLEMENTATION_STATUS.md`, the ADRs, and the
architectural/security docs as the authoritative reference.

## Format-Expansion Program (Phases 14-15)

A new authoritative expansion spec (`docs/AUTHORITATIVE_EXISTING_MATERIAL.md`) mandates
converting PocketLab from an APK/archive-only analyzer into a general-purpose local-first
static file-analysis platform (PDF, OOXML, OLE, images, email, archives, scripts, PE, ...).
The master development plan was **restored** from git history
(`git checkout 1bb0455~1 -- docs/ANDROID_STATIC_ANALYSIS_APP_DEVELOPMENT_PLAN.md`) because
the expansion task requires maintaining it; `AGENTS.md` was also re-pointed at it.
`docs/FILE_FORMAT_EXPANSION_REVIEW.md` records the gap analysis and phase order.

### Phase 14: Generic Artifact Analysis Framework ✅ IMPLEMENTED
- ✅ `core:model` — `ArtifactNode` (stable id, parent id, relation, original/sanitized name, claimed MIME, detected type/subtype, size, SHA-256, metadata, indicators, findings, facts, children, parser errors, completeness, limitations, analyzer attribution), `AnalyzerInfo`, `AnalyzerUse`, `ArtifactMetadataEntry`, `ParserErrorRecord`
- ✅ Report schema bumped additively `1.0.0 → 1.1.0` (`artifacts`, `analyzerInfo` lists with defaults); golden snapshot regenerated
- ✅ `engine:api` — `CaseBudget` (shared case-level counters with checked arithmetic, overflow-as-rejection, never reset for nested containers), `AnalysisCancellation` (thread-safe cooperative cancel), `AnalysisContext` (budget + cancellation + wall-clock deadline + op accounting), `ArtifactAnalyzer`/`ArtifactRef`/`AnalyzerResult`/`ParsedChild`/`DetectionLayer` (pure engine interfaces, no Android framework types)
- ✅ `engine:orchestrator` — `AnalysisDispatcher` (layered detection: content signature → structural → container → advisory MIME → extension; multi-analyzer fan-out for polyglots; recursive child dispatch under one shared budget with max nesting depth; analyzer-crash → `ParserErrorRecord` marks node incomplete, never false-clean; cancellation/timeout captured in `AnalysisOutcome`)
- ✅ `FileArtifactSource` (budgeted/cancel-safe file reads), `LayeredTypeDetector` (magic-first detection with mismatch flags)
- ✅ `AnalysisOrchestrator` wired: keeps a per-job `AnalysisCancellation`, `cancel(jobId)` now actually cancels, and the dispatcher's artifact tree + analyzer metadata are merged into the final report (schema `1.1.0`)
- ✅ ADR-0004 (`docs/adr/ADR-0004-generic-artifact-analysis-framework.md`)
- ✅ 14 new tests: `AnalysisDispatcherTest` (routing, polyglot fan-out, recursive child dispatch, nesting-depth bound, analyzer crash, cancellation, deadline timeout, mismatch detection, extension fallback), `CaseBudgetTest` (byte caps, overflow rejection, shared-budget non-reset, counter acquire/release)
- ✅ Full regression suite passing (290 tests), lint clean

### Phase 15 (Format Expansion Stages) — Stage 15.1 PDF ✅ IMPLEMENTED
- ✅ New module `:engine:pdf` (`settings.gradle.kts`, `engine/pdf/build.gradle.kts`) — library module, pure engine code (no Android framework types), deps: `core:common`, `core:model`, `engine:api`, coroutines, timber
- ✅ `PdfScanReport` — bounded scan result model (object/stream counts, header, abnormalities, actions, features, encryption/metadata/signature flags, indicators)
- ✅ `PdfScanner` — bounded, read-only, text-oriented scan: max `16 MiB` (`PdfAnalyzer.MAX_SCAN_BYTES`) read via `ArtifactRef.readRange`; detects `%PDF` header, `%%EOF`/startxref, `/JavaScript`/`/JS`, `/OpenAction`, `/Launch`, `/AcroForm`, `/XFA`, `/EmbeddedFiles`/`/Filespec`, annotations, `/RichMedia`, `/URI`/`/GoToR` remote references, `/Encrypt`, `/Metadata`, signatures; extracts bounded URL indicators (defanged). Never decodes or executes JS, never extracts embedded files, never contacts URIs
- ✅ `PdfAnalyzer` — `ArtifactAnalyzer` (id `pdf.analyzer`, v `1.0.0`, supports `PDF`); 7 findings (`PDF-JS-001`, `PDF-OPENACTION-001`, `PDF-LAUNCH-001`, `PDF-XFA-001`, `PDF-EMBEDDED-001`, `PDF-REMOTE-001`), facts, metadata, `incomplete` flag on truncation/parser error (never false-clean), defensive type-mismatch guard
- ✅ `engine:api` — `ArtifactRef` extended with `readNBytes`/`readRange` (bounded, budget-aware reads); orchestrator/dispatcher/test refs implement them
- ✅ `AnalysisOrchestrator` — dead dispatcher removed; `analyzerRegistry()` now registers `PdfAnalyzer()`; `dispatcherRunner` passes it to `AnalysisDispatcher`
- ✅ 8 analyzer tests + 2 fuzz tests (`PdfAnalyzerTest`, `PdfAnalyzerFuzzTest`, `PdfTestFixture`/`ByteArtifactRef`)
- ✅ Pre-existing API-33 `readNBytes` lint errors fixed in `engine:archive` (`CaseZipTextScanner`, `SelectiveExtractor`) and `engine:pipeline` (`AnalysisPipeline`)
- ✅ `:engine:pdf:testDebugUnitTest` green; touched modules lint-clean; full regression suite passing (302 tests)

### Phase 15.2 (Format Expansion Stages) — Stage 15.2 OOXML ✅ IMPLEMENTED
- ✅ New module `:engine:ooxml` (`settings.gradle.kts`, `engine/ooxml/build.gradle.kts`) — library module, pure engine code (no Android framework types), deps: `core:common`, `core:model`, `engine:api`, `engine:ioc`, commons-compress, coroutines, timber
- ✅ `OoxmlScanReport` — bounded scan result model (part count, content-types/macro/ActiveX/embedded-OLE/external-link/custom-XML/signature flags, hyperlink + external targets, indicators, abnormalities, parser errors)
- ✅ `OoxmlScanner` — opens the package through a read-only, bounds-checked `SeekableByteChannel` over `ArtifactRef` (nothing extracted to disk) using commons-compress `ZipFile`; enumerates parts under entry/size/expanded caps; detects `[Content_Types].xml`, `vbaProject.bin`/`vbaData.xml` (macros), `activeX`, `embeddings/`+`oleObject` (embedded OLE), `xl/externalLinks/` (external data links), `customXml/`, `_xmlsignatures/`; reads small `*.rels` parts (bounded UTF-8) to collect external relationship targets (hyperlinks, remote templates) and extract defanged indicators; marks truncated/malformed scans incomplete (never false-clean); honors cancellation on every loop iteration
- ✅ `OoxmlAnalyzer` — `ArtifactAnalyzer` (id `ooxml.analyzer`, v `1.0.0`, supports `OOXML`); findings `OOXML-MACRO-001`, `OOXML-ACTIVEX-001`, `OOXML-EMBEDDED-001`, `OOXML-EXTLINK-001`; facts + metadata; defensive type-mismatch guard
- ✅ `core:model` — added `DetectedType.OOXML`; `LayeredTypeDetector` maps OOXML extensions and treats ZIP-signature + OOXML extension as an OOXML structural signal (ZIP container), with `ZIP_CONTAINER` subtype
- ✅ `AnalysisOrchestrator` — `analyzerRegistry()` now registers `OoxmlAnalyzer()` alongside `PdfAnalyzer()`
- ✅ 8 analyzer tests + 2 fuzz tests (`OoxmlAnalyzerTest`, `OoxmlAnalyzerFuzzTest`, `OoxmlTestFixture`/`ByteArtifactRef`) covering plain-doc no-false-findings, VBA, external links + URL defanging, embedded OLE, custom XML + signatures, plain ZIP (no OOXML flag), malformed/non-ZIP, type mismatch, and randomized hostile corpora
- ✅ `:engine:ooxml:testDebugUnitTest` green (312 total); `:engine:ooxml` + `:engine:orchestrator` lint-clean; full regression suite passing (312 tests)
- ⏭ Next: Stage 15.3 Legacy OLE/CFB. Each stage adds one `ArtifactAnalyzer` to `analyzerRegistry()`.
### Phase 15.3 (Format Expansion Stages) — Stage 15.3 Legacy OLE/CFB ✅ IMPLEMENTED
- ✅ New module `:engine:ole` (`settings.gradle.kts`, `engine/ole/build.gradle.kts`) — library module, pure engine code, deps: `core:common`, `core:model`, `engine:api`, `engine:ioc`, coroutines, timber
- ✅ `OleScanReport` — bounded scan result model (CFB major/minor version, sector size, sector/stream/storage counts, macro/embedded/suspicious stream lists, indicators, abnormalities, parser errors, scan-truncation flag)
- ✅ `OleScanner` — bounded, read-only CFB binary parser: validates magic, byte order, version 3/4, sector shift/geometry; reads a bounded window via `ArtifactRef.readRange`; builds the FAT from the inline DIFAT and DIFAT-sector chain (capped entries/sectors); enumerates directory sectors/entries with chain-loop and out-of-range guards; emits a flat stream inventory; detects VBA macro streams, embedded-OLE streams, and suspicious names; reads a bounded set of small regular streams to extract defanged URL/domain/IP/email indicators via `IocExtractor`; never executes/extracts/opens embedded objects; marks truncated/malformed containers incomplete (never false-clean); honors cancellation on every loop
- ✅ `OleAnalyzer` — `ArtifactAnalyzer` (id `ole.analyzer`, v `1.0.0`, supports `OLE`); findings `OLE-MACRO-001`, `OLE-EMBEDDED-001`, `OLE-REMOTE-001`; facts + metadata; defensive type-mismatch guard; limitations document the mini-stream / property-set scope boundary
- ✅ `AnalysisOrchestrator` — `analyzerRegistry()` now registers `OleAnalyzer()` alongside `PdfAnalyzer()` and `OoxmlAnalyzer()`; dependency added
- ✅ 7+2 tests (`OleAnalyzerTest`, `OleAnalyzerFuzzTest`, `OleTestFixture`/`OleArtifactRef`, minimal structurally valid CFB builder with chained directory sectors) covering plain-doc no-false-findings, VBA macro, embedded-OLE, URL indicators + defanging, non-CFB, truncated header, type mismatch, and randomized hostile corpora
- ✅ `:engine:ole:testDebugUnitTest` green; `:engine:ole` + `:engine:orchestrator` lint-clean; full regression suite passing (525 tests, 0 failures)
- ⏭ Next: Stage 15.4 Images and QR/barcode. Each stage adds one `ArtifactAnalyzer` to `analyzerRegistry()`.


### Immediate (Next)
1. Stage 15.4 Images and QR/barcode: dedicated modules for JPEG/PNG/GIF/WebP/HEIF/AVIF/BMP/TIFF/SVG etc. + local QR/barcode decode, each bound and never rendering hostile SVG/HTML
2. Expand physical test corpus and instrumentation coverage (process isolation, cancellation, export surface)

