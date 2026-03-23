---
# Add Signup with Google Feature

## Overview
Implement a signup with Google flow. The user sees a signup screen with a name input and a "Sign up with Google" button. Tapping the button triggers Google Sign-In (via the existing `GoogleSignInClient`), obtains an `idToken`, then calls `SignupWithGoogleUseCase(idToken, name)` to register the account. The full stack (remote data source, repository, use case, ViewModel, UI) must be created.

## Context
- Files involved:
  - Modify: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/data/source/remote/AuthApiRoutes.kt`
  - Modify: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/data/source/remote/dto/AuthDtos.kt`
  - Modify: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/data/source/remote/RemoteAuthDataSourceError.kt`
  - Modify: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/data/source/remote/RemoteAuthDataSource.kt`
  - Modify: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/data/source/remote/RemoteAuthDataSourceImpl.kt`
  - Create: `core/domain/src/main/kotlin/timur/gilfanov/messenger/domain/usecase/auth/repository/GoogleSignupRepositoryError.kt`
  - Modify: `core/domain/src/main/kotlin/timur/gilfanov/messenger/domain/usecase/auth/AuthRepository.kt`
  - Modify: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/data/repository/AuthRepositoryImpl.kt`
  - Modify: `core/domain/src/testFixtures/kotlin/timur/gilfanov/messenger/domain/usecase/auth/AuthRepositoryFake.kt`
  - Create: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/validation/ProfileNameValidator.kt`
  - Create: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/validation/ProfileNameValidatorImpl.kt`
  - Create: `feature/auth/src/testFixtures/kotlin/timur/gilfanov/messenger/auth/validation/ProfileNameValidatorStub.kt`
  - Create: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/domain/usecase/SignupWithGoogleUseCase.kt`
  - Create: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/domain/usecase/SignupWithGoogleUseCaseError.kt`
  - Create: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/domain/usecase/SignupWithGoogleUseCaseImpl.kt`
  - Create: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/ui/screen/signup/SignupUiState.kt`
  - Create: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/ui/screen/signup/SignupSideEffects.kt`
  - Create: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/ui/screen/signup/SignupViewModel.kt`
  - Create: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/ui/screen/signup/SignupScreen.kt`
  - Create: `feature/auth/src/debug/kotlin/timur/gilfanov/messenger/auth/ui/SignupScreenTestActivity.kt`
  - Modify: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/di/AuthViewModelModule.kt`
  - Modify: `app/src/main/java/timur/gilfanov/messenger/ui/activity/MainActivity.kt`
- Related patterns:
  - `LoginWithGoogleUseCase` + `LoginWithGoogleUseCaseImpl` + `GoogleLoginUseCaseError` as direct use case reference
  - `LoginScreen` + `LoginViewModel` + `LoginUiState` as direct UI/ViewModel reference
  - `LoginViewModelGoogleSignInTest` as direct reference for `SignupViewModelGoogleSignupTest`
  - `LoginFeatureTest` + `LoginScreenTestActivity` as direct reference for `SignupFeatureTest` + `SignupScreenTestActivity`
  - `CredentialsValidator` + `CredentialsValidatorStub` for `ProfileNameValidator` pattern
  - `GoogleLoginRepositoryError` for `GoogleSignupRepositoryError` shape
  - `AuthRepositoryFake.enqueueLoginWithGoogleResult()` for new `enqueueSignupWithGoogleResult()`
- Dependencies: none new

## Development Approach
- **Testing approach**: Regular (code first, then tests)
- Complete each task fully before moving to the next
- Follow Google login pattern for all layers; signup adds a `name` field on top of the idToken
- **CRITICAL: every task MUST include new/updated tests**
- **CRITICAL: all tests must pass before starting next task**

## Implementation Steps

### Task 1: Remote Data Source — Google Signup

**Files:**
- Modify: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/data/source/remote/AuthApiRoutes.kt`
- Modify: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/data/source/remote/dto/AuthDtos.kt`
- Modify: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/data/source/remote/RemoteAuthDataSourceError.kt`
- Modify: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/data/source/remote/RemoteAuthDataSource.kt`
- Modify: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/data/source/remote/RemoteAuthDataSourceImpl.kt`
- Create: `feature/auth/src/test/kotlin/timur/gilfanov/messenger/auth/data/source/remote/RemoteAuthDataSourceSignupWithGoogleTest.kt`

- [x] Add `GOOGLE_REGISTER = "auth/google/register"` to `AuthApiRoutes`
- [x] Add `GoogleSignupRequestDto(idToken: String, name: String)` to `AuthDtos.kt`
- [x] Add `SignupWithGoogleError` sealed interface to `RemoteAuthDataSourceError.kt`: `InvalidToken`, `AccountAlreadyExists`, `InvalidName(reason: ProfileNameValidationError)`, `RemoteDataSource(error: RemoteDataSourceError)`
- [x] Add `signupWithGoogle(idToken: GoogleIdToken, name: String): ResultWithError<AuthTokens, SignupWithGoogleError>` to `RemoteAuthDataSource` interface
- [x] Implement `signupWithGoogle` in `RemoteAuthDataSourceImpl` following the existing `loginWithGoogle` / `register` patterns: POST to `GOOGLE_REGISTER`, map server error codes to `SignupWithGoogleError` variants
- [x] Write `RemoteAuthDataSourceSignupWithGoogleTest` covering success, each error code, and `RemoteDataSource` failure
- [x] run `./gradlew :feature:auth:testDebugUnitTest` — must pass before task 2

### Task 2: Repository Domain Layer — Google Signup

**Files:**
- Create: `core/domain/src/main/kotlin/timur/gilfanov/messenger/domain/usecase/auth/repository/GoogleSignupRepositoryError.kt`
- Modify: `core/domain/src/main/kotlin/timur/gilfanov/messenger/domain/usecase/auth/AuthRepository.kt`
- Modify: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/data/repository/AuthRepositoryImpl.kt`
- Modify: `core/domain/src/testFixtures/kotlin/timur/gilfanov/messenger/domain/usecase/auth/AuthRepositoryFake.kt`
- Create: `feature/auth/src/test/kotlin/timur/gilfanov/messenger/auth/data/repository/AuthRepositorySignupWithGoogleTest.kt`

- [ ] Create `GoogleSignupRepositoryError`: `InvalidToken`, `AccountAlreadyExists`, `InvalidName(reason: ProfileNameValidationError)`, `LocalOperationFailed(error: LocalStorageError)`, `RemoteOperationFailed(error: UnauthRemoteError)` — KDoc following `GoogleLoginRepositoryError`
- [ ] Add `signupWithGoogle(idToken: GoogleIdToken, name: String): ResultWithError<AuthSession, GoogleSignupRepositoryError>` to `AuthRepository` interface
- [ ] Implement `signupWithGoogle` in `AuthRepositoryImpl`: call `remoteDataSource.signupWithGoogle()`, save session with `AuthProvider.GOOGLE`, update `_authState`, map errors via private `mapSignupWithGoogleError()` — same structure as `loginWithGoogle`
- [ ] Add `signupWithGoogleQueue`, `defaultSignupWithGoogleResult`, `enqueueSignupWithGoogleResult()`, and `signupWithGoogle()` override to `AuthRepositoryFake` following the existing queue pattern
- [ ] Write `AuthRepositorySignupWithGoogleTest` covering: success (state updated, session saved), each `SignupWithGoogleError` variant mapped, local storage failure
- [ ] run `./gradlew :feature:auth:testDebugUnitTest` — must pass before task 3

### Task 3: Use Case Layer

**Files:**
- Create: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/validation/ProfileNameValidator.kt`
- Create: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/validation/ProfileNameValidatorImpl.kt`
- Create: `feature/auth/src/testFixtures/kotlin/timur/gilfanov/messenger/auth/validation/ProfileNameValidatorStub.kt`
- Create: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/domain/usecase/SignupWithGoogleUseCase.kt`
- Create: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/domain/usecase/SignupWithGoogleUseCaseError.kt`
- Create: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/domain/usecase/SignupWithGoogleUseCaseImpl.kt`
- Create: `feature/auth/src/test/kotlin/timur/gilfanov/messenger/auth/domain/usecase/SignupWithGoogleUseCaseTest.kt`

- [ ] Create `ProfileNameValidator` as a `fun interface` with `validate(name: String): ResultWithError<Unit, ProfileNameValidationError>` — KDoc noting it is a contract interface
- [ ] Create `ProfileNameValidatorImpl` with client-side checks: blank/empty → `LengthOutOfBounds`, length min/max bounds matching server expectations
- [ ] Create `ProfileNameValidatorStub` in testFixtures matching `CredentialsValidatorStub` pattern
- [ ] Create `SignupWithGoogleUseCaseError`: `InvalidToken`, `AccountAlreadyExists`, `InvalidName(reason: ProfileNameValidationError)`, `LocalOperationFailed(error: LocalStorageError)`, `RemoteOperationFailed(error: UnauthRemoteError)` — KDoc with Logical Errors / Data Source Errors sections; include internal `GoogleSignupRepositoryError.toUseCaseError()` extension
- [ ] Create `SignupWithGoogleUseCase` as a `fun interface` taking `(idToken: GoogleIdToken, name: String): ResultWithError<Unit, SignupWithGoogleUseCaseError>`
- [ ] Create `SignupWithGoogleUseCaseImpl`: validate name via `ProfileNameValidator`, call `repository.signupWithGoogle(idToken, name)`, map errors, log failures — following `LoginWithGoogleUseCaseImpl`
- [ ] Write `SignupWithGoogleUseCaseTest` covering: name validation failure, success, each `GoogleSignupRepositoryError` variant mapped correctly
- [ ] run `./gradlew :feature:auth:testDebugUnitTest` — must pass before task 4

### Task 4: ViewModel, UI, Wiring, and Feature Test

**Files:**
- Create: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/ui/screen/signup/SignupUiState.kt`
- Create: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/ui/screen/signup/SignupSideEffects.kt`
- Create: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/ui/screen/signup/SignupViewModel.kt`
- Create: `feature/auth/src/testFixtures/kotlin/timur/gilfanov/messenger/auth/ui/SignupViewModelTestFixtures.kt`
- Create: `feature/auth/src/test/kotlin/timur/gilfanov/messenger/auth/ui/SignupViewModelInitialStateTest.kt`
- Create: `feature/auth/src/test/kotlin/timur/gilfanov/messenger/auth/ui/SignupViewModelSubmitTest.kt`
- Create: `feature/auth/src/test/kotlin/timur/gilfanov/messenger/auth/ui/SignupViewModelGoogleSignupTest.kt`
- Create: `feature/auth/src/test/kotlin/timur/gilfanov/messenger/auth/ui/SignupViewModelProcessDeathTest.kt`
- Create: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/ui/screen/signup/SignupScreen.kt`
- Create: `feature/auth/src/debug/kotlin/timur/gilfanov/messenger/auth/ui/SignupScreenTestActivity.kt`
- Create: `feature/auth/src/androidTest/java/timur/gilfanov/messenger/feature/auth/SignupFeatureTest.kt`
- Modify: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/di/AuthViewModelModule.kt`
- Modify: `app/src/main/java/timur/gilfanov/messenger/ui/activity/MainActivity.kt`

- [ ] Create `SignupUiState`: fields `name: String`, `isLoading: Boolean`, `nameError: ProfileNameValidationError?`, `generalError: SignupGeneralError?`, `blockingError: SignupBlockingError?`; sealed `SignupGeneralError` (InvalidToken, AccountAlreadyExists, InvalidName); sealed `SignupBlockingError` (StorageFull, StorageCorrupted, StorageReadOnly, StorageAccessDenied); sealed `SignupSnackbarMessage` — following `LoginUiState` pattern
- [ ] Create `SignupSideEffects`: `NavigateToChatList`, `OpenAppSettings`, `OpenStorageSettings`, `ShowSnackbar(message: SignupSnackbarMessage)`
- [ ] Create `SignupViewModel` backed by `SavedStateHandle` for the `name` field; `updateName` clears `nameError`; `submitSignupWithGoogle(idToken: GoogleIdToken)` guards against double-submission, calls `SignupWithGoogleUseCase(idToken, name)`, dispatches all `SignupWithGoogleUseCaseError` variants to state or side effects; `retryLastAction`, `onOpenAppSettingsClick`, `onOpenStorageSettingsClick` following `LoginViewModel` pattern
- [ ] Create `SignupViewModelTestFixtures` in testFixtures (uses `SignupWithGoogleUseCaseImpl`, `ProfileNameValidatorStub`, `AuthRepositoryFake`)
- [ ] Write `SignupViewModelInitialStateTest`: default state has empty name and no errors
- [ ] Write `SignupViewModelSubmitTest`: local name validation failure sets `nameError`; `updateName` clears `nameError`
- [ ] Write `SignupViewModelGoogleSignupTest` following `LoginViewModelGoogleSignInTest` pattern: success emits `NavigateToChatList`; `InvalidToken` sets `generalError`; `AccountAlreadyExists` sets `generalError`; `InvalidName` (server) sets `nameError`; `RemoteOperationFailed` variants emit `ShowSnackbar`; `LocalOperationFailed` blocking variants set `blockingError`; `LocalOperationFailed` transient variants emit `ShowSnackbar`; `isLoading` true while in progress; double-submit prevention; `onOpenAppSettingsClick`; `onOpenStorageSettingsClick`; `retryLastAction`
- [ ] Write `SignupViewModelProcessDeathTest`: name field restored from `SavedStateHandle`
- [ ] Create `SignupScreen`: name text field with inline error, "Sign up with Google" button (triggers `googleSignInClient` sign-in, on success calls `viewModel.submitSignupWithGoogle(idToken)`), loading indicator, snackbar for transient errors, blocking error dialog for storage errors; accepts `onNavigateToChatList`, `onNavigateBack`, `googleSignInClient`; all interactive elements have test tags
- [ ] Create `SignupScreenTestActivity` in `feature/auth/src/debug/kotlin/...` following `LoginScreenTestActivity` pattern: `@AndroidEntryPoint`, injects `GoogleSignInClient`, sets `SignupScreen` as content with empty navigation callbacks
- [ ] Write `SignupFeatureTest` following `LoginFeatureTest` pattern: `@HiltAndroidTest @UninstallModules(AuthModule::class, AuthViewModelModule::class, AuthDataModule::class)`; `SignupTestModule` provides `AuthRepositoryFake`, `ProfileNameValidatorImpl`, `SignupWithGoogleUseCaseImpl`, `NoOpLogger`, `GoogleSignInClientStub`; tests cover: displays form elements, handles rotation, preserves name on rotation, Google signup cancelled shows no error, Google signup invalid token shows error, button disabled while loading, storage full shows blocking dialog
- [ ] Add `@Provides` for `ProfileNameValidator → ProfileNameValidatorImpl` in the auth DI module; add `@Provides` for `SignupWithGoogleUseCase` (inject `ProfileNameValidator`, `AuthRepository`, `Logger`) in `AuthViewModelModule`
- [ ] Replace `entry<Signup>` placeholder in `MainActivity` with `SignupScreen(onNavigateToChatList = { backStack.clear(); backStack.add(Main) }, onNavigateBack = { backStack.removeLastOrNull() }, googleSignInClient = googleSignInClient)`
- [ ] run `./gradlew :feature:auth:testDebugUnitTest :feature:auth:connectedDebugAndroidTest` — must pass before task 5

### Task 5: Verify Acceptance Criteria

- [ ] run `./gradlew preCommit`
- [ ] run `./gradlew :feature:auth:testDebugUnitTest`
- [ ] Confirm all new tests pass and coverage is complete for all added code

### Task 6: Update Documentation

- [ ] Update `CLAUDE.md` if new patterns were introduced (e.g. `ProfileNameValidator` location)
- [ ] Move this plan to `docs/plans/completed/`
