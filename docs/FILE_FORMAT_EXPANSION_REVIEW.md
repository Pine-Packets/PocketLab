# PocketLab File-Format Expansion Review

**Status:** Draft review — prerequisite for Stage 1 implementation
**Date:** 2026-08-07
**Author:** PocketLab engineering (autonomous agent)
**Purpose:** Assess whether the existing architecture can safely support a large, heterogeneous set of file formats, containers, nested artifacts, and cross-artifact correlation before any new parsers are written.

---

## 1. Executive Summary

PocketLab's current engine is **sound but narrow**: its safety model (offline, isolated process,
hostile-input handling, bounded archives) is strong, but its analysis pipeline is **hard-wired to
the three Android/APK-centric cases** — ZIP/APK, raw DEX, and "everything else (nothing)". Expanding
into a general static file-analysis platform requires a **common artifact framework** and a
**content-driven dispatcher** before individual format parsers will be safe or beneficial.

Three concrete facts drive this conclusion:

1. `AnalysisPipeline.analyze()` (`engine/pipeline/.../AnalysisPipeline.kt:42`) dispatches only on
   `DetectedType.ZIP/APK` and `DetectedType.DEX`. PDF, OLE, 7z, RAR, GZIP, ELF, PE, TEXT, SCRIPT
   are detected by `FileTypeDetector` but then receive **no parser** — the report is emitted with an
   empty container/file tree and a single `file_type` stage.
2. Report-level **`containers`, `files`, and `facts` are always emitted as empty lists** (pipeline
   around `:887/:889/:894`). Archive inventory surfaces only through the single `archive` section;
   there is no generic artifact tree.
3. **Budgets are unevenly enforced.** Byte/entry/ratio/depth limits are enforced inside the ZIP
   analyzer and the isolated-service copy. But `maxStringCount`/`maxIocCount`/`maxFindingCount`/
   `maxReportSizeBytes`/`maxAnalysisDurationMs` are **carried but never enforced**, and
   `extractStrings` performs an unbounded `file.readBytes()`.

These gaps must be fixed in a shared, versioned artifact/budget layer before any wide format
expansion, otherwise each new parser would either duplicate quota logic or introduce an unsafe
unbounded read.

---

## 2. Current Architecture Assessment

### 2.1 What is already strong (reuse it)

| Component | Strengths |
|---|---|
| Offline / no-network invariant | `INTERNET` not declared; ADR-0001 |
| Isolated analysis process | `AnalyzerService` `isolatedProcess="true"`, `exported=false`, read-only FD; ADR-0002 |
| Hostile-input posture | content garbage treated hostile; SAF intake; staging with hashing |
| Bound ZIP/APK parsing | entry-count, expanded-byte, ratio, single-entry, nesting-depth, path-normalization limits |
| Path safety | `ArchivePathNormalizer` + property tests; traversal/absolute/drive/NUL rejection |
| Deterministic reporting | versioned canonical JSON (`AnalysisReport`), golden snapshots, escaping in all exporters |
| Declarative rules | `RuleInterpreter`/`FactExtractor`, dated `default-rules.json` |
| Test/fuzz base | 278 passing tests incl. seed-fixed `FuzzHarness`, kotest-property suites |
| Case lifecycle | Room index, retention, encryption, backup exclusion |

### 2.2 Gaps preventing safe expansion

1. **Single-purpose dispatch.** The analyzer registry is a hand-ordered `if/else` on one enum.
   There is no registry of `(detected enum → analyzer)` , no dynamic dispatch, no multi-analyzer
   fan-out, no generic child emission.
2. **No artifact abstraction.** No `artifactId / parentId / detectedType / subtype / children /
   metadata / integrity / analyzer version` model. `ContainerInfo`/`FileInfo` exist but are not
   wired to real output.
3. **No shared case-level budget object.** Nested analysis uses an in-`ArchiveAnalyzer` quota; there
   is no common budget passed through every reader/parser so string/IOC/finding/evidence/report
   counts are not globally capped.
