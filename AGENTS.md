# PocketLab Agent Instructions

## Project

PocketLab is a local-first Android static malware-triage application developed for Pine and Packets LLC.

The application analyzes user-selected APKs, DEX files, archives, executables, documents, scripts, and other supported formats without installing, launching, executing, detonating, or automatically uploading imported content.

## Authoritative documents

Before performing substantial work, read:

1. `docs/IMPLEMENTATION_STATUS.md`, when present
2. Relevant ADRs under `docs/adr/`
3. Relevant architecture, security, testing, privacy, and compliance documents

The repository and test results define what currently exists. Never assume functionality is complete merely because documentation describes it.

## Autonomous operation

Work autonomously inside the development sandbox.

Do not ask the user for routine permission, confirmation, command execution, dependency selection, or ordinary implementation decisions.

When ambiguity exists, prefer:

1. Explicit user requirements
2. User and device safety
3. Prevention of sample execution or data leakage
4. Existing ADRs and architecture
5. Current test evidence
6. Official Android documentation
7. The simplest secure and reversible implementation

Document consequential decisions in an ADR.

Document genuine unresolved matters in `docs/OPEN_DECISIONS.md`, but continue all work that can safely proceed.

## Core security invariants

These requirements may not be weakened for convenience:

* Never install, launch, execute, emulate, or detonate imported samples.
* Never pass imported content to a shell, process launcher, class loader, DEX loader, JNI loader, scripting engine, JavaScript engine, WebView execution context, package installer, or application-launch intent.
* Never automatically upload samples, hashes, indicators, reports, filenames, or metadata.
* Do not add internet access to the offline analysis path.
* Do not request broad storage access.
* Do not enumerate installed applications.
* Treat every imported byte and metadata field as hostile.
* Apply explicit limits to reads, allocations, decompression, recursion, parsing loops, collection sizes, output sizes, and analysis duration.
* Keep raw samples and temporary files in application-private storage.
* Exclude samples and workspaces from backup.
* Never expose sample contents through logs, analytics, crash reporting, notifications, clipboard data, recent-task previews, or shared storage.
* Never commit real malware, credentials, signing keys, private data, or sensitive generated reports.
* Parser failures must produce incomplete-analysis results, never false-clean results.
* Reports must separate observed facts, interpretations, severity, confidence, evidence, parser errors, and limitations.
* Lack of detections must never be described as proof that a file is safe.

Any proposed exception requires an ADR, threat-model update, security tests, policy review, and compelling evidence that no safer alternative exists.

## Development workflow

Work through the master development plan sequentially.

For each increment:

1. Review the applicable requirements.
2. Inspect the existing implementation and tests.
3. Identify threats, abuse cases, and failure modes.
4. Define acceptance criteria.
5. Implement the smallest complete secure increment.
6. Write tests alongside the implementation.
7. Run focused tests.
8. Correct failures.
9. Run relevant regression tests.
10. Run lint, formatting, static analysis, and policy checks.
11. Inspect the diff for security regressions.
12. Update documentation and implementation status.
13. Commit a coherent checkpoint when appropriate.
14. Continue to the next incomplete requirement.

Do not defer testing until the end.

Do not weaken tests merely to make them pass.

Every corrected defect should receive a regression test when technically practical.

## Required security testing mindset

Assume malicious files are crafted to:

* traverse paths;
* escape temporary storage;
* trigger decompression bombs;
* exhaust memory, CPU, storage, or file descriptors;
* exploit integer overflows;
* create infinite loops or excessive recursion;
* crash parser dependencies;
* conceal their actual type;
* exploit Unicode or bidirectional text;
* inject content into HTML, Markdown, JSON, CSV, or PDF reports;
* leak sample data;
* create false-clean results;
* exploit concurrency, cancellation, or cleanup races.

Use safe synthetic and inert fixtures for these tests.

Add fuzz, property-based, malformed-input, timeout, cancellation, quota, process-crash, serialization, report-escaping, manifest-policy, permission, and forbidden-API tests where appropriate.

## Dependency rules

Before adding a parser or security-sensitive dependency, review:

* license;
* maintenance status;
* Android compatibility;
* native-code usage;
* transitive dependencies;
* malformed-input behavior;
* memory and allocation behavior;
* cancellation support;
* replaceability;
* known security concerns.

Wrap external parsers behind project-owned interfaces.

Prefer memory-safe and actively maintained implementations.

Native parsers require explicit architectural justification, additional isolation, and fuzz testing.

Do not use dynamic dependency versions.

## Android policy rules

Any change involving permissions, exported components, networking, external storage, file providers, WebView, package visibility, background execution, analytics, advertising, dynamic code loading, or external services requires:

* an ADR;
* threat-model review;
* Play Store policy review;
* manifest tests;
* privacy review;
* documentation of the user benefit and safer alternatives considered.

All components must be non-exported unless external reachability is explicitly required and secured.

## Progress persistence

Maintain `docs/IMPLEMENTATION_STATUS.md`.

After each meaningful checkpoint, record:

* current phase;
* completed requirements;
* active work;
* tests added;
* commands run;
* passing and failing checks;
* blockers;
* security concerns;
* next concrete action.

Repository state and this status document must allow a different model to resume without relying on chat history.

Never mark work complete unless it has been implemented and verified.

## Validation

Use repository-defined commands once established.

Before declaring a phase or project complete, run all applicable:

* builds;
* unit tests;
* instrumentation tests;
* parser corpus tests;
* archive safety tests;
* fuzz or property-based tests;
* UI tests;
* accessibility tests;
* lint;
* formatting;
* static analysis;
* manifest-policy checks;
* permission checks;
* forbidden-API checks;
* dependency verification;
* report schema and golden tests.

Never report a command as successful unless it was actually executed successfully.

When a command fails, diagnose it, correct the underlying problem where possible, rerun it, and record any genuine environmental blocker.

## Completion standard

Passing tests do not prove the application is secure.

At completion, clearly distinguish:

* implemented and verified functionality;
* partially implemented functionality;
* untested behavior;
* environment-blocked work;
* known limitations;
* residual security risk;
* work requiring physical-device testing;
* work requiring independent security review.
