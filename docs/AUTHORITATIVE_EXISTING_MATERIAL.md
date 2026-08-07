You are the autonomous lead Android engineer, malware-analysis engineer, file-format specialist, and defensive application-security reviewer for the PocketLab project.

PocketLab currently focuses primarily on safe, local static analysis of Android application packages and archive containers.

The project is now being expanded into a general-purpose, local-first **mobile static file-analysis and malware-triage platform**.

The purpose of this task is NOT to bolt individual file parsers onto the existing application independently.

Your first responsibility is to determine whether the existing architecture can safely support a large number of file formats, containers, nested artifacts, and cross-artifact correlation.

Then update the architecture and development plan as necessary and implement the expansion sequentially in controlled phases.

Continue autonomously through the phases that can reasonably be completed in the development environment.

Do not ask the user for routine confirmation.

## Authoritative existing material

Before making changes, read:

- `AGENTS.md`
- `docs/ANDROID_STATIC_ANALYSIS_APP_DEVELOPMENT_PLAN.md`
- `docs/IMPLEMENTATION_STATUS.md`
- `docs/ARCHITECTURE.md`
- `docs/THREAT_MODEL.md`
- `docs/REPORT_SCHEMA.md`
- `docs/PLAY_STORE_COMPLIANCE.md`
- `docs/PRIVACY_MODEL.md`
- `docs/RISK_REGISTER.md`
- `docs/OPEN_DECISIONS.md`
- all ADRs
- current parser/analyzer implementations
- existing test corpus and test infrastructure

Inspect the actual repository rather than trusting completion claims in documentation.

## Fundamental requirement

PocketLab must remain a **static-analysis application**.

Supporting a file format NEVER authorizes execution of the submitted content.

Imported content must never be:

- installed;
- launched;
- executed;
- interpreted as executable program logic;
- loaded through `DexClassLoader`;
- loaded through native dynamic linking;
- passed to a shell;
- passed to `Runtime.exec`;
- passed to `ProcessBuilder`;
- opened by another application for analysis;
- rendered through an unsafe WebView;
- submitted to an operating-system handler;
- automatically contacted over the network;
- automatically uploaded to any remote service.

Parsers inspect structures and bytes only.

All existing security invariants in `AGENTS.md` continue to apply.

# Stage 0 — Architecture review before new format implementation

Before implementing a new format, perform an architecture review.

Determine whether the current system adequately supports:

- generic artifacts;
- nested artifacts;
- containers;
- recursive analysis;
- globally enforced resource budgets;
- parent-child relationships;
- multiple analyzers;
- content-based type detection;
- claimed-type versus detected-type comparison;
- partial analysis;
- parser failure;
- cross-artifact IOC correlation;
- deterministic reporting;
- parser versioning;
- format-specific limitations;
- cancellation;
- timeout enforcement;
- isolated-process parsing.

If not, refactor the architecture BEFORE implementing large numbers of new analyzers.

The target conceptual model should resemble:

Artifact:
- artifact ID
- case ID
- parent artifact ID
- source/container relationship
- original filename
- safe display filename
- claimed MIME type
- detected type
- detected subtype
- size
- hashes
- metadata
- children
- indicators
- findings
- parser errors
- completeness
- limitations

ArtifactAnalyzer:
- analyzer ID
- analyzer version
- supported detected types
- analysis requirements
- analyze(context, artifact)

AnalysisContext:
- global budget
- recursion depth
- cancellation state
- temporary workspace
- safe bounded reader
- child-artifact emitter
- finding emitter
- IOC emitter
- parser-error emitter

The exact implementation may differ if the repository architecture provides a safer or cleaner abstraction.

Document the final design in an ADR.

## Global resource accounting

Nested files MUST NOT receive independent unlimited quotas.

Maintain a case-level analysis budget governing at minimum:

- total bytes read;
- total bytes decompressed;
- total temporary storage;
- number of artifacts;
- archive entries;
- recursion depth;
- strings extracted;
- indicators extracted;
- parser operations where practical;
- total analysis duration;
- total report size.

