---
# Merge local and remote field validation errors in feature:auth (#304, #314, #318)

## Overview
Three related issues addressed in one PR:
- #304: Split `CredentialsValidationError` into nested `Email` and `Password` sealed interfaces for compile-time type safety
- #318: Create `EmailValidationUseCaseError` and `PasswordValidationUseCaseError` merged types that unify local + remote errors under a single use-case-level path, eliminating the confusing dual paths (`ValidationFailed` for local, `InvalidEmail` for remote)
- #314: Route server-side field errors (`EmailTaken`, `PasswordTooShort` from server, etc.) to the relevant field (`emailError`, `passwordError`) instead of the general error area

No externally observable behavior changes except #314's UX fix.

## Context
- Files involved: `CredentialsValidationError.kt`, `CredentialsValidatorImpl.kt`, `LoginUseCaseError.kt`, `SignupWithCredentialsUseCaseError.kt`, both use case impl files, both UiState files, both ViewModel files, both Screen composable files, `strings.xml`, testFixtures, multiple test files
- Related patterns: ResultWithError pattern, MVI ViewModel pattern, validator fun interfaces, sealed error hierarchies with cause preservation
- Dependencies: none new

## Development Approach
- **Testing approach**: Regular (code first, then tests)
- Complete each task fully before moving to the next
- **CRITICAL: every task MUST include new/updated tests**
- **CRITICAL: all tests must pass before starting next task**

## Implementation Steps

### Task 1: Domain layer — split CredentialsValidationError and create merged use-case types

**Files:**
- Modify: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/domain/validation/CredentialsValidationError.kt`
- Modify: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/domain/validation/CredentialsValidatorImpl.kt`
- Create: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/domain/usecase/EmailValidationUseCaseError.kt`
- Create: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/domain/usecase/PasswordValidationUseCaseError.kt`
- Modify: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/domain/usecase/LoginUseCaseError.kt`
- Modify: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/domain/usecase/SignupWithCredentialsUseCaseError.kt`
- Modify: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/domain/usecase/LoginWithCredentialsUseCaseImpl.kt`
- Modify: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/domain/usecase/SignupWithCredentialsUseCaseImpl.kt`
- Modify: `feature/auth/src/test/kotlin/timur/gilfanov/messenger/auth/domain/validation/CredentialsValidatorImplTest.kt`
- Modify: `feature/auth/src/test/kotlin/timur/gilfanov/messenger/auth/ui/LoginWithCredentialsUseCaseTest.kt`
- Modify: `feature/auth/src/test/kotlin/timur/gilfanov/messenger/auth/domain/usecase/SignupWithCredentialsUseCaseTest.kt`

- [x] Convert `CredentialsValidationError` from a sealed class to a sealed interface with two nested sealed interfaces: `Email` (BlankEmail, InvalidEmailFormat, NoAtInEmail, EmailTooLong, NoDomainAtEmail, ForbiddenCharacterInEmail) and `Password` (PasswordTooShort, PasswordTooLong, ForbiddenCharacterInPassword, PasswordMustContainNumbers, PasswordMustContainAlphabet)
- [x] Update `CredentialsValidatorImpl` to return the nested types (e.g., `CredentialsValidationError.Email.BlankEmail`)
- [x] Create `EmailValidationUseCaseError` sealed interface with local email cases (same names, same field types as `CredentialsValidationError.Email`) plus remote cases from `EmailValidationError` (EmailTaken, EmailNotExists, UnknownRuleViolation)
- [x] Create `PasswordValidationUseCaseError` sealed interface merging local password cases and server cases; use `Int?` for `PasswordTooShort.minLength` and `PasswordTooLong.maxLength` to accommodate server returning null; add `UnknownRuleViolation` from server
- [x] Update `LoginUseCaseError`: remove `ValidationFailed`, update `InvalidEmail` to use `EmailValidationUseCaseError`, add `InvalidPassword(reason: PasswordValidationUseCaseError)`; update `LoginRepositoryError.toUseCaseError()`; add internal mapping extensions `CredentialsValidationError.Email.toUseCaseError(): EmailValidationUseCaseError` and `CredentialsValidationError.Password.toUseCaseError(): PasswordValidationUseCaseError` and `repository.EmailValidationError.toEmailUseCaseError()` for use by both use case impls
- [x] Update `SignupWithCredentialsUseCaseError`: remove `ValidationFailed`, update `InvalidEmail` and `InvalidPassword` to use merged types; update `SignupRepositoryError.toUseCaseError()` to map `PasswordValidationError` through a mapper; add mapping extension `repository.PasswordValidationError.toUseCaseError(): PasswordValidationUseCaseError`
- [x] Update `LoginWithCredentialsUseCaseImpl`: map `CredentialsValidationError.Email` → `LoginUseCaseError.InvalidEmail`, `CredentialsValidationError.Password` → `LoginUseCaseError.InvalidPassword`
- [x] Update `SignupWithCredentialsUseCaseImpl`: map `CredentialsValidationError.Email` → `SignupWithCredentialsUseCaseError.InvalidEmail`, `CredentialsValidationError.Password` → `SignupWithCredentialsUseCaseError.InvalidPassword`
- [x] Update `CredentialsValidatorImplTest` to reference nested type names (e.g., `CredentialsValidationError.Email.BlankEmail`)
- [x] Update `LoginWithCredentialsUseCaseTest`: replace `ValidationFailed` assertions with `InvalidEmail(EmailValidationUseCaseError.BlankEmail)` and `InvalidPassword(PasswordValidationUseCaseError.PasswordTooShort)` assertions
- [x] Update `SignupWithCredentialsUseCaseTest`: replace `ValidationFailed` assertion with `InvalidEmail`, update `InvalidEmail`/`InvalidPassword` assertions to use new merged types

### Task 2: UI layer — route server field errors to fields and update screens

**Files:**
- Modify: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/ui/screen/login/LoginUiState.kt`
- Modify: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/ui/screen/signup/SignupUiState.kt`
- Modify: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/ui/screen/login/LoginViewModel.kt`
- Modify: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/ui/screen/signup/SignupViewModel.kt`
- Modify: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/ui/screen/login/LoginScreen.kt`
- Modify: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/ui/screen/signup/SignupScreen.kt`
- Modify: `feature/auth/src/test/kotlin/timur/gilfanov/messenger/auth/ui/LoginViewModelSubmitTest.kt`
- Modify: `feature/auth/src/test/kotlin/timur/gilfanov/messenger/auth/ui/SignupViewModelSubmitTest.kt`
- Modify: `feature/auth/src/test/kotlin/timur/gilfanov/messenger/auth/ui/SignupViewModelCredentialsSignupTest.kt`

- [x] Update `LoginUiState`: change `emailError: CredentialsValidationError?` → `emailError: EmailValidationUseCaseError?`, `passwordError: CredentialsValidationError?` → `passwordError: PasswordValidationUseCaseError?`; remove `LoginGeneralError.InvalidEmail` (server email errors now go to `emailError`)
- [x] Update `SignupUiState`: change `emailError` and `passwordError` to use merged use-case types; remove `SignupGeneralError.InvalidEmail` and `SignupGeneralError.InvalidPassword` (these now go to `emailError`/`passwordError`)
- [x] Update `LoginViewModel`: remove `handleValidationError()`; in the `when (error)` block route `InvalidEmail` → `state.copy(emailError = error.reason, isCredentialsValid = false)` and `InvalidPassword` → `state.copy(passwordError = error.reason, isCredentialsValid = false)`
- [x] Update `SignupViewModel`: remove `handleValidationError()`; route `InvalidEmail` → `emailError` and `InvalidPassword` → `passwordError` directly (removing the `SignupGeneralError.InvalidEmail`/`InvalidPassword` paths); update `submitSignupWithCredentials` and `submitSignupWithGoogle` as needed
- [x] Update `LoginScreen`: replace `CredentialsValidationError.toDisplayString()` with two `@Composable` extension functions — `EmailValidationUseCaseError.toDisplayString()` (local cases reuse existing `login_error_*` strings; server cases EmailNotExists/EmailTaken/UnknownRuleViolation reuse existing `login_error_invalid_email`); `PasswordValidationUseCaseError.toDisplayString()` (local cases reuse existing strings; null minLength/maxLength and UnknownRuleViolation fall back to `signup_error_invalid_password_server`); remove `LoginGeneralError.InvalidEmail` case from `LoginGeneralError.toDisplayString()`; update error previews
- [x] Update `SignupScreen`: similarly replace `CredentialsValidationError.toDisplayString()` with typed functions; for `EmailValidationUseCaseError` use signup-appropriate strings for `EmailTaken` and generic for others; for `PasswordValidationUseCaseError` use field-appropriate strings; remove `SignupGeneralError.InvalidEmail` and `InvalidPassword` cases from `SignupGeneralError.toDisplayString()`; update previews
- [x] Update `LoginViewModelSubmitTest`: change `repository InvalidEmail sets generalError InvalidEmail` test to assert `emailError` is `EmailValidationUseCaseError.EmailNotExists` (or relevant type) instead of `generalError`; update `validatorError = CredentialsValidationError.BlankEmail` call sites to `CredentialsValidationError.Email.BlankEmail`; update assertions to check `EmailValidationUseCaseError.BlankEmail` in `emailError` and `PasswordValidationUseCaseError.PasswordTooShort` in `passwordError`
- [x] Update `SignupViewModelSubmitTest`: update `CredentialsValidationError.BlankEmail` references to nested types; update assertions from `CredentialsValidationError.BlankEmail` in `emailError` to `EmailValidationUseCaseError.BlankEmail`; similarly for password
- [x] Update `SignupViewModelCredentialsSignupTest`: change `InvalidEmail server sets generalError InvalidEmail` to assert `emailError` is `EmailValidationUseCaseError.EmailTaken`; change `InvalidPassword server sets generalError InvalidPassword` to assert `passwordError` is `PasswordValidationUseCaseError.PasswordTooShort`; update `CredentialsValidationError.BlankEmail`/`PasswordTooShort` references to nested types; update assertions on `emailError`/`passwordError`

### Task 3: Verify acceptance criteria

- [x] run `./gradlew :feature:auth:assembleDebugAndroidTest`
- [x] Confirm `LoginGeneralError` no longer has `InvalidEmail` variant
- [x] Confirm `SignupGeneralError` no longer has `InvalidEmail` or `InvalidPassword` variants
- [x] Confirm `CredentialsValidationError` has nested `Email` and `Password` subtypes
- [x] Confirm `ValidationFailed` is removed from both use case error types

### Task 4: Update documentation

- [x] Move this plan to `docs/plans/completed/`
