# Add Password Confirmation Field on Signup Screen

## Overview
Add a "Confirm Password" `OutlinedTextField` to the Signup screen that validates locally that both password fields match before allowing credential-based signup. The `isCredentialsSubmitEnabled` computed property is updated to require passwords to match. The Google signup path is unaffected.

## Context
- Files involved:
  - `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/ui/screen/signup/SignupUiState.kt`
  - `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/ui/screen/signup/SignupViewModel.kt`
  - `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/ui/screen/signup/SignupScreen.kt`
  - `feature/auth/src/test/kotlin/timur/gilfanov/messenger/auth/ui/SignupViewModelButtonStateTest.kt`
- Related patterns: `isCredentialsValid` / `isNameValid` boolean flags in `SignupUiState`; computed `isCredentialsSubmitEnabled`; passwords not persisted to `SavedStateHandle` for security; `updatePassword` recomputes validation on every keystroke
- Dependencies: none

## Development Approach
- **Testing approach**: Regular (code first, then tests)
- Complete each task fully before moving to the next
- **CRITICAL: every task MUST include new/updated tests**
- **CRITICAL: all tests must pass before starting next task**

## Implementation Steps

### Task 1: Update SignupUiState and SignupViewModel

**Files:**
- Modify: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/ui/screen/signup/SignupUiState.kt`
- Modify: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/ui/screen/signup/SignupViewModel.kt`
- Modify: `feature/auth/src/test/kotlin/timur/gilfanov/messenger/auth/ui/SignupViewModelButtonStateTest.kt`

- [x] Add `confirmPassword: String = ""` to `SignupUiState` (not backed by `SavedStateHandle`, same security reason as `password`)
- [x] Add `isPasswordConfirmed: Boolean = false` to `SignupUiState`
- [x] Update `isCredentialsSubmitEnabled` to `!isLoading && isNameValid && isCredentialsValid && isPasswordConfirmed`
- [x] Add `updateConfirmPassword(confirmPassword: String)` to `SignupViewModel`: computes `isPasswordConfirmed = confirmPassword == _state.value.password`, updates state with new `confirmPassword` and `isPasswordConfirmed`
- [x] Update `updatePassword()` to also recompute `isPasswordConfirmed` against current `confirmPassword` in state
- [x] Update existing tests that set "all valid" state (`all valid - both buttons enabled`, `loading state - both buttons disabled`, `both buttons re-enabled after loading completes`) to also call `viewModel.updateConfirmPassword("password1")`
- [x] Add new tests: `valid credentials with mismatched confirm password - credentials button disabled`, `valid credentials after confirm password matches - credentials button enabled`, `credentials button disabled when confirm password cleared after match`
- [x] Run `./gradlew :feature:auth:testDebugUnitTest` — must pass before Task 2

### Task 2: Add Confirm Password Field to SignupScreen

**Files:**
- Modify: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/ui/screen/signup/SignupScreen.kt`

- [x] Add confirm password `OutlinedTextField` below the password field, using the same password visual transformation (masking) as the password field
- [x] Wire `onValueChange` to `viewModel.updateConfirmPassword()`
- [x] Run `./gradlew :feature:auth:testDebugUnitTest` — must pass before Task 3

### Task 3: Update documentation

- [x] Move this plan to `docs/plans/completed/`
