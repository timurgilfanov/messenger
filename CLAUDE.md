# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Development Commands

Full reference: `docs/development-commands.md`

- Do not generate comments
- Run code quality checks and auto corrections after code editing
```bash
./gradlew preCommit                   # Pre-commit checks (formatting + lint + detekt + tests)
./gradlew ktlintFormat detekt --auto-correct  # Auto-format and static analysis
./gradlew testMockDebugUnitTest       # Run unit tests
```

### Coverage Reports
See `docs/coverage-reports.md` for detailed coverage configuration, Codecov components, and test-specific flags.

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
- **MVI Pattern**: ViewModels expose a single read-only `StateFlow` for persistent UI state and a `Channel`-backed `Flow` for one-off side effects (navigation, toasts, input clearing)
  - When a screen has overlapping async operations with business ordering invariants, apply AR-01: centralize all state commits in an `actor` with a `reduce` function (see `docs/architecture/AR-01-single-authority-for-ordering-rules.md`)
- **Vertical Feature Delivery**: Features are developed and shipped as complete vertical units (domain + data + UI, fully tested) rather than partial horizontal layers across multiple features

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

### Code Quality Tools
- **Detekt**: Static analysis with custom rules in `config/detekt/detekt.yml`
- **Ktlint**: Code formatting with Compose rules
- **Kover**: Test coverage reporting
- **Custom Rules**: Compose-specific detekt and ktlint rules enabled

### Package Organization
```
app/src/main/java/timur/gilfanov/messenger/
├── domain/
│   ├── entity/           # Core business entities with validation
│   │   ├── chat/        # Chat-related entities and validation
│   │   ├── message/     # Message-related entities and validation
│   │   ├── profile/     # Profile entity and validation
│   │   └── settings/    # Settings entity
│   └── usecase/         # Business logic operations
│       ├── chat/        # Chat management use cases
│       ├── message/     # Message management use cases
│       ├── profile/     # Profile use cases
│       ├── settings/    # Settings use cases
│       └── common/      # Shared use cases
├── data/
│   ├── repository/      # Repository implementations
│   ├── source/          # Data sources
│   │   ├── local/       # Room database
│   │   ├── remote/      # API calls
│   │   └── paging/      # Paging data sources
│   └── worker/          # WorkManager workers
├── ui/
│   ├── screen/          # Compose screens and ViewModels
│   ├── theme/           # Theme and styling
│   └── activity/        # Activity classes
├── di/                  # Hilt dependency injection modules
└── navigation/          # Navigation destinations
```

### Self-Learning
When you discover important project knowledge during implementation that is not already captured in this file or linked docs — such as new architectural patterns, non-obvious conventions, critical gotchas, or structural changes — propose an update to this CLAUDE.md or the relevant doc file. Present the proposed change to the user for approval before applying it.