4. **Cancellation & cooperation.** `AnalysisOrchestrator.cancel()` is a stub; the pipeline never calls
   `ensureActive()`/`coroutineContext.isActive`. Long-running new parsers could not be interrupted.
5. **Unbounded reads in helpers.** `extractStrings` reads the entire file; new text-based parsers must
   not replicate this.
6. **Timeout enforcement incomplete.** The isolated service checks wall time only at completion; the
   in-process orchestrator path does not enforce `maxAnalysisDurationMs`.
7. **Format versioning absent.** `engineVersion`/`reportSchemaVersion` are hardcoded strings; no per-analyzer
   version is recorded, so a changed parser cannot be attributed to findings.
8. **Detection is signature-centric but dispatch is not layered.** `FileTypeDetector` returns a single
   `magicType` that the pipeline trusts, with no structural confirmation for new families, and polyglots
   (file that is both a valid ZIP and a valid PDF preamble) are not run through multiple safe analyzers.
9. **`facts` and `containers`/`files` never populated** — correlation and artifact tree reporting cannot
   proceed until the common emitter exists.
10. **No cross-arrival correlation** — no case-level join by domain/hash between artifacts.

---

## 3. Required Refactors (before Stage 1)

Proposed in dependency order. Each is data-model + engine-common work, orthogonal to format specifics,
so it must land first.

1. **Generic `Artifact`/`ArtifactNode` model** in `core:model`
   - stable IDs, `parentId`, container relation, source/display/claimed/detected/subtype, size,
     hashes (lazy), metadata map, indicators, findings, child tree, parser errors, completeness,
     limitations, `analyzerId`+`analyzerVersion`.
2. **`AnalysisContext`** in `engine:api`
   - holds the **case-level shared budget** and read-side census; exposes:
     `withBudget(bytes)`, `reserveStrings/indings/indicators(n)`, `checkCancelled()`, `emitArtifact`,
     `emitFinding`, `emitIndicator`, `emitParserError`, `workspace` (private temp dir), safe bounded
     reader.
3. **Shared budget accounting** — lift the quota into a common object (`AnalysisBudget`) enforced by
   `AnalysisContext` and *also independently re-checked* by the isolated-service copy so the
   two-process path cannot be bypassed.
4. **Dispatcher/registry** — `AnalysisDispatcher` maps `DetectedType` + structural result to a set of
   analyzers; supports layered detection (magic+structure+container+MIME+extension advisory), polyglot
   multi-analysis, and child recursive dispatch.
5. **Cooperative plumbing** — pass `coroutineScope`/`isActive` and check `ensureActive()`; connect
   `AnalysisOrchestrator.cancel()` to a job registry and to the flow.
6. **Versioning** — introduce `AnalyzerVersion` and record in each finding/decode; introduce an actual
   `ReportSchemaVersion` constant.
7. **Bound text/XML/JSON readers** — bounded `readText(limit)`, `readElements(limit)` reusable helpers in
   `core:io`, no unbounded `readBytes()`.
8. **Report populator** — emit `containers`/`files`/`facts`/`artifactTree` from the framework so existing
   exporters and the new tree section can consume them.

---

## 3. Current Format Support

A type is "supported" only if it reaches a Deep parser producing evidence and errors, no exception to
false-clean. Magic detection alone is **not** support.

| Format | Detected | Analyzed | Notes |
|---|---|---|---|
| ZIP | ✅ | ✅ | Robust quota/path/nesting/encryption/PWD |
| APK | ✅ (structural after ZIP) | ✅ | Manifest/resources/signing/DEX |
| DEX | ✅ | ✅ | Header/classes/instructions/reflection |
| APKS / XAPK | ✅ (ext) | ✅ | Package-set merge |
| ELF/SO | ✅ magic | ❌ (pipeline) | (ELF parser exists in engine:native but not dispatched) |
| GZIP / 7z / RAR / ZIPX | ✅ magic | ❌ | Detected, not analyzed by pipeline |
| PDF | ✅ magic | ❌ | **Stage 1 target** |
| OLE (doc/xls/ppt) | ✅ magic | ❌ | **Stage 3 target** |
| PE (exe/dll) | ✅ magic | ❌ | **Stage 11 target** |
| TEXT / SCRIPT | ⚠️ ext | ❌ | **Stage 7/10 target** |