For example:

ZIP
→ DOCX
→ embedded XLSM
→ embedded OLE object

must consume one shared case budget.

A nested container must never reset resource limits.

When a limit is reached:

- terminate the affected operation safely;
- preserve completed evidence;
- mark the affected artifact incomplete;
- identify the exact quota;
- continue unrelated safe analysis where possible.

# Stage 1 — PDF support

Implement safe static PDF analysis.

Support:

`.pdf`

Extract and analyze where technically supportable:

- PDF version;
- object counts;
- xref structures;
- trailers;
- incremental revisions;
- object streams;
- encryption status;
- signatures;
- metadata;
- page count;
- embedded files;
- JavaScript;
- OpenAction;
- additional actions;
- Launch actions;
- URI actions;
- AcroForms;
- XFA;
- annotations;
- RichMedia;
- remote resources;
- embedded images;
- suspicious filters;
- URLs;
- domain/IP/email indicators;
- structural abnormalities.

Never execute PDF JavaScript.

Never launch actions.

Never automatically follow URLs.

Never use the submitted PDF as active content inside a WebView.

Create malformed PDF fixtures and appropriate parser fuzz/property tests.

# Stage 2 — Microsoft Office Open XML

Implement OOXML container analysis.

Support:

Word:
- `.docx`
- `.docm`
- `.dotx`
- `.dotm`

Excel:
- `.xlsx`
- `.xlsm`
- `.xlsb`
- `.xltx`
- `.xltm`
- `.xlam`

PowerPoint:
- `.pptx`
- `.pptm`
- `.ppsx`
- `.ppsm`
- `.potx`
- `.potm`
- `.sldx`
- `.sldm`
- `.ppam`

Analyze:

- package structure;
- `[Content_Types].xml`;
- relationships;
- external relationships;
- remote templates;
- hyperlinks;
- embedded files;
- embedded OLE objects;
- VBA projects;
- ActiveX;
- macros;
- external data connections;
- custom XML;
- digital signatures;
- encryption status;
- hidden sheets/slides where discoverable;
- formulas with external/network significance;
- high-entropy objects;
- unusual content types;
- file-extension/content mismatches;
- nested artifacts.

Reuse the hardened ZIP infrastructure.

Do not assume an Office file is safe merely because it uses a non-macro-enabled extension.

# Stage 3 — Legacy Microsoft Office and OLE/CFB

Implement a safe bounded Compound File Binary/OLE parser or integrate a thoroughly reviewed dependency.

Support:

- `.doc`
- `.dot`
- `.xls`
- `.xlt`
- `.xla`
- `.ppt`
- `.pps`
- `.pot`
- `.ppa`
- `.rtf`

Analyze:

- compound-file directory structure;
- streams;
- VBA;
- XLM macros where applicable;
- embedded objects;
- ActiveX;
- external links;
- DDE-related structures where supportable;
- document metadata;
- suspicious streams;
- high-entropy data;
- embedded files;
- indicators.

Treat OLE parsing as a hostile binary-parser attack surface.

Create extensive malformed CFB fixtures and fuzz/property tests.

# Stage 4 — Images and QR/barcode analysis

Implement safe analysis for:

- JPEG/JPG
- PNG
- GIF
- WebP
- HEIF/HEIC
- AVIF
- BMP
- TIFF
- SVG
- SVGZ
- ICO

Extract where applicable:

- dimensions;
- format;
- animation;
- EXIF;
- XMP;
- GPS;
- camera/device metadata;
- ICC profiles;
- comments;
- thumbnails;
- trailing data;
- suspicious appended content;
- embedded artifacts;
- file-signature inconsistencies;
- polyglot indicators;
- entropy anomalies.

Add local QR/barcode decoding.

Support recognition of at least:

- QR;
- Data Matrix;
- Aztec;
- PDF417;
- Code 128.

Treat decoded content as hostile input.

Extract and classify:

- HTTP/HTTPS URLs;
- custom URIs;
- mailto;
- tel;
- SMS;
- Wi-Fi configuration;
- vCard;
- calendar payloads;
- payment URIs;
- plain text.

Never activate decoded URI schemes.

SVG must use a dedicated static XML analyzer.

Never display hostile SVG through an execution-capable WebView.

Test parser behavior against extreme dimensions, malformed metadata, truncated images, decompression bombs, XML entity attacks, excessive SVG nesting, and hostile URI values.

# Stage 5 — Email containers

Implement:

- `.eml`
- `.msg`
- TNEF / `winmail.dat`

Parse MIME recursively.

Extract:

- From;
- Reply-To;
- Return-Path;
- Sender;
- To/Cc;
- Subject;
- Message-ID;
- Received headers;
- Authentication-Results;
- MIME structure;
- text/plain body;
- text/html body;
- attachments;
- attached messages;
- links;
- image resources.

Detect where possible:

- display-name/domain mismatches;
- From/Reply-To mismatches;
- suspicious URL display/target differences;
- dangerous attachment types;
- nested emails;
- malformed MIME;
- excessive nesting;
- attachment bombs.

Every attachment must enter the normal artifact-analysis pipeline.

Do not rely on email-provided MIME types for security decisions.

# Stage 6 — Calendar and contact formats

Implement:

Calendar:
- `.ics`
- `.ifb`
- legacy `.vcs`

Contacts:
- `.vcf`
- `.vcard`

For calendar data analyze:

- METHOD;
- UID;
- ORGANIZER;
- ATTENDEE;
- SUMMARY;
- DESCRIPTION;
- LOCATION;
- URL;
- ATTACH;
- conferencing URIs;
- recurrence;
- custom X-properties;
- organizer/link domain inconsistencies.

For vCard analyze:

- names;
- organization;
- email;
- telephone;
- URL;
- GEO;
- PHOTO;
- LOGO;
- CALURI;
- custom URI values;
- embedded data resources;
- custom properties.

Never invoke URI values.

Create tests for line folding, Unicode, malformed properties, excessive values and oversized embedded data.

# Stage 7 — HTML, web archives and structured text

Implement:

- `.html`
- `.htm`
- `.xhtml`
- `.mhtml`
- `.mht`
- `.xml`
- `.json`
- `.jsonl`
- `.yaml`
- `.yml`
- `.txt`
- `.md`
- `.csv`
- `.tsv`

For HTML inspect:

- scripts;
- external scripts;
- iframes;
- forms;
- credential inputs;
- form actions;
- meta refresh;
- JavaScript URIs;
- data URIs;
- external images;
- CSS URLs;
- hidden elements;
- hyperlinks;
- base64 content;
- indicators;
- embedded artifacts.

Do not execute or render submitted HTML as active content.

For XML:

- disable DTD processing;
- disable external entity resolution;
- prevent XXE;
- limit depth;
- limit attributes;
- limit entity expansion;
- bound document size.

For CSV, protect report/export pipelines against spreadsheet formula injection.

# Stage 8 — OpenDocument

Implement:

- `.odt`
- `.ods`
- `.odp`
- `.ott`
- `.ots`
- `.otp`

Reuse ZIP/XML analysis infrastructure.

Inspect:

- manifest;
- scripts/macros;
- external links;
- embedded files;
- embedded documents;
- formulas;
- metadata;
- nested artifacts;
- suspicious XML structures.

# Stage 9 — Expanded archive formats

Evaluate and incrementally support:

- `.7z`
- `.rar`
- `.tar`
- `.gz`
- `.tgz`
- `.bz2`
- `.tbz2`
- `.xz`
- `.txz`
- `.zipx`
- `.jar`
- `.war`

Later evaluate:

- `.cab`
- `.iso`

Every container implementation must support:

- bounded enumeration;
- global decompression quotas;
- path traversal defenses;
- absolute-path rejection;
- link handling;
- duplicate-entry detection;
- nested-artifact handling;
- recursion limits;
- encrypted archive handling where appropriate;
- cancellation;
- deterministic cleanup.

# Stage 10 — Script analysis

Implement safe text/static analyzers for:

- PowerShell
- JavaScript
- VBScript
- Windows Script Files
- HTA
- batch/CMD
- shell scripts
- Python
- Perl
- PHP
- Ruby
- Lua

Never invoke interpreters.

Extract and classify:

- obfuscation;
- encoded strings;
- Base64;
- download URLs;
- system-command construction;
- persistence-related commands;
- credential-related commands;
- registry operations;
- scheduled-task references;
- service operations;
- suspicious subprocess usage;
- encoded PowerShell;
- shellcode-like arrays;
- IOCs.

Prefer evidence-based capability descriptions over malware verdicts.

# Stage 11 — Windows executable and shortcut analysis

Implement:

PE:
- `.exe`
- `.dll`
- `.sys`
- `.scr`
- `.cpl`
- `.ocx`

Installer:
- `.msi`
- `.msp`

Shortcut/launcher formats where safely supportable:
- `.lnk`
- `.url`

Extract:

- PE headers;
- architecture;
- imports;
- exports;
- sections;
- entropy;
- timestamps;
- resources;
- version metadata;
- digital signatures;
- TLS callbacks;
- debug metadata;
- suspicious imports;
- packer indicators;
- embedded objects;
- indicators;
- static capability findings.

Do not emulate or execute binaries.

# Stage 12 — ELF, Mach-O and mobile ecosystem expansion

Implement or evaluate:

Linux:
- ELF binaries
- `.so`

Apple:
- Mach-O
- `.dylib`
- `.ipa`

For IPA, reuse hardened ZIP/container infrastructure and inspect:

- Info.plist;
- entitlements;
- embedded frameworks;
- executable metadata;
- URLs;
- domains;
- permissions/capabilities represented by usage descriptions;
- signing metadata.

No execution or emulation.

# Stage 13 — OneNote and additional compound documents

Evaluate:

- `.one`
- `.onepkg`
- `.onetoc2`

Extract:

- structure;
- metadata;
- embedded files;
- images;
- hyperlinks;
- indicators;
- nested artifacts.

Do not implement an unsafe parser merely to claim support.

If available libraries cannot meet PocketLab's parser-isolation and bounded-resource requirements, document the limitation and defer support.

# Stage 14 — E-books

Implement or evaluate:

- `.epub`
- `.mobi`
- `.azw`
- `.azw3`

For EPUB reuse archive + HTML + XML + image analyzers.

Extract:

- metadata;
- links;
- scripts;
- external resources;
- embedded files;
- indicators.

# Stage 15 — Media containers

Evaluate:

Video:
- MP4
- MOV
- MKV
- WebM
- AVI
- 3GP

Audio:
- MP3
- M4A
- AAC
- WAV
- FLAC
- OGG
- Opus

Subtitle:
- SRT
- WebVTT
- ASS/SSA

Focus on structural and metadata analysis.

Do not decode arbitrary media merely for completeness if doing so materially increases parser attack surface.

Prefer parsing container metadata with bounded, memory-safe code.

# Stage 16 — Fonts and specialist formats

Evaluate:

- TTF
- OTF
- WOFF
- WOFF2

Treat font parsing as high-risk hostile binary parsing.

Do not load untrusted fonts into the Android UI rendering stack.

Only implement support if the security value exceeds the additional parser attack surface.

# Cross-artifact correlation

As formats are added, implement correlation at the case level.

Examples:

- same domain appearing in an email body, PDF and QR image;
- same hash embedded in multiple containers;
- Word document containing an executable also attached separately;
- organizer domain differing from calendar meeting URL;
- QR URL matching a link embedded in a PDF;
- email Reply-To domain differing from linked login domain.

Correlation findings must reference the exact artifacts and evidence involved.

Do not infer causality merely from indicator equality.

# Unified report requirements

Reports must support arbitrary artifact trees.

The analyst report should include:

1. Case overview.
2. Overall assessment.
3. Analysis completeness.
4. Artifact tree.
5. Correlated findings.
6. Findings by artifact.
7. Indicators.
8. Metadata.
9. Nested content.
10. Parser errors.
11. Resource limits reached.
12. Unsupported content.
13. Analysis limitations.
14. Engine/analyzer versions.

Each analyzer must declare:

- analyzer ID;
- version;
- supported format;
- capabilities;
- limitations.

# Format detection

Never select a parser solely from a filename extension.

Use layered detection:

1. Content signature/magic.
2. Structural validation.
3. Container characteristics.
4. Declared MIME type as advisory information.
5. Filename extension as advisory information.

Record discrepancies such as:

`invoice.pdf` detected as `PE executable`.

Polyglots may legitimately match multiple structures.

Do not force ambiguous input into a single parser when multiple safe analyzers can inspect it.

# Parser isolation

All newly introduced parsers must conform to the existing PocketLab trust boundary.

Where currently supported by the architecture, hostile parsing should occur outside the main UI process.

A parser failure must:

- not crash the main app;
- not classify the artifact as clean;
- not discard already collected safe findings;
- produce a parser-error record;
- mark appropriate analysis portions incomplete.

# Testing requirements for every phase

Do not implement a new analyzer without tests.

Each analyzer requires:

- valid minimal fixture;
- valid realistic synthetic fixture;
- extension spoofing fixture;
- MIME spoofing fixture;
- truncated fixture;
- malformed fixture;
- excessive-size/count fixture;
- cancellation test;
- timeout/resource-quota test;
- parser-error test;
- report-schema test;
- hostile-string/output-escaping test;
- nested-container test where relevant;
- regression fixtures for discovered bugs.

Use fuzz/property testing for binary and structured parsers where practical.

Never weaken a test solely to make it pass.

# Dependency review

Before adding any parsing dependency:

- inspect maintenance status;
- license;
- Android support;
- transitive dependencies;
- native code;
- CVE history;
- malformed-input behavior;
- allocation behavior;
- streaming capabilities;
- cancellation support.

Parser convenience is not sufficient justification.

Prefer memory-safe implementations.

# Implementation sequencing

Do NOT attempt to add all formats simultaneously.

For each stage:

1. Review the existing architecture.
2. Update threat model.
3. Write format requirements.
4. Review candidate dependencies.
5. Create safe fixtures.
6. Write baseline tests.
7. Implement parser.
8. Run parser tests.
9. Run fuzz/property tests.
10. Run regression suite.
11. Verify no permission or manifest change occurred unexpectedly.
12. Verify no sample execution pathway exists.
13. Update documentation.
14. Update `IMPLEMENTATION_STATUS.md`.
15. Commit a coherent checkpoint.
16. Continue to the next stage.

If implementation of one format exposes a weakness in the common artifact framework, correct the common architecture rather than adding a format-specific workaround.

# Initial review deliverable

Before implementing Stage 1, create:

`docs/FILE_FORMAT_EXPANSION_REVIEW.md`

Include:

- assessment of current architecture;
- required refactors;
- supported formats today;
- proposed format matrix;
- security value of each format;
- implementation complexity;
- parser risk;
- reusable infrastructure;
- dependency candidates;
- phase ordering;
- tests required;
- storage/resource implications;
- report schema changes;
- UI changes;
- Play Store implications;
- recommended changes to the master development plan.

Then update:

`docs/ANDROID_STATIC_ANALYSIS_APP_DEVELOPMENT_PLAN.md`

to integrate the approved expansion phases and requirements.

Do not delete or weaken existing requirements.

After the review and plan integration are complete, proceed autonomously through implementation in the phased order above.

The objective is not to maximize the number of extensions PocketLab recognizes.

The objective is to create a safe, accurate, composable static-analysis platform where each supported format contributes meaningful evidence without increasing risk to the user's Android device beyond an acceptable and explicitly tested level.