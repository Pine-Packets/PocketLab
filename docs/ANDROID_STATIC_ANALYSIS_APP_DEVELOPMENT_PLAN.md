# Android On-Device Static Malware Analysis App
## Agent-Ready Phased Development Plan and Engineering Specification

**Working title:** PocketLab (placeholder only)  
**Product owner:** Pine and Packets LLC  
**Document status:** Initial master specification  
**Document version:** 1.0.0  
**Prepared:** 2026-08-04  
**Primary implementation platform:** Android phones, tablets, foldables, and compatible ChromeOS devices  
**Primary implementation language:** Kotlin  
**Target SDK:** Android 16 / API 36 or newer at release  
**Recommended minimum SDK:** API 29 initially; validate API 28 support during Phase 1  

---

# 0. How an AI Agent Must Use This Document

This file is the authoritative product and engineering plan unless a later Architecture Decision Record, product decision, or explicitly approved amendment supersedes it.

An AI coding agent working on the repository must:

1. Read this entire document before proposing architecture or modifying source code.
2. Identify the active development phase and implement only work permitted by that phase unless explicitly instructed otherwise.
3. Treat every imported file, URI, filename, archive entry, APK field, DEX string, certificate field, and report value as hostile input.
4. Preserve the primary safety guarantees:
   - no sample execution;
   - no sample installation;
   - no automatic upload;
   - no unrestricted shared-storage access;
   - no analysis-network access;
   - no unbounded extraction, allocation, recursion, parsing, or report generation.
5. Avoid adding permissions, exported components, native dependencies, analytics SDKs, advertising SDKs, remote code loading, or cloud services without an approved Architecture Decision Record.
6. Keep the canonical report schema backward-compatible or increment its schema version with a migration plan.
7. Add or update tests whenever behavior changes.
8. Update the relevant documentation, acceptance criteria, threat model, and decision log when implementation differs from this plan.
9. Never label a sample “malware” solely because of one heuristic. Reports must distinguish facts, interpretations, confidence, and limitations.
10. Stop and flag any requested change that would execute imported code, expose imported samples to another app, bypass archive quotas, weaken path validation, or silently transmit sample-derived data.

Recommended repository documentation:

```text
/docs
├── ANDROID_STATIC_ANALYSIS_APP_DEVELOPMENT_PLAN.md
├── ARCHITECTURE.md
├── THREAT_MODEL.md
├── REPORT_SCHEMA.md
├── RULE_AUTHORING.md
├── TEST_CORPUS_POLICY.md
├── PLAY_STORE_COMPLIANCE.md
├── PRIVACY_MODEL.md
├── RELEASE_CHECKLIST.md
├── OPEN_DECISIONS.md
└── adr/
    ├── ADR-0001-local-first-no-network-mvp.md
    ├── ADR-0002-isolated-analysis-process.md
    ├── ADR-0003-canonical-report-json.md
    └── ...
```

---

# 1. Executive Product Definition

PocketLab is an on-device static malware-triage application for Android. A user imports an APK, DEX, supported archive, or later a supported executable/document/script. The app safely inspects the file without installing or executing it, extracts verifiable technical facts, applies evidence-based rules, and produces two synchronized views:

- a **Simple Report** for ordinary users and help-desk personnel;
- an **Analyst Report** for SOC analysts, incident responders, malware analysts, threat researchers, administrators, and developers.

The application must remain useful without an account, server, subscription, or internet connection. The first public release should contain no analytics SDK and no advertising SDK. Rule updates should initially ship with app releases so the Play Store package can truthfully be described as offline and local-first.

The product is not a replacement for dynamic detonation, endpoint detection, antivirus, or a full desktop reverse-engineering suite. It is a private, mobile triage tool designed to answer:

- What is this file?
- Is its structure valid?
- What capabilities does its code appear to contain?
- What permissions and Android components does it declare?
- What network indicators or embedded payloads are present?
- What evidence makes it concerning or ordinary?
- What should a user or analyst do next?
- How can the findings be exported into a ticket, incident record, or investigation package?

## 1.1 Core value proposition

> Analyze suspicious Android applications and archives privately on the device. Understand their declared capabilities, embedded indicators, signing information, and suspicious code patterns without installing, executing, or automatically uploading the sample.

## 1.2 Primary differentiators

1. **Local-first privacy:** sample bytes remain on-device by default.
2. **No detonation:** the imported file is never launched, installed, or invoked through another application.
3. **Evidence-backed reporting:** every conclusion links to concrete evidence.
4. **Dual audience:** plain-language explanations and analyst-grade details use the same underlying facts.
5. **Archive-aware:** users may import password-protected or ordinary ZIP case packages, not only raw APKs.
6. **Resource-bounded:** hostile archives and malformed files are handled with strict quotas.
7. **Android-native workflow:** file picker, Android Sharesheet intake, adaptive Material UI, and secure report export.
8. **Extensible engine:** parsers and rule packs are modular so future formats can be added without rewriting the product.

---

# 2. Product Goals, Non-Goals, and Safety Invariants

## 2.1 Goals

### G-001 — Safe local inspection
The application shall inspect imported files without executing code from those files.

### G-002 — Useful APK analysis
The first production version shall deeply analyze APK and DEX content rather than serving only as a package-information viewer.

### G-003 — Safe archive ingestion
The application shall support ZIP-based transport and case packages with defenses against traversal, bombs, malformed metadata, excessive recursion, and unsafe filenames.

### G-004 — Evidence-based conclusions
Each finding shall contain evidence, severity, confidence, explanation, and limitations.

### G-005 — Reproducible reports
The canonical report shall be deterministic for the same file, engine version, rule-pack version, and settings.

### G-006 — Minimal permissions
The MVP shall not request broad storage, package visibility, accessibility, VPN, SMS, contacts, location, microphone, camera, or device-administration permissions.

### G-007 — Professional export
Users shall be able to export reports and IOC collections in common machine-readable and human-readable formats.

### G-008 — Maintainability
The codebase shall be modular, testable, versioned, documented, and suitable for iterative AI-agent-assisted development.

### G-009 — Honest uncertainty
The product shall never imply that absence of a finding proves safety or that a static capability definitely executed.

## 2.2 Non-goals for the MVP

The MVP shall not:

- install APKs;
- launch APK activities, services, receivers, or providers;
- execute DEX, JavaScript, shell commands, macros, native code, or embedded payloads;
- provide a malware-download repository;
- scan every file on the device;
- enumerate installed applications;
- monitor network traffic;
- act as a VPN, firewall, accessibility service, antivirus, or device administrator;
- upload files or extracted content to a server;
- perform internet reputation lookups;
- decompile every method into high-level Java/Kotlin source;
- guarantee a malware/benign verdict;
- crack archive passwords;
- bypass copy protection, licensing, device protections, or authentication;
- support arbitrary nested archive depth;
- allow user-supplied executable plugins;
- dynamically download DEX, JAR, SO, rules-as-code, or parser modules.

## 2.3 Safety invariants

These invariants are mandatory throughout all phases:

1. Imported data is never trusted based on filename, extension, MIME type, source application, or user statement.
2. Imported sample bytes are never passed to `PackageInstaller`, `startActivity`, `Runtime.exec`, a shell, a scripting engine, a WebView, a native dynamic loader, or an external application.
3. The analysis process must have no network capability.
4. Archive extraction may occur only into an app-private per-case workspace after canonical-path validation and quota approval.
5. Prefer random-access or streaming inspection over full extraction.
6. Every read is bounded by bytes, time, recursion, entries, and cancellation state.
7. Every parser failure produces a controlled finding or error record rather than a crash loop.
8. Report rendering must escape all sample-controlled text.
9. Temporary samples are deleted by default after the configured retention period.
10. User-visible verdicts always include limitations.

---

# 3. Users and High-Value Workflows

## 3.1 Personas

### P-001 — Ordinary Android user
Receives an APK or ZIP through a message, website, or file share and wants to know whether it appears risky before doing anything with it.

### P-002 — Help-desk technician
Needs to triage a file submitted by an employee and provide a clear recommendation.

### P-003 — SOC analyst
Needs hashes, certificate details, indicators, permissions, components, ATT&CK mappings, and a report suitable for a ticket.

### P-004 — Mobile application security engineer
Wants to review exported components, deep links, WebView exposure, signing configuration, debuggable flags, network-security settings, and code capabilities.

### P-005 — Malware analyst or researcher
Needs a rapid first-pass analysis away from a workstation and may later transfer hashes, evidence, or the sample to a controlled lab.

### P-006 — App developer
Wants to inspect their own APK for accidental secrets, risky permissions, debug flags, exported components, or embedded endpoints.

## 3.2 Primary workflows

### WF-001 — Analyze a raw APK
1. User taps **Analyze File**.
2. System document picker opens.
3. User selects an APK.
4. Intake screen shows source name, reported size, detected type, hash preview, and privacy statement.
5. User taps **Start Local Analysis**.
6. App copies and hashes the input into a private case workspace.
7. Analysis progresses through visible stages.
8. Simple Report opens when minimum findings are available.
9. Analyst sections populate as deeper stages complete.
10. User exports or deletes the case.

### WF-002 — Share a file into the app
1. User selects **Share** in another app.
2. PocketLab appears as **Analyze with PocketLab**.
3. The exported intake activity validates the URI and copies only after confirmation.
4. The app never auto-starts analysis solely because an external app shared content.

### WF-003 — Analyze a ZIP containing one APK
1. User imports ZIP.
2. App performs archive preflight without extraction.
3. App identifies encrypted status and supported entries.
4. User enters password if required.
5. App displays archive tree and planned analysis set.
6. App analyzes the APK entry and records archive/container evidence.

### WF-004 — Analyze a case ZIP
A ZIP contains an APK, suspicious message text, screenshot, URL text, or analyst notes. MVP may analyze the APK and inventory all other entries; later phases correlate IOCs across files.

### WF-005 — Compare two APKs
Future workflow: import two related APKs and compare certificate, package metadata, permissions, components, classes, methods, strings, domains, and resources.

### WF-006 — Export for incident response
User exports:
- a Markdown narrative;
- an HTML report;
- canonical JSON;
- IOC CSV;
- optionally a PDF in a later phase.

---

# 4. Requirements Catalog

## 4.1 Functional requirements

### Intake

- **FR-IN-001:** Import one file through `ACTION_OPEN_DOCUMENT`.
- **FR-IN-002:** Receive explicitly supported shared files through `ACTION_SEND`.
- **FR-IN-003:** Optionally receive multiple files through `ACTION_SEND_MULTIPLE` only after case-package support is complete.
- **FR-IN-004:** Display an intake confirmation before copying or analyzing external content.
- **FR-IN-005:** Determine file type using magic bytes and structural checks, not extension alone.
- **FR-IN-006:** Compute SHA-256 while staging the file; optionally compute SHA-1 and MD5 for compatibility reporting.
- **FR-IN-007:** Enforce configurable and hard maximum input sizes.
- **FR-IN-008:** Permit cancellation during staging.
- **FR-IN-009:** Detect duplicate imports by SHA-256 and offer reuse, reanalysis, or separate case creation.

### Archive management

- **FR-AR-001:** Enumerate ZIP entries before extraction.
- **FR-AR-002:** Detect ZIP encryption and request a password only when required.
- **FR-AR-003:** Support the conventional password `infected` as a user-selectable shortcut, not as automatic brute force.
- **FR-AR-004:** Never persist archive passwords by default.
- **FR-AR-005:** Enforce entry-count, uncompressed-size, per-entry-size, ratio, depth, name-length, and time quotas.
- **FR-AR-006:** Reject or quarantine traversal paths, absolute paths, device paths, drive paths, and invalid normalization.
- **FR-AR-007:** Detect duplicate normalized paths and report ambiguity.
- **FR-AR-008:** Treat nested archives as child containers with explicit depth budgets.
- **FR-AR-009:** Analyze supported entries without extracting unrelated entries.
- **FR-AR-010:** Preserve container-to-child provenance in the report.

### APK management

- **FR-APK-001:** Recognize ordinary APKs structurally.
- **FR-APK-002:** Recognize APKs inside ZIP, XAPK, and APKS containers as phased support is added.
- **FR-APK-003:** Parse package name, version, SDK values, application metadata, permissions, components, features, and intent filters.
- **FR-APK-004:** Parse and report signing certificates and available signature schemes.
- **FR-APK-005:** Inventory DEX files, native libraries, resources, assets, certificates, scripts, nested archives, and secondary APKs.
- **FR-APK-006:** Handle multidex APKs.
- **FR-APK-007:** Detect malformed, truncated, inconsistent, or duplicate ZIP/APK structures.
- **FR-APK-008:** Never install or invoke the APK.

### DEX and code analysis

- **FR-DEX-001:** Validate DEX headers and section bounds.
- **FR-DEX-002:** Extract strings, types, prototypes, fields, methods, classes, annotations, and code items.
- **FR-DEX-003:** Decode supported Dalvik instructions safely.
- **FR-DEX-004:** Build method-reference and API-reference indexes.
- **FR-DEX-005:** Map APIs and instruction patterns to capabilities.
- **FR-DEX-006:** Locate evidence by DEX name, class, method, instruction offset, and string identifier.
- **FR-DEX-007:** Detect reflection, dynamic code loading, native loading, shell execution, network APIs, accessibility APIs, overlay APIs, SMS APIs, package enumeration, boot persistence, and other high-value patterns.
- **FR-DEX-008:** Perform limited constant propagation for common `const-string` and call-site patterns.
- **FR-DEX-009:** Calculate obfuscation metrics without equating obfuscation with malware.
- **FR-DEX-010:** Cap method count, instruction count, CFG complexity, string count, and analysis duration.

### Findings and reports

- **FR-RP-001:** Store canonical findings in versioned JSON-compatible models.
- **FR-RP-002:** Every finding includes ID, title, category, severity, confidence, evidence, explanation, limitations, remediation, and references where applicable.
- **FR-RP-003:** Generate Simple and Analyst views from the same canonical report.
- **FR-RP-004:** Export JSON, Markdown, HTML, and IOC CSV.
- **FR-RP-005:** Add PDF export after the report renderer is stable.
- **FR-RP-006:** Include engine version, rule-pack version, report schema version, device-independent settings, timestamps, and analysis completeness.
- **FR-RP-007:** Allow users to add analyst notes without modifying original findings.
- **FR-RP-008:** Clearly mark incomplete, skipped, failed, unsupported, or quota-limited stages.

### Generic artifact framework and format expansion

- **FR-GA-001:** Represent every analyzed input (root and nested/container child) as a versioned artifact with stable ID, parent ID, container relation, original and sanitized display filename, claimed MIME, detected type and subtype, size, hashes, metadata, indicators, findings, evidence, parser errors, completeness, limitations, and analyzer ID/version.
- **FR-GA-002:** Detect format using layered signals — content signature → structural validation → container characteristics → advisory MIME → advisory extension — and report mismatches; never select a parser solely from the filename extension.
- **FR-GA-003:** Allow multiple safe analyzers to inspect one artifact where structures are ambiguous (polyglots), each contributing independent evidence.
- **FR-GA-004:** Maintain a single case-level `AnalysisBudget` shared across nested containers, artifacts, and analyzers that is never reset for a child; a nested container must consume the same case budget.
- **FR-GA-005:** Bound total bytes read, expanded bytes, temp storage, artifact count, archive entries, recursion depth, strings, indicators, findings, evidence, parser operations, analysis duration, and report size.
- **FR-GA-006:** When a limit is reached, stop the affected operation safely, preserve completed evidence, mark the affected artifact incomplete, identify the exact quota, and continue unrelated safe analysis where possible; never silently omit content.
- **FR-GA-007:** Support cooperative cancellation and wall-clock timeouts on every analyzer, in both in-process and isolated-process paths.
- **FR-GA-008:** A parser failure must not crash the main app, must not classify the artifact as clean, must not discard already collected safe findings, and must produce a parser-error record marking the affected analysis incomplete.
- **FR-GA-009:** Record `analyzerId` and `analyzerVersion` so findings and decoded output are attributable to a specific parser revision.
- **FR-GA-010:** Expose an artifact tree (`artifactTree`/`artifacts`) plus populated `containers`, `files`, and `facts` in the canonical report and include analyzer metadata (ID, version, supported format, capabilities, limitations).
- **FR-GA-011:** Correlate indicators and hashes across artifacts at the case level, referencing exact artifacts and evidence, without inferring causality from indicator equality.
- **FR-GA-012:** New parsers must be pure, dependency-isolated engine code runnable both in-process and inside the isolated `:analyzer` process, with no Android framework types, no networking, and no dynamic code loading.
- **FR-GA-013:** Before adopting any parsing dependency, review license, maintenance, Android compatibility, transitive dependencies, native code, CVE history, malformed-input behavior, allocation behavior, streaming capability, and cancellation support; wrap external libraries behind PocketLab-owned interfaces and prefer memory-safe implementations.

## 4.2 Non-functional requirements

- **NFR-001:** No imported code execution.
- **NFR-002:** No network permission in the MVP build.
- **NFR-003:** No dangerous runtime permission in the MVP.
- **NFR-004:** No broad storage permission.
- **NFR-005:** Main UI remains responsive during every analysis stage.
- **NFR-006:** Analysis is cancellable and cleanup is idempotent.
- **NFR-007:** A parser crash must not corrupt the case database.
- **NFR-008:** The application shall recover gracefully after process death.
- **NFR-009:** Reports shall be deterministic when inputs and versions match.
- **NFR-010:** All sample-controlled output is escaped.
- **NFR-011:** Accessibility labels and scalable text are supported.
- **NFR-012:** UI adapts to phone, foldable, tablet, landscape, and desktop-window sizes.
- **NFR-013:** All critical parsers have malformed-input and fuzz-test coverage.
- **NFR-014:** No secret keys, API tokens, signing credentials, or live malware samples are committed to the repository.
- **NFR-015:** Dependencies have pinned versions, license records, and vulnerability monitoring.

---

# 5. Google Play Policy and Android Permission Plan

This section is a product requirement, not legal advice. Revalidate all policies immediately before each Play submission.

## 5.1 Target SDK and platform posture

- Build and test against Android 16 / API 36 or newer.
- Design edge-to-edge layouts from the beginning.
- Support predictive back.
- Do not lock orientation.
- Use adaptive layouts because Android 16 applies stricter large-screen behavior.
- Maintain compatibility tests for API 29 through current Android.

## 5.2 MVP permission matrix

| Permission/API | MVP status | Reason |
|---|---:|---|
| `INTERNET` | Do not declare | Enforces offline claim and reduces sample-exfiltration risk. |
| `ACCESS_NETWORK_STATE` | Do not declare | Not needed without networking. |
| `READ_MEDIA_*` | Do not declare | Use Storage Access Framework. |
| `READ_EXTERNAL_STORAGE` | Do not declare | Use user-selected content URIs. |
| `WRITE_EXTERNAL_STORAGE` | Do not declare | Export through `ACTION_CREATE_DOCUMENT` or FileProvider. |
| `MANAGE_EXTERNAL_STORAGE` | Prohibited for MVP | Broad access is unnecessary and policy-sensitive. |
| `QUERY_ALL_PACKAGES` | Prohibited for MVP | App analyzes imported packages, not installed inventory. |
| `REQUEST_INSTALL_PACKAGES` | Prohibited | Product must never install samples. |
| `POST_NOTIFICATIONS` | Do not request initially | Avoid notification and background-analysis complexity. |
| `FOREGROUND_SERVICE*` | Do not declare initially | Keep first release user-visible and foreground-bound. |
| `CAMERA` | Do not declare | No QR scanning in initial product. |
| Accessibility service | Prohibited | Unnecessary and policy-sensitive. |
| VPN service | Prohibited | Outside scope. |
| SMS/call log/contacts/location/microphone | Prohibited | Outside scope. |
| Biometrics | Optional later | Could protect retained cases; not required initially. |

## 5.3 File access model

Use the Storage Access Framework:

- `ACTION_OPEN_DOCUMENT` for importing one file.
- `ACTION_CREATE_DOCUMENT` for explicit export to user-selected storage.
- Persist URI permission only when required for a queued operation; otherwise copy immediately and release.
- Never require a user to grant a whole directory.
- Never request the Downloads root.
- Use `ContentResolver.openFileDescriptor()` or `openInputStream()` and treat metadata as advisory.

## 5.4 Share-target model

Create a narrow exported intake activity for supported MIME types:

- `application/vnd.android.package-archive`
- `application/zip`
- `application/octet-stream` only after strict content sniffing and explicit confirmation
- later MIME types as support is added

Do not register `*/*` for the initial release. Validate:

- action;
- URI scheme (`content://` preferred; reject unsafe `file://`);
- granted read permission;
- MIME type as advisory only;
- item count;
- declared size and actual streamed bytes;
- filename controls and display safety.

The intake activity must not analyze automatically. It must present a confirmation screen and create an internal case token before staging.

## 5.5 Foreground/background analysis policy

Initial release strategy:

- Analysis is user-initiated while the app is visible.
- A bound analysis service survives configuration changes but is not promoted to a foreground service.
- If the application is backgrounded, the analysis may pause at a checkpoint or continue only while the process remains available; the UI must state that leaving the app may pause analysis.
- Enforce a default analysis budget intended to complete on modern phones within a few minutes.

Later strategy, only if telemetry from voluntary testers shows a real need:

- Evaluate a user-visible foreground service or long-running WorkManager flow.
- Create an ADR documenting the correct foreground service type, Play Console declaration, notification UX, timeout handling, cancellation, and policy review evidence.
- Do not misclassify local analysis as data synchronization.
- `specialUse` may be evaluated only after policy review and a prototype submission; it must not be assumed acceptable.

## 5.6 Google Play listing posture

Use accurate language:

- “static analysis”
- “local malware triage”
- “inspect APK capabilities”
- “does not execute or install analyzed files”
- “results are indicators, not a guarantee of safety”

Avoid unsupported claims:

- “detects all malware”
- “antivirus replacement”
- “100% safe”
- “sandbox” if no execution occurs
- “anonymous” if reports can contain user-supplied identifiers

Provide Play reviewers:

- a built-in inert demonstration sample;
- step-by-step review instructions;
- explanation that no sample is executed or installed;
- privacy policy;
- Data Safety answers;
- screenshots of file import, analysis progress, findings, and deletion;
- a video if a later foreground service declaration requires one.

## 5.7 Privacy and Data Safety posture

MVP goals:

- no account;
- no analytics;
- no ads;
- no network transmission;
- no cloud backup of samples or reports;
- local deletion controls;
- privacy policy accessible in-app and on a public webpage.

The privacy policy must explain:

- what files the user selects;
- that sample content is processed locally;
- where temporary files are stored;
- retention defaults;
- report contents;
- deletion behavior;
- crash-reporting status;
- whether future optional services transmit hashes or indicators;
- contact information for Pine and Packets LLC.

Even a local-only app must complete the Play Data Safety form and provide a privacy policy.

---

# 6. Threat Model and Trust Boundaries

## 6.1 Protected assets

- User-selected sample bytes.
- Extracted sample content.
- Archive passwords.
- Generated findings and analyst notes.
- User privacy and device integrity.
- App signing keys.
- Rule-pack integrity.
- Report integrity.
- Play developer account standing.

## 6.2 Adversaries

- A malicious file author attempting parser exploitation.
- A malicious archive designed to exhaust storage, CPU, memory, or battery.
- A malicious external app sharing a deceptive URI or blocking content provider.
- A sample containing misleading Unicode names or report-injection content.
- A sample designed to trigger excessive graphs, strings, classes, or recursive containers.
- A user who unintentionally exports sensitive findings.
- A compromised dependency or malicious rule update.
- A local attacker with access to app backups or shared exports.

## 6.3 Trust boundaries

```text
[External app / DocumentsProvider]
              |
              | content URI + temporary grant
              v
[Exported Intake Activity]
              |
              | validated request / user confirmation
              v
[Main App Process]
  - UI
  - case metadata
  - orchestration
  - encrypted report storage
              |
              | read-only ParcelFileDescriptor + bounded config
              v
[Isolated Analyzer Process]
  - no Android permissions
  - no network
  - no direct app database access
  - parser and rule execution
              |
              | structured result stream
              v
[Main App Process]
  - validation
  - persistence
  - rendering
  - export
              |
              v
[User-selected export destination / receiving app]
```

## 6.4 Major threats and mitigations

### T-001 — ZIP path traversal
Mitigations:
- normalize entry paths;
- reject absolute paths and parent traversal;
- compare canonical destination to canonical workspace root;
- reject symlinks and hard links during initial archive support;
- never trust archive library defaults alone.

### T-002 — ZIP bomb or decompression bomb
Mitigations:
- preflight central-directory metadata;
- runtime compressed/uncompressed counters;
- ratio threshold;
- total expanded-byte threshold;
- per-entry threshold;
- entry-count threshold;
- nested-depth threshold;
- wall-clock and CPU budgets;
- cancellation.

### T-003 — Malformed parser exploit
Mitigations:
- isolated process;
- memory-safe implementation where practical;
- bounds checks;
- fuzzing;
- dependency patching;
- parser-specific quotas;
- process restart after severe failure.

### T-004 — Hostile content provider
Mitigations:
- do not trust `DISPLAY_NAME` or `SIZE`;
- stream with a hard byte cap;
- cancel by closing file descriptors;
- limit open duration;
- copy into private storage before deep analysis;
- reject repeated or inconsistent reads.

### T-005 — Report injection
Mitigations:
- escape HTML, Markdown-sensitive constructs where necessary, CSV formula prefixes, and control characters;
- no active JavaScript in exported HTML;
- strict Content Security Policy in HTML;
- no remote fonts, images, scripts, or styles;
- display Unicode controls visibly in analyst mode.

### T-006 — Sample accidentally installed or opened
Mitigations:
- no install permission;
- no launch actions;
- never export sample by default;
- sample actions limited to hash copy, delete, report export, or explicit secure re-share behind warnings in a future professional mode.

### T-007 — Sensitive sample backed up
Mitigations:
- store samples under `noBackupFilesDir`;
- configure backup exclusion rules;
- encrypt retained reports and samples;
- default to temporary retention.

### T-008 — Analysis-engine network exfiltration
Mitigations:
- omit `INTERNET` from MVP;
- run engine in `isolatedProcess`;
- no web views or URL loading;
- future networking exists only in a separate main-process module and never receives sample bytes without explicit user action.

---

# 7. High-Level Architecture

## 7.1 Recommended application architecture

Use a single-activity Jetpack Compose application with unidirectional data flow, ViewModels, repositories, use cases where complexity warrants them, and a modular analyzer engine.

Recommended layers:

```text
UI layer
  Compose screens, navigation, state holders, view models

Domain layer
  StartAnalysis, CancelAnalysis, BuildReport, ExportReport,
  DeleteCase, CompareCases, ApplyRulePack

Data layer
  CaseRepository, ReportRepository, SettingsRepository,
  RulePackRepository, ExportRepository

Engine boundary
  AnalysisClient (main process) <-> AIDL <-> AnalyzerService (isolated process)

Engine internals
  Intake validation, type detection, archive parser, APK parser,
  DEX parser, native inventory, IOC extraction, rules, result writer
```

## 7.2 Proposed Gradle modules

```text
:app

:core:common
:core:model
:core:io
:core:crypto
:core:database
:core:report
:core:rules-model
:core:testing

:engine:api
:engine:service
:engine:orchestrator
:engine:filetype
:engine:archive
:engine:apk
:engine:dex
:engine:native
:engine:ioc
:engine:rules

:feature:onboarding
:feature:home
:feature:intake
:feature:analysis
:feature:report
:feature:cases
:feature:settings
:feature:about

:benchmark
```

Future modules:

```text
:engine:pe
:engine:elf
:engine:pdf
:engine:office
:engine:scripts
:engine:rules-yara
:engine:compare
:integration:reputation
:integration:stix
:integration:misp
```

## 7.3 Main process responsibilities

The main process shall:

- receive intents;
- show confirmation UI;
- create cases;
- stage imported bytes;
- manage case lifecycle;
- bind to isolated analyzer;
- display progress;
- validate returned result objects;
- persist canonical reports;
- render reports;
- handle export;
- handle settings and retention;
- perform future opt-in network lookups, never the isolated engine.

## 7.4 Isolated process responsibilities

Declare an internal, non-exported service with `android:isolatedProcess="true"`. It shall:

- accept a read-only `ParcelFileDescriptor`;
- accept a bounded immutable `AnalysisRequest`;
- parse and analyze bytes;
- periodically emit progress;
- write structured results to a pipe or callback in bounded chunks;
- support cancellation;
- close all descriptors;
- retain no sample after completion;
- terminate cleanly after a task or after a configured number of tasks.

It shall not:

- open arbitrary paths;
- access the app database;
- access shared storage;
- make network requests;
- display UI;
- launch intents;
- persist passwords;
- load unapproved code.

## 7.5 IPC contract

Use AIDL or a carefully versioned Binder interface.

Conceptual interface:

```kotlin
interface IAnalyzerService {
    fun startAnalysis(
        request: AnalysisRequestParcel,
        input: ParcelFileDescriptor,
        output: ParcelFileDescriptor,
        callback: IAnalysisCallback
    ): String

    fun cancelAnalysis(jobId: String)
    fun getEngineInfo(): EngineInfoParcel
}
```

Do not send full reports through Binder transactions because transaction sizes are limited. Prefer:

- newline-delimited JSON over a pipe;
- protobuf messages with length prefixes;
- or a temporary output file descriptor controlled by the main process.

The main process must validate schema version, object counts, string lengths, and nesting depth before persistence.

## 7.6 Technology choices

### Required baseline

- Kotlin
- Gradle Kotlin DSL
- Version catalogs
- Jetpack Compose Material 3
- Room for case index and structured metadata
- Kotlin serialization for canonical JSON
- Coroutines and Flow
- AndroidX Lifecycle and ViewModel
- Android Keystore for encryption-key protection
- Storage Access Framework
- FileProvider for controlled report sharing

### Candidate dependencies requiring a technical spike

- Apache Commons Compress for archive support and compressed/uncompressed counters.
- AOSP `apksig` for APK signature verification; verify Android runtime compatibility because its upstream documentation says it is intended for use outside Android devices.
- `dexlib2` or a focused custom DEX reader; evaluate footprint, maintenance, Android compatibility, and license.
- YARA or YARA-X via NDK/Rust only in a later phase after size, ABI, 16 KB page-size, and policy testing.
- `libarchive` only if Java/Kotlin archive support becomes insufficient.

No candidate dependency becomes architectural fact until a spike records:

- binary size impact;
- transitive dependencies;
- license;
- supported formats;
- malformed-input behavior;
- Android compatibility;
- performance;
- memory usage;
- 16 KB page-size support for native code;
- security history;
- maintenance activity;
- fuzzing status.

---

# 8. Repository and Engineering Standards

## 8.1 Repository structure

```text
/
├── app/
├── core/
├── engine/
├── feature/
├── integration/
├── benchmark/
├── docs/
├── test-corpus/
│   ├── README.md
│   ├── synthetic/
│   ├── malformed/
│   └── metadata/
├── gradle/
├── build-logic/
├── scripts/
├── .github/workflows/
├── LICENSE
├── NOTICE
├── PRIVACY.md
├── SECURITY.md
├── CONTRIBUTING.md
├── AGENTS.md
└── README.md
```

## 8.2 Git rules

- Never commit real live malware samples.
- Never commit password-bearing archives of real malware.
- Never commit app signing keys or Play credentials.
- Keep generated reports and staged samples in ignored directories.
- Use conventional or clearly documented commit messages.
- Require review for parser, permission, export, native, and policy changes.
- Tag report-schema releases separately from app releases when useful.

## 8.3 Code quality gates

Every pull request shall run:

- Gradle build;
- Android lint;
- Kotlin formatting and static analysis;
- unit tests;
- parser corpus tests;
- report snapshot tests;
- dependency vulnerability scan;
- license report;
- forbidden-permission manifest test;
- exported-component manifest test;
- reproducibility or deterministic-output checks for core fixtures.

## 8.4 Versioning

Maintain independent versions:

- app version;
- engine version;
- canonical report schema version;
- rule-pack version;
- each parser version if behavior changes materially.

Example:

```json
{
  "appVersion": "1.2.0",
  "engineVersion": "1.4.1",
  "reportSchemaVersion": "1.1.0",
  "rulePackVersion": "2026.08.1"
}
```

---

# 9. Case and Storage Model

## 9.1 Case lifecycle states

```text
CREATED
  -> STAGING
  -> READY
  -> ANALYZING
  -> PARTIAL
  -> COMPLETE
  -> EXPORTING
  -> COMPLETE

Failure states:
STAGING_FAILED
ANALYSIS_FAILED
CANCELLED
CORRUPT_RESULT
DELETION_PENDING
DELETED
```

## 9.2 Private workspace layout

```text
noBackupFilesDir/
└── cases/
    └── <case-uuid>/
        ├── original.bin
        ├── workspace/
        │   ├── extracted/
        │   └── scratch/
        ├── report.json.enc
        ├── notes.json.enc
        ├── thumbnails/
        └── state.json
```

Rules:

- `original.bin` never preserves an executable extension internally.
- Workspaces use random UUID names, never sample filenames.
- Only display names are preserved in metadata.
- The sample is read-only after staging.
- Temporary extracted content is deleted after analysis unless a retained-case setting requires it.
- Reports and retained samples are encrypted using a per-installation or per-case key wrapped by Android Keystore.
- Backups exclude all case directories.

## 9.3 Suggested Room entities

### CaseEntity

```text
id: UUID
createdAt
updatedAt
status
sourceDisplayName
sourceMimeType
sourceSizeReported
sourceSizeActual
sha256
sha1
md5
primaryDetectedType
containerType
analysisProfile
engineVersion
rulePackVersion
reportSchemaVersion
riskBand
maxSeverity
findingCount
retentionMode
samplePresent
reportPresent
lastErrorCode
```

### AnalysisStageEntity

```text
caseId
stageId
state
startedAt
completedAt
progressCurrent
progressTotal
warningCount
errorCode
messageKey
```

### ExportEntity

```text
id
caseId
format
createdAt
sha256
userDestinationUriRedacted
status
```

Avoid storing large findings as thousands of relational rows in the first implementation. Keep a canonical encrypted report blob and a small denormalized index for case lists and filtering. Revisit normalization when search requirements are proven.

## 9.4 Encryption model

Recommended MVP approach:

1. Generate a random AES-256 key per installation or per case.
2. Store/wrap the key using Android Keystore.
3. Encrypt report and notes using AES-GCM with unique nonces.
4. Store nonce, format version, and authentication tag alongside ciphertext.
5. Never reuse a nonce with the same key.
6. Avoid logging keys, plaintext reports, passwords, or IOC values.
7. Provide a user setting to require device authentication before opening retained reports in a future phase.

## 9.5 Retention modes

- **Temporary:** delete original sample and scratch data after report completion; keep encrypted report until user deletes it.
- **Session only:** delete sample and report when app closes or after a short expiration.
- **Retain sample:** advanced opt-in with warning; encrypt original sample; never default.
- **Auto-delete:** configurable 1 day, 7 days, 30 days, or manual.

Default recommendation:

- delete extracted scratch data immediately;
- delete original sample after report generation unless user chooses retention;
- keep encrypted report for 30 days;
- provide **Delete all cases now**.


---

# 10. Secure File Intake Pipeline

## 10.1 Intake principles

The file picker and share target are hostile-input boundaries. The intake pipeline must separate **metadata discovery**, **user confirmation**, **staging**, and **analysis**.

Never perform deep parsing directly against an external content provider. External providers may:

- report the wrong size;
- report a misleading filename or MIME type;
- return a changing stream;
- block indefinitely;
- throw late exceptions;
- revoke access;
- expose a virtual document;
- return content much larger than declared;
- reuse a URI for different data.

## 10.2 Intake sequence

### Step 1 — Receive selection

Inputs:

- `Uri`
- grant flags
- advisory MIME type
- source action
- optional `ClipData`

Reject:

- null URI;
- more items than the intake limit;
- unsupported URI schemes;
- ungranted inaccessible URI;
- malformed intent extras;
- `file://` URI from another application;
- unexpected nested intents.

### Step 2 — Query advisory metadata

Query `OpenableColumns.DISPLAY_NAME` and `SIZE`, but never trust them.

Normalize display name for UI:

- retain original raw name in escaped metadata;
- replace control characters in display representation;
- reveal bidirectional overrides and zero-width characters in Analyst mode;
- cap visible length;
- never use the name as a filesystem path.

### Step 3 — User confirmation

Show:

- source application when available;
- display filename;
- reported size or “unknown”;
- reported MIME type;
- statement: “This file will be copied into private app storage and analyzed locally. It will not be installed or executed.”
- configurable retention summary;
- **Cancel** and **Start Local Analysis** actions.

### Step 4 — Stage and hash

Copy to `<case>/original.bin` using a bounded buffered stream.

During copy:

- compute SHA-256;
- optionally compute SHA-1 and MD5 in the same pass;
- count actual bytes;
- update progress;
- check cancellation;
- reject when hard size limit is crossed;
- flush and fsync as appropriate;
- close the descriptor on cancellation or timeout;
- remove partial output on failure.

Recommended initial limits:

| Profile | Soft warning | Hard maximum |
|---|---:|---:|
| Standard phone | 250 MiB | 512 MiB |
| High-memory device | 512 MiB | 1 GiB |
| Developer override | configurable | absolute 2 GiB |

The engine should calculate device capability and available storage before accepting a large sample. Hard limits must remain even in developer mode.

### Step 5 — Type detection

Read a bounded header and selected structural ranges. Extension and MIME type are only hints.

Initial signatures:

| Type | Detection evidence |
|---|---|
| ZIP/APK | ZIP signatures plus valid central directory; APK requires Android structure |
| DEX | `dex\n` magic and supported version plus header validation |
| ELF | `0x7F 45 4C 46` plus valid class/endian/header bounds |
| PE | `MZ`, valid `e_lfanew`, and `PE\0\0` signature |
| PDF | `%PDF-` plus structural sanity |
| OLE | compound-file magic |
| GZIP | gzip header and method |
| 7z | 7z signature |
| RAR | RAR signature |
| text/script | BOM/encoding checks plus content heuristics |

File type result:

```text
reportedType
extensionType
magicType
structuralType
confidence
mismatchFlags[]
```

### Step 6 — Deduplication

If SHA-256 exists:

- show existing case date and engine version;
- allow opening existing report;
- allow reanalysis with current engine/rules;
- allow separate case with new notes;
- do not silently discard the new import.

## 10.3 Intake error taxonomy

Use stable error codes:

```text
IN_URI_UNSUPPORTED
IN_URI_PERMISSION_DENIED
IN_PROVIDER_TIMEOUT
IN_PROVIDER_CHANGED
IN_SIZE_LIMIT
IN_STORAGE_INSUFFICIENT
IN_STREAM_TRUNCATED
IN_COPY_CANCELLED
IN_HASH_FAILURE
IN_TYPE_UNSUPPORTED
IN_TYPE_MISMATCH
IN_FILE_EMPTY
IN_INTERNAL_IO
```

User-facing errors must avoid raw stack traces. Analyst diagnostics may include sanitized exception class and stage.

---

# 11. Archive and ZIP Management

## 11.1 Archive philosophy

ZIP is a transport and container format, not a sandbox. It may reduce accidental interaction with a raw APK, but importing a ZIP creates additional parser and resource-exhaustion risks.

The preferred strategy is:

1. inventory the archive;
2. validate paths and metadata;
3. select supported entries;
4. stream or selectively extract only those entries;
5. preserve container provenance;
6. delete scratch content.

Do not automatically extract the entire archive.

## 11.2 Archive profiles

### Standard profile

- maximum compressed input: 512 MiB;
- maximum total expanded bytes: 1 GiB;
- maximum single expanded entry: 256 MiB;
- maximum entries: 5,000;
- maximum nested depth: 2;
- maximum path length: 512 characters;
- maximum filename length: 255 characters;
- maximum observed compression ratio: 100:1 after a minimum output threshold;
- maximum per-entry analysis time: configurable;
- maximum total archive stage time: configurable.

### Advanced profile

May increase soft thresholds on high-memory devices, but hard caps remain. The UI must warn about battery, heat, storage, and time.

### Hard absolute profile

Set compile-time or centrally defined absolute limits that settings cannot exceed. The values should be revisited through benchmarking and fuzzing, not guessed permanently.

## 11.3 Archive preflight

Before reading entry contents:

- validate end-of-central-directory records;
- validate Zip64 metadata where present;
- detect overlapping or contradictory directory records;
- enumerate entries;
- calculate declared compressed and expanded totals using overflow-safe arithmetic;
- identify encrypted entries;
- identify unsupported methods;
- identify duplicate raw and normalized paths;
- detect suspicious timestamps;
- detect invalid UTF-8 or encoding ambiguity;
- identify nested archive candidates;
- classify entries by magic where feasible using bounded reads;
- generate an extraction plan.

All size summations must use checked arithmetic. Integer overflow is a rejection condition.

## 11.4 Path normalization policy

For every entry:

1. Replace archive separators consistently for validation.
2. Reject NUL characters.
3. Reject absolute paths.
4. Reject Windows drive paths and UNC paths.
5. Normalize `.` and `..` segments.
6. Reject paths escaping the virtual archive root.
7. Reject empty normalized paths except directory markers.
8. Reject reserved or dangerous device names when materialized.
9. Map to a random internal extraction name rather than preserving the path directly.
10. Preserve original path only as escaped metadata.

If physical extraction is necessary, calculate the canonical destination and verify it remains under the canonical case workspace.

## 11.5 Runtime decompression controls

Declared metadata may lie. During streaming:

- count compressed bytes consumed;
- count uncompressed bytes emitted;
- continuously evaluate ratio;
- abort when an entry or total exceeds its budget;
- check cancellation every buffer iteration;
- limit output buffer size;
- do not allocate based on declared uncompressed size;
- reject unsupported data descriptors or structures that the chosen parser cannot safely handle;
- record quota-triggered termination as evidence.

## 11.6 Password-protected ZIP support

Implement only after ordinary ZIP safety is complete.

Requirements:

- identify encryption before prompting;
- support a defined set such as ZipCrypto and AES-128/192/256 if the selected library safely supports them;
- password field uses secure input;
- password remains in mutable memory for the shortest practical duration;
- do not log or persist password;
- clear UI state after use;
- include a user-invoked **Use common malware-sample password: infected** action;
- limit attempts to prevent accidental brute force and battery abuse;
- do not offer dictionary attacks;
- distinguish wrong password from corrupt archive where possible without leaking excessive parser detail.

## 11.7 Nested archives

Nested archives are represented as a tree:

```text
ContainerNode
├── parentContainerId
├── entryPathEscaped
├── depth
├── compressedSize
├── expandedSizeObserved
├── detectedType
├── sha256
├── status
└── children[]
```

Rules:

- every child consumes global byte, entry, time, and depth budgets;
- no independent reset of quotas for a nested archive;
- repeated identical nested objects may be deduplicated by SHA-256;
- recursive archive loops are impossible at the byte level but repeated embedded copies can be detected and capped;
- nested depth default is two for public release.

## 11.8 APK as ZIP

APK analysis must not blindly rely on generic extraction. Prefer direct random-access reads for:

- `AndroidManifest.xml`;
- `resources.arsc`;
- `classes*.dex`;
- `META-INF/*`;
- `lib/<abi>/*.so`;
- `assets/*`;
- `res/xml/*` and selected resources.

Record APK ZIP anomalies:

- duplicate names;
- unexpected compression methods;
- misaligned native libraries;
- malformed central directory;
- APK Signing Block anomalies;
- trailing or prepended data;
- unusual extra fields;
- very high-entropy assets;
- nested executable content.

## 11.9 APKS, XAPK, and split package management

### APKS

An APKS file commonly contains a base APK, configuration splits, and metadata generated by bundle tooling. Support should:

- inventory all APK entries;
- identify base and split APKs;
- parse package consistency;
- compare signing certificates;
- merge manifest/permission summaries carefully;
- report device-specific split limitations;
- avoid pretending one split represents the complete app.

### XAPK

XAPK is not one universal formal standard. Implement tolerant detection:

- ZIP container;
- one or more APKs;
- optional `manifest.json` or vendor metadata;
- optional OBB/assets.

Report exactly what was observed rather than relying on the extension.

### Split APK set

A user may select several APKs in a future phase. Validate:

- same package name;
- compatible version codes;
- matching signing lineage;
- base APK presence;
- split names and dependencies;
- duplicate split conflicts.

## 11.10 Archive report section

Include:

- archive type;
- encryption;
- entry count;
- declared and observed sizes;
- maximum observed ratio;
- nested depth;
- suspicious paths;
- duplicate entries;
- unsupported entries;
- analyzed child list;
- skipped child list and reason;
- quota events;
- archive integrity status.

---

# 12. APK Static Analysis Pipeline

## 12.1 Stage overview

```text
APK structural validation
    -> package metadata
    -> manifest and components
    -> resources and configuration
    -> signing verification
    -> file inventory
    -> DEX inventory and parsing
    -> native library inventory
    -> IOC extraction
    -> capability facts
    -> correlated rules
    -> report synthesis
```

Each stage emits facts independently. A later failure must not erase earlier valid findings.

## 12.2 APK structural validation

Validate:

- valid ZIP structures;
- presence and readable status of `AndroidManifest.xml`;
- at least one expected code/resource structure or a clear no-code classification;
- duplicate critical entries;
- multiple manifests;
- impossible or contradictory sizes;
- unsupported compression;
- truncated entries;
- suspicious preamble or overlay data;
- central-directory consistency;
- expected alignment where relevant.

Output:

```text
apkStructureStatus = VALID | VALID_WITH_WARNINGS | MALFORMED | PARTIAL
```

## 12.3 Package metadata

Extract:

- package/application ID;
- version name and code;
- compile SDK if available;
- min SDK;
- target SDK;
- shared user ID if present;
- install location;
- application label;
- icon metadata, rendered only through safe resource parsing;
- debuggable;
- test-only;
- backup flags;
- cleartext-traffic policy;
- network security configuration reference;
- uses-feature and required hardware;
- uses-library;
- application class;
- process declarations;
- task affinity;
- extract-native-libs setting;
- request-legacy-external-storage where applicable;
- large heap flag;
- direct boot awareness.

Do not rely only on `PackageManager.getPackageArchiveInfo()`. Use it as one source, then compare it with the internal parser. Differences become diagnostic evidence.

## 12.4 Permission analysis

For each declared permission:

- name;
- known Android protection category for relevant API levels;
- source manifest location;
- max SDK restriction;
- SDK-conditional declaration;
- whether known code references related APIs;
- plain-language capability;
- common legitimate uses;
- risk when combined with other permissions/components.

Permission groups for UI:

- accounts and identity;
- SMS and phone;
- contacts and calendar;
- location;
- camera and microphone;
- files and media;
- accessibility and UI control;
- overlays and display capture;
- notifications;
- package installation and app discovery;
- device administration;
- VPN and networking;
- background execution and boot;
- biometrics and credentials;
- Bluetooth and nearby devices;
- system/privileged permissions;
- deprecated or ignored permissions.

Important distinction:

```text
declaredPermission
relatedApiReferenced
componentRequiresPermission
permissionLikelyNecessary
permissionRiskContext
```

Static analysis cannot prove that a permission is used at runtime.

## 12.5 Component analysis

Parse:

- activities;
- activity aliases;
- services;
- receivers;
- providers;
- instrumentation;
- permissions declared by the app;
- metadata.

For each component:

```text
name
resolvedName
componentType
exportedDeclared
exportedEffective
permission
readPermission
writePermission
process
intentFilters
scheme/host/path constraints
autoVerify
directBootAware
enabled
riskFacts[]
```

Rules should detect:

- exported component with no permission;
- exported component accepting broad external data;
- provider with weak read/write controls;
- mutable or unsafe deep-link patterns;
- boot receiver;
- accessibility service;
- device-admin receiver;
- notification listener;
- VPN service;
- input method;
- tile service;
- media projection indicators;
- package-added/removed receivers;
- SMS receiver priority patterns where visible;
- exported debug or internal activities.

Do not declare an exported component vulnerable solely because it is exported. Explain reachability and missing controls.

## 12.6 Intent filter and deep-link analysis

Extract:

- actions;
- categories;
- MIME types;
- URI schemes;
- hosts;
- ports;
- paths and patterns;
- `autoVerify`;
- browsable status.

Flag:

- custom schemes without host restrictions;
- HTTP rather than HTTPS links;
- overly broad hosts or paths;
- sensitive actions on exported components;
- overlap with authentication callback patterns;
- unvalidated external file/view actions;
- potential intent-redirection indicators only when code evidence supports them.

## 12.7 Resources and configuration

Parse selected resources safely:

- application labels;
- XML configurations;
- network-security config;
- file-provider paths;
- accessibility-service config;
- device-admin config;
- backup/data extraction rules;
- cleartext domains;
- certificate pin sets;
- trusted certificates;
- resource strings containing endpoints or secrets;
- locales and suspicious brand impersonation strings.

High-value checks:

- trust of user-added CAs in production;
- cleartext traffic allowed globally or for sensitive domains;
- overly broad FileProvider paths;
- WebView-related configuration;
- backup enabled for sensitive app categories;
- debug overrides in network security config;
- hardcoded environment endpoints.

## 12.8 Signing and certificate analysis

Desired output:

- signature schemes present: v1, v2, v3, v3.1, v4 information where available;
- verification result;
- signer count;
- signing lineage;
- certificate subject and issuer;
- serial number;
- validity period;
- public-key algorithm and size;
- signature algorithm;
- SHA-256 certificate fingerprint;
- self-signed status;
- expiration status;
- debug-certificate indicators;
- inconsistent signer evidence among split APKs;
- v1 metadata anomalies;
- stripped-signature or downgrade warnings where supported.

Implementation plan:

1. Conduct a spike against AOSP `apksig`.
2. Confirm it works on target Android runtimes despite upstream “outside Android devices” guidance.
3. If compatible, wrap only documented public APIs.
4. If not compatible, implement a reduced verifier or call platform parsing for metadata while documenting limitations.
5. Never claim signature validity unless cryptographic verification completed.

Certificate identity is not proof of trustworthiness. Reports must state that a valid signature proves package integrity relative to the signer, not benign behavior.

## 12.9 APK inventory

Inventory entries by category:

- DEX;
- native libraries;
- resources;
- assets;
- certificates;
- secondary APKs;
- scripts;
- HTML/JavaScript;
- databases;
- machine-learning models;
- archives;
- unknown/high-entropy blobs;
- executable headers hidden under other extensions.

For every entry:

```text
virtualPathEscaped
compressedSize
expandedSize
compressionMethod
crc
magicType
extensionType
sha256 optional
entropy optional
analysisStatus
parentContainerId
```

Hash individual entries only when size and performance budgets allow. Always hash high-interest executable or configuration entries selected for analysis.

## 12.10 Embedded secrets and configuration

Detect possible:

- API keys;
- cloud access tokens;
- Firebase URLs;
- AWS-style key patterns;
- OAuth client IDs;
- private keys;
- certificates;
- webhook URLs;
- Telegram bot tokens;
- Discord webhooks;
- basic-auth URLs;
- hardcoded passwords;
- JWT-like strings;
- database connection strings.

False-positive controls:

- label as “possible secret” until format validation;
- redact most of the value in Simple mode;
- allow full value reveal only after warning in Analyst mode;
- never include full secrets in exported reports by default;
- provide hash or partial form;
- distinguish public client identifiers from confidential secrets.

---

# 13. DEX Analysis Behind the Scenes

## 13.1 DEX parser goals

The DEX engine shall build a safe, bounded intermediate representation sufficient to identify capabilities and evidence without requiring full high-level decompilation.

## 13.2 Header and map validation

Validate:

- magic and version;
- checksum/signature fields as appropriate;
- file size;
- header size;
- endian tag;
- link/data offsets;
- map list;
- alignment;
- all section counts and offsets;
- non-overlap or valid overlap rules;
- overflow-safe bounds for every item.

Malformed DEX must produce explicit diagnostics such as:

```text
DEX_BAD_MAGIC
DEX_UNSUPPORTED_VERSION
DEX_SECTION_OUT_OF_RANGE
DEX_INTEGER_OVERFLOW
DEX_TRUNCATED_ITEM
DEX_INVALID_LEB128
DEX_BAD_INSTRUCTION_WIDTH
DEX_REFERENCE_OUT_OF_RANGE
DEX_BUDGET_EXCEEDED
```

## 13.3 Indexed tables

Parse with lazy or bounded access:

- string IDs;
- type IDs;
- proto IDs;
- field IDs;
- method IDs;
- class definitions;
- annotations;
- encoded arrays;
- class data;
- code items;
- debug info only when required.

Avoid materializing all strings or methods into heavyweight objects. Use integer IDs, slices, and compact data classes.

## 13.4 Instruction decoding

Decode Dalvik opcodes with:

- validated width;
- validated register references where practical;
- validated string/type/field/method indexes;
- payload handling for switch and array data;
- exception-handler bounds;
- instruction-count quotas.

The first release does not need to produce a complete decompiler. It needs reliable evidence snippets:

```text
class descriptor
method name and prototype
instruction offset
opcode
resolved referenced API/string/type
surrounding bounded context
```

## 13.5 API reference index

Build reverse indexes:

```text
API method -> call sites
API field -> access sites
string -> use sites
class -> methods
method -> referenced methods
method -> referenced strings
native library -> load sites
```

Indexes enable findings without rescanning all instructions.

## 13.6 Capability extraction

Create atomic facts such as:

```text
CODE_USES_DEXCLASSLOADER
CODE_USES_PATHCLASSLOADER
CODE_USES_SYSTEM_LOADLIBRARY
CODE_USES_RUNTIME_EXEC
CODE_USES_PROCESSBUILDER
CODE_USES_ACCESSIBILITY_API
CODE_USES_OVERLAY_API
CODE_USES_NOTIFICATION_LISTENER_API
CODE_USES_SMS_SEND
CODE_USES_SMS_READ
CODE_USES_DEVICE_ADMIN
CODE_USES_PACKAGE_INSTALLER
CODE_USES_PACKAGE_ENUMERATION
CODE_USES_WEBVIEW_JS_INTERFACE
CODE_USES_WEBVIEW_FILE_ACCESS
CODE_USES_CRYPTO
CODE_USES_CLIPBOARD
CODE_USES_SCREEN_CAPTURE
CODE_USES_ROOT_COMMANDS
CODE_USES_EMULATOR_CHECKS
CODE_USES_DEBUGGER_CHECKS
CODE_USES_REFLECTION
CODE_USES_NATIVE_BRIDGE
CODE_USES_SOCKET_API
CODE_USES_HTTP_CLIENT
CODE_USES_TLS_PINNING
CODE_USES_TOR_OR_PROXY_INDICATORS
```

Each fact has evidence locations and confidence.

## 13.7 Limited constant propagation

Implement a bounded intraprocedural pass for common patterns:

- `const-string` to nearby invocation argument;
- `StringBuilder` concatenation with constant values;
- simple `String.concat`;
- static final constant lookup;
- byte-array literal to string when encoding is obvious and bounded;
- URI builder constants;
- class name passed to reflection;
- library name passed to `System.loadLibrary`;
- command array passed to `Runtime.exec` or `ProcessBuilder`.

Do not implement unbounded symbolic execution. Limits:

- maximum instructions per method;
- maximum tracked registers;
- maximum branch states;
- maximum string length;
- maximum recursion depth;
- no execution of application methods.

Output labels:

- literal;
- statically reconstructed;
- partially reconstructed;
- unresolved.

## 13.8 Reflection and dynamic loading

Detect:

- `Class.forName`;
- `Method.invoke`;
- `Constructor.newInstance`;
- `DexClassLoader`;
- `PathClassLoader`;
- in-memory DEX loading;
- asset/resource extraction followed by class loading;
- native `dlopen` indicators where visible;
- encrypted/high-entropy payload near loader code.

Correlation example:

```text
Fact A: DexClassLoader referenced
Fact B: encrypted asset exists
Fact C: asset is written to private storage
Fact D: resulting path passed to loader
=> Finding: probable embedded dynamic payload loading
```

Use “probable” or “possible” based on evidence completeness.

## 13.9 Shell and command analysis

Extract constant or partially resolved commands passed to:

- `Runtime.exec`;
- `ProcessBuilder`;
- native execution wrappers;
- root-management libraries.

Detect high-interest tokens:

- `su`;
- `sh`, `bash`;
- `chmod`;
- `pm install`;
- `settings`;
- `am start`;
- `iptables`;
- `mount`;
- `getprop`;
- `setprop`;
- `logcat`;
- `/proc` access;
- SELinux commands;
- package disable/hide commands.

Do not execute or normalize commands through a shell.

## 13.10 Network analysis

Static network facts:

- URL and URI literals;
- hostnames;
- IP literals;
- ports;
- HTTP client libraries;
- socket APIs;
- DNS APIs;
- TLS/custom trust managers;
- hostname verifier implementations;
- certificate pinning;
- proxy/Tor indicators;
- WebSocket libraries;
- cloud-service endpoints;
- bot/webhook APIs.

IOC extractor must reduce false positives:

- validate IP ranges;
- reject obvious version strings as IPs;
- normalize domains using IDNA safely;
- preserve punycode and Unicode representations;
- strip punctuation carefully;
- classify private, loopback, reserved, and public addresses;
- avoid contacting any indicator.

## 13.11 Obfuscation metrics

Metrics may include:

- percentage of very short class/method/field names;
- identifier entropy;
- package-depth irregularity;
- non-ASCII identifier use;
- string encryption indicators;
- control-flow complexity;
- reflection density;
- dynamic-loading presence;
- resource-name stripping;
- known packer fingerprints;
- malformed debug metadata.

Obfuscation is common in legitimate apps. Report it as an analysis limitation and context factor, not proof of malware.

## 13.12 Packer/protector detection

Use fingerprints from:

- known class names;
- native library names;
- manifest patterns;
- loader structures;
- asset names;
- abnormal DEX size/count;
- encrypted secondary payloads.

Output:

```text
protectorName
confidence
indicators[]
analysisImpact
recommendedNextStep
```

Avoid trademark or vendor assertions unless fingerprints are reliable and maintained.

## 13.13 Call graph and control-flow scope

MVP:

- method reference graph, not a fully sound call graph;
- basic-block boundaries for selected suspicious methods;
- limited CFG for constant propagation and evidence display.

Future:

- class-hierarchy analysis;
- interface resolution;
- interprocedural summaries;
- taint-style source-to-sink analysis;
- compare-mode graph diffing.

Resource protections:

- cap nodes and edges;
- prioritize entry points and suspicious APIs;
- fall back to summary mode when limits are reached.

---

# 14. Native Library Analysis

## 14.1 MVP native inventory

For each `lib/<abi>/*.so`:

- ABI/path;
- size and hash;
- ELF class and endianness;
- architecture;
- entry point;
- program and section headers;
- imported and exported symbols where available;
- dynamic dependencies;
- build ID;
- stripped status;
- executable/writable segment combinations;
- suspicious strings;
- JNI exports;
- packing/high-entropy indicators.

## 14.2 Native findings

Possible facts:

- native code present;
- unusual ABI set;
- executable writable memory indicators;
- anti-debug symbols or strings;
- root/emulator checks;
- shell/process APIs;
- dynamic loading;
- sockets and TLS libraries;
- embedded executable blobs;
- JNI methods associated with suspicious Java callers.

## 14.3 Implementation options

### Option A — Focused Kotlin parser
Pros:
- no native dependency;
- easier Play compatibility;
- memory safety.

Cons:
- substantial implementation effort;
- limited advanced parsing initially.

### Option B — Rust parser via JNI
Pros:
- memory-safe native performance;
- mature binary-parsing ecosystem.

Cons:
- build complexity;
- ABI size;
- 16 KB page-size and NDK maintenance;
- JNI attack surface.

### Option C — LIEF or similar native library
Pros:
- broad format support.

Cons:
- large footprint;
- native complexity;
- dependency security and licensing review;
- may be excessive for MVP.

Recommendation: begin with a focused read-only ELF parser or carefully selected pure-Java library. Defer full disassembly.

---

# 15. IOC Extraction and Normalization

## 15.1 Indicator types

- domain;
- URL;
- IPv4;
- IPv6;
- email;
- phone number only when context is strong;
- file hash;
- certificate fingerprint;
- package name;
- Android component;
- mutex/registry path in future PE support;
- file path;
- user agent;
- bot token/webhook;
- cryptocurrency address in future.

## 15.2 IOC model

```json
{
  "type": "DOMAIN",
  "displayValue": "example.com",
  "canonicalValue": "example.com",
  "defangedValue": "example[.]com",
  "source": {
    "container": "base.apk",
    "entry": "classes.dex",
    "class": "Lcom/example/Net;",
    "method": "connect()V",
    "offset": 1234
  },
  "confidence": "HIGH",
  "context": "URL argument to HTTP client",
  "classification": ["PUBLIC", "NETWORK_DESTINATION"]
}
```

## 15.3 Normalization rules

- keep original evidence unchanged;
- canonicalize separately;
- use safe IDNA processing;
- lowercase domain hostnames;
- preserve URL path/query exactly in evidence but optionally redact likely secrets;
- normalize IPv6 presentation;
- identify private/reserved addresses;
- defang only in presentation/export, not canonical storage;
- deduplicate by type and canonical value while preserving all evidence locations.

## 15.4 IOC export safety

CSV cells beginning with `=`, `+`, `-`, or `@` must be escaped to prevent spreadsheet formula injection. HTML and Markdown must be escaped. Full tokens and credentials should be redacted by default.

---

# 16. Facts, Rules, Findings, Severity, and Confidence

## 16.1 Three-layer model

### Facts
Direct observations:

- permission declared;
- method referenced;
- string present;
- component exported;
- certificate expired;
- archive ratio exceeded.

### Rules
Deterministic logic over facts:

- one fact may create an informational finding;
- combinations may create higher-severity findings;
- exclusions and legitimate-context checks reduce severity.

### Findings
User-facing interpretations containing evidence and limitations.

## 16.2 Rule schema

Conceptual YAML or JSON authoring format:

```yaml
id: APK-CORR-ACCESSIBILITY-OVERLAY-BOOT-001
version: 1
status: stable
title: Accessibility control combined with overlay and boot persistence
category: behavior_combination
severity: high
confidence: high
scope: apk
when:
  all:
    - fact: MANIFEST_ACCESSIBILITY_SERVICE
    - any:
        - fact: PERMISSION_SYSTEM_ALERT_WINDOW
        - fact: CODE_USES_OVERLAY_API
    - any:
        - fact: MANIFEST_BOOT_RECEIVER
        - fact: CODE_USES_BOOT_ACTION
unless:
  any:
    - fact: KNOWN_ACCESSIBILITY_ASSISTIVE_CATEGORY_WITH_DISCLOSURE
simpleExplanation: >-
  The app can request broad screen interaction, display over other apps,
  and start again after the phone boots.
analystExplanation: >-
  This combination is frequently relevant to remote-control, credential-theft,
  or persistent automation behavior, but legitimate accessibility and automation
  tools may use the same capabilities.
limitations:
  - Static analysis cannot prove the service is enabled or used.
remediation:
  - Do not enable Accessibility unless the publisher and purpose are trusted.
attackMappings:
  - framework: MITRE_ATTACK_MOBILE
    technique: T1453
```

Rule files are data, not executable code. The interpreter supports only a restricted declarative grammar.

## 16.3 Severity scale

- **Informational:** descriptive fact with little inherent concern.
- **Low:** noteworthy configuration or capability.
- **Medium:** meaningful risk requiring context.
- **High:** strong suspicious combination, major exposure, or dangerous capability.
- **Critical:** reserved for exceptionally strong evidence such as verified destructive payload structure, credential-theft configuration, or integrity failure with major impact. Use rarely.

## 16.4 Confidence scale

- **Low:** weak heuristic, ambiguous pattern, or incomplete parse.
- **Medium:** multiple supporting facts or reliable single pattern with uncertainty.
- **High:** direct structural/code evidence with clear interpretation.

Severity and confidence are independent.

## 16.5 Overall risk presentation

Do not reduce the report to an opaque score. Use a risk band with explanation:

- No major concerns observed
- Review recommended
- Suspicious capabilities observed
- High-risk indicators observed
- Analysis incomplete

If a numeric score is included for sorting, it must be secondary and documented.

Example scoring approach:

1. Assign bounded weights to stable findings.
2. Apply correlation bonuses only once per rule family.
3. Cap repeated low-level facts.
4. Reduce confidence when major stages fail or obfuscation prevents inspection.
5. Never output “safe.”
6. Provide top contributing findings.

## 16.6 Suppression and legitimate context

Rules may include contextual reductions:

- accessibility app category;
- enterprise device-management declaration;
- VPN app declaration;
- recognized open-source library pattern;
- test/debug build;
- target-SDK behavior;
- permission present but no related component or code reference.

Suppressions must be visible in Analyst mode so the engine is explainable.

## 16.7 Rule-pack integrity

MVP:

- rule pack bundled and signed inside the app package;
- version recorded in report;
- no remote rules.

Future:

- signed data-only updates downloaded by main process;
- signature verification with embedded public key;
- rollback protection;
- update transparency log;
- no executable expressions beyond restricted grammar;
- analysis process receives verified rule bytes through read-only descriptor.

---

# 17. Canonical Report Model

## 17.1 Report design principles

- canonical JSON is the source of truth;
- UI, Markdown, HTML, PDF, CSV, STIX, and other exports are projections;
- facts and interpretations remain separate;
- every evidence item points to provenance;
- partial analysis is explicit;
- report versions are immutable once finalized;
- user notes are stored separately and merged at render time.

## 17.2 Top-level report structure

```json
{
  "schemaVersion": "1.0.0",
  "reportId": "uuid",
  "caseId": "uuid",
  "createdAt": "ISO-8601",
  "analysisStartedAt": "ISO-8601",
  "analysisCompletedAt": "ISO-8601",
  "engine": {},
  "settings": {},
  "source": {},
  "containers": [],
  "files": [],
  "apk": {},
  "dex": [],
  "nativeLibraries": [],
  "indicators": [],
  "facts": [],
  "findings": [],
  "summary": {},
  "stageResults": [],
  "limitations": [],
  "errors": [],
  "integrity": {}
}
```

## 17.3 Finding model

```json
{
  "id": "finding-instance-uuid",
  "ruleId": "APK-DYNLOAD-001",
  "title": "Embedded code may be loaded dynamically",
  "category": "dynamic_code_loading",
  "severity": "HIGH",
  "confidence": "HIGH",
  "status": "ACTIVE",
  "simpleExplanation": "...",
  "analystExplanation": "...",
  "evidence": [
    {
      "type": "DEX_CALL_SITE",
      "fileId": "...",
      "dexName": "classes.dex",
      "className": "Lx/y/z;",
      "method": "a()V",
      "offset": 4242,
      "excerpt": "DexClassLoader(...)",
      "excerptEncoding": "escaped-text"
    }
  ],
  "limitations": ["Static analysis cannot confirm this path executes."],
  "recommendations": ["Do not install until the payload source is verified."],
  "mappings": [],
  "references": []
}
```

## 17.4 Integrity block

Include:

- source SHA-256;
- canonical report SHA-256;
- engine/rule versions;
- deterministic settings hash;
- export hash where applicable;
- indication of whether the original sample was retained or deleted.

Future professional mode may sign exported reports with an app-generated signing key, but must explain that this proves report origin from that device/app installation, not sample authenticity.

---

# 18. Report Formats and Presentation

## 18.1 Simple Report

Audience: ordinary users, help desk, managers.

Sections:

1. **Result banner**
   - risk band;
   - confidence;
   - completeness.
2. **What this file is**
   - APK/archive/type;
   - package and version;
   - source name;
   - hash copy action.
3. **Top reasons**
   - three to five strongest findings.
4. **What the app could do**
   - permission/capability cards in plain language.
5. **Recommended action**
   - do not install;
   - verify source;
   - preserve for security team;
   - no major concerns observed but not guaranteed safe.
6. **Privacy statement**
   - analyzed locally;
   - no execution;
   - no automatic upload.
7. **Limitations**
   - static analysis limitations.

Example:

```text
HIGH-RISK INDICATORS OBSERVED
Confidence: High | Analysis completeness: 92%

Why:
1. The app defines an Accessibility service that can interact with screen content.
2. It can display windows over other apps.
3. It starts after the device boots.
4. It contains an encrypted secondary DEX payload and code to load it.

Recommended action:
Do not install this file unless its publisher and purpose can be independently verified.
Static analysis cannot prove that every detected code path will execute.
```

## 18.2 Analyst Report

Suggested order:

1. Executive summary
2. Analysis metadata and completeness
3. File and container metadata
4. Hashes
5. APK package metadata
6. Signing and certificates
7. Permissions
8. Components and exposure
9. Deep links and intent filters
10. Network security configuration
11. DEX inventory
12. Code capabilities
13. Dynamic loading/reflection
14. Shell/root/anti-analysis
15. Network indicators
16. Native libraries
17. Embedded files and archives
18. IOC table
19. Correlated findings
20. ATT&CK or other framework mappings
21. Errors, skipped items, and limits
22. Analyst notes
23. Reproducibility and engine versions

## 18.3 JSON export

- exact canonical schema;
- UTF-8;
- stable property conventions;
- no secrets unredacted by default;
- optionally pretty printed;
- include schema URL once published;
- use semantic versioning.

## 18.4 Markdown export

Requirements:

- copy/paste friendly for GitHub, Jira, ticket systems, and notes;
- escape sample-controlled Markdown;
- use tables sparingly for mobile readability;
- include defanged IOCs by default;
- include a generated-at and version footer.

## 18.5 HTML export

Requirements:

- one self-contained file;
- no JavaScript required;
- no remote resources;
- embedded minimal CSS;
- strict escaping;
- printable layout;
- collapsible behavior should not depend on active script; use `<details>` if desired;
- content security metadata;
- clear redaction labels;
- accessible headings and contrast.

## 18.6 PDF export

Add only after report content and pagination are stable.

Options:

- native `PdfDocument` renderer;
- controlled print adapter from a local static document;
- a vetted PDF library.

Requirements:

- no remote content;
- no active links by default for suspicious URLs;
- defanged indicators;
- repeating headers/footers;
- table-of-contents for long reports;
- page numbers;
- report ID and source SHA-256 on every page footer;
- consistent wrapping of long class names and hashes;
- no invisible overflow.

## 18.7 IOC CSV

Columns:

```text
type,value,defanged_value,confidence,context,file,entry,class,method,offset
```

Protect against formula injection and preserve UTF-8.

## 18.8 Future export formats

- STIX 2.1 bundle;
- MISP event/package;
- SARIF 2.1.0 for code/config findings;
- CycloneDX-style component inventory where meaningful;
- Splunk lookup CSV;
- Elastic bulk JSON;
- Microsoft Sentinel watchlist CSV;
- human-readable incident timeline;
- comparison/diff report.


---

# 19. User Experience and Visual Design

## 19.1 Design direction

Use a professional security-tool appearance without imitating a terminal or overwhelming ordinary users.

Recommended characteristics:

- Material 3 foundation;
- dark and light themes;
- strong information hierarchy;
- neutral surfaces with severity color used only for findings;
- monospace only for hashes, paths, APIs, classes, and evidence;
- generous spacing;
- no animated “hacker” effects;
- accessible contrast;
- icons plus labels, never color alone;
- edge-to-edge layouts;
- adaptive list/detail panes on large screens.

Working navigation destinations:

- Home
- Cases
- Analyze
- Settings
- About

On compact phones, use bottom navigation for Home, Cases, and Settings, with Analyze as the primary floating or prominent action. On expanded screens, use a navigation rail or drawer and list-detail report layout.

## 19.2 Onboarding

Maximum three concise screens:

### Screen 1 — What it does
“Inspect APKs and supported archives without installing or running them.”

### Screen 2 — Privacy
“Analysis is local. The first release does not upload samples or require an account.”

### Screen 3 — Limitations
“Static analysis identifies capabilities and suspicious patterns, but cannot prove that code executed or guarantee a file is safe.”

Actions:

- **Analyze a demonstration sample**
- **Choose a file**
- **Continue to app**

Do not require sign-in, email, notification permission, storage permission, or a tutorial completion gate.

## 19.3 Home screen

Suggested layout:

```text
PocketLab
Local static malware triage

[ Analyze a file ]
[ Analyze an archive ]

Privacy status
✓ Offline analysis
✓ No execution
✓ No automatic uploads

Recent cases
- suspicious_update.apk     High risk     2 min ago
- sample_case.zip           Review        Yesterday

Quick actions
- Paste/hash lookup (future)
- Compare APKs (future)
- View rule pack
```

The distinction between **file** and **archive** may be removed after intake automatically detects both; separate buttons are useful only if archive workflow differs.

## 19.4 Intake confirmation screen

Sections:

- source icon and application;
- escaped filename;
- reported size;
- detected preliminary type;
- warning if extension/MIME disagree;
- retention setting;
- analysis profile;
- local-processing statement;
- unsupported or risky conditions.

Primary action: **Start local analysis**  
Secondary: **Cancel**

Advanced expandable area:

- hash while importing;
- maximum expansion policy;
- retain original sample toggle;
- password-protected archive options.

## 19.5 Archive preview screen

Display:

- archive integrity;
- encrypted status;
- entry count;
- declared expanded size;
- suspicious paths;
- supported analyzable entries;
- skipped entries;
- nested archive warning;
- password input if required.

Entry tree should use safe escaped display. Do not render embedded HTML or preview unknown files.

Actions:

- analyze recommended entries;
- choose entries in advanced mode;
- cancel;
- delete staged file.

## 19.6 Analysis progress screen

Stages:

1. Importing and hashing
2. Inspecting container
3. Parsing APK metadata
4. Verifying signatures
5. Parsing DEX files
6. Extracting indicators
7. Evaluating rules
8. Building report
9. Cleaning workspace

UI behavior:

- overall progress when measurable;
- indeterminate indicator when not;
- current stage and short explanation;
- elapsed time without promising completion time;
- cancel button;
- keep-screen-on toggle for current analysis;
- warnings as non-blocking events;
- ability to open partial report after minimum viable stages;
- clear notice if leaving the app may pause work.

## 19.7 Report dashboard

Compact layout:

```text
[ Risk band ] [ Confidence ] [ Completeness ]
Package / source
SHA-256 [copy]

Top findings
Permissions
Components
Code capabilities
Network indicators
Signing
Files
Limitations
```

Analyst mode adds tabs or sections:

- Overview
- Findings
- Manifest
- Code
- IOCs
- Files
- Raw metadata

On tablets/foldables:

- left pane: finding/section list;
- right pane: selected detail and evidence;
- optional supporting pane: file tree or raw evidence.

## 19.8 Finding detail screen

Must show:

- severity and confidence;
- simple explanation;
- analyst explanation;
- evidence count;
- evidence cards;
- why it matters;
- legitimate explanations;
- limitations;
- recommended action;
- related findings;
- framework mappings;
- rule ID and version.

Evidence card example:

```text
classes.dex
Lcom/example/loader/PayloadManager;
loadPayload()V @ 0x10A2

Referenced API:
dalvik.system.DexClassLoader.<init>

Associated string:
assets/payload.dat
```

## 19.9 Settings

### Privacy and storage

- default sample retention;
- report retention;
- delete all cases;
- require authentication for retained cases, future;
- export redaction defaults.

### Analysis

- Standard/Advanced profile;
- archive quotas, within hard limits;
- hash algorithms;
- native analysis toggle;
- deep DEX analysis toggle;
- IOC extraction options;
- show experimental findings.

### Appearance

- system/light/dark;
- text size follows system;
- reduce motion;
- monospace wrapping preference.

### About

- engine version;
- rule-pack version;
- report schema;
- licenses;
- privacy policy;
- security contact;
- source code link if open sourced;
- Play policy disclosures.

## 19.10 Accessibility

- TalkBack descriptions for severity and progress;
- minimum touch targets;
- no color-only meaning;
- support font scaling without clipped hashes or buttons;
- selectable and copyable technical text;
- keyboard and pointer navigation on large screens;
- focus order tests;
- reduced-motion support;
- use semantic headings.

---

# 20. Performance, Thermal, Memory, and Battery Management

## 20.1 Device capability profile

At analysis start, capture non-identifying local capability data:

- total and available storage;
- memory class;
- low-RAM device flag;
- ABI set;
- available processors;
- battery level/charging status only through non-sensitive APIs if useful;
- thermal status when available;
- current app memory budget.

Do not persist detailed hardware telemetry unless needed in the report diagnostics and clearly disclosed.

Profiles:

- Low-memory
- Standard
- High-memory

Profiles adjust soft limits and concurrency, not safety invariants.

## 20.2 Concurrency model

Default:

- one active case analysis at a time;
- one archive decompression stream at a time;
- bounded parallel hashing only for small independent entries;
- DEX methods analyzed in bounded worker pools;
- no unbounded coroutine launches;
- shared global memory budget.

Suggested concurrency:

```text
workers = min(availableProcessors - 1, configuredMax)
minimum 1
maximum 4 initially
```

Benchmark before increasing.

## 20.3 Memory strategy

- use streams and memory maps only after platform testing;
- avoid reading full APK/DEX into heap;
- use primitive arrays and integer indexes;
- lazy-load long strings;
- cap string length retained in report;
- spill large intermediate indexes to bounded scratch files if necessary;
- release parser structures after facts are emitted;
- do not render thousands of rows at once; use lazy lists and paging.

## 20.4 Thermal behavior

- observe thermal status when supported;
- reduce worker concurrency at elevated thermal levels;
- pause deep analysis at severe thermal status;
- show “Analysis paused to cool the device” rather than failing;
- resume only with user-visible state;
- recommend charging for large advanced analyses;
- do not hold aggressive wake locks.

## 20.5 Analysis budgets

Every stage receives:

```text
maxBytesRead
maxObjects
maxStrings
maxMethods
maxInstructions
maxGraphNodes
maxGraphEdges
maxRecursionDepth
maxWallTime
maxOutputBytes
```

Budgets are included in the report so incomplete analysis is reproducible.

## 20.6 Checkpointing

Checkpoint after major stages:

- source staged;
- archive inventory;
- APK metadata;
- signing;
- each DEX file;
- native inventory;
- IOC extraction;
- rules;
- report finalization.

If the main process dies, resume only from a safe checkpoint. Never trust a partially written result without checksum and completion marker.

## 20.7 Cancellation

Cancellation must:

- propagate from UI to orchestrator and isolated service;
- close descriptors;
- stop parser loops cooperatively;
- delete incomplete scratch data;
- retain partial report only if user chooses;
- mark stage state accurately;
- never leave a foreground service or worker orphaned in later phases.

---

# 21. Security Engineering Requirements

## 21.1 Exported components

MVP expected exported components:

- launcher activity;
- narrow share/intake activity if needed.

All services, providers, and receivers shall be non-exported unless explicitly required. Add an automated manifest test that fails the build when a new exported component appears without an allowlisted justification.

## 21.2 Intent safety

- use explicit intents internally;
- validate all external intents;
- remove or reject nested intents;
- do not forward untrusted intents;
- immutable PendingIntents only unless mutability is required and documented;
- verify URI grants;
- never launch a URI from sample evidence directly;
- copy indicators to clipboard in defanged form by default.

## 21.3 Native code policy

Before adding NDK code:

- ADR required;
- memory-safety rationale;
- supported ABIs;
- 16 KB page-size test;
- fuzzing strategy;
- symbol stripping and crash diagnostics plan;
- license review;
- CVE monitoring;
- sandbox/isolation validation;
- binary size budget.

## 21.4 Logging policy

Never log:

- sample contents;
- archive passwords;
- full URLs with tokens;
- possible secrets;
- analyst notes;
- private paths;
- external content URIs;
- full report JSON.

Use structured event IDs and sanitized values:

```text
ANALYSIS_STAGE_FAILED case=<random local id> stage=DEX_PARSE error=DEX_INVALID_LEB128
```

Release builds must disable verbose parser logs.

## 21.5 Crash reporting

MVP recommendation: no third-party crash-reporting SDK.

Provide:

- local sanitized crash report;
- user-controlled copy/export;
- no sample data;
- engine and stage version;
- stack trace with path redaction.

Future opt-in crash reporting requires privacy update, Data Safety update, payload inspection, and explicit user disclosure.

## 21.6 Supply-chain security

- pin dependency versions;
- use dependency verification/checksums;
- generate SBOM;
- monitor CVEs;
- minimize parser dependencies;
- verify release provenance;
- use protected signing credentials;
- separate upload key from app-signing key under Play App Signing;
- review GitHub Actions permissions;
- pin actions by commit SHA;
- prohibit untrusted pull-request secrets.

## 21.7 Report renderer security

- no WebView needed for in-app report viewing;
- HTML export is generated as inert text;
- escape every field;
- truncate oversized values with a downloadable/copyable raw section only when safe;
- no clickable suspicious links by default;
- show defanged URLs;
- warn before copying refanged indicators;
- protect CSV formula injection;
- remove control characters from PDF display while preserving escaped representation.

---

# 22. Testing Strategy

## 22.1 Test layers

### Unit tests

- checked arithmetic;
- path normalization;
- type signatures;
- hash pipeline;
- DEX LEB128 parsing;
- opcode decoding;
- IOC normalization;
- rule evaluation;
- report serialization;
- redaction;
- CSV/HTML escaping.

### Property-based tests

- arbitrary archive paths never escape root;
- encode/decode report round trips;
- IOC normalization idempotence;
- parser never reads outside supplied range;
- quotas always stop output above threshold;
- rule evaluation deterministic.

### Corpus tests

- valid synthetic APKs;
- multidex APK;
- no-code APK;
- debug-signed APK;
- v1/v2/v3 signed fixtures;
- malformed manifest;
- malformed resources;
- truncated DEX;
- duplicate ZIP entries;
- encrypted ZIP;
- nested ZIP;
- high-ratio controlled fixture;
- Unicode and traversal filenames;
- split APK sets;
- XAPK/APKS fixtures.

### Fuzzing

Fuzz targets:

- file-type detector;
- ZIP metadata parser;
- path normalizer;
- binary XML parser;
- resources parser;
- DEX header/map/parser;
- opcode decoder;
- ELF parser;
- IOC parser;
- report renderer.

Use JVM fuzzing and native libFuzzer where applicable. Crashes become minimized regression fixtures.

### Instrumentation tests

- file picker flow with test provider;
- share target with malformed intents;
- process isolation;
- cancellation;
- process death and resume;
- export with FileProvider;
- deletion;
- adaptive layout;
- accessibility semantics.

### UI screenshot tests

- light/dark;
- compact/medium/expanded widths;
- long filenames;
- very long hashes/classes;
- high font scale;
- right-to-left locale;
- partial report;
- error states;
- no findings;
- hundreds of findings.

### Benchmark tests

Measure:

- staging throughput;
- hashing throughput;
- archive enumeration;
- DEX parse time;
- instructions per second;
- memory peak;
- report rendering;
- export time;
- thermal throttling behavior.

## 22.2 Test corpus policy

Public repository:

- synthetic inert fixtures;
- intentionally malformed but non-executing data;
- source code used to generate fixtures;
- hashes and expected results;
- EICAR-like harmless signature text where useful;
- no real malware.

Private controlled research corpus, outside repository:

- legally obtained samples;
- access controlled;
- password protected;
- hash-indexed;
- never synchronized to personal cloud storage casually;
- analyzed only on designated test devices or lab systems;
- no inclusion in Play review artifacts.

## 22.3 Golden reports

For each canonical fixture, store expected canonical JSON with normalized timestamps removed. A change requires:

- review of intended behavior;
- schema migration if needed;
- updated explanation;
- rule-pack version bump where appropriate.

## 22.4 Adversarial test cases

- content provider lies about size;
- content provider blocks mid-read;
- content changes during staging;
- archive declares negative/overflowing values;
- duplicate normalized paths;
- filenames containing RTL override;
- one million empty entries simulated/mocked;
- recursive nested archives hitting depth limit;
- DEX with huge counts and tiny file;
- string with extreme length;
- method graph explosion;
- HTML/Markdown/CSV injection strings;
- report over output budget;
- isolated process crash at every stage.

## 22.5 Definition of “safe parser failure”

A safe parser failure:

- does not crash the main app;
- does not escape the workspace;
- does not exceed quotas;
- closes descriptors;
- creates a stable error code;
- records partial completeness;
- allows deletion/export of partial diagnostics;
- passes repeated reanalysis without corrupting state.

---

# 23. Detailed Phased Development Plan

Sprint counts are planning estimates, not promises. Each phase ends with a demonstrable artifact and acceptance gate.

## Phase 0 — Product, Policy, and Architecture Lock

**Purpose:** Remove ambiguity before coding the analysis engine.

### Work items

1. Choose final working package namespace under Pine and Packets LLC.
2. Create repository, branch protections, CI skeleton, AGENTS.md, and ADR process.
3. Revalidate Google Play policies and API 36 requirements.
4. Decide initial `minSdk` through a device-support and engineering-cost matrix.
5. Write privacy model and no-network MVP ADR.
6. Write isolated-process ADR.
7. Define report schema 0.1.
8. Define test corpus policy.
9. Prototype app navigation and adaptive shell.
10. Build dependency-spike matrix:
    - archive library;
    - DEX parser;
    - APK signing verifier;
    - binary XML/resources parser.
11. Create a risk register.
12. Create mockups for all primary screens.
13. Define exact MVP release scope and explicit exclusions.

### Deliverables

- approved architecture diagram;
- policy checklist;
- permission allowlist/denylist;
- repository skeleton;
- initial UI prototype;
- report schema draft;
- dependency spike plan;
- threat model v0.1.

### Acceptance gate

- build targets API 36;
- manifest contains no unapproved permission;
- no network dependency;
- architecture supports isolated analysis;
- all MVP requirements have IDs and tests planned;
- owner approves product wording and non-goals.

---

## Phase 1 — Application Foundation and Case Lifecycle

**Purpose:** Build a stable Android application before introducing hostile parsing.

### Work items

1. Configure Gradle convention plugins and version catalog.
2. Implement Compose theme, edge-to-edge, predictive back, adaptive navigation.
3. Implement onboarding.
4. Implement Home, Cases, Settings, About placeholders.
5. Implement Room case index.
6. Implement encrypted report-blob storage prototype.
7. Configure backup exclusions and no-backup workspace.
8. Implement case state machine.
9. Implement local demonstration case with static fixture data.
10. Implement deletion, retention settings, and cleanup worker that does not require long-running background execution.
11. Implement sanitized local diagnostics.
12. Add forbidden-permission and exported-component tests.
13. Add accessibility and large-font baseline tests.

### Deliverables

- functional shell app;
- case creation/open/delete;
- encrypted dummy report;
- adaptive UI;
- no file parsing yet.

### Acceptance gate

- cases survive rotation and normal process recreation;
- deletion removes all case files and DB indexes;
- no case content enters backup;
- large-screen layouts are usable;
- no dangerous permission;
- automated manifest policy tests pass.

---

## Phase 2 — Secure File Intake, Hashing, and Type Detection

**Purpose:** Safely ingest arbitrary user-selected files without deep parsing.

### Work items

1. Implement `ACTION_OPEN_DOCUMENT` intake.
2. Implement narrow `ACTION_SEND` target.
3. Validate external intents and content URIs.
4. Implement metadata display escaping and Unicode-control visualization.
5. Implement confirmation screen.
6. Implement bounded staging into `original.bin`.
7. Compute SHA-256/SHA-1/MD5 in one pass.
8. Implement cancellation and partial-file cleanup.
9. Implement available-storage and hard-size checks.
10. Implement magic/structural file-type detector.
11. Implement duplicate SHA-256 workflow.
12. Implement intake errors and partial diagnostics.
13. Build malicious content-provider test double.
14. Benchmark copy/hash performance.

### Deliverables

- import APK/ZIP/unknown file;
- safe local staging;
- hashes;
- type result;
- duplicate detection.

### Acceptance gate

- provider-reported size is never trusted;
- byte hard cap works;
- cancellation closes input and deletes partial output;
- extension/MIME mismatch is detected;
- share target never auto-analyzes;
- no main-thread I/O;
- hostile filenames render safely.

---

## Phase 3 — Archive Preflight and Ordinary ZIP Support

**Purpose:** Safely inventory and selectively read ordinary ZIP archives.

### Work items

1. Complete archive-library spike and ADR.
2. Implement central-directory validation.
3. Implement checked arithmetic for totals.
4. Implement path normalization and traversal rejection.
5. Implement entry, size, ratio, depth, time, and output limits.
6. Implement archive tree model.
7. Implement runtime decompression counters.
8. Implement selective extraction to randomized private names.
9. Implement duplicate normalized-path detection.
10. Implement nested archive identification but initially stop before recursive analysis.
11. Implement archive preview UI.
12. Implement archive report section.
13. Add malformed ZIP, Zip64, Unicode, duplicate, traversal, and controlled high-ratio fixtures.
14. Fuzz archive preflight and path normalization.

### Deliverables

- safe ZIP inventory;
- user-visible archive preview;
- selective read/extract;
- quota findings.

### Acceptance gate

- no fixture escapes workspace;
- no full-archive extraction by default;
- runtime counters stop lying metadata;
- integer overflow tests pass;
- archive parser failure does not crash main app;
- cleanup is complete after cancellation.

---

## Phase 4 — APK Container, Manifest, Resources, and Package Metadata

**Purpose:** Produce a useful report before DEX deep analysis.

### Work items

1. Detect APK structurally inside raw files and ZIP entries.
2. Implement APK critical-entry validation.
3. Compare platform package parsing with internal parsing.
4. Parse binary AndroidManifest XML.
5. Parse relevant `resources.arsc` values or integrate vetted parser.
6. Resolve package/application/component names.
7. Extract permissions, components, features, SDK values, flags, and intent filters.
8. Parse selected XML resources:
   - network security config;
   - FileProvider paths;
   - accessibility config;
   - device admin config;
   - backup rules.
9. Implement permission knowledge base by API level.
10. Implement manifest facts and initial rules.
11. Implement APK file inventory.
12. Implement package summary UI.
13. Add manifest corruption and namespace edge cases.

### Deliverables

- APK metadata report;
- permissions and components;
- deep links;
- selected security configuration;
- file inventory.

### Acceptance gate

- multidex APK inventory works even before DEX parsing;
- manifest values resolve consistently on fixtures;
- exported-effective logic respects target SDK behavior;
- component evidence is traceable;
- Simple Report can explain permissions without code analysis;
- malformed manifest produces partial report.

---

## Phase 5 — Signing and Certificate Analysis

**Purpose:** Provide trustworthy package-integrity information.

### Work items

1. Complete AOSP `apksig` compatibility spike.
2. Implement signature-scheme detection.
3. Implement cryptographic verification where supported.
4. Extract signer certificates and fingerprints.
5. Parse signing lineage where available.
6. Detect debug certificates and anomalies.
7. Handle v1/v2/v3/v3.1/v4 limitations explicitly.
8. Implement split-package signer comparison model.
9. Add signing section UI and export.
10. Build signed fixture matrix.

### Deliverables

- signing status;
- certificate detail;
- verification errors/warnings;
- clear integrity explanation.

### Acceptance gate

- app never calls a signature “valid” without verification;
- certificate fields are escaped;
- fixtures cover multiple schemes and invalid signatures;
- report states that a valid signature is not proof of benign behavior.

---

## Phase 6 — DEX Parser and Code Inventory

**Purpose:** Build a robust, bounded DEX foundation.

### Work items

1. Complete DEX library/custom-parser ADR.
2. Implement header, map, section, and bounds validation.
3. Parse strings/types/protos/fields/methods/classes.
4. Parse class data and code items lazily.
5. Decode opcodes with validated references.
6. Build API, method, string, and class indexes.
7. Handle multidex with global budgets.
8. Create evidence-location model.
9. Create code inventory UI.
10. Add malformed DEX corpus and fuzz targets.
11. Benchmark large DEX files.
12. Implement safe fallback summary mode.

### Deliverables

- DEX inventory;
- classes/methods/API references;
- bounded instruction decode;
- evidence snippets.

### Acceptance gate

- no out-of-range read on fuzz corpus;
- large method/class counts hit controlled budgets;
- multidex results preserve file provenance;
- engine memory remains under target profile budget;
- parser crash is isolated.

---

## Phase 7 — Capability Analysis, IOC Extraction, and Correlation Rules

**Purpose:** Transform raw DEX facts into useful security findings.

### Work items

1. Implement API capability map.
2. Implement limited constant propagation.
3. Implement reflection and dynamic-loading analysis.
4. Implement shell/process analysis.
5. Implement network API and endpoint extraction.
6. Implement accessibility/overlay/boot/device-admin/package-installation capability facts.
7. Implement WebView risk facts.
8. Implement root/emulator/debugger-check facts.
9. Implement IOC normalization and provenance.
10. Implement declarative rule interpreter.
11. Create initial stable rule pack.
12. Add severity/confidence model.
13. Implement overall risk band and completeness adjustment.
14. Build false-positive review fixtures from legitimate synthetic apps.
15. Add rules documentation and authoring tests.

### Deliverables

- meaningful findings;
- IOC table;
- top reasons;
- evidence-linked capability report;
- rule pack v1.

### Acceptance gate

- every finding has evidence and limitations;
- no rule marks malware from one weak indicator;
- report remains deterministic;
- rule interpreter cannot execute code;
- false-positive review completed for sensitive rule combinations;
- IOC extraction does not contact indicators.

---

## Phase 8 — Analyst-Grade Reporting and Export

**Purpose:** Turn findings into a professional deliverable.

### Work items

1. Finalize canonical report schema 1.0.
2. Implement Simple/Analyst synchronized views.
3. Implement finding detail and evidence navigation.
4. Implement JSON export.
5. Implement Markdown export.
6. Implement self-contained HTML export.
7. Implement IOC CSV with formula-injection defense.
8. Implement report redaction settings.
9. Implement analyst notes.
10. Implement report and export hashes.
11. Implement `ACTION_CREATE_DOCUMENT` and FileProvider sharing.
12. Add golden report snapshots.
13. Conduct report readability review on phones and tablets.
14. Defer PDF or implement only after HTML/Markdown stability.

### Deliverables

- complete report UX;
- JSON/Markdown/HTML/CSV exports;
- evidence navigation;
- redaction controls.

### Acceptance gate

- exported HTML contains no remote resources or active script;
- all malicious text fixtures render safely;
- JSON validates against schema;
- CSV formula injection tests pass;
- user can delete exported temporary files;
- report clearly displays incomplete stages.

---

## Phase 9 — Process Isolation, Hardening, and Recovery

**Purpose:** Move hostile parsing behind a hardened process boundary and prove failure safety.

Some isolation work should be prototyped earlier; this phase makes it production-grade.

### Work items

1. Implement final isolated service.
2. Define versioned AIDL and result-stream protocol.
3. Pass read-only file descriptors.
4. Enforce engine-side budgets independent of main-process settings validation.
5. Implement process crash detection and restart.
6. Implement output-stream integrity and size limits.
7. Implement checkpoint recovery.
8. Run parser crash injection at every stage.
9. Verify isolated process has no permissions and no network.
10. Verify no direct case-storage access.
11. Harden exported intake component.
12. Perform local threat-model review and penetration testing.
13. Run dependency and native security review.

### Deliverables

- production isolation boundary;
- robust recovery;
- updated threat model;
- security test report.

### Acceptance gate

- forced analyzer crash leaves main app usable;
- corrupt output is rejected;
- analyzer cannot open arbitrary app files;
- analyzer has no network capability;
- cancellation works across IPC;
- repeated analysis does not leak descriptors or memory materially.

---

## Phase 10 — Password-Protected ZIP, Nested Archives, and Package Sets

**Purpose:** Complete malware-sample transport workflows.

### Work items

1. Add vetted encrypted-ZIP library or implementation.
2. Support limited encryption algorithms.
3. Implement password lifecycle and `infected` shortcut.
4. Add nested archive recursion under global budgets.
5. Add APKS parsing.
6. Add XAPK tolerant parsing.
7. Add multi-file split APK intake.
8. Merge and compare split manifests/signatures.
9. Add archive correlation report.
10. Add case ZIP support with notes/text inventory.
11. Extend fuzzing and quota tests.

### Deliverables

- encrypted ZIP workflow;
- nested archives;
- APKS/XAPK/split package reports.

### Acceptance gate

- passwords are never logged or persisted;
- wrong-password handling is controlled;
- global quotas apply across recursion;
- split package inconsistencies are reported;
- no archive entry is executed or installed.

---

## Phase 11 — Native ELF Analysis and Advanced APK Intelligence

**Purpose:** Improve detection of native and protected Android malware.

### Work items

1. Implement/complete ELF parser.
2. Extract imports, exports, dependencies, JNI, strings, and segment protections.
3. Correlate Java native-load sites with libraries.
4. Add protector/packer fingerprints.
5. Add high-entropy embedded payload analysis.
6. Add app-comparison groundwork.
7. Evaluate YARA/YARA-X integration in isolated process.
8. Evaluate built-in data-only signature packs.
9. Add native corpus and fuzzing.

### Deliverables

- native library findings;
- JNI correlation;
- packer/protector context;
- optional signature scanning prototype.

### Acceptance gate

- native implementation supports required page sizes/ABIs;
- NDK dependency review complete;
- signature rules are data-only and bounded;
- false-positive language remains cautious;
- binary size remains within product budget.

---

## Phase 12 — Public Beta and Play Store Release

**Purpose:** Validate product behavior, policy posture, and reliability.

### Work items

1. Complete privacy policy and in-app policy page.
2. Complete Data Safety form.
3. Prepare store listing and screenshots.
4. Provide inert demo sample.
5. Prepare reviewer instructions.
6. Run closed test with cybersecurity and ordinary users.
7. Gather local opt-in diagnostics manually without analytics SDK.
8. Validate battery, heat, memory, and crash behavior across device classes.
9. Conduct accessibility review.
10. Conduct Play pre-review and permission audit.
11. Run release checklist and reproducible build.
12. Publish staged rollout.
13. Monitor reviews and Play vitals without collecting sample data.