---

## 4. Proposed Format Families and Phase Order

Planned extension stages (from `docs/AUTHORITATIVE_EXISTING_MATERIAL.md`), costed by risk/complexity/security-value:

| Order | Family | Extensions | Primary risk | Reusable infra | Suggested sequencing note |
|---|---|---|---|---|---|
| Frame | Generic framework | — | design | — | **Do first** |
| 1 | PDF | `.pdf` | xref/object streams, encryption, JS | own bounded tokens | medium — high value, moderate risk |
| 2 | OOXML | `.docx/.xlsx/.pptx/.docm/...` | ZIP+XML macros | hardened ZIP + XML reader | lower risk (reuses ZIP) |
| 3 | Legacy OLE/CFB | `.doc/.xls/.ppt/.rtf` | compound-file dir | custom bytes | high risk; needs CFB parser |
| 4 | Images + QR | `.jpg/.png/.gif/.webp/.svg/...` | dimensions, EXIF, QR decode | custom bytes + XML | medium risk; QR lib review |
| 5 | Email | `.eml/.msg/.tnef` | nested MIME | text + ZIP | **requires content pipelining** |
| 6 | Calendar/Contact | `.ics/.vcf` | text, folding, properties | text | low risk — safe starting point |
| 7 | HTML/XML/struct | `.html/.xml/.json/.txt/.csv` | XXE, injection | bounded text | lower risk — safe starting point |
| 8 | OpenDocument | `.odt/.ods/.odp` | XML | ZIP+XML | lower risk |
| 9 | Archives (ext) | `.7z/.rar/.tar/.gz/.jar` | decompression bombs | archive | moderate |
| 10 | Scripts | `.ps1/.js/.py/.php/...` | detection, encoding | text | **need textual capability rules** |
| 11 | PE + installers | `.exe/.dll/.msi/.lnk` | rich parsing | bytes | high risk |
| 12 | ELF/Mach-O/IPA | ELF, `.so`, `.ipa` | ELF reuse | existing ELF + ZIP | ELF done; expand |
| 13 | OneNote | `.one/.onepkg` | CFB-like | bytes | research first |
| 14 | E-books | `.epub/.mobi` | | ZIP/HTML | lower risk (reuse) |
| 15 | Media | `.mp4/.mp3/...` | metadata | bytes | low-medium |
| 16 | Fonts | `.ttf/.otf/.woff` | binary | bytes | **defer** unless value justifies |

**Suggested execution order** for this session, consistent with risk: do **Stage 1 (PDF)** because it is
the highest explicit priority and exercises the generic framework; then reapply the framework quickly to
**low-risk families** (Stage 6 text, Stage 7 HTML/JSON/XML) to validate reuse; then container-driven
families (Stage 2 OOXML, Stage 5 email) which reuse ZIP/text; then medium-risk image/barcode; then binary
families (PE, OLE) last. Every stage is gated on the framework being complete.

### Stage status

