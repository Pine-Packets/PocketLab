# PocketLab Architecture

## Overview

PocketLab is a local-first, offline Android application for static malware triage. The application inspects user-selected files without installing, executing, or uploading them.

## Architecture Principles

1. **Local-first**: All analysis occurs on-device without network access
2. **No execution**: Imported samples are never installed, launched, or executed
3. **Evidence-based**: All findings include supporting evidence and limitations
4. **Resource-bounded**: All operations have explicit limits to prevent resource exhaustion
5. **Security-first**: All input is treated as hostile and validated accordingly

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      UI Layer                                │
│  Compose Screens, Navigation, ViewModels                    │
└─────────────────────────────────────────────────────────────┘
                            │
┌─────────────────────────────────────────────────────────────┐
│                    Domain Layer                              │
│  Use Cases, Business Logic, Validation                      │
└─────────────────────────────────────────────────────────────┘
                            │
┌─────────────────────────────────────────────────────────────┐
│                     Data Layer                               │
│  Repositories, Database, Preferences                        │
└─────────────────────────────────────────────────────────────┘
                            │
┌─────────────────────────────────────────────────────────────┐
│                   Engine Layer                               │
│  Analysis Orchestrator, Parsers, Rules Engine               │
│  (Designed for isolated process execution)                  │
└─────────────────────────────────────────────────────────────┘
```

## Module Structure

### Core Modules

- **core:common** - Shared utilities, constants, error types
- **core:model** - Data models and domain objects
- **core:io** - File I/O, streaming, hashing utilities
- **core:crypto** - Encryption, key management
- **core:database** - Room database for case metadata
- **core:report** - Report generation and serialization
- **core:rules-model** - Rule definitions and schema
- **core:testing** - Test utilities and fixtures

### Engine Modules

- **engine:api** - Analysis engine interfaces
- **engine:service** - Isolated analysis service (future)
- **engine:orchestrator** - Analysis coordination
- **engine:filetype** - File type detection
- **engine:archive** - Archive parsing and validation
- **engine:apk** - APK structure and manifest parsing
- **engine:dex** - DEX file parsing and analysis
- **engine:native** - Native library analysis (future)
- **engine:ioc** - Indicator of Compromise extraction
- **engine:rules** - Rule evaluation engine

### Feature Modules

- **feature:onboarding** - First-run experience
- **feature:home** - Main dashboard
- **feature:intake** - File import and confirmation
- **feature:analysis** - Analysis progress display
- **feature:report** - Report viewing and navigation
- **feature:cases** - Case management
- **feature:settings** - Application settings
- **feature:about** - About and attribution

## Security Architecture

### Trust Boundaries

1. **External Input Boundary**: Content URIs, filenames, MIME types
2. **Archive Boundary**: ZIP entries, paths, compression metadata
3. **Parser Boundary**: APK structures, DEX files, certificates
4. **Report Boundary**: Findings, evidence, indicators

### Security Controls

- All input validated and bounded
- No network access in analysis path
- No sample execution or installation
- Encrypted report storage
- Backup exclusion for sensitive data
- Process isolation for parsers (planned)

## Data Flow

1. User selects file via SAF or share intent
2. Intake confirmation screen displays metadata
3. File copied to private app storage with hashing
4. File type detected via magic bytes
5. Analysis orchestrator coordinates parsers
6. Parsers extract facts and evidence
7. Rules engine evaluates facts
8. Report generated and encrypted
9. User views report and exports if needed
10. Case deleted or retained per policy

## Technology Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose with Material 3
- **Architecture**: MVVM with unidirectional data flow
- **Database**: Room for case metadata
- **Serialization**: Kotlin serialization
- **Concurrency**: Coroutines and Flow
- **Security**: Android Keystore, AES-256-GCM

## Future Enhancements

- Isolated process for analysis engine
- YARA rule support
- Additional file format parsers (PE, ELF, PDF, Office)
- APK comparison features
- Optional network intelligence (hash lookups)
