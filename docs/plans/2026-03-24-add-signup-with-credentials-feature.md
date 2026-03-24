# Add Signup with Credentials Feature

## Overview
Implement email/password signup. The data layer (remote `register()` endpoint, `AuthRepositoryImpl.signup()`, fakes) is pre-scaffolded. This feature adds: dedicated data-layer test coverage, a `SignupWithCredentialsUseCase`, and extends `SignupViewModel`, `SignupUiState`, and `SignupScreen` with email/password form fields and a credentials submit path.

## Context
- Files involved:
  - Create: `feature/auth/src/test/.../data/source/remote/RemoteAuthDataSourceRegisterTest.kt`
  - Create: `feature/auth/src/test/.../data/repository/AuthRepositorySignupTest.kt`
  - Create: `feature/auth/src/main/.../domain/usecase/SignupWithCredentialsUseCase.kt`
  - Create: `feature/auth/src/main/.../domain/usecase/SignupWithCredentialsUseCaseError.kt`
  - Create: `feature/auth/src/main/.../domain/usecase/SignupWithCredentialsUseCaseImpl.kt`
  - Create: `feature/auth/src/test/.../ui/SignupViewModelCredentialsSignupTest.kt`
  - Modify: `feature/auth/src/main/.../ui/screen/signup/SignupUiState.kt`
  - Modify: `feature/auth/src/main/.../ui/screen/signup/SignupViewModel.kt`
  - Modify: `feature/auth/src/main/.../ui/screen/signup/SignupScreen.kt`
  - Modify: `feature/auth/src/testFixtures/.../ui/SignupViewModelTestFixtures.kt`
  - Modify: `feature/auth/src/test/.../ui/SignupViewModelSubmitTest.kt`
  - Modify: `feature/auth/src/test/.../ui/SignupViewModelProcessDeathTest.kt`
  - Modify: `feature/auth/src/test/.../ui/SignupViewModelInitialStateTest.kt`
  - Modify: `feature/auth/src/main/.../di/AuthViewModelModule.kt`
  - Modify: `feature/auth/src/androidTest/java/timur/gilfanov/messenger/feature/auth/SignupFeatureTest.kt`
- Related patterns:
  - `LoginWithCredentialsUseCaseImpl` + `LoginUseCaseError` as reference for use case structure
  - `SignupWithGoogleUseCaseImpl` + `SignupWithGoogleUseCaseError` as reference for name validation pattern
  - `LoginViewModel.submitLogin()` + `LoginViewModel.LastLoginAction` as reference for credentials submit + retry pattern
  - `LoginUiState` as reference for email/password/emailError/passwordError fields
  - `AuthRepositorySignupWithGoogleTest` as reference for `AuthRepositorySignupTest`
  - `RemoteAuthDataSourceSignupWithGoogleTest` as reference for `RemoteAuthDataSourceRegisterTest`
- Dependencies: none new

## Development Approach
- **Testing approach**: Regular (code first, then tests)
- Complete each task fully before moving to the next
- **CRITICAL: every task MUST include new/updated tests**
- **CRITICAL: all tests must pass before starting next task**

## Implementation Steps

### Task 1: Data Layer Tests

**Files:**
- Create: `feature/auth/src/test/kotlin/timur/gilfanov/messenger/auth/data/source/remote/RemoteAuthDataSourceRegisterTest.kt`
- Create: `feature/auth/src/test/kotlin/timur/gilfanov/messenger/auth/data/repository/AuthRepositorySignupTest.kt`

- [x] Write `RemoteAuthDataSourceRegisterTest` covering: success returns AuthTokens; `EMAIL_TAKEN` → `InvalidEmail(EmailTaken)`; `EMAIL_NOT_EXISTS` → `InvalidEmail(EmailNotExists)`; `PASSWORD_TOO_SHORT` with/without `min_length` detail; `PASSWORD_TOO_LONG` with/without `max_length` detail; `INVALID_NAME` → `InvalidName(UnknownRuleViolation)`; unknown/null error code → `RemoteDataSource` — follow `RemoteAuthDataSourceSignupWithGoogleTest` pattern using `MockEngine`
- [x] Write `AuthRepositorySignupTest` covering: success stores session and sets `Authenticated(EMAIL)`; each `RegisterError` variant maps to the correct `SignupRepositoryError`; local storage failure maps to `LocalOperationFailed` — follow `AuthRepositorySignupWithGoogleTest` pattern using `RemoteAuthDataSourceFake` + `LocalAuthDataSourceFake`
- [x] run `./gradlew :feature:auth:testDebugUnitTest` — must pass before task 2

### Task 2: Use Case Layer

**Files:**
- Create: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/domain/usecase/SignupWithCredentialsUseCase.kt`
- Create: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/domain/usecase/SignupWithCredentialsUseCaseError.kt`
- Create: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/domain/usecase/SignupWithCredentialsUseCaseImpl.kt`
- Create: `feature/auth/src/test/kotlin/timur/gilfanov/messenger/auth/domain/usecase/SignupWithCredentialsUseCaseTest.kt`

- [x] Create `SignupWithCredentialsUseCase` as `fun interface` with `suspend operator fun invoke(credentials: Credentials, name: String): ResultWithError<Unit, SignupWithCredentialsUseCaseError>`
- [x] Create `SignupWithCredentialsUseCaseError`: `ValidationFailed(error: CredentialsValidationError)`, `InvalidName(reason: ProfileNameValidationError)`, `InvalidEmail(reason: EmailValidationError)`, `InvalidPassword(reason: PasswordValidationError)`, `LocalOperationFailed(error: LocalStorageError)`, `RemoteOperationFailed(error: UnauthRemoteError)` — KDoc with Validation / Logical / Data Source Error sections; include internal `SignupRepositoryError.toUseCaseError()` extension
- [x] Create `SignupWithCredentialsUseCaseImpl(validator: CredentialsValidator, nameValidator: ProfileNameValidator, repository: AuthRepository, logger: Logger)`: validate credentials first (→ `ValidationFailed`), then name (→ `InvalidName`), then call `repository.signup(credentials, name)`, map errors, log failures — follow `LoginWithCredentialsUseCaseImpl` + `SignupWithGoogleUseCaseImpl` patterns
- [x] Write `SignupWithCredentialsUseCaseTest`: credentials validation failure returns `ValidationFailed`; name validation failure returns `InvalidName`; success; each `SignupRepositoryError` variant maps correctly — follow `LoginWithCredentialsUseCaseTest` + `SignupWithGoogleUseCaseTest` patterns
- [x] run `./gradlew :feature:auth:testDebugUnitTest` — must pass before task 3

### Task 3: ViewModel, UI, Wiring, and Feature Test

**Files:**
- Modify: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/ui/screen/signup/SignupUiState.kt`
- Modify: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/ui/screen/signup/SignupViewModel.kt`
- Modify: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/ui/screen/signup/SignupScreen.kt`
- Modify: `feature/auth/src/testFixtures/kotlin/timur/gilfanov/messenger/auth/ui/SignupViewModelTestFixtures.kt`
- Modify: `feature/auth/src/test/kotlin/timur/gilfanov/messenger/auth/ui/SignupViewModelInitialStateTest.kt`
- Modify: `feature/auth/src/test/kotlin/timur/gilfanov/messenger/auth/ui/SignupViewModelSubmitTest.kt`
- Modify: `feature/auth/src/test/kotlin/timur/gilfanov/messenger/auth/ui/SignupViewModelProcessDeathTest.kt`
- Create: `feature/auth/src/test/kotlin/timur/gilfanov/messenger/auth/ui/SignupViewModelCredentialsSignupTest.kt`
- Modify: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/di/AuthViewModelModule.kt`
- Modify: `feature/auth/src/androidTest/java/timur/gilfanov/messenger/feature/auth/SignupFeatureTest.kt`

- [ ] Extend `SignupUiState`: add `email: String = ""`, `password: String = ""`, `emailError: CredentialsValidationError? = null`, `passwordError: CredentialsValidationError? = null`; extend `SignupGeneralError` with `data class InvalidEmail(val reason: EmailValidationError)` and `data class InvalidPassword(val reason: PasswordValidationError)`
- [ ] Extend `SignupViewModel`: add `KEY_EMAIL`/`KEY_PASSWORD` saved-state keys; `updateEmail` (saves to `SavedStateHandle`, clears `emailError`); `updatePassword` (saves to `SavedStateHandle`, clears `passwordError`); `submitSignupWithCredentials()` following same guard/load/dispatch pattern as `submitSignupWithGoogle`; replace `lastIdToken: String?` with `private sealed interface LastSignupAction { Credentials; Google(val idToken: String) }` so `retryLastAction` handles both paths; dispatch `ValidationFailed` to `emailError`/`passwordError` per field type (same split logic as `LoginViewModel.handleValidationError`); dispatch server `InvalidEmail`/`InvalidPassword` to `generalError`; inject `SignupWithCredentialsUseCase`
- [ ] Update `SignupViewModelTestFixtures.createViewModel` to accept `credentialsValidatorError: CredentialsValidationError? = null`, `signupWithCredentialsResult: ResultWithError<AuthSession, SignupRepositoryError>? = null` and wire `SignupWithCredentialsUseCaseImpl` with `CredentialsValidatorStub` into the ViewModel
- [ ] Update `SignupViewModelInitialStateTest`: assert `email = ""`, `password = ""`, `emailError = null`, `passwordError = null` in initial state
- [ ] Update `SignupViewModelSubmitTest`: add `updateEmail clears emailError`; add `updatePassword clears passwordError`
- [ ] Update `SignupViewModelProcessDeathTest`: add email and password fields restored from `SavedStateHandle`
- [ ] Write `SignupViewModelCredentialsSignupTest` covering: success emits `NavigateToChatList`; `ValidationFailed` email error sets `emailError`; `ValidationFailed` password error sets `passwordError`; `InvalidName` client-side sets `nameError`; `InvalidName` server sets `nameError`; `InvalidEmail` server sets `generalError`; `InvalidPassword` server sets `generalError`; `RemoteOperationFailed` variants emit `ShowSnackbar`; `LocalOperationFailed` blocking variants set `blockingError`; `LocalOperationFailed` transient variants emit `ShowSnackbar`; `isLoading` true while in progress; double-submit prevention; `retryLastAction` after credentials signup; `retryLastAction` after Google signup (still works)
- [ ] Extend `SignupScreen`: add `EmailField`, `PasswordField` composables with test tags `signup_email_field`, `signup_email_error`, `signup_password_field`, `signup_password_error`; add `signup_credentials_sign_up_button` button calling `viewModel.submitSignupWithCredentials()`; add `signup_general_error` to show server `InvalidEmail`/`InvalidPassword` variants with display strings; wire `onEmailChange`/`onPasswordChange` through `SignupScreenContent` parameter list
- [ ] Add `@Provides fun provideSignupWithCredentialsUseCase(validator: CredentialsValidator, nameValidator: ProfileNameValidator, repository: AuthRepository, logger: Logger): SignupWithCredentialsUseCase = SignupWithCredentialsUseCaseImpl(...)` to `AuthViewModelModule`
- [ ] Update `SignupFeatureTest.SignupTestModule` to provide `SignupWithCredentialsUseCase`; add test cases: `signupScreen_displaysCredentialsFormElements`; `signupScreen_preservesEmailOnRotation`; `signupScreen_credentialsSignupSuccess_navigatesToChatList`; `signupScreen_credentialsSignupClientEmailError_showsEmailFieldError`; `signupScreen_credentialsSignup_buttonDisabledWhileLoading`
- [ ] Provide No-op of `SignupWithCredentialsUseCase` for `LoginFeatureTest` following `provideSignupWithGoogleUseCase` pattern
- [ ] run `./gradlew :feature:auth:testDebugUnitTest`

### Task 4: Verify Acceptance Criteria

- [ ] run `./gradlew :feature:auth:testDebugUnitTest`

### Task 5: Update Documentation

- [ ] Update `CLAUDE.md` if new patterns were introduced
- [ ] Move this plan to `docs/plans/completed/`