| Stage | Family | Status | Notes |
|---:|---|---|---|
| Frame | Generic framework | ✅ implemented | Phase 14; ADR-0004 |
| 1 | PDF | ✅ implemented | `:engine:pdf` (`PdfScanner`/`PdfAnalyzer`, id `pdf.analyzer` v1.0.0); bounded 16 MiB read-only scan; JS/OpenAction/Launch/XFA/embedded/remote/encryption/signature detection; defanged URL indicators; findings PDF-*-001; fuzz tests (`PdfAnalyzerFuzzTest`) |
| 2 | OOXML | ✅ implemented | `:engine:ooxml` (`OoxmlScanner`/`OoxmlAnalyzer`, id `ooxml.analyzer` v1.0.0); ZIP container read via bounded channel (nothing extracted); VBA/ActiveX/embedded-OLE/external-links/custom-XML/signature detection; defanged external/hyperlink targets; findings OOXML-*-001; fuzz tests (`OoxmlAnalyzerFuzzTest`) |

---

## 5. Test/Resource/Security Implications

**Testing** — each analyzer needs: minimal valid; realistic synthetic; extension-spoof; MIME-spoof;
truncated; malformed; excessive count/size; cancellation; timeout/quota; parser-error; report-schema;
hostile-string escaping; nested-container (where relevant); fuzz/property where practical. The `FuzzHarness`
in `core:testing` should be extended with per-format seeds.

**Resource/storage** — the shared case budget must cap temp expansion; extracted child bytes go to the
per-case private workspace already used by staging; no new enumerable storage.

**Report** — add an `artifactTree`/`artifacts` list and populate `containers`/`files`/`facts`; add
`AnalyzerInfo(id, version, format, capabilities, limitations)`; schema goes to `1.1.0` (additive).

**UI** — no new screens required for the first stage; the existing report/evidence UI can render the new
artifact fields. A later "artifact tree" navigation refinement is optional.

**Play Store** — expansion does not add permissions, exports, or networking; Data Safety unchanged
(no data collection). Risk listing keeps "static/local" language. No policy hazard introduced by the
framework.

---

## 7. Recommended Architecture Changes (summary)

- **Create `Artifact` (core:model), `AnalysisContext` + `CaseBudget` (engine:api), `AnalysisDispatcher` (engine:orchestrator)**
- **Wire the pipeline** to (a) build a root artifact, (b) mask the feedback by a dispatcher over a
  registry, (c) maintain a single `CaseBudget`, (d) check cancellation cooperatively, (e) record
  analyzer version.
- **Populate `containers`/`files`/`facts`/`artifactTree`** from emitted artifacts so the report reflects
  the whole tree (ZIP → DOCX → embedded XLSM → ...).
- **Layered detection + polyglot analysis**; record mismatch findings.
- **Enforce `max*` counters** by centralizing quota checks in `AnalysisContext` (shared by every parser)
  and boundaries in `AnalyzerService`.
- **Version `reportSchemaVersion` as a constant** and gate change to `1.1.0`.

These framework changes are **preconditions** for Stages 1–16 and must be implemented and tested before
new platform-specific parsers are added.

---

## 8. Deliverables Produced by This Review

- Restore + update the master development plan (`docs/ANDROID_STATIC_ANALYSIS_APP_DEVELOPMENT_PLAN.md`)
  to register the expansion phases and requirement IDs.
- Create `docs/adr/ADR-0004-generic-artifact-analysis-framework.md`.
- Create `core:model` `Artifact`/`ArtifactNode`; `engine:api` `AnalysisContext`/`CaseBudget`/`AnalyzerInfo`.
- Wire via `engine:orchestrator` `AnalysisDispatcher`.
- Populate report `containers/files/facts/artifactTree`.
- Add tests for the framework (budget, cancellation, nesting, dispatch, report population).
- Proceed to Stage 1 (PDF) as the first staged parser.

---

## 9. Open Risks / Limitations

- Isolated-process and in-process paths must stay consistent; new analyzers must be pure engine
  (no Android framework types) so they run both in-process and in `:analyzer`.
- Parsing libraries must meet the isolation + boundedness bars; **JIT/bsjon or a heavy dumb parser** is
  not acceptable without review.
- Font/OneNote/media parsing may be **deferred** if a bounded, current, Android-safe implementation is
  not readily available; deferral is explicitly documented rather than shim a weak parser.