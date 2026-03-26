# Disable Submit Buttons When Required Fields Are Invalid

## Overview
Add `isNameValid` and `isCredentialsValid` stored booleans to `SignupUiState`, and `isCredentialsValid` to `LoginUiState`.
These are set by injected validators on every field update. Computed properties `isCredentialsSubmitEnabled`,
`isGoogleSubmitEnabled`, and `isSubmitEnabled` derive from these booleans plus `!isLoading`, giving the complete button
enabled state. Screens use these properties directly.

## Context
- Files involved:
  - `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/ui/screen/signup/SignupUiState.kt`
  - `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/ui/screen/signup/SignupViewModel.kt`
  - `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/ui/screen/signup/SignupScreen.kt`
  - `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/ui/screen/login/LoginUiState.kt`
  - `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/ui/screen/login/LoginViewModel.kt`
  - `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/ui/screen/login/LoginScreen.kt`
  - `feature/auth/src/testFixtures/kotlin/timur/gilfanov/messenger/auth/ui/SignupViewModelTestFixtures.kt`
  - `feature/auth/src/testFixtures/kotlin/timur/gilfanov/messenger/auth/ui/LoginViewModelTestFixtures.kt`
  - `feature/auth/src/test/kotlin/timur/gilfanov/messenger/auth/ui/SignupViewModelInitialStateTest.kt`
  - `feature/auth/src/test/kotlin/timur/gilfanov/messenger/auth/ui/LoginViewModelInitialStateTest.kt`
  - Create: `feature/auth/src/test/kotlin/timur/gilfanov/messenger/auth/ui/SignupViewModelButtonStateTest.kt`
  - Create: `feature/auth/src/test/kotlin/timur/gilfanov/messenger/auth/ui/LoginViewModelButtonStateTest.kt`
- Related patterns: MVI UiState computed properties; validators already provided by Hilt (`ProfileNameValidator` at
  `ViewModelComponent`, `CredentialsValidator` at `SingletonComponent`) — no DI changes needed
- Button state rules: Google signup requires valid name only; credentials signup requires valid name + valid email +
  valid password; login requires valid email + valid password
- `isSigningUpWithGoogle` / `isSigningInWithGoogle` are screen-local composable state (credential manager flow) —
  Google buttons keep `&& !isSigningUpWithGoogle` in the screen

## Development Approach
- **Testing approach**: Regular (code first, then tests)
- Complete each task fully before moving to the next
- **CRITICAL: every task MUST include new/updated tests**
- **CRITICAL: all tests must pass before starting next task**

## Implementation Steps

### Task 1: SignupScreen — add button enable state

**Files:**
- Modify: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/ui/screen/signup/SignupUiState.kt`
- Modify: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/ui/screen/signup/SignupViewModel.kt`
- Modify: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/ui/screen/signup/SignupScreen.kt`
- Modify: `feature/auth/src/testFixtures/kotlin/timur/gilfanov/messenger/auth/ui/SignupViewModelTestFixtures.kt`
- Modify: `feature/auth/src/test/kotlin/timur/gilfanov/messenger/auth/ui/SignupViewModelInitialStateTest.kt`
- Create: `feature/auth/src/test/kotlin/timur/gilfanov/messenger/auth/ui/SignupViewModelButtonStateTest.kt`

- [x] Add `isNameValid: Boolean = false` and `isCredentialsValid: Boolean = false` to `SignupUiState`
- [x] Add computed properties to `SignupUiState`:
  - `val isGoogleSubmitEnabled: Boolean get() = !isLoading && isNameValid`
  - `val isCredentialsSubmitEnabled: Boolean get() = !isLoading && isNameValid && isCredentialsValid`
- [x] Add `ProfileNameValidator` and `CredentialsValidator` constructor params to `SignupViewModel` (Hilt injects both automatically)
- [x] Compute initial `isNameValid` and `isCredentialsValid` in the `_state` initializer using saved state handle values and both validators
- [x] Update `updateName` to call `profileNameValidator.validate(name)` and include `isNameValid` result in the `copy()`
- [x] Update `updateEmail` to call `credentialsValidator.validate(Credentials(Email(email), Password(currentPassword)))` and include `isCredentialsValid` result in the `copy()`
- [x] Update `updatePassword` to call `credentialsValidator.validate(Credentials(Email(currentEmail), Password(password)))` and include `isCredentialsValid` result in the `copy()`
- [x] Update credentials submit button in `SignupScreen`: `enabled = state.isCredentialsSubmitEnabled`
- [x] Update Google submit button in `SignupScreen`: `enabled = state.isGoogleSubmitEnabled && !isSigningUpWithGoogle`
- [x] Add `viewModelNameValidator: ProfileNameValidator = ProfileNameValidatorImpl()` and `viewModelCredentialsValidator: CredentialsValidator = CredentialsValidatorImpl()` params to `SignupViewModelTestFixtures.createViewModel()` and pass them to `SignupViewModel` constructor
- [x] Add `assertFalse(state.isGoogleSubmitEnabled)` and `assertFalse(state.isCredentialsSubmitEnabled)` to `SignupViewModelInitialStateTest`
- [x] Create `SignupViewModelButtonStateTest` using default `createViewModel()` (real validators); test cases: all empty → both disabled; valid name only → only Google enabled; valid credentials (no name) → both disabled; valid name + valid email but invalid password → Google enabled only; all valid → both enabled; loading state → both disabled
- [x] Run `./gradlew :feature:auth:testDebugUnitTest` — must pass before task 2

### Task 2: LoginScreen — add button enable state

**Files:**
- Modify: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/ui/screen/login/LoginUiState.kt`
- Modify: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/ui/screen/login/LoginViewModel.kt`
- Modify: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/ui/screen/login/LoginScreen.kt`
- Modify: `feature/auth/src/testFixtures/kotlin/timur/gilfanov/messenger/auth/ui/LoginViewModelTestFixtures.kt`
- Modify: `feature/auth/src/test/kotlin/timur/gilfanov/messenger/auth/ui/LoginViewModelInitialStateTest.kt`
- Create: `feature/auth/src/test/kotlin/timur/gilfanov/messenger/auth/ui/LoginViewModelButtonStateTest.kt`

- [x] Add `isCredentialsValid: Boolean = false` to `LoginUiState`
- [x] Add computed property `val isSubmitEnabled: Boolean get() = !isLoading && isCredentialsValid`
- [x] Add `CredentialsValidator` constructor param to `LoginViewModel` (Hilt injects automatically)
- [x] Compute initial `isCredentialsValid` in the `_state` initializer from saved state handle values
- [x] Update `updateEmail` to call `credentialsValidator.validate(Credentials(Email(email), Password(currentPassword)))` and include `isCredentialsValid` in the `copy()`
- [x] Update `updatePassword` to call `credentialsValidator.validate(Credentials(Email(currentEmail), Password(password)))` and include `isCredentialsValid` in the `copy()`
- [x] Update credentials sign-in button in `LoginScreen`: `enabled = state.isSubmitEnabled`
- [x] Google sign-in button is not gated on field validation — leave it unchanged
- [x] Add `viewModelCredentialsValidator: CredentialsValidator = CredentialsValidatorImpl()` param to `LoginViewModelTestFixtures.createViewModel()` and pass it to `LoginViewModel` constructor
- [x] Add `assertFalse(state.isSubmitEnabled)` to `LoginViewModelInitialStateTest`
- [x] Create `LoginViewModelButtonStateTest` using default `createViewModel()` (real validators); test cases: both empty → disabled; valid email only → disabled; valid password only → disabled; both valid → enabled; loading state → disabled
- [x] Run `./gradlew :feature:auth:testDebugUnitTest` — must pass before task 3

### Task 3: Final verification

- [x] Run `./gradlew :feature:auth:testDebugUnitTest`
- [x] Move this plan to `docs/plans/completed/`
