# PocketLab Gap Analysis and Implementation Plan

**Date:** 2026-08-05  
**Status:** HISTORICAL - SUPERSEDED by `IMPLEMENTATION_STATUS.md`  
**Actual Progress at time of writing:** Phase 1-2 of 13 (not Phase 12 as claimed)

> **NOTE:** This document records the initial Phase 1-2 codebase audit. Since this
> document was written the project has progressed through Phase 11 (native ELF
> analysis) and now includes a deterministic parser-fuzzing harness (see
> `IMPLEMENTATION_STATUS.md`). Treat the "❌ NOT IMPLEMENTED" items below as of
> historical interest only; most were implemented in subsequent phases. The
> authoritative, current state is `docs/IMPLEMENTATION_STATUS.md`.

---

## Executive Summary

The implementation status document is **wildly inaccurate**. The codebase is at roughly Phase 1-2 out of 13 planned phases. Most features are either stubs, single-file implementations, or incomplete. The app cannot yet perform a complete end-to-end analysis of a real APK and produce a useful report.

**Critical finding:** The APK manifest parser uses `XmlPullParser` on binary XML, which will fail on every real APK. This is a blocking issue.

---

## Critical Gaps (Blocking)

### 1. Binary XML Manifest Parsing ❌
**Status:** IMPLEMENTED (just now)  
**File:** `engine/apk/src/main/java/com/pineandpackets/pocketlab/engine/apk/BinaryXmlParser.kt`  
**Issue:** APK manifests are binary XML, not text XML. Previous implementation would crash on real APKs.  
**Fix:** Implemented `BinaryXmlParser` to parse AXML format.  
**Remaining:**
- [ ] Add comprehensive tests for binary XML parser
- [ ] Handle edge cases (malformed chunks, missing strings, etc.)
- [ ] Test with real APK fixtures

### 2. AnalyzerService Not in Manifest ❌
**Status:** NOT IMPLEMENTED  
**File:** `app/src/main/AndroidManifest.xml`  
**Issue:** The isolated analyzer service is not declared in the manifest, so it cannot run.  
**Fix Required:**
```xml
<service
    android:name="com.pineandpackets.pocketlab.engine.service.AnalyzerService"
    android:exported="false"
    android:process=":analyzer"
    android:isolatedProcess="true" />
```

### 3. File Picker Uses */* MIME Type ❌
**Status:** NOT IMPLEMENTED  
**File:** `feature/home/src/main/java/com/pineandpackets/pocketlab/feature/home/HomeScreen.kt:56`  
**Issue:** Uses `GetContent()` with `"*/*"` which violates Play Store policy (Section 5.4 of plan).  
**Fix Required:** Use narrow MIME types:
- `application/vnd.android.package-archive` (APK)
- `application/zip` (ZIP)
- `application/octet-stream` (with content sniffing)

### 4. No ACTION_SEND Share Target ❌
**Status:** NOT IMPLEMENTED  
**File:** `app/src/main/AndroidManifest.xml`  
**Issue:** No share-target activity declared. Primary intake vector missing.  
**Fix Required:**
- Create `ShareIntakeActivity` with narrow MIME type filters
- Validate URI scheme (reject `file://`)
- Validate granted permissions
- Present confirmation screen before staging

### 5. Signature Verification is Fake ❌
**Status:** NOT IMPLEMENTED  
**File:** `engine/apk/src/main/java/com/pineandpackets/pocketlab/engine/apk/SigningAnalyzer.kt:43`  
**Issue:** `verified = true` is hardcoded. Produces false assurance.  
**Fix Required:**
- Integrate AOSP `apksig` library OR
- Implement basic signature verification OR
- Set `verified = false` and document limitations

### 6. Archive Encryption Detection is Fake ❌
**Status:** NOT IMPLEMENTED  
**File:** `engine/archive/src/main/java/com/pineandpackets/pocketlab/engine/archive/ArchiveAnalyzer.kt:63`  
**Issue:** `isEncrypted = false` is hardcoded. Encrypted archives are silently misreported.  
**Fix Required:**
- Actually detect encryption from ZIP entry flags
- Pass password to Commons Compress when provided
- Report encryption status accurately

---

## High Priority Gaps (Core Functionality)

### 7. DEX Parser Extracts Zero Code Data ❌
**Status:** HEADER ONLY  
**File:** `engine/dex/src/main/java/com/pineandpackets/pocketlab/engine/dex/DexAnalyzer.kt`  
**Issue:** Only reads header counts. No strings, methods, classes, or instructions extracted.  
**Missing:**
- String table extraction
- Method/field/class ID table parsing
- Code item parsing
- Dalvik instruction decoding
- API reference indexes
- Capability fact extraction

**Impact:** Cannot perform any code-level analysis. The entire DEX-based finding pipeline is absent.

### 8. No Report UI ❌
**Status:** PLACEHOLDER  
**File:** `feature/report/src/main/java/com/pineandpackets/pocketlab/feature/report/ReportScreen.kt`  
**Issue:** Report screen explicitly states "will be implemented in Phase 8". Users cannot view findings.  
**Missing:**
- Simple Report view (user-friendly)
- Analyst Report view (detailed)
- Finding detail screen with evidence navigation
- Synchronized Simple/Analyst views

### 9. No Test Corpus Fixtures ❌
**Status:** EMPTY  
**Directory:** `test-corpus/synthetic/`  
**Issue:** Zero synthetic APKs, zero malformed inputs, zero golden reports.  
**Missing:**
- Demo APK fixture for Play Store reviewers
- Synthetic test APKs with known properties
- Malformed test fixtures
- Golden report JSON files
- Hash records for fixtures

### 10. No CSV Export ❌
**Status:** NOT IMPLEMENTED  
**File:** `core/report/src/main/java/com/pineandpackets/pocketlab/core/report/ReportExporter.kt`  
**Issue:** IOC CSV export required by plan (FR-RP-004) but not implemented.  
**Missing:**
- CSV export with proper columns
- Formula injection defense (prefix with `'`)
- Defanged IOCs

---

## Medium Priority Gaps (Deep Analysis)

### 11. No Intent Filter / Deep Link Analysis ❌
**Status:** NOT IMPLEMENTED  
**File:** `engine/apk/src/main/java/com/pineandpackets/pocketlab/engine/apk/ApkAnalyzer.kt`  
**Issue:** Intent filters not parsed. Cannot detect deep links, custom schemes, or broad data acceptance.  
**Missing:**
- Parse `<intent-filter>` elements
- Extract actions, categories, data elements
- Analyze URI schemes, hosts, paths
- Flag risky patterns (HTTP, broad hosts, custom schemes)

### 12. No Permission Knowledge Base ❌
**Status:** NOT IMPLEMENTED  
**Issue:** Permission protection levels not known. Cannot assess permission risk accurately.  
**Missing:**
- Map of Android permissions to protection levels
- API level-specific protection levels
- Plain-language capability descriptions
- Risk context for permission combinations

### 13. No resources.arsc Parsing ❌
**Status:** NOT IMPLEMENTED  
**Issue:** Cannot resolve string resources. Application labels always null.  
**Missing:**
- Parse resource table
- Resolve string references
- Extract application label
- Extract other resource values

### 14. No Declarative Rule Interpreter ❌
**Status:** NOT IMPLEMENTED  
**File:** `engine/rules/src/main/java/com/pineandpackets/pocketlab/engine/rules/RulesEngine.kt`  
**Issue:** All rules are hardcoded Kotlin. Cannot add rules without code changes.  
**Missing:**
- YAML/JSON rule file format
- Rule interpreter with restricted grammar
- Rule-pack versioning
- Rule-pack integrity verification

### 15. No Checked Arithmetic in Archive Analyzer ❌
**Status:** NOT IMPLEMENTED  
**File:** `engine/archive/src/main/java/com/pineandpackets/pocketlab/engine/archive/ArchiveAnalyzer.kt:49`  
**Issue:** Uses simple `+=` which can overflow silently.  
**Fix Required:** Use `addChecked()` from `AnalysisUtils.kt`

### 16. No APK File Inventory ❌
**Status:** NOT IMPLEMENTED  
**Issue:** Cannot inventory DEX files, native libraries, assets, resources.  
**Missing:**
- List all DEX files with sizes
- List all native libraries by ABI
- List assets and resources
- Calculate entropy for suspicious files
- Detect executable headers hidden under other extensions

---

## Implementation Priority Order

### Phase A: Critical Fixes (Week 1)
1. ✅ Fix binary XML manifest parsing
2. Add AnalyzerService to manifest
3. Fix file picker MIME types
4. Add ACTION_SEND share target
5. Fix signature verification (or be honest)
6. Fix archive encryption detection
7. Add checked arithmetic to archive analyzer
8. Create demo APK fixture

### Phase B: Core Functionality (Weeks 2-3)
9. Implement DEX string table extraction
10. Implement DEX method/class/field ID parsing
11. Implement DEX instruction decoding (basic)
12. Build API capability map
13. Implement Simple Report UI
14. Implement Analyst Report UI
15. Implement CSV export
16. Add comprehensive tests

### Phase C: Deep Analysis (Weeks 4-6)
17. Implement intent filter analysis
18. Implement permission knowledge base
19. Implement resources.arsc parsing
20. Implement declarative rule interpreter
21. Implement APK file inventory
22. Implement correlation rules
23. Add golden report tests

### Phase D: Hardening (Weeks 7-8)
24. Add central directory validation
25. Implement nested archive support
26. Implement native library correlation
27. Add fuzzing tests
28. Performance optimization
29. Complete documentation

---

## Test Coverage Gaps

| Module | Current Tests | Missing Tests |
|---|---|---|
| engine/apk | 0 | Binary XML parser, manifest parsing, signing |
| engine/dex | 0 | String extraction, instruction decoding, quotas |
| engine/archive | 9 | Central directory, checked arithmetic, nested, fuzzing |
| engine/rules | 5 | Correlation rules, capability facts, rule interpreter |
| engine/pipeline | 0 | Integration tests, end-to-end analysis |
| feature/intake | 0 | Share target, confirmation, staging |
| feature/report | 0 | Simple/Analyst views, finding detail |

**Current test count:** ~80 tests  
**Required test count:** ~300+ tests for MVP

---

## Security Concerns

1. **Binary XML parser** - New code, needs thorough testing for malformed input
2. **Archive analyzer** - Uses unchecked arithmetic, can overflow
3. **DEX analyzer** - Reads entire file into memory, can OOM on large files
4. **Signature verification** - Fake `verified=true` is a security lie
5. **Encryption detection** - Fake `isEncrypted=false` is a security lie
6. **File picker** - Uses `*/*` which violates Play policy
7. **No share target validation** - Missing URI scheme and permission checks

---

## Estimated Effort

| Phase | Estimated Hours | Status |
|---|---|---|
| Phase A: Critical Fixes | 40-60 hours | 10% complete |
| Phase B: Core Functionality | 80-120 hours | 0% complete |
| Phase C: Deep Analysis | 120-160 hours | 0% complete |
| Phase D: Hardening | 60-80 hours | 0% complete |
| **Total** | **300-420 hours** | **~3% complete** |

---

## Recommendation

The implementation status document should be **completely rewritten** to reflect reality. The project is not ready for Play Store submission. Estimated time to MVP: **8-12 weeks** of full-time development.

**Immediate actions:**
1. Fix all critical gaps (Phase A)
2. Create comprehensive test suite
3. Rewrite IMPLEMENTATION_STATUS.md to reflect actual progress
4. Do not claim phases are complete until they actually work on real APKs

---

## Files Changed in This Session

1. ✅ Created `BinaryXmlParser.kt` - Binary XML parser for AXML format
2. ✅ Updated `ApkAnalyzer.kt` - Use binary XML parser instead of XmlPullParser
3. ⏳ Pending: Add AnalyzerService to manifest
4. ⏳ Pending: Fix file picker MIME types
5. ⏳ Pending: Add ACTION_SEND share target
6. ⏳ Pending: Fix signature verification
7. ⏳ Pending: Fix archive encryption detection

---

**Last Updated:** 2026-08-05  
**Next Review:** After Phase A completion
