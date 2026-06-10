# Migrate Activity and Chat Side Effects to AR-03

## Overview
Refactor `MainActivitySideEffect` and `ChatSideEffect` from flat single-level interfaces into the AR-03 two-level category hierarchy. No externally observable behavior changes — this is a structural refactor that removes the last AR-03 exceptions and provides reference examples for the `Navigation` and `UiCommand` categories. Part of #327, resolves #358.

## Context
- Files involved:
  - `app/src/main/java/timur/gilfanov/messenger/ui/activity/MainActivitySideEffect.kt`
  - `app/src/main/java/timur/gilfanov/messenger/ui/activity/MainActivityViewModel.kt`
  - `app/src/main/java/timur/gilfanov/messenger/ui/activity/MainActivity.kt`
  - `app/src/main/java/timur/gilfanov/messenger/ui/screen/chat/ChatSideEffect.kt`
  - `app/src/main/java/timur/gilfanov/messenger/ui/screen/chat/ChatViewModel.kt`
  - `app/src/main/java/timur/gilfanov/messenger/ui/screen/chat/ChatScreen.kt`
  - `app/src/test/java/timur/gilfanov/messenger/ui/activity/MainActivityViewModelTest.kt`
  - `app/src/test/java/timur/gilfanov/messenger/ui/activity/MainActivityNavigationEffectTest.kt`
  - `app/src/test/java/timur/gilfanov/messenger/ui/screen/chat/ChatViewModelMessageSendingTest.kt`
  - `app/src/test/java/timur/gilfanov/messenger/ui/screen/chat/ChatViewModelRetryTest.kt`
- Related patterns: `docs/architecture/AR-03-side-effects-organization.md` defines the exact target shapes for the `Navigation` and `UiCommand` categories.
- Dependencies: none

## Target shapes (from AR-03)
```kotlin
sealed interface MainActivitySideEffect {
    sealed interface Navigation : MainActivitySideEffect {
        data object Authenticated : Navigation
        data object Unauthenticated : Navigation
    }
}

sealed interface ChatSideEffect {
    sealed interface UiCommand : ChatSideEffect {
        data object InputCleared : UiCommand
    }
}
```

## Development Approach
- **Testing approach**: Regular (code first, then update tests) — this is a rename/restructure refactor with existing test coverage; tests are updated alongside the code change in each task.
- Complete each task fully before moving to the next.
- **CRITICAL: every task MUST include new/updated tests**
- **CRITICAL: all tests must pass before starting the next task**

## Implementation Steps

### Task 1: Migrate MainActivitySideEffect to AR-03 Navigation category

**Files:**
- Modify: `app/src/main/java/timur/gilfanov/messenger/ui/activity/MainActivitySideEffect.kt`
- Modify: `app/src/main/java/timur/gilfanov/messenger/ui/activity/MainActivityViewModel.kt`
- Modify: `app/src/main/java/timur/gilfanov/messenger/ui/activity/MainActivity.kt`
- Modify: `app/src/test/java/timur/gilfanov/messenger/ui/activity/MainActivityViewModelTest.kt`
- Modify: `app/src/test/java/timur/gilfanov/messenger/ui/activity/MainActivityNavigationEffectTest.kt`

- [x] Wrap `Authenticated`/`Unauthenticated` in a `Navigation` category interface in `MainActivitySideEffect.kt`
- [x] Update `MainActivityViewModel.kt` mapping to emit `Navigation.Authenticated` / `Navigation.Unauthenticated`
- [x] Update the effect handler in `MainActivity.kt` to dispatch on the outer category first (`is Navigation`), then the inner case
- [x] Update `MainActivityViewModelTest.kt` and `MainActivityNavigationEffectTest.kt` to reference the new nested names
- [x] Run the narrowest verification: `./gradlew :app:testMockDebugUnitTest --tests "timur.gilfanov.messenger.ui.activity.MainActivityViewModelTest" --tests "timur.gilfanov.messenger.ui.activity.MainActivityNavigationEffectTest"` — must pass before Task 2

### Task 2: Migrate ChatSideEffect to AR-03 UiCommand category, rename to InputCleared

**Files:**
- Modify: `app/src/main/java/timur/gilfanov/messenger/ui/screen/chat/ChatSideEffect.kt`
- Modify: `app/src/main/java/timur/gilfanov/messenger/ui/screen/chat/ChatViewModel.kt`
- Modify: `app/src/main/java/timur/gilfanov/messenger/ui/screen/chat/ChatScreen.kt`
- Modify: `app/src/test/java/timur/gilfanov/messenger/ui/screen/chat/ChatViewModelMessageSendingTest.kt`
- Modify: `app/src/test/java/timur/gilfanov/messenger/ui/screen/chat/ChatViewModelRetryTest.kt`

- [x] Wrap the effect in a `UiCommand` category and rename `ClearInputText` to `InputCleared` in `ChatSideEffect.kt`
- [x] Update the emission in `ChatViewModel.kt` to `UiCommand.InputCleared`
- [x] Update the effect handler in `ChatScreen.kt` to dispatch on the category first (`is UiCommand`), then the inner case
- [x] Update `ChatViewModelMessageSendingTest.kt` (all `assertIs` sites) and `ChatViewModelRetryTest.kt` type references to the new name
- [x] Run: `./gradlew :app:testMockDebugUnitTest --tests "timur.gilfanov.messenger.ui.screen.chat.ChatViewModelMessageSendingTest" --tests "timur.gilfanov.messenger.ui.screen.chat.ChatViewModelRetryTest"` — must pass before Task 3

### Task 3: Verify acceptance criteria

- [x] Run `./gradlew ktlintFormat detekt --auto-correct`
- [x] Run `./gradlew :app:testMockDebugUnitTest`
- [x] Run `./gradlew :app:compileMockDebugAndroidTestKotlin` (signature changes may touch androidTest)
- [x] Confirm no remaining references to `ClearInputText` or flat `MainActivitySideEffect.Authenticated` / `MainActivitySideEffect.Unauthenticated`
- [x] Verify test coverage remains at 80%+ (no coverage drop; refactor preserves existing tests)

### Task 4: Update documentation

- [ ] No README/CLAUDE.md changes expected (no user-facing change or new internal pattern); AR-03 already documents the shape
- [ ] Move this plan to `docs/plans/completed/`
