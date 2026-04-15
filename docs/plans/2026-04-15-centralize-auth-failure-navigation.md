# Centralize Runtime Auth-Failure Navigation in MainActivityViewModel (#335)

## Overview
Replace scattered `onAuthFailure` callbacks across screens with a single
centralized flow in `MainActivityViewModel`. The ViewModel observes
`authState` continuously; any runtime transition to `Unauthenticated` emits a
`NavigateToLogin` side effect that `MainActivity` handles centrally. No
externally observable behavior change.

## Context
- Files involved:
  - `app/.../ui/activity/MainActivityViewModel.kt` — replace `first()` with continuous collect, add effects channel
  - `app/.../ui/activity/MainActivity.kt` — collect effects centrally, remove `onAuthFailure` lambda
  - `app/.../ui/activity/MainActivityUiState.kt` — unchanged
  - `app/.../ui/screen/chatlist/ChatListScreen.kt` — remove `onAuthFailure` param and the Unauthorized LaunchedEffect
  - `app/.../ui/screen/chat/ChatScreen.kt` — remove `onAuthFailure` param, remove Unauthorized branch from LaunchedEffect
  - `app/.../ui/screen/settings/SettingsScreen.kt` — remove `onAuthFailure` param, remove Unauthorized/LoggedOut navigation branches
  - `app/.../ui/screen/settings/LanguageScreen.kt` — remove `onAuthFailure` param, remove Unauthorized branch from LaunchedEffect
  - `app/.../ui/screen/main/MainScreen.kt` — remove `onAuthFailure` param
  - `app/src/debug/.../ChatListScreenTestActivity.kt` — remove `onAuthFailure` arg
  - `app/src/debug/.../ChatScreenTestActivity.kt` — remove `onAuthFailure` arg
  - `app/src/debug/.../SettingsScreenTestActivity.kt` — remove `onAuthFailure` arg
  - `app/src/test/.../ui/activity/MainActivityViewModelTest.kt` — add tests for runtime NavigateToLogin effect
  - `app/src/test/.../ui/screen/settings/LanguageScreenSideEffectsTest.kt` — remove `calls onAuthFailure when unauthorized` test
- Related patterns: MVI side effects via Channel-backed Flow (see `SettingsViewModel.effects`); `repeatOnLifecycle(STARTED)` in Composable for effect collection
- New file: `app/.../ui/activity/MainActivitySideEffect.kt`

## Development Approach
- **Testing approach**: Regular (code first, then tests)
- Complete each task fully before moving to the next
- **CRITICAL: every task MUST include new/updated tests**
- **CRITICAL: all tests must pass before starting next task**

## Implementation Steps

### Task 1: Add `MainActivitySideEffect` and update `MainActivityViewModel`

The key design: check `_uiState.value is Loading` to distinguish the initial
auth emission (which sets `Ready`) from runtime emissions (which trigger
`NavigateToLogin`). This replaces the current `authState.first()` call.

**Files:**
- Create: `app/src/main/java/timur/gilfanov/messenger/ui/activity/MainActivitySideEffect.kt`
- Modify: `app/src/main/java/timur/gilfanov/messenger/ui/activity/MainActivityViewModel.kt`
- Modify: `app/src/test/java/timur/gilfanov/messenger/ui/activity/MainActivityViewModelTest.kt`

- [x] Create `MainActivitySideEffect` sealed interface with `data object NavigateToLogin`
- [x] In `MainActivityViewModel`: add `_effects: Channel<MainActivitySideEffect>(Channel.BUFFERED)` and `val effects = _effects.receiveAsFlow()`
- [x] Replace `authState.first()` with `authState.collect { state -> if (_uiState.value is Loading) { setReadyState(state) } else if (state is Unauthenticated) { _effects.send(NavigateToLogin) } }`
- [x] Run `./gradlew ktlintFormat detekt --auto-correct`
- [x] Add test: `authenticated runtime session expiry emits NavigateToLogin effect` (Authenticated initial state → then Unauthenticated → effect emitted)
- [x] Add test: `initial Unauthenticated state does not emit NavigateToLogin effect` (verify no effect on startup)
- [x] Add test: `initial Authenticated state does not emit NavigateToLogin effect`
- [x] Run `./gradlew :app:testMockDebugUnitTest --tests "MainActivityViewModelTest"` — must pass

### Task 2: Update `MainActivity` to collect effects centrally

**Files:**
- Modify: `app/src/main/java/timur/gilfanov/messenger/ui/activity/MainActivity.kt`

- [x] Add `effects: Flow<MainActivitySideEffect>` parameter to `MessengerApp` and pass `viewModel.effects` from `setContent`
- [x] Add `effects` parameter to `MessengerAppReady`
- [x] In `MessengerAppReady`: add `LaunchedEffect(lifecycleOwner)` with `repeatOnLifecycle(STARTED)` that collects effects; handle `NavigateToLogin` by `backStack.clear(); backStack.add(Login)`
- [x] Remove the `val onAuthFailure: () -> Unit` lambda
- [x] Remove `onAuthFailure = onAuthFailure` from every screen call inside `entryProvider` (ChatList, Chat, Main, Language entries)
- [x] Run `./gradlew ktlintFormat detekt --auto-correct`
- [x] Run `./gradlew :app:testMockDebugUnitTest` — must pass

### Task 3: Remove `onAuthFailure` from individual screens

**Files:**
- Modify: `app/src/main/java/timur/gilfanov/messenger/ui/screen/chatlist/ChatListScreen.kt`
- Modify: `app/src/main/java/timur/gilfanov/messenger/ui/screen/chat/ChatScreen.kt`
- Modify: `app/src/main/java/timur/gilfanov/messenger/ui/screen/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/timur/gilfanov/messenger/ui/screen/settings/LanguageScreen.kt`
- Modify: `app/src/main/java/timur/gilfanov/messenger/ui/screen/main/MainScreen.kt`

- [x] `ChatListScreen`: remove `onAuthFailure` parameter; remove the `LaunchedEffect` that only handled `ChatListSideEffects.Unauthorized` (since `Unauthorized` was its only effect)
- [x] `ChatScreen`: remove `onAuthFailure` parameter; remove the `ChatSideEffect.Unauthorized -> currentOnAuthFailure.value()` branch from the LaunchedEffect; remove `currentOnAuthFailure`
- [x] `SettingsScreen`: remove `onAuthFailure` parameter and `currentOnAuthFailure`; remove `ProfileSideEffects.Unauthorized -> currentOnAuthFailure()` branch; remove `SettingsSideEffects.Unauthorized -> currentOnAuthFailure()` branch; remove `SettingsSideEffects.LoggedOut -> currentOnAuthFailure()` branch
- [x] `LanguageScreen`: remove `onAuthFailure` parameter and `currentOnAuthFailure`; remove `LanguageSideEffects.Unauthorized -> currentOnAuthFailure()` branch
- [x] `MainScreen`: remove `onAuthFailure` parameter; remove it from `ChatListScreen(...)` and `SettingsScreen(...)` calls
- [x] Run `./gradlew ktlintFormat detekt --auto-correct`
- [x] Run `./gradlew :app:testMockDebugUnitTest` — must pass

### Task 4: Update debug test activities and tests

**Files:**
- Modify: `app/src/debug/java/timur/gilfanov/messenger/ChatListScreenTestActivity.kt`
- Modify: `app/src/debug/java/timur/gilfanov/messenger/ChatScreenTestActivity.kt`
- Modify: `app/src/debug/java/timur/gilfanov/messenger/SettingsScreenTestActivity.kt`
- Modify: `app/src/test/java/timur/gilfanov/messenger/ui/screen/settings/LanguageScreenSideEffectsTest.kt`

- [x] Remove `onAuthFailure = {}` and `onAuthFailure = { finish() }` from debug test activities
- [x] In `LanguageScreenSideEffectsTest`: remove `calls onAuthFailure when unauthorized` test (behavior now centralized in `MainActivityViewModel`); remove `onAuthFailure` from remaining test invocations
- [ ] Run `./gradlew ktlintFormat detekt --auto-correct`
- [ ] Run `./gradlew :app:testMockDebugUnitTest` — must pass

### Task 5: Verify acceptance criteria

- [ ] Run full test suite: `./gradlew :app:testMockDebugUnitTest`
- [ ] Run linter: `./gradlew ktlintFormat detekt --auto-correct`
- [ ] Confirm no `onAuthFailure` references remain in production source: `grep -r "onAuthFailure" app/src/main`

### Task 6: Update documentation

- [ ] Move this plan to `docs/plans/completed/`
