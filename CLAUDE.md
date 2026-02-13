# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Development Commands

### Building and Testing
```bash
# Build the project
./gradlew build

# Run unit tests (default: mock flavor)
./gradlew testMockDebugUnitTest

# Run unit tests for specific flavors
./gradlew testDevDebugUnitTest     # Development environment
./gradlew testStagingDebugUnitTest # Staging environment
./gradlew testProductionDebugUnitTest # Production environment

# Run instrumentation tests
./gradlew connectedMockDebugAndroidTest

# Run tests with coverage
./gradlew testMockDebugUnitTest -Pcoverage

# Run a single test class
./gradlew testMockDebugUnitTest --tests "ClassName"
./gradlew connectedMockDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=ClassName

# Run a single test method
./gradlew testMockDebugUnitTest --tests "ClassName.testMethodName"

# Run tests by category (property-based approach)
./gradlew testMockDebugUnitTest -PtestCategory=timur.gilfanov.messenger.annotations.Architecture
./gradlew testMockDebugUnitTest -PtestCategory=timur.gilfanov.messenger.annotations.Unit
./gradlew testMockDebugUnitTest -PtestCategory=timur.gilfanov.messenger.annotations.Component
./gradlew testMockDebugUnitTest -PtestCategory=timur.gilfanov.messenger.annotations.Feature
./gradlew connectedMockDebugAndroidTest -Pannotation=timur.gilfanov.messenger.annotations.FeatureTest
./gradlew connectedMockDebugAndroidTest -Pannotation=timur.gilfanov.messenger.annotations.ApplicationTest

# Pre-commit checks
./gradlew preCommit                   # Run all pre-commit checks (checks staged files)
                                      # IMPORTANT: Run this before committing when changes are ready for compilation and testing
./gradlew preCommit -Pforce           # Run all checks unconditionally (CI / agent)

# Exclude specific categories
./gradlew testMockDebugUnitTest -PexcludeCategory=timur.gilfanov.messenger.annotations.Architecture
```

### Code Quality
- Do not generate comments
- Run code quality checks and auto corrections after code editing
```bash
# Auto-format code with ktlint and use detekt to static analysis with autocorrection
./gradlew ktlintFormat detekt --auto-correct

# Run kover test coverage
./gradlew koverXmlReportMockDebug
```

### Test Categories with Coverage
```bash
# Run specific test categories with coverage
./gradlew testMockDebugUnitTest -PtestCategory=timur.gilfanov.messenger.annotations.Unit -Pcoverage
./gradlew testMockDebugUnitTest -PtestCategory=timur.gilfanov.messenger.annotations.Component -Pcoverage

# Generate category-specific coverage reports
./gradlew koverXmlReportMockDebug && ./gradlew generateCategorySpecificReports -PtestCategory=timur.gilfanov.messenger.annotations.Unit
```

### Codecov Components
Components are configured in `codecov.yml` and automatically organize coverage by code paths:
```bash
# Components are path-based and work automatically with any coverage upload
# No special commands needed - just run tests with coverage and upload to Codecov
# View component coverage in Codecov dashboard under "Components" tab
```

### Coverage Reports
The CI/CD pipeline provides **two-dimensional coverage analysis** using both Codecov Components and test-specific flags for comprehensive insights:

#### **Test-Specific Flags (What was tested)**
**Test Category Coverage (Local Tests):**
- `architecture,local` - Coverage from Architecture tests only
- `unit,local` - Coverage from Unit tests only  
- `component,robolectric` - Coverage from Component tests only
- `feature,local` - Coverage from Feature tests only
- `application,local` - Coverage from Application unit tests only

**Device-Specific Coverage (Firebase Test Lab):**
- `application,emulator,phone` - Coverage from Application tests on phone emulators only
- `application,device,phone` - Coverage from Application tests on phone devices only
- `application,device,foldable` - Coverage from Application tests on foldable devices only
- `release_candidate,device,phone` - Coverage from Release Candidate tests on phone devices only
- `release_candidate,device,tablet` - Coverage from Release Candidate tests on tablet devices only
- `release_candidate,device,foldable` - Coverage from Release Candidate tests on foldable devices only

#### **Codecov Components (Which code was tested)**
**Domain Layer Components:**
- `domain_entities` - Core business entities (Chat, Message, Participant)
- `domain_usecases` - Business logic operations
- `validation_logic` - Entity validation rules

**Feature-Based Components:**
- `chat_feature` - Chat-related entities, use cases, and UI
- `message_feature` - Message-related entities and use cases

**UI Layer Components:**
- `ui_screens` - Compose screens and ViewModels
- `ui_theme` - Theme and styling components

**Architecture Components:**
- `data_layer` - Repository implementations
- `dependency_injection` - Hilt modules
- `application_core` - MainActivity and Application class

#### **Two-Dimensional Analysis**
Combine Components + Flags for insights like:
- **"Chat Feature Unit Test Coverage"**: `chat_feature` component + `unit,local` flag
- **"Domain Entities Device Coverage"**: `domain_entities` component + `application,device,phone` flag  
- **"UI Screens Component Test Coverage"**: `ui_screens` component + `component,robolectric` flag
- **"Validation Logic Architecture Coverage"**: `validation_logic` component + `architecture,local` flag

#### **Coverage Precision**
Each coverage report contains **only** the coverage data from:
- **Specific test category** that was executed (Unit, Component, Architecture, Feature, Application, Release Candidate)
- **Specific environment** where tests ran (local, robolectric, emulator, device)  
- **Specific device type** that was used (phone, tablet, foldable)
- **Specific code components** as defined in `codecov.yml` (automatically filtered by path)

This ensures accurate coverage tracking without cross-contamination and provides rich insights for both testing strategy and code organization decisions.

## Architecture Overview

This is an Android messenger application built with Kotlin and Jetpack Compose, following Clean Architecture principles with a domain-driven design approach.

### Domain Layer Structure
- **Entities**: Core business objects (`Chat`, `Message`, `Participant`) with validation logic
- **Use Cases**: Business logic operations for chat and message management
- **Repository Interface**: Data access abstraction
- **Validation**: Dedicated validators for domain entities with error types

### Key Architectural Patterns
- **Result Pattern**: `ResultWithError<T, E>` wrapper for handling success/failure states
- **Use Case Pattern**: Each business operation is encapsulated in a dedicated use case class
- **Validation Pattern**: Separate validator classes with specific error types
- **Immutable Collections**: Uses `kotlinx-collections-immutable` for thread-safe data structures
- **Error Handling Pattern**: Repository errors follow a consistent cause preservation contract:
  - All `Unknown` error cases are `data class UnknownError(val cause: Throwable)` with non-nullable cause, never `data object`
  - Data source errors with `cause: Throwable` preserve it when mapping to repository errors
  - Use cases make decisions based on sealed interface hierarchy, not cause inspection
  - Causes are extracted ONLY for logging and diagnostics
  - See `SyncAllPendingSettingsUseCase.kt` for reference implementation pattern
- **MVI Store Pattern**: MVI model classes should end with `Store` and must follow AR-01:
  - State is private mutable, exposed as read-only
  - Only `actor` can commit UI state updates (call `reduce`)
  - **Tests must exist for all ordering invariants** (e.g., "search clears paging", "refresh cancels pending load", "latest wins")
  - See `docs/architecture/AR-01-single-authority-for-ordering-rules.md` for details

### Testing
Full strategy in `docs/Testing Strategy.md`.
- **Fakes over Mocks**: Use test doubles other than mock or spy by default. We test behaviour not implementation.
- **Reproducibility**: Use constants for time and IDs instead of current time or randomly generated IDs to have a constant input for better reproduction and issue location.
- **Test Categories**: Tests are organized by category:
  - `Architecture`: Verify architecture rules
  - `Unit`: Test single method or class with minimal dependencies
  - `Component`: Test multiply classes together  
  - `Feature`: Test integration between two or more components
  - `Application`: Test deployable binary to verify application functionality
  - `ReleaseCandidate`: Verifies the critical user journeys of a release build and performance

### CI/CD Pipeline
The CI/CD pipeline is organized by test categories and follows the Testing Strategy execution matrix:

**Every Commit (push + PR):**
- `build`: lint + detekt + architecture tests + APK generation
- `unit-component-tests`: Unit and Component tests (fast feedback)

**Local Development:**
- `preCommit`: Run all pre-commit checks locally before committing (auto-formatting + lint + detekt + architecture + unit + component tests)

**Pre-merge (PR only):**
- `feature-tests`: Feature tests on emulators
- `application-tests-emulator`: Application unit tests locally + instrumentation tests on Firebase Test Lab emulators

**Post-merge (main branch):**
- `application-tests-devices`: Application instrumentation tests on real devices via Firebase Test Lab

**Pre-release (tags):**
- `release-candidate-tests`: Release Candidate tests on multiple devices with release build

**Coverage Integration:**
- Each job uploads test-specific coverage reports with precise flags (e.g., `unit,local`, `application,device,phone`)
- Codecov Components automatically organize coverage by code structure (defined in `codecov.yml`)
- No CI/CD changes needed for Components - they work automatically with existing coverage uploads
- Codecov dashboard provides both flag-based and component-based views of coverage data

### Code Quality Tools
- **Detekt**: Static analysis with custom rules in `config/detekt/detekt.yml`
- **Ktlint**: Code formatting with Compose rules
- **Kover**: Test coverage reporting
- **Custom Rules**: Compose-specific detekt and ktlint rules enabled

### Package Organization
```
domain/
├── entity/           # Core business entities with validation
│   ├── chat/        # Chat-related entities and validation
│   └── message/     # Message-related entities and validation
└── usecase/         # Business logic operations
    ├── chat/        # Chat management use cases
    └── message/     # Message management use cases
```

