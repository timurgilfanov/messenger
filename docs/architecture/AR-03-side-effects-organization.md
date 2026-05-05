# Architecture Rule 3: Side effects organization in ViewModels

## Status
Proposed

## Context
ViewModels emit one-off side effects through a `Channel`-backed `Flow`. Two naming conventions have appeared in the codebase:

- **Cause-based** (`SettingsSideEffects`): names describe the domain event — `Unauthorized`, `LoggedOut`, `ObserveSettingsFailed(error: LocalStorageError)`
- **Action-based** (`LoginSideEffects`): names describe the UI instruction — `NavigateToChatList`, `OpenAppSettings`, `ShowSnackbar(message)`

Both create problems at scale. Cause-based names leak domain error types into the UI and force the UI handler to make routing decisions ("is `Unauthorized` a snackbar or a navigation?"). Action-based names let the ViewModel commit to UI primitives (`ShowSnackbar`) and navigation destinations (`NavigateToChatList`), which couple it to the UI layer.

A structured debate across three architectural positions concluded that neither convention is correct in isolation and converged on a third approach.

## Applicability
This rule applies to every `XxxSideEffects` sealed interface emitted via the ViewModel's `Channel`.

## Rule

Organize side effects as a **two-level sealed hierarchy**: the outer level declares the effect category; the inner level names the specific event in user-facing terms.

Standard categories:

| Category | Purpose | Examples |
|---|---|---|
| `Navigation` | Route-agnostic transition the user must experience | `Authenticated`, `Unauthenticated`, `SignupRequested` |
| `Feedback` | Ambient information the user should receive (toast, snackbar, banner) | `NetworkUnavailable`, `LogoutFailed(isRetryable: Boolean)` |
| `System` | OS-level intent the app must fire | `OpenAppSettings`, `OpenStorageSettings` |
| `UiCommand` | Transient local UI command that is neither navigation, feedback, nor an OS intent | `InputCleared`, `Focused`, `ScrolledToTop` |

```kotlin
sealed interface SettingsSideEffects {
    sealed interface Feedback : SettingsSideEffects {
        data class ObserveFailed(val isFatal: Boolean) : Feedback
        data class LogoutFailed(val isRetryable: Boolean) : Feedback
    }
}

sealed interface LoginSideEffects {
    sealed interface Feedback : LoginSideEffects {
        data object NetworkUnavailable : Feedback
        data object ServiceUnavailable : Feedback
        data object GoogleSignInFailed : Feedback
        data object StorageTemporarilyUnavailable : Feedback
        data class TooManyAttempts(val remaining: Duration) : Feedback
    }
    sealed interface System : LoginSideEffects {
        data object OpenAppSettings : System
        data object OpenStorageSettings : System
    }
}

sealed interface ChatSideEffects {
    sealed interface UiCommand : ChatSideEffects {
        data object InputCleared : UiCommand
    }
}
```

## Naming rules for inner cases

Inner cases must be named after **what happened to the user**, not after the domain class or use case that produced it.

- Correct: `CredentialsRejected`, `NetworkUnavailable`, `ObserveFailed`
- Incorrect: `AuthRepositoryReturnedInvalidCredentials`, `LocalStorageError`, `ObserveSettingsUseCaseFailed`

Domain error types (sealed interfaces from the domain or data layer, e.g. `LocalStorageError`, `LogoutUseCaseError`, `ObserveProfileRepositoryError`) must not appear as parameter types in the side effects interface. The ViewModel maps them to UI-relevant descriptors (booleans, enums, strings) before emitting.

## Rationale

**Why categories:** The ViewModel knows what kind of UI response is needed (navigate, notify, fire intent, run a local UI command). The UI knows how to render within that kind. The outer category is the stable contract between them; the inner case is the semantic detail that drives content. This stops the ViewModel from committing to UI primitives (snackbar vs dialog) while also stopping the UI from reasoning about domain error semantics.

**Why route-agnostic navigation:** Navigation side effects must describe the user-visible state or intent, not a concrete route name. ViewModels should not depend on the current navigation graph or route keys. The handler maps route-agnostic cases such as `Navigation.SignupRequested` to the concrete route.

**Why two levels instead of flat:** The category and naming rules solve the action-based and cause-based leaks. The two-level shape makes those rules explicit in the type system: handlers dispatch first by category, then by the user-facing event within that category. A flat hierarchy can still use good names, but it makes category boundaries implicit and easier to erode during later changes.

**Why inner cases must be user-facing:** If inner cases carry raw domain error types, the UI must import and interpret domain layer types, which recreates the cause-based leak. If inner cases carry implementation-flavored names, the UI still reasons about domain specifics. User-facing names are domain-neutral and stable.

## Implications
- ViewModel must map domain errors to UI-relevant descriptors before emitting
- Feature ViewModels do not emit per-screen auth navigation effects; auth routing follows ADR-004
- `ShowSnackbar` is no longer a valid effect name — use `Feedback.XxxEvent`; the UI handler decides the widget
- Transient local UI commands use `UiCommand.XxxEvent`; do not force input clearing, focus, or scroll commands into `Feedback`, `Navigation`, or `System`
- UI handlers dispatch on the outer category first, then branch on inner case for content

## Enforcement
- Side effects interfaces use the two-level hierarchy: an outer category (`Navigation`, `Feedback`, `System`, `UiCommand`) and inner named cases
- Use only the categories that apply to the screen; do not add empty category interfaces
- Domain error types do not appear as parameter types in any `XxxSideEffects` interface
- Inner case names are user-facing, not domain-class names
- Navigation case names are route-agnostic; `ToXxx` and `NavigateToXxx` names are rejected unless the route itself is the user-facing concept
- `ShowSnackbar`, `ShowDialog`, `ShowToast` are rejected as effect names in code review
- Flat single-level side effects interfaces are flagged in code review

## Anti-patterns
- `data class ShowSnackbar(val message: SnackbarMessage)` — ViewModel commits to rendering mechanism
- `data class ObserveSettingsFailed(val error: LocalStorageError)` — raw domain error type crosses the boundary
- `data object NavigateToChatList` — ViewModel commits to a concrete route name
- `data object Unauthorized` and `data object LoggedOut` as sibling feature-screen cases that duplicate centralized auth navigation
- Inner cases named after the domain class or use case: `ObserveSettingsUseCaseFailed`
- Adding a new category (e.g., `Analytics`) without updating this rule — category creep degrades dispatch discipline
