See `docs/Specification.md` for project-level business requirements, UX requirements, sequrity requirements, and non-functional requirements.

## Architecture Overview
This is an Android messenger application built with Kotlin and Jetpack Compose, following Clean Architecture principles with a domain-driven design approach.

Package organization in `docs/package-organization.md`.

### Architecture Records and Decisions
Architecture Records (AR) and Architecture Decision Records (ADR) are kept in `docs/architecture/` and must be followed when implementing or reviewing code. Always check this directory for relevant records before making architectural decisions.

### Key Architectural Patterns
- **Result Pattern**: `ResultWithError<T, E>` wrapper for handling success/failure states
- **Use Case Pattern**: Each business operation is encapsulated in a dedicated use case class
- **Validation Pattern**: Separate validator classes with specific error types; feature-specific validators (interface + implementation) live in the feature module's `domain/validation` package (e.g., `feature/auth/.../auth/domain/validation/`); validator interfaces are `fun interface` so test stubs can be lambdas; the corresponding validation error sealed interface lives in `core/domain` when referenced by repository error types (e.g., `ProfileNameValidationError` in `...domain.usecase.auth.repository`), or in the feature module when purely feature-internal
- **Immutable Collections**: Uses `kotlinx-collections-immutable` for thread-safe data structures
- **Error Handling Pattern**: Repository errors follow a consistent cause preservation contract:
  - All `Unknown` error cases are `data class UnknownError(val cause: Throwable)` with non-nullable cause, never `data object`
  - Data source errors with `cause: Throwable` preserve it when mapping to repository errors
  - Use cases make decisions based on sealed interface hierarchy, not cause inspection
  - Causes are extracted ONLY for logging and diagnostics
  - See `SyncAllPendingSettingsUseCase.kt` for reference implementation pattern
  - Error dispatch helpers must be self-contained: each helper handles all its cases without assuming another helper has pre-filtered the input. Cross-coupled helpers (where one relies on the other to have already excluded certain cases) create silent dead branches and hidden `error()` guards — use flat dispatch instead
- **MVI Pattern**: ViewModels expose a single read-only `StateFlow` for persistent UI state and a `Channel`-backed `Flow` for one-off side effects (navigation, toasts, input clearing)
  - UI state must survive process termination: back the `StateFlow` with `SavedStateHandle` so state is restored after the process is killed and recreated
  - When a screen has overlapping async operations with business ordering invariants, apply AR-01: centralize all state commits in an `actor` with a `reduce` function (see `docs/architecture/AR-01-single-authority-for-ordering-rules.md`)
  - When a ViewModel supports multiple submit paths (e.g., credentials and Google), track the last attempted action via a private `sealed interface Last<ScreenName>Action` (e.g., `LastLoginAction`, `LastSignupAction`) and a matching `var last<ScreenName>Action` field; `retryLastAction()` dispatches on it to re-invoke the correct path
  - Button-enabled state is exposed as a computed `val` on `UiState` (e.g., `val isSubmitEnabled: Boolean get() = !isLoading && isCredentialsValid`). The ViewModel calls injected validators on every field update and stores the boolean result (e.g., `isCredentialsValid`, `isNameValid`) in state; the computed property combines these with `!isLoading`. Passwords are intentionally not persisted to `SavedStateHandle` for security — the computed property falls back to disabled on process death until the user re-enters the password fields
- **Vertical Feature Delivery**: Features are developed and shipped as complete vertical units (domain + data + UI, fully tested) rather than partial horizontal layers across multiple features

## KDoc
- Do not generate inline comments or code comments
- Generate KDoc only where the contract is not obvious from the type signature alone:
  - Interfaces and their members that define contracts between layers (repositories, data sources, validators, services)
  - Sealed error/validation hierarchies
  - Use cases with complex preconditions, non-standard return types (Flow), or side effects
- Update existing KDocs when modifying documented code

## Dependencies
- When adding a new dependency, verify it is not deprecated and use the latest stable version (no alpha, beta, RC, or SNAPSHOT unless explicitly approved)

## Testing
Full strategy in `docs/Testing Strategy.md`.
- **Fakes over Mocks**: Use test doubles other than mock or spy by default. We test behaviour not implementation.
- **Test fixtures**: For both JVM and Android library modules, use native Gradle `testFixtures { enable = true }` to share test doubles across modules. Requires `android.experimental.enableTestFixturesKotlinSupport=true` in `gradle.properties` for Android libraries. Consume with `testImplementation(testFixtures(project(":feature:X")))`.
- **Reproducibility**: Use constants for time and IDs instead of current time or randomly generated IDs to have a constant input for better reproduction and issue location.
- **Turbine `cancelAndIgnoreRemainingEvents()`**: Only use it when there are genuinely unconsumed events to discard (e.g. after `advanceUntilIdle()` on a `StateFlow` that emitted further updates). Do not use it as a default `StateFlow` teardown — Turbine cancels the flow and asserts no unconsumed events automatically when the `test {}` block ends.
- **`assertTrue` over `assert`**: Use `assertTrue` from `kotlin.test` for boolean assertions in tests. Kotlin's built-in `assert()` is a JVM assertion that can be silently disabled at runtime with `-da`.

## Workflows
- After editing any source file: run `./gradlew ktlintFormat detekt --auto-correct`

### Tests running
- When a specific test fails — isolate before running the full suite: run `./gradlew <module>:<TestTask> --tests "ClassName.testMethodName"`.
  - `:app` → `MockDebug` variant; Android library (e.g. `:feature:auth`) → `Debug`; pure JVM → no variant
  - Instrumented: `connected<Variant>AndroidTest`; Android unit: `test<Variant>UnitTest`; JVM: `test`
  - Category filter: `-Pannotation=timur.gilfanov.messenger.annotations.<Category>Test` (instrumented) or `-PtestCategory=timur.gilfanov.messenger.annotations.<Category>` (JVM). Categories explained in `docs/Testing Strategy.md`.
- When Android emulator needed run `$ANDROID_HOME/emulator/emulator -list-avds` to get a list of available emulators and run `$ANDROID_HOME/emulator/emulator -avd <name> -no-boot-anim` in background process to launch a emulator. 

## GitHub
### Issue creation
- Never add or remove the `agent` label on issues — it triggers paid automation.
- Follow relevant template in `.github/ISSUE_TEMPLATE`. Ask a user whitch template to choose if can not decide.
- Check for relevant labels, milestones and relation to existent issues.
- Get an approval from user befere create an issue. In approval request show issue text and meta-data.

### PR creation
- Follow template in `.github/pull_request_template.md`.

## CI/CD
- CI/CD pipeline details in `docs/ci-cd-pipeline.md`.
- See `docs/coverage-reports.md` for detailed coverage configuration, Codecov components, and test-specific flags.

## Self-Learning
When you discover important project knowledge during implementation that is not already captured in this file or linked docs — such as new architectural patterns, non-obvious conventions, critical gotchas, or structural changes — propose an update to this CLAUDE.md or the relevant doc file. Present the proposed change to the user for approval before applying it.