### Deliverables

- production release;
- policy package;
- support and security-reporting process;
- rollback plan.

### Acceptance gate

- no policy-declaration mismatch;
- no severe crash/ANR issue;
- no sample bytes in logs;
- privacy statements match implementation;
- deletion works;
- report limitations are visible;
- support documentation published.

---

## Phase 13 — Optional Network Intelligence

**Purpose:** Add value without weakening the local-first trust model.

This is not part of MVP and requires a separate privacy and policy review.

Possible features:

- hash-only reputation lookup;
- user-supplied VirusTotal or enterprise API key where licensing permits;
- RDAP/DNS/TLS lookup for extracted indicators;
- signed rule-pack updates;
- CISA KEV/EPSS enrichment for embedded vulnerability identifiers;
- organization webhook/ticket export.

Requirements:

- explicit feature enablement;
- clear disclosure of exactly what leaves device;
- preview before transmission;
- no sample upload by default;
- isolated analyzer still has no network;
- network module receives only user-approved values;
- privacy policy and Data Safety updated;
- commercial API terms reviewed;
- offline functionality remains complete.

---

## Phase 14 — Generic Artifact Framework and Format-Expansion Platform

**Purpose:** Convert the single-purpose Android/APK pipeline into a general static-analysis platform
capable of arbitrary artifact types, nested artifacts, containers, recursive analysis, shared case-level
budgets, layered detection, multi-analyzer fan-out, partial analysis, parser-version tracking, and
cross-artifact correlation.

**Source of authority:** `docs/AUTHORITATIVE_EXISTING_MATERIAL.md` and `docs/FILE_FORMAT_EXPANSION_REVIEW.md`.

### Work items

1. Define the generic artifact model in `core:model`:
   - stable `artifactId`; `parentId`; container relation; original filename; sanitized display name;
     claimed MIME; detected type; detected subtype; size; hashes; metadata; indicators; findings;
     evidence; children; parser errors; completeness; limitations; `analyzerId`/`analyzerVersion`.
2. Define `AnalysisContext` + `CaseBudget` in `engine:api`:
   - global case-level accounting for bytes read, bytes decompressed, temp storage, artifact count,
     archive entries, recursion depth, strings, indicators, findings, evidence, parser operations,
     analysis duration, report size; a nested container must never reset limits.
   - cooperative cancellation; timeout; bounded reader; workspace; child-artifact/finding/IOC/error emitters.
3. Implement `AnalysisDispatcher` (registry) in `engine:orchestrator`:
   - layered detection: content signature → structural validation → container characteristics →
     advisory MIME → advisory extension; report mismatches.
   - allow multiple safe analyzers on one artifact (polyglots) and recursive child dispatch.
4. Wire the framework into `AnalysisPipeline`; enforce all `max*` counters; populate report
   `containers`/`files`/`facts`/`artifactTree`; record analyzer versions.
5. Connect real cancellation from `AnalysisOrchestrator.cancel()`; enforce wall-time timeout in both
   in-process and isolated paths.
6. Update report schema to `1.1.0` (additive) with `artifacts`/`analyzerInfo`; bump golden reports.
7. Add tests: budget inheritance across nested containers, cancellation, timeout, dispatcher routing,
   polyglot multi-analysis, report population, deterministic artifact tree.
8. Write `docs/adr/ADR-0004-generic-artifact-analysis-framework.md`.

### Deliverables

- `Artifact`/`ArtifactNode` in `core:model`;
- `AnalysisContext`/`CaseBudget`/`AnalyzerInfo` in `engine:api`;
- `AnalysisDispatcher` in `engine:orchestrator`;
- populated `containers`/`files`/`facts`/`artifactTree` in reports;
- enforced shared quotas + cancellation + timeout;
- ADR-0004 and this plan section.

### Acceptance gate

- existing APK/DEX/archive tests still pass;
- nested containers consume one shared budget (test);
- a parser failure marks artifact incomplete, never false-clean;
- cancellation interrupts a long parse; timeout aborts with `TIMEOUT`;
- report determinism preserved; schema `1.1.0`; golden reports updated;
- no new permission, export, or networking.

---

## Phase 15 — Format Expansion Stages (Sequential)

Each stage below is one complete analyzer. Every stage follows the identical discipline:
review spec → update threat model → requirements → dependency review → fixtures → baseline tests →
smallest secure implementation → focused tests → fuzz/property tests → regression → manifest/permission
verification → no-execution verification → report/UI → docs → status update → coherent commit.

Stage order (from `docs/AUTHORITATIVE_EXISTING_MATERIAL.md`), implemented one at a time:

### Phase 15.1 — PDF (Stage 1) ✅ IMPLEMENTED (`:engine:pdf`)
`.pdf` — version, object counts, xref/trailer, object streams, encryption, signatures, metadata, page
count, embedded files, JavaScript, OpenAction/AA, Launch/URI actions, AcroForms, XFA, annotations,
RichMedia, remote resources, images, URLs/domains/IP/email indicators, structural abnormalities.
Never execute JS or follow URLs. Malformed fuzz tests.

Status note: `:engine:pdf` `PdfScanner`/`PdfAnalyzer` (analyzer id `pdf.analyzer` v1.0.0) reads a bounded
16 MiB Latin-1 window to detect header, `%%EOF`, `/JavaScript`/`/JS`, `/OpenAction`, `/Launch`,
`/AcroForm`, `/XFA`, `/EmbeddedFiles`/`/Filespec`, annotations, `/RichMedia`, `/URI`/`/GoToR`,
`/Encrypt`, `/Metadata`, and signature tokens, and extracts defanged URL indicators. Never decodes/executes
JS, never extracts embedded files, never contacts URIs. Registered in `AnalyzerOrchestrator.analyzerRegistry()`.
Incomplete on truncation/parser-error (never false-clean). Extracted parse currently skips full object-tree
graph reconstruction, page count, and font/image enumeration (future scope).

### Phase 15.2 — OOXML (Stage 2) ✅ IMPLEMENTED (`:engine:ooxml`)
`.docx/.docm/.dotx/.dotm`, `.xlsx/.xlsm/.xlsb/.xltx/.xltm/.xlam`,
`.pptx/.pptm/.ppsx/.ppsm/.potx/.potm/.sldx/.sldm/.ppam` — reuse hardened ZIP; `[Content_Types].xml`,
relationships, external relationships, remote templates, hyperlinks, embedded files/OLE, VBA, ActiveX,
macros, external connections, custom XML, signatures, encryption, hidden sheets/slides, network-significant
formulas, high-entropy objects, content-type/extension mismatches, nested artifacts. Never assume a
non-macro extension is safe.

Status note: `:engine:ooxml` `OoxmlScanner`/`OoxmlAnalyzer` (analyzer id `ooxml.analyzer` v1.0.0) opens
the package through a bounded read-only `SeekableByteChannel` over `ArtifactRef` using commons-compress
`ZipFile` (nothing extracted to disk), enumerates parts under caps, and reads only small `*.rels` parts
to collect external relationship targets (hyperlinks, remote/external links) as defanged indicators. Detects
`[Content_Types].xml`, `vbaProject.bin`/`vbaData.xml` (VBA), `activeX`, `embeddings/`+`oleObject` (embedded
OLE), `xl/externalLinks/` (external data links), `customXml/`, and `_xmlsignatures/`. Never executes macros,
never extracts embedded binary parts, never contacts targets. Registered in
`AnalyzerOrchestrator.analyzerRegistry()`. Incomplete on truncation/parser-error (never false-clean).
`DetectedType.OOXML` added; the layered detector maps ZIP-signature + OOXML extension as an OOXML structural
signal. Current scan collects package parts and relationships but skips full part-body parsing (e.g. VBA
bytecode, embedded connection XML bodies), crafted macro/Office formula internals, and hidden sheet/slide
enumeration (future scope).

### Phase 15.3 — Legacy OLE/CFB (Stage 3) ✅ IMPLEMENTED (`:engine:ole`)
`.doc/.dot/.xls/.xlt/.xla/.ppt/.pps/.pot/.ppa/.rtf` — bounded compound-file parser: directory, streams,
VBA, XLM, embedded objects, ActiveX, external links, DDE where supportable, metadata, suspicious streams,
high-entropy data, embedded files, indicators. Treat as hostile binary attack surface. Extensive malformed
CFB fixtures + fuzz/property tests.

Status note: `:engine:ole` `OleScanner`/`OleAnalyzer` (analyzer id `ole.analyzer` v1.0.0) validates the
CFB header/geometry (magic, byte order, version 3/4, sector shift), builds the FAT from the inline DIFAT
and DIFAT-sector chain, walks the bounded directory with chain-loop/out-of-range guards, emits a flat
stream inventory, detects VBA macro streams, embedded-OLE objects and suspicious names, and extracts
defanged URL/domain/IP/email indicators from a bounded set of small regular streams. It never executes,
extracts, or opens embedded objects and marks truncated/malformed containers incomplete (never
false-clean). Registered in `AnalyzerOrchestrator.analyzerRegistry()`. Mini-stream and deep property-set
(such as SummaryInformation) decoding and DDE/XLM structure analysis are future scope; fuzz tests cover
hostile CFB byte sequences.

### Phase 15.4 — Images and QR/barcode (Stage 4)
JPEG/PNG/GIF/WebP/HEIF/HEIC/AVIF/BMP/TIFF/SVG/SVGZ/ICO — dimensions, format, animation, EXIF, XMP, GPS,
camera/device metadata, ICC profiles, comments, thumbnails, trailing data, embedded artifacts,
signature inconsistencies, polyglot indicators, entropy anomalies. Local QR/Data Matrix/Aztec/PDF417/
Code 128 decoding with hostile-content classification (URL, custom URI, mailto, tel, sms, WiFi, vCard,
calendar, payment, text). SVG uses a dedicated static XML analyzer; never render hostile SVG in an
execution-capable WebView. Tests: extreme dimensions, malformed metadata, truncated images, decompression
bombs, XML entity attacks, excessive SVG nesting, hostile URIs.

### Phase 15.5 — Email containers (Stage 5)
`.eml/.msg/.tnef` — recursive MIME parsing: From/Reply-To/Return-Path/Sender/To/Cc/Subject/Message-ID/
Received/Authentication-Results, MIME structure, plain/HTML bodies, attachments, attached messages, links,
image resources; detect display-name/domain mismatch, From/Reply-To mismatch, URL display/target
differences, dangerous attachments, nested emails, malformed MIME, excessive nesting, attachment bombs.
Every attachment enters the normal artifact-analysis pipeline. Never rely on email-provided MIME types.

### Phase 15.6 — Calendar and contact formats (Stage 6)
`.ics/.ifb/.vcs` and `.vcf/.vcard` — METHOD/UID/ORGANIZER/ATTENDEE/SUMMARY/DESCRIPTION/LOCATION/URL/ATTACH/
conferencing URIs/recurrence/custom X-properties; vCard names/org/email/tel/URL/GEO/PHOTO/LOGO/CALURI/
embedded resources/custom properties. Never invoke URI values. Tests: line folding, Unicode, malformed
properties, excessive values, oversized embedded data.

### Phase 15.7 — HTML, web archives, structured text (Stage 7)
`.html/.htm/.xhtml/.mhtml/.mht/.xml/.json/.jsonl/.yaml/.yml/.txt/.md/.csv/.tsv` — HTML: scripts, external
scripts, iframes, forms, credential inputs, form actions, meta refresh, javascript:/data: URIs, external
images, CSS URLs, hidden elements, hyperlinks, base64, embedded artifacts, indicators. Never execute or
render as active content. XML: no DTD, no external entities, no XXE, bounded depth/attributes/entity
expansion/document size. CSV: formula-injection protection in report/export.

### Phase 15.8 — OpenDocument (Stage 8)
`.odt/.ods/.odp/.ott/.ots/.otp` — reuse ZIP + XML; manifest, scripts/macros, external links, embedded
files/documents, formulas, metadata, nested artifacts, suspicious XML.

### Phase 15.9 — Expanded archives (Stage 9)
`.7z/.rar/.tar/.gz/.tgz/.bz2/.tbz2/.xz/.txz/.zipx/.jar/.war`; later `.cab/.iso`. Every container:
bounded enumeration, global decompression quotas, traversal/absolute-path rejection, link handling,
duplicate-entry detection, nested artifacts, recursion limits, encrypted handling, cancellation,
deterministic cleanup.

### Phase 15.10 — Script analysis (Stage 10)
PowerShell, JavaScript, VBScript, WSF, HTA, batch/CMD, shell, Python, Perl, PHP, Ruby, Lua — never
interpret; extract obfuscation, encoded strings, Base64, download URLs, command construction,
persistence/credential/registry/scheduled-task/service references, encoded PowerShell, shellcode-like
arrays, IOCs. Evidence-based capability descriptions, never malware verdicts.

### Phase 15.11 — Windows PE and shortcuts (Stage 11)
`.exe/.dll/.sys/.scr/.cpl/.ocx`, `.msi/.msp`, `.lnk/.url` — PE headers, architecture, imports/exports,
sections, entropy, timestamps, resources, version metadata, digital signatures, TLS callbacks, debug
metadata, suspicious imports, packer indicators, embedded objects, indicators, static capability findings.
No emulation/execution.

### Phase 15.12 — ELF/Mach-O/IPA (Stage 12)
ELF binaries + `.so` (reuse `ElfAnalyzer`); Mach-O/`.dylib`; `.ipa` — reuse hardened ZIP; Info.plist,
entitlements, embedded frameworks, executable metadata, URLs, domains, permission usage descriptions,
signing metadata. No execution.

### Phase 15.13 — OneNote and compound docs (Stage 13)
`.one/.onepkg/.onetoc2` — structure, metadata, embedded files, images, hyperlinks, indicators, nested
artifacts. Only if a bounded, isolated-safe implementation is available; otherwise document deferral.

### Phase 15.14 — E-books (Stage 14)
`.epub/.mobi/.azw/.azw3` — reuse archive+HTML+XML+image; metadata, links, scripts, external resources,
embedded files, indicators.

### Phase 15.15 — Media containers (Stage 15)
MP4/MOV/MKV/WebM/AVI/3GP, MP3/M4A/AAC/WAV/FLAC/OGG/Opus, SRT/WebVTT/ASS/SSA — structural and metadata
analysis only with bounded memory-safe code; do not decode arbitrary media if it materially increases
parser attack surface.

### Phase 15.16 — Fonts and specialist formats (Stage 16)
TTF/OTF/WOFF/WOFF2 — evaluate only if security value exceeds additional binary parser attack surface.
Never load untrusted fonts into the Android UI rendering stack. May be deferred.

### Phase 15.17 — Cross-artifact correlation and unified report

Case-level correlation across artifacts (same domain in email body + PDF + QR; same hash in multiple
containers; Word embedded executable also attached separately; organizer domain vs meeting URL; QR URL
matching PDF link; Reply-To domain differing from login domain). Correlation findings must reference
exact artifacts and evidence; never infer causality from indicator equality. Unified report sections:
case overview, overall assessment, completeness, artifact tree, correlated findings, findings by
artifact, indicators, metadata, nested content, parser errors, resource limits, unsupported content,
limitations, engine/analyzer versions.

---

# 24. Future File-Type Extensions

Each format must be added as a separate parser module with its own threat model, quotas, corpus, fuzzing, facts, rules, UI, and report section. As of the format-expansion program (Phases 14–15), most families below are promoted into Phase 15 stages above; this section retains the original design guidance.

## 24.1 Standalone DEX and ODEX/VDEX

Priority: High after APK MVP.

Features:

- DEX parsing without APK context;
- multidex ZIP sets;
- ODEX/VDEX inventory where feasible;
- reduced manifest-dependent conclusions.

## 24.2 Java JAR

Priority: Medium.

Features:

- ZIP safety;
- manifest metadata;
- class inventory;
- bytecode API capabilities;
- signatures;
- embedded resources;
- scripts and native libraries.

## 24.3 Windows PE

Priority: High for broader cybersecurity audience, but after Android depth.

Static analysis:

- DOS/PE headers;
- architecture;
- sections and entropy;
- imports/exports;
- resources;
- Authenticode metadata and verification limitations;
- TLS callbacks;
- relocations;
- debug data;
- Rich header where useful;
- packer indicators;
- strings;
- capability rules;
- embedded payloads;
- .NET metadata where present.

No Windows execution.

## 24.4 Linux ELF

Priority: Medium.

Features:

- headers, segments, sections;
- dynamic symbols and dependencies;
- security properties such as PIE/NX/RELRO where determinable;
- strings and capabilities;
- architecture and build ID;
- embedded content.

## 24.5 Mach-O

Priority: Low to medium depending demand.

Static metadata, architectures, signatures/entitlements where feasible, imports, strings, and capabilities.

## 24.6 Scripts

Priority: High because implementation can be lighter than binary decompilation.

Formats:

- PowerShell;
- JavaScript/JScript;
- VBScript;
- shell;
- batch;
- Python;
- PHP;
- Lua.

Static features:

- encoding layers;
- Base64/hex/URL decoding with bounded transforms;
- command extraction;
- URLs and indicators;
- download/execute patterns;
- persistence commands;
- credential-access patterns;
- obfuscation metrics;
- deobfuscated preview with provenance.

Never execute scripts.

## 24.7 PDF

Priority: Medium.

Analyze:

- version and structure;
- objects and streams;
- JavaScript;
- OpenAction/AA;
- Launch actions;
- embedded files;
- URLs;
- forms;
- suspicious filters and malformed structures;
- metadata;
- encryption status.

Do not render suspicious PDF content through an embedded viewer during analysis.

## 24.8 Office documents

Priority: Medium to high.

Formats:

- OLE legacy documents;
- OOXML ZIP documents;
- macro-enabled variants;
- RTF.

Analyze:

- macros and VBA metadata;
- relationships and external templates;
- embedded OLE/packages;
- DDE fields;
- URLs;
- scripts;
- suspicious document properties;
- encrypted status;
- decompression limits.

## 24.9 Email files

Formats:

- EML;
- MSG later.

Analyze:

- headers;
- authentication results as provided, with caution;
- URLs;
- attachments;
- display-name/address mismatches;
- nested archives;
- body indicators.

## 24.10 Mobile configuration and profiles

Potentially analyze:

- Android configuration exports;
- iOS configuration profiles;
- certificates;
- VPN/Wi-Fi payloads;
- MDM enrollment endpoints.

## 24.11 Firmware and disk images

Long-term only. Too resource-intensive for early phone workflows. Consider inventory-only support with strict size limits or a desktop companion.

---

# 25. Advanced Future Features

## 25.1 APK comparison

Compare two reports or samples:

- hashes;
- certificates;
- version;
- permissions added/removed;
- components changed;
- deep links;
- DEX classes/methods;
- API capabilities;
- domains/URLs;
- native libraries;
- embedded files;
- findings.

Use cases:

- official vs suspicious APK;
- old vs new version;
- repackaged app detection;
- campaign sample families.

## 25.2 Certificate and family pivoting

Local case database may group:

- same signer fingerprint;
- same package name;
- same embedded domain;
- same library hash;
- same secondary payload hash.

No installed-app enumeration required.

## 25.3 Rule authoring workbench

Professional future mode:

- create data-only local rules;
- test against retained reports or synthetic fixtures;
- preview matches;
- export/import signed rule packs;
- never accept executable plugins.

## 25.4 Desktop/lab companion

Optional future product:

- phone sends user-approved sample/report to a user-owned lab;
- desktop performs deeper decompilation or dynamic analysis;
- phone receives result;
- end-to-end encryption and explicit confirmation;
- not required for core app.

## 25.5 AI-assisted explanation

Only after deterministic reporting is mature.

Rules:

- AI explains existing evidence; it does not invent evidence.
- local model preferred if practical.
- cloud use requires opt-in and preview/redaction.
- never transmit sample bytes automatically.
- preserve deterministic non-AI report.
- clearly label AI-generated text.
- enforce safety boundaries against exploit or malware-generation assistance.

---

# 26. Product Analytics, Monetization, and Trust

## 26.1 Analytics

MVP: none.

Use closed-test interviews, issue reports, optional sanitized diagnostic exports, and Play vitals.

If analytics are added later:

- no sample names, hashes, IOCs, package names, report content, file sizes precise enough to fingerprint, or analyst notes;
- explicit privacy review;
- minimal event set;
- opt out;
- Data Safety update;
- SDK security review.

## 26.2 Monetization recommendation

Avoid ads in a security analysis application because ad SDKs weaken privacy messaging and increase dependency/policy risk.

Possible model:

- free core APK analysis;
- one-time Pro unlock or subscription for advanced DEX analysis, comparison, professional exports, larger limits, and additional formats;
- enterprise licensing for custom rules and integrations;
- no feature should falsely imply that free users receive unsafe or incomplete basic warnings.

All payment features must remain separated from sample processing.

## 26.3 Trust features

- publish privacy architecture;
- consider open-sourcing parser/rule engine or entire app;
- reproducible builds where practical;
- public security contact;
- vulnerability disclosure policy;
- visible engine and rule versions;
- no hidden network traffic;
- optional network-status proof screen showing offline MVP has no permission;
- clear limitations.

---

# 27. Play Store Submission Checklist

## 27.1 Technical

- target API requirement current;
- app bundle generated;
- 64-bit/native compliance if native code exists;
- 16 KB page-size tests if native code exists;
- no forbidden permissions;
- exported component audit;
- backup exclusions verified;
- release logging disabled;
- no test endpoints or secrets;
- no sample files accidentally included except inert demo fixture;
- app size reviewed;
- crash/ANR baseline acceptable.

## 27.2 Policy

- privacy policy matches behavior;
- Data Safety matches behavior and SDKs;
- security-app data handling explicitly explained;
- store listing does not overclaim;
- reviewer can access all features without account;
- sample workflow explained;
- no malware distribution;
- no install/execute feature;
- no dynamic code download;
- no broad storage access;
- no foreground service unless declared and justified.

## 27.3 Store listing content

Suggested short description:

> Analyze APKs and suspicious archives locally without installing or executing them.

Suggested disclosure bullets:

- Static analysis only
- Local processing
- No automatic uploads
- No sample execution
- Evidence-backed reports

Required disclaimer:

> Static analysis can identify capabilities and suspicious indicators but cannot guarantee that a file is safe or malicious.

## 27.4 Reviewer demonstration

Include an inert APK fixture containing deliberately visible but harmless patterns:

- sample permissions;
- exported test component;
- harmless hardcoded example domain;
- harmless dynamic-loader reference not used to load a payload;
- debug certificate.

Clearly label it a demonstration fixture and document expected findings.

---

# 28. Open Architecture Decisions

Track these in `/docs/OPEN_DECISIONS.md` and ADRs.

1. Final app/product name.
2. Package namespace.
3. Minimum SDK 28 vs 29.
4. Pure Kotlin vs library DEX parser.
5. Archive library selection.
6. Password-protected ZIP library.
7. AOSP apksig Android compatibility.
8. Binary XML/resources parser.
9. Per-case vs per-install encryption keys.
10. Whether original samples are deleted immediately or after a short recovery period.
11. Whether PDF export is in version 1.0 or 1.1.
12. Open-source scope.
13. Rule-pack licensing and contribution model.
14. Native ELF parser approach.
15. Whether app remains permanently offline or gains optional integrations.
16. Foreground/background analysis strategy after beta data.
17. App size budget.
18. Maximum public file-size limit.
19. Report signing.
20. Monetization.

Each decision record must include:

- context;
- options;
- decision;
- consequences;
- security impact;
- Play policy impact;
- migration/rollback.

---

# 29. Risk Register

| Risk | Likelihood | Impact | Mitigation |
|---|---:|---:|---|
| Parser vulnerability | Medium | Critical | Isolation, fuzzing, bounded reads, dependency patching. |
| ZIP bomb exhausts storage/memory | High | High | Preflight plus runtime quotas and global budgets. |
| Play rejects security-related wording or behavior | Medium | High | Accurate listing, no execution, reviewer instructions, policy audit. |
| DEX parser scope becomes too large | High | Medium | Inventory-first IR, no full decompiler, phased capabilities. |
| False positives damage trust | High | High | Evidence, confidence, legitimate contexts, corpus review. |
| Users believe “no findings” means safe | High | High | Never say safe; persistent limitations and completeness. |
| Native dependency inflates app or introduces CVEs | Medium | High | Delay NDK, dependency spike, SBOM, isolation. |
| Analysis overheats phone | Medium | Medium | Concurrency limits, thermal pause, profiles. |
| Report leaks secrets | Medium | High | Redaction defaults, encryption, no backup, safe exports. |
| External provider hangs | Medium | Medium | descriptor cancellation, staging timeout, separate thread. |
| Rule updates become code-execution channel | Low | Critical | Data-only grammar, signatures, no remote rules in MVP. |
| Development spreads across too many formats | High | High | APK depth before format breadth; phase gates. |
| Real malware enters public repo | Medium | Critical | corpus policy and CI secret/sample scanning. |
| Background execution creates FGS policy burden | Medium | Medium | foreground-bound MVP; later ADR and review. |

---

# 30. Definition of Done for Version 1.0

Version 1.0 is done only when all conditions are true:

## Product

- User can import raw APK and ordinary ZIP containing APK.
- User can share supported files into app.
- App confirms before staging.
- App never installs or executes sample.
- App generates Simple and Analyst reports.
- App exports JSON, Markdown, HTML, and IOC CSV.
- App deletes samples/cases reliably.

## Analysis

- hashes;
- type detection;
- archive safety/inventory;
- APK structure;
- manifest/resources;
- permissions;
- components/deep links;
- signing/certificates;
- file inventory;
- multidex parsing;
- API capabilities;
- IOC extraction;
- correlated rules;
- completeness/limitations.

## Safety

- no `INTERNET`;
- no dangerous permissions;
- no all-files access;
- isolated analysis process;
- quotas verified;
- traversal and bomb tests pass;
- no sample content in logs or backup;
- report injection tests pass;
- cancellation and crash recovery pass.

## Quality

- adaptive UI;
- accessibility baseline;
- deterministic golden reports;
- fuzz regression corpus;
- acceptable performance on low/standard/high device profiles;
- policy and privacy review complete;
- public documentation complete.

## Release

- target API current;
- Data Safety and privacy policy accurate;
- Play listing accurate;
- reviewer fixture and instructions ready;
- staged rollout and rollback prepared.

---

# 31. Suggested Initial Backlog

## Epic A — Foundation

- A-001 repository and CI
- A-002 Compose adaptive shell
- A-003 case state machine
- A-004 encrypted report storage
- A-005 deletion and retention
- A-006 policy manifest tests

## Epic B — Intake

- B-001 document picker
- B-002 share target
- B-003 intake validation
- B-004 bounded copy and hashing
- B-005 type detector
- B-006 duplicate detection

## Epic C — Archives

- C-001 library spike
- C-002 central directory model
- C-003 path normalization
- C-004 quotas
- C-005 selective extraction
- C-006 archive UI
- C-007 archive report

## Epic D — APK metadata

- D-001 APK structural detector
- D-002 manifest parser
- D-003 resources parser
- D-004 permission model
- D-005 component model
- D-006 deep links
- D-007 APK inventory

## Epic E — Signing

- E-001 apksig spike
- E-002 verifier wrapper
- E-003 certificate model
- E-004 signing UI

## Epic F — DEX

- F-001 parser decision
- F-002 header/map parser
- F-003 indexed tables
- F-004 code items/opcodes
- F-005 reference indexes
- F-006 evidence locations
- F-007 fuzzing

## Epic G — Rules and reports

- G-001 facts model
- G-002 rule grammar
- G-003 capability map
- G-004 IOC extraction
- G-005 finding synthesis
- G-006 canonical schema
- G-007 Simple Report
- G-008 Analyst Report
- G-009 exports

## Epic H — Hardening

- H-001 isolated service
- H-002 AIDL protocol
- H-003 process crash recovery
- H-004 redaction
- H-005 security tests
- H-006 Play compliance

---

# 32. AI Agent Implementation Rules

When an AI agent is assigned a task, its response or work log should begin with:

```text
Active phase:
Requirement IDs addressed:
Safety invariants affected:
Files/modules expected to change:
Tests required:
Open decision or ADR required:
```

The agent must follow these rules:

1. Do not add functionality merely because a library makes it easy.
2. Do not add a permission to solve an implementation inconvenience.
3. Do not use broad MIME filters without documented need.
4. Do not deserialize unbounded objects from hostile input.
5. Do not allocate using hostile length fields before validation.
6. Do not use signed integer arithmetic for untrusted offsets without overflow checks.
7. Do not render sample-controlled HTML.
8. Do not execute commands discovered in the sample.
9. Do not resolve or connect to extracted domains.
10. Do not report a finding without evidence provenance.
11. Do not change rule semantics without rule-pack versioning and golden tests.
12. Do not change canonical JSON incompatibly without schema versioning.
13. Do not store sample files outside no-backup private storage.
14. Do not persist archive passwords.
15. Do not assume parser library safety; wrap it in independent quotas.
16. Do not hide skipped or failed stages.
17. Do not expose raw suspicious URLs as tappable links.
18. Do not include full possible secrets in default export.
19. Do not commit real malware.
20. Prefer a smaller truthful feature over a broad unreliable claim.

## 32.1 Pull request checklist for agents

```text
[ ] Requirement IDs listed
[ ] Active phase permits this work
[ ] No new permission or exported component
[ ] Threat model reviewed
[ ] Untrusted lengths/paths bounded
[ ] Cancellation handled
[ ] Main thread remains free of blocking I/O
[ ] Parser errors mapped to stable codes
[ ] Evidence provenance preserved
[ ] Report/schema impact reviewed
[ ] Unit tests added
[ ] Malformed-input tests added
[ ] Golden report updated intentionally
[ ] Docs/ADR updated
[ ] No sample content in logs
[ ] No live malware or secrets committed
```

---

# 33. Source and Policy References

The following sources were checked while preparing this plan. Recheck them before implementation milestones and every Play release.

## Android platform and app architecture

- Storage Access Framework / opening documents:  
  https://developer.android.com/training/data-storage/shared/documents-files
- Receiving shared data:  
  https://developer.android.com/training/sharing/receive
- Secure file sharing and FileProvider:  
  https://developer.android.com/training/secure-file-sharing
- ZIP path traversal risk:  
  https://developer.android.com/privacy-and-security/risks/zip-path-traversal
- Service manifest and isolated process:  
  https://developer.android.com/guide/topics/manifest/service-element
- Android app architecture:  
  https://developer.android.com/topic/architecture
- Adaptive Compose apps:  
  https://developer.android.com/develop/ui/compose/build-adaptive-apps
- Android Keystore:  
  https://developer.android.com/privacy-and-security/keystore
- Android 16 behavior changes:  
  https://developer.android.com/about/versions/16/behavior-changes-16
- Foreground service types and constraints:  
  https://developer.android.com/develop/background-work/services/fgs/service-types
- WorkManager and persistent work:  
  https://developer.android.com/develop/background-work/background-tasks/persistent

## Google Play policies

- Target API requirements:  
  https://support.google.com/googleplay/android-developer/answer/11926878
- Device and Network Abuse:  
  https://support.google.com/googleplay/android-developer/answer/16559646
- Malware policy:  
  https://support.google.com/googleplay/android-developer/answer/9888380
- All files access policy:  
  https://support.google.com/googleplay/android-developer/answer/10467955
- Broad package visibility policy:  
  https://support.google.com/googleplay/android-developer/answer/10158779
- Data Safety:  
  https://support.google.com/googleplay/android-developer/answer/10787469
- User Data and security-app disclosure:  
  https://support.google.com/googleplay/android-developer/answer/10144311
- Current Developer Program Policy:  
  https://support.google.com/googleplay/android-developer/answer/17190352

## APK signing and candidate libraries

- AOSP APK signing overview:  
  https://source.android.com/docs/security/features/apksigning
- AOSP apksig project:  
  https://android.googlesource.com/platform/tools/apksig/
- Apache Commons Compress:  
  https://commons.apache.org/proper/commons-compress/
- Commons Compress ZIP package:  
  https://commons.apache.org/proper/commons-compress/zip
- YARA:  
  https://github.com/VirusTotal/yara
- YARA-X:  
  https://github.com/VirusTotal/yara-x
- libarchive:  
  https://www.libarchive.org/

---

# 34. Final Product Principle

The app succeeds by being the safest, clearest, and most useful **mobile first-pass static analysis tool**, not by pretending to replace a desktop reverse-engineering lab or dynamic sandbox.

Every technical decision should reinforce this promise:

> The user intentionally imports a file. PocketLab examines it locally under strict limits, never installs or executes it, explains what was observed, identifies uncertainty, and gives the user a report they can understand, preserve, and act upon.

