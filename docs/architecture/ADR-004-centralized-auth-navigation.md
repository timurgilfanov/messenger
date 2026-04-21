# ADR-004: Centralized authentication navigation

## Status
Accepted

## Context
Session expiry, initial auth resolution, successful login, and token-refresh re-emissions all need to route the user to the correct top-level destination (`Login` or `Main`). Prior iterations of the codebase drove this from individual ViewModels — each screen that could fail with `Unauthorized` defined its own `Unauthorized` side effect, and the main screen observed `AuthState` on top of that. The duplication created drift risk (a new feature forgets to add its `Unauthorized` effect) and double-navigation bugs (logout triggered both a per-screen effect and the main observer).

`MainActivity` uses Navigation 3 (`androidx.navigation3.runtime`) with a `rememberNavBackStack` stack held by the composable. Navigation decisions depend on the current stack (e.g., on token refresh the stack may already be `[Main, Chat]` and should not be clobbered). The ViewModel cannot own the stack because the stack lives in Compose state.

## Decision
Centralize auth-driven navigation in two places:

1. **`MainActivityViewModel`** collects `AuthRepository.authState` and emits a `MainActivitySideEffect` on every transition:
    - `AuthState.Authenticated` → `MainActivitySideEffect.Authenticated`
    - `AuthState.Unauthenticated` → `MainActivitySideEffect.Unauthenticated`

   Effects are **state-descriptive** (what happened), not **action-imperative** (what to do). The ViewModel cannot know the right action because it cannot see the back stack.

2. **`MessengerApp`** owns the `NavBackStack` and collects the effect flow via `repeatOnLifecycle(STARTED)`. It delegates each effect to the pure, `@VisibleForTesting` function `applyMainActivityEffect(effect, backStack: MutableList<NavKey>)`, which decides the action based on the stack:

    - **`Unauthenticated`**: if the top is already `Login` or `Signup`, keep the stack; otherwise clear and push `Login`.
    - **`Authenticated`**: if the stack is empty or the top is `Login`/`Signup`, clear and push `Main`; otherwise preserve the stack (so a restored `[Main, Chat]` survives token-refresh re-emissions and process recreation).

Individual ViewModels and screens MUST NOT observe `AuthState` for navigation, introduce per-screen `Unauthorized` side effects, or accept `onAuthFailure: () -> Unit` callbacks. Session expiry is covered by this single flow.

## Consequences
Positive:
- Single source of truth — screens cannot drift.
- `AuthState` re-emissions (token refresh, process recreation) are safe because the guard skips when the stack already reflects the new state.
- `applyMainActivityEffect` is a pure function over `MutableList<NavKey>` — tested exhaustively in `MainActivityNavigationEffectTest` without Compose, DI, or Robolectric.
- Dead side-effect and state duplication disappear from feature ViewModels (`ProfileSideEffects.Unauthorized`, `SettingsSideEffects.Unauthorized`, `LanguageSideEffects.Unauthorized`, `MainActivityUiState.initialDestination` are all gone).

Negative:
- Adding a new top-level authenticated destination that should survive re-emissions (e.g., an onboarding screen) requires updating the `Authenticated` guard branch to recognize it.
- Adding a new unauthenticated screen (e.g., password reset) requires updating the `Unauthenticated` guard branch to skip it, matching how it skips `Login`/`Signup`.
- The effect type leaks the assumption that auth has exactly two relevant states for navigation. If a third intermediate state is added (e.g., requires-2FA), the effect type and guard must both grow.

## Alternatives Considered
1. **Per-screen `Unauthorized` side effects** (prior state). Each screen observed its own repository's `Unauthorized` error and emitted an effect that the screen turned into a `Navigate to Login` callback. Rejected: drift risk (new features forget to wire it), duplication with the main observer, and no way to suppress double-navigation during logout.

2. **Store the initial destination in `MainActivityUiState` and keep the effect channel for runtime expiry only.** Rejected: the state-vs-effect split duplicates the same information and forces `MainActivity` to merge two sources of truth at `setContent`. Eliminating `MainActivityUiState.initialDestination` in favor of "the first effect resolves the initial destination too" is simpler and has the same observable behavior.