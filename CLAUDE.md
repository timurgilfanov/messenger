# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Development Commands

### Building and Testing
```bash
# Build the project
./gradlew build

# Run unit tests
./gradlew testDebugUnitTest

# Run instrumentation tests 
./gradlew connectedDebugAndroidTest

# Run tests with coverage
./gradlew testDebugUnitTest -Pcoverage

# Run a single test class
./gradlew testDebugUnitTest --tests "ClassName"

# Run a single test method
./gradlew testDebugUnitTest --tests "ClassName.testMethodName"

# Run tests by category (property-based approach)
./gradlew testDebugUnitTest -PtestCategory=timur.gilfanov.messenger.Unit
./gradlew testDebugUnitTest -PtestCategory=timur.gilfanov.messenger.Component
./gradlew testDebugUnitTest -PtestCategory=timur.gilfanov.messenger.Architecture
./gradlew testDebugUnitTest -PtestCategory=timur.gilfanov.messenger.Feature
./gradlew testDebugUnitTest -PtestCategory=timur.gilfanov.messenger.Application
./gradlew connectedDebugAndroidTest -PtestCategory=timur.gilfanov.messenger.Application

# Pre-commit checks
./gradlew preCommit                   # Run all pre-commit checks (formatting, lint, detekt, architecture, unit, component tests)

# Exclude specific categories
./gradlew testDebugUnitTest -PexcludeCategory=timur.gilfanov.messenger.Architecture
```

### Code Quality
```bash
# Run detekt static analysis
./gradlew detekt

# Run ktlint formatting check
./gradlew ktlintCheck

# Auto-format code with ktlint
./gradlew ktlintFormat

# Run kover test coverage
./gradlew koverHtmlReport
```

### Test Categories with Coverage
```bash
# Run specific test categories with coverage
./gradlew testArchitectureWithCoverage
./gradlew testUnitWithCoverage
./gradlew testComponentWithCoverage

# Run individual categories with coverage (alternative)
./gradlew testDebugUnitTest -PtestCategory=timur.gilfanov.messenger.Architecture -Pcoverage
./gradlew testDebugUnitTest -PtestCategory=timur.gilfanov.messenger.Unit -Pcoverage
./gradlew testDebugUnitTest -PtestCategory=timur.gilfanov.messenger.Component -Pcoverage
```

### Coverage Reports
The CI/CD pipeline uploads **test-specific** coverage reports to Codecov with precise flags that match exactly what was tested:

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

**Flag Precision:**
Each coverage report contains **only** the coverage data from the specific:
- Test category that was executed (Unit, Component, Architecture, Feature, Application, Release Candidate)
- Environment where tests ran (local, robolectric, emulator, device)  
- Device type that was used (phone, tablet, foldable)

This ensures accurate coverage tracking without cross-contamination between different test types or execution environments.

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

### Testing Strategy
Full strategy in `Testing Strategy.md`.
- **Fakes over Mocks**: Uses fake implementations (`RepositoryFake`) instead of mocking frameworks
- **Builder Pattern**: Test builders for domain entities (`ChatBuilder`, `MessageBuilder`)
- **Integration Tests**: Tests cover use case interactions with repository layer
- **Turbine**: For testing Kotlin Flow emissions
- **Test Categories**: Tests are organized by category using JUnit's `@Category` annotation (as defined in Testing Strategy.md):
  - `Unit`: Test single method or class with minimal dependencies
  - `Component`: Test multiply classes together  
  - `Architecture`: Verify architecture rules
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

### Annotation Processing
- **KAPT**: Currently using KAPT for Hilt dependency injection (Hilt 2.56.2)
- **KSP Migration**: Multiple KSP migration attempts failed due to compatibility issues
  - Hilt 2.51.1: KSP generates invalid factory class names for Hilt modules
  - Hilt 2.56.2: Same issue persists - method names like `chatId-aANd5Fw` are invalid Java identifiers
  - Root cause: Known bug in KSP+Hilt integration where generated method names contain invalid characters
  - Decision: Stay with KAPT until KSP+Hilt compatibility is fully resolved