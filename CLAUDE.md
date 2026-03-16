# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

- Do not generate inline comments or code comments
- Generate KDoc only where the contract is not obvious from the type signature alone:
  - Interfaces and their members that define contracts between layers (repositories, data sources, validators, services)
  - Sealed error/validation hierarchies
  - Use cases with complex preconditions, non-standard return types (Flow), or side effects
- Update existing KDocs when modifying documented code

See `docs/Specification.md` for project-level business requirements, UX requirements, sequrity requirements, and non-functional requirements.

## Development Commands

Full reference: `docs/development-commands.md`

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

Package organization in `docs/package-organization.md`.

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
CI/CD pipeline details in `docs/ci-cd-pipeline.md`.
- **Fakes over Mocks**: Use test doubles other than mock or spy by default. We test behaviour not implementation.
- **Test fixtures**: For both JVM and Android library modules, use native Gradle `testFixtures { enable = true }` to share test doubles across modules. Requires `android.experimental.enableTestFixturesKotlinSupport=true` in `gradle.properties` for Android libraries. Consume with `testImplementation(testFixtures(project(":feature:X")))`.
- **Reproducibility**: Use constants for time and IDs instead of current time or randomly generated IDs to have a constant input for better reproduction and issue location.

### Self-Learning
When you discover important project knowledge during implementation that is not already captured in this file or linked docs — such as new architectural patterns, non-obvious conventions, critical gotchas, or structural changes — propose an update to this CLAUDE.md or the relevant doc file. Present the proposed change to the user for approval before applying it.
