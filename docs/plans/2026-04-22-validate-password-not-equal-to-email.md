---
# feat: Validate Password Not Equal to Email (#328)

## Overview
Add a cross-field validation rule to `CredentialsValidatorImpl` that rejects passwords identical to the email address. Involves adding a new `PasswordEqualToEmail` error case to the domain model, wiring it into the validator, mapping it to a display string, and adding string resources.

## Context
- Files involved:
  - `core/domain/src/main/kotlin/timur/gilfanov/messenger/domain/usecase/auth/repository/PasswordValidationError.kt`
  - `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/domain/validation/CredentialsValidatorImpl.kt`
  - `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/ui/utils/DisplayStrings.kt`
  - `feature/auth/src/main/res/values/strings.xml`
  - `feature/auth/src/main/res/values-de/strings.xml`
  - `feature/auth/src/test/kotlin/timur/gilfanov/messenger/auth/domain/validation/CredentialsValidatorImplTest.kt`
- Related patterns: Validation Pattern in CLAUDE.md; `2026-04-06-real-time-field-validation-login-signup.md` for task structure
- Dependencies: none

## Development Approach
- **Testing approach**: Regular (code first, then tests)
- Complete each task fully before moving to the next
- **CRITICAL: every task MUST include new/updated tests**
- **CRITICAL: all tests must pass before starting next task**

## Implementation Steps

### Task 1: Add error case, display string, and string resources

**Files:**
- Modify: `core/domain/src/main/kotlin/timur/gilfanov/messenger/domain/usecase/auth/repository/PasswordValidationError.kt`
- Modify: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/ui/utils/DisplayStrings.kt`
- Modify: `feature/auth/src/main/res/values/strings.xml`
- Modify: `feature/auth/src/main/res/values-de/strings.xml`

- [x] Add `data object PasswordEqualToEmail : PasswordValidationError` to the sealed interface
- [x] Add `is PasswordValidationError.PasswordEqualToEmail -> stringResource(R.string.auth_error_password_must_not_equal_email)` branch to `PasswordValidationError.toDisplayString()` in `DisplayStrings.kt`
- [x] Add `<string name="auth_error_password_must_not_equal_email">Password must not be the same as your email</string>` to `values/strings.xml`
- [x] Add German translation `<string name="auth_error_password_must_not_equal_email">Passwort darf nicht mit der E-Mail-Adresse übereinstimmen</string>` to `values-de/strings.xml`
- [x] Run `./gradlew ktlintFormat detekt --auto-correct`
- [x] Run `./gradlew :feature:auth:testDebugUnitTest` to confirm no regressions from the new sealed case (exhaustive `when` in `DisplayStrings` will be checked at compile time via `compileMockDebugAndroidTestKotlin`)
- [x] Run `./gradlew :app:compileMockDebugAndroidTestKotlin` to verify exhaustive `when` coverage across all modules

### Task 2: Add validation logic and tests

**Files:**
- Modify: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/domain/validation/CredentialsValidatorImpl.kt`
- Modify: `feature/auth/src/test/kotlin/timur/gilfanov/messenger/auth/domain/validation/CredentialsValidatorImplTest.kt`

- [x] In `validate(credentials: Credentials)`, after the existing password check succeeds, add: if `credentials.password.value == credentials.email.value` return `Failure(CredentialsValidationError.Password(PasswordValidationError.PasswordEqualToEmail))`
- [x] Add test: `password equal to email returns PasswordEqualToEmail`
- [x] Add test: `password different from email passes` (confirm the happy-path control, email=`"user@example.com"`, password=`"user@example.com1"` returns success)
- [x] Run `./gradlew ktlintFormat detekt --auto-correct`
- [x] Run `./gradlew :feature:auth:testDebugUnitTest --tests "timur.gilfanov.messenger.auth.domain.validation.CredentialsValidatorImplTest"` — must pass

### Task 3: Verify acceptance criteria

- [ ] Run `./gradlew ktlintFormat detekt --auto-correct`
- [ ] Run `./gradlew :feature:auth:testDebugUnitTest`
- [ ] Run `./gradlew :app:compileMockDebugAndroidTestKotlin`

### Task 4: Update documentation

- [ ] Move this plan to `docs/plans/completed/`
