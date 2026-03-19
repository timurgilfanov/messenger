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

Three mandatory categories:

| Category | Purpose | Examples |
|---|---|---|
| `Navigation` | Screen transition the user must experience | `ToLogin`, `ToChatList`, `ToSignup` |
| `Feedback` | Ambient information the user should receive (toast, snackbar, banner) | `NetworkUnavailable`, `LogoutFailed(isRetryable: Boolean)` |
| `System` | OS-level intent the app must fire | `OpenAppSettings`, `OpenStorageSettings` |

```kotlin
sealed interface SettingsSideEffects {
    sealed interface Navigation : SettingsSideEffects {
        data object ToLogin : Navigation
    }
    sealed interface Feedback : SettingsSideEffects {
        data class ObserveFailed(val isFatal: Boolean) : Feedback
        data class LogoutFailed(val isRetryable: Boolean) : Feedback
    }
}

sealed interface LoginSideEffects {
    sealed interface Navigation : LoginSideEffects {
        data object ToChatList : Navigation
        data object ToSignup : Navigation
    }
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
```

## Naming rules for inner cases

Inner cases must be named after **what happened to the user**, not after the domain class or use case that produced it.

- Correct: `CredentialsRejected`, `NetworkUnavailable`, `ObserveFailed`
- Incorrect: `AuthRepositoryReturnedInvalidCredentials`, `LocalStorageError`, `ObserveSettingsUseCaseFailed`

Domain error types (sealed interfaces from the domain or data layer, e.g. `LocalStorageError`, `LogoutError`, `ObserveProfileRepositoryError`) must not appear as parameter types in the side effects interface. The ViewModel maps them to UI-relevant descriptors (booleans, enums, strings) before emitting.

## Rationale

**Why categories:** The ViewModel knows what kind of UI response is needed (navigate, notify, fire intent). The UI knows how to render within that kind. The outer category is the stable contract between them; the inner case is the semantic detail that drives content. This stops the ViewModel from committing to UI primitives (snackbar vs dialog) while also stopping the UI from reasoning about domain error semantics.

**Why two levels instead of flat:** A flat action-based hierarchy forces the ViewModel to commit to rendering mechanisms (`ShowSnackbar`, `ShowDialog`). A flat cause-based hierarchy forces the UI to resolve many-to-one mappings (`Unauthorized` and `LoggedOut` both navigate to login — the UI must know this). The two-level structure absorbs both problems: many-to-one domain causes map to one inner case (`Navigation.ToLogin`); rendering decisions stay in the UI.

**Why inner cases must be user-facing:** If inner cases carry raw domain error types, the UI must import and interpret domain layer types, which recreates the cause-based leak. If inner cases carry implementation-flavored names, the UI still reasons about domain specifics. User-facing names are domain-neutral and stable.

## Implications
- ViewModel must map domain errors to UI-relevant descriptors before emitting
- `Unauthorized` and `LoggedOut` both emit `Navigation.ToLogin` — the ViewModel resolves the many-to-one mapping
- `ShowSnackbar` is no longer a valid effect name — use `Feedback.XxxEvent`; the UI handler decides the widget
- UI handlers dispatch on the outer category first, then branch on inner case for content
- A centralized composable can handle `Navigation.ToLogin` uniformly across features without branching on screen identity

## Enforcement
- Side effects interfaces use the two-level hierarchy: an outer category (`Navigation`, `Feedback`, `System`) and inner named cases
- Domain error types do not appear as parameter types in any `XxxSideEffects` interface
- Inner case names are user-facing, not domain-class names
- `ShowSnackbar`, `ShowDialog`, `ShowToast` are rejected as effect names in code review
- Flat single-level side effects interfaces are flagged in code review unless the screen has exactly one category of effect

## Anti-patterns
- `data class ShowSnackbar(val message: SnackbarMessage)` — ViewModel commits to rendering mechanism
- `data class ObserveSettingsFailed(val error: LocalStorageError)` — raw domain error type crosses the boundary
- `data object Unauthorized` and `data object LoggedOut` as sibling cases that always produce identical UI behavior — many-to-one mapping deferred to UI
- Inner cases named after the domain class or use case: `ObserveSettingsUseCaseFailed`
- Adding a fourth category (e.g., `Analytics`) without updating this rule — category creep degrades dispatch discipline

## Open issues
This rule is proposed but not yet applied to the existing codebase. The following interfaces were produced under the old conventions and will need migration:
- `SettingsSideEffects` (cause-based, raw domain error types as parameters)
- `ProfileSideEffects` (cause-based, raw domain error type as parameter)
- `LanguageSideEffects` (cause-based, raw domain error type as parameter)
- `LoginSideEffects` (action-based, `ShowSnackbar` as a category)
- `ProfileEditSideEffects` (mixed, raw domain error type as parameter)
- `ChatListSideEffects` (cause-based, single case — may be acceptable as-is after migration if `Unauthorized` becomes `Navigation.ToLogin`)