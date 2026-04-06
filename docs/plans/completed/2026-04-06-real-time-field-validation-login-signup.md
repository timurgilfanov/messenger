# Real-Time Field Validation on Login and Signup Screens (#321)

## Overview
Wire the existing validators to set per-field inline errors in the ViewModels during field
updates, not only on form submission. The UI composables and UiState types already support
displaying these errors; only the ViewModels need to change.

## Context
- Files involved:
  - `feature/auth/src/main/kotlin/.../ui/screen/login/LoginViewModel.kt`
  - `feature/auth/src/main/kotlin/.../ui/screen/signup/SignupViewModel.kt`
  - `feature/auth/src/test/kotlin/.../ui/LoginViewModelRealTimeValidationTest.kt` (new)
  - `feature/auth/src/test/kotlin/.../ui/SignupViewModelRealTimeValidationTest.kt` (new)
- Related patterns:
  - `CredentialsValidationError.Email(reason)` where `reason: EmailValidationError`
    implements `LoginEmailError` and `SignupEmailError` directly — no mapping needed
  - `CredentialsValidationError.Password(reason)` where `reason: PasswordValidationError`
  - The UI already reads `state.emailError`, `state.passwordError`, `state.nameError` and
    passes them to `supportingText`/`isError` — no UI changes needed
  - UiState already has all error fields — no UiState changes needed
  - `isSubmitEnabled` already guards on both `isCredentialsValid` and `emailError == null`
    — button behavior unchanged
- Dependencies: none

## Development Approach
- **Testing approach**: Regular (code first, then tests)
- Complete each task fully before moving to the next
- **CRITICAL: every task MUST include new/updated tests**
- **CRITICAL: all tests must pass before starting next task**

## Implementation Steps

### Task 1: Real-time validation in LoginViewModel

**Files:**
- Modify: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/ui/screen/login/LoginViewModel.kt`

- [x] In `updateEmail()`: run `credentialsValidator.validate()`, extract `CredentialsValidationError.Email(reason)` if present and set `emailError = reason`; otherwise set `emailError = null`
- [x] In `updatePassword()`: run `credentialsValidator.validate()`, extract `CredentialsValidationError.Password(reason)` if present and set `passwordError = reason`; otherwise set `passwordError = null`
- [x] Run `./gradlew ktlintFormat detekt --auto-correct`

### Task 2: Real-time validation in SignupViewModel

**Files:**
- Modify: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/ui/screen/signup/SignupViewModel.kt`

- [x] In `updateName()`: extract failure reason from `profileNameValidator.validate()` and set `nameError`; clear on success
- [x] In `updateEmail()`: same extraction pattern as LoginViewModel
- [x] In `updatePassword()`: same extraction pattern as LoginViewModel
- [x] Run `./gradlew ktlintFormat detekt --auto-correct`

### Task 3: ViewModel unit tests

**Files:**
- Create: `feature/auth/src/test/kotlin/timur/gilfanov/messenger/auth/ui/LoginViewModelRealTimeValidationTest.kt`
- Create: `feature/auth/src/test/kotlin/timur/gilfanov/messenger/auth/ui/SignupViewModelRealTimeValidationTest.kt`

- [x] LoginViewModel: entering invalid email sets `emailError`; fixing it clears `emailError`
- [x] LoginViewModel: entering invalid password sets `passwordError`; fixing it clears `passwordError`
- [x] LoginViewModel: email error does not appear in initial state (before any edits)
- [x] SignupViewModel: entering invalid name sets `nameError`; fixing it clears `nameError`
- [x] SignupViewModel: entering invalid email sets `emailError`; fixing it clears `emailError`
- [x] SignupViewModel: entering invalid password sets `passwordError`; fixing it clears `passwordError`
- [x] SignupViewModel: name/email error do not appear on restore from SavedStateHandle (before any edits in current session)
- [x] Run `./gradlew :feature:auth:testDebugUnitTest`

### Task 4: Verify acceptance criteria

- [x] Run full test suite: `./gradlew :feature:auth:testDebugUnitTest`
- [x] Run linter: `./gradlew ktlintFormat detekt --auto-correct`
- [x] Move this plan to `docs/plans/completed/`
