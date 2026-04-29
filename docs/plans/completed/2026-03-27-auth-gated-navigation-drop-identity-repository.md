---
# Feature Integration — Auth-Gated Navigation, Drop IdentityRepository (#274)

## Overview
`AuthRepository` (in `:core:domain`) already carries everything `IdentityRepository` provides. Delete `IdentityRepository`, `Identity`, `DeviceId`, and `DefaultIdentityRepository`. Wire `AuthRepository` directly into the settings use cases and `ObserveProfileUseCaseImpl`. Add `isCurrentUser: Boolean` to `Participant` so `ChatViewModel` can identify the current user without a userId field. Remove the hardcoded `currentUserId` from `ChatViewModel`. Wire auth-gated startup navigation. Add an end-to-end ApplicationTest covering login → chat → logout → login.

## Context
- Files involved:
  - `core/domain/.../entity/chat/Participant.kt` — add isCurrentUser
  - `core/domain/.../usecase/profile/IdentityRepository.kt` — delete
  - `core/domain/.../entity/profile/Identity.kt` — delete
  - `core/domain/.../entity/profile/DeviceId.kt` — delete
  - `core/domain/testFixtures/.../usecase/profile/IdentityRepositoryStub.kt` — delete
  - `core/domain/.../usecase/profile/ObserveProfileUseCaseImpl.kt` — inject AuthRepository
  - `core/domain/.../usecase/settings/repository/SettingsRepository.kt` — Identity → AuthSession
  - `core/domain/.../usecase/settings/ObserveSettingsUseCaseImpl.kt` — inject AuthRepository
  - `core/domain/.../usecase/settings/ObserveUiLanguageUseCase.kt` — inject AuthRepository
  - `core/domain/.../usecase/settings/ChangeUiLanguageUseCase.kt` — inject AuthRepository
  - `core/domain/.../usecase/settings/SyncSettingUseCase.kt` — inject AuthRepository, remove userId param
  - `core/domain/.../usecase/settings/SyncAllPendingSettingsUseCase.kt` — inject AuthRepository
  - `app/.../data/repository/DefaultIdentityRepository.kt` — delete
  - `app/.../data/repository/SettingsRepositoryImpl.kt` — Identity → AuthSession
  - `app/.../di/RepositoryModule.kt` — remove DefaultIdentityRepository binding
  - `app/.../di/SettingsObservationModule.kt`, `SettingsSyncUseCaseModule.kt`, `LanguageModule.kt`, `LocaleApplicationModule.kt`, `ProfileModule.kt` — remove IdentityRepository injection
  - `app/.../ui/screen/chat/ChatViewModel.kt` — inject AuthRepository, use isCurrentUser
  - `app/.../ui/screen/chat/ChatScreen.kt` — remove currentUserId param
  - `app/.../ui/activity/MainActivityViewModel.kt` — add initialDestination
  - `app/.../ui/activity/MainActivity.kt` — auth-gated nav, remove hardcoded UUID
  - New: `app/src/androidTest/.../application/AuthApplicationTest.kt`
  - New (if needed): `app/src/androidTest/.../test/AuthRepositoryStub.kt`
- Related patterns: `authRepository.authState.flatMapLatest { }` replaces the `identityRepository.identity.flatMapLatest { }` pattern throughout; existing `NavigationApplicationTest` as structural reference for the new ApplicationTest
- Dependencies: none new

## Development Approach
- **Testing approach**: Regular (code first, then tests)
- Complete each task fully before moving to the next
- Fakes over mocks; `fun interface` stubs for use cases injected into ViewModels
- **CRITICAL: every task MUST include new/updated tests**
- **CRITICAL: all tests must pass before starting next task**

## Implementation Steps

### Task 1: Add isCurrentUser to Participant entity

**Files:**
- Modify: `core/domain/src/main/kotlin/.../entity/chat/Participant.kt`
- Modify: any test fixtures/fakes that construct Participant explicitly

- [x] Add `val isCurrentUser: Boolean = false` to `Participant` data class (default false preserves existing test constructions)
- [x] Search for explicit `Participant(...)` constructions in test fixtures and update where the current-user participant should be marked true
- [x] Write or update unit tests verifying `isCurrentUser` is preserved through domain copy operations

### Task 2: Delete IdentityRepository; inject AuthRepository directly into settings and profile use cases

Replace every use of `identityRepository.identity` with `authRepository.authState`; replace `getIdentity(userId)` call in `SyncSettingUseCase` with `authRepository.authState.first()`; delete all identity abstractions.

**Files:**
- Delete: `core/domain/src/main/kotlin/.../usecase/profile/IdentityRepository.kt`
- Delete: `core/domain/src/main/kotlin/.../entity/profile/Identity.kt`
- Delete: `core/domain/src/main/kotlin/.../entity/profile/DeviceId.kt`
- Delete: `core/domain/src/testFixtures/.../usecase/profile/IdentityRepositoryStub.kt`
- Delete: `app/src/main/java/.../data/repository/DefaultIdentityRepository.kt`
- Modify: `core/domain/src/main/kotlin/.../usecase/settings/repository/SettingsRepository.kt`
- Modify: `core/domain/src/main/kotlin/.../usecase/settings/ObserveSettingsUseCaseImpl.kt`
- Modify: `core/domain/src/main/kotlin/.../usecase/settings/ObserveUiLanguageUseCase.kt`
- Modify: `core/domain/src/main/kotlin/.../usecase/settings/ChangeUiLanguageUseCase.kt`
- Modify: `core/domain/src/main/kotlin/.../usecase/settings/SyncSettingUseCase.kt`
- Modify: `core/domain/src/main/kotlin/.../usecase/settings/SyncAllPendingSettingsUseCase.kt`
- Modify: `core/domain/src/main/kotlin/.../usecase/profile/ObserveProfileUseCaseImpl.kt`
- Modify: `app/src/main/java/.../data/repository/SettingsRepositoryImpl.kt`
- Modify: `app/src/main/java/.../di/RepositoryModule.kt`
- Modify: relevant DI modules in app (SettingsObservationModule, SettingsSyncUseCaseModule, LanguageModule, LocaleApplicationModule, ProfileModule)

- [x] In `SettingsRepository`: replace all `identity: Identity` parameters with `session: AuthSession`
- [x] Update `ObserveSettingsUseCaseImpl`, `ObserveUiLanguageUseCase`, `ChangeUiLanguageUseCase`, `SyncAllPendingSettingsUseCase`: replace `identityRepository: IdentityRepository` with `authRepository: AuthRepository`; replace `identityRepository.identity.flatMapLatest { identityResult -> identityResult.fold(...) }` with `authRepository.authState.flatMapLatest { state -> when(state) { is Authenticated -> ...; Unauthenticated -> flowOf(Failure(Unauthorized)) } }`
- [x] Update `SyncSettingUseCase`: replace `identityRepository: IdentityRepository` with `authRepository: AuthRepository`; remove `userId: UserId` parameter; replace `identityRepository.getIdentity(userId)` with `authRepository.authState.first().let { if it is Authenticated use session else return Failure }`; update all callers of `SyncSettingUseCase` to remove the `userId` argument
- [x] Update `ObserveProfileUseCaseImpl`: replace `IdentityRepository` with `AuthRepository`; same `flatMapLatest` on `authState` pattern
- [x] Update `SettingsRepositoryImpl`: replace all `identity: Identity` params with `session: AuthSession`; replace uses of `identity.userId` / `identity.deviceId` with corresponding `session` fields
- [x] Remove `DefaultIdentityRepository` `@Binds` from `RepositoryModule`; delete `DefaultIdentityRepository.kt`; remove `IdentityRepository` from all DI injection sites
- [x] Delete `Identity.kt`, `DeviceId.kt`, `IdentityRepository.kt`, `IdentityRepositoryStub.kt`
- [x] Update all settings use case tests to use `AuthRepositoryFake` instead of `IdentityRepositoryStub`; update `SettingsRepositoryImpl` tests to use `AuthSession`

### Task 2a: Introduce UserScopeKey; scope settings storage by refreshToken

After Task 2 removed `userId` from `AuthSession`, settings storage needed a stable per-user key that does not require a user ID field. `refreshToken` was chosen as the scoping key because it is already available in `AuthSession.tokens` and is stable for a given session.

**What was fixed / why it was needed:**
- `AuthSession` no longer carries `userId`; the old `SettingEntity.userId` column had no valid source
- Scoping settings by `refreshToken` aligns with the principle that one refresh token = one user session

**Files:**
- Create: `core/domain/src/main/kotlin/.../domain/UserScopeKey.kt`
- Modify: `app/.../data/source/local/database/entity/SettingEntity.kt` — rename column `userId` → `userKey`; bump Room schema version 3 → 4 with destructive migration
- Modify: `app/.../data/source/local/database/dao/SettingsDao.kt` — rename parameter `userId` → `userKey`
- Modify: `app/.../data/source/local/LocalSettingsDataSource.kt` — replace `userKey: String` with `userKey: UserScopeKey`
- Modify: `app/.../data/source/local/LocalSettingsDataSourceImpl.kt` — same
- Modify: `app/.../data/repository/SettingsSyncScheduler.kt` — replace `userId: UserId` with `userKey: UserScopeKey`
- Modify: `app/.../data/repository/SettingsRepositoryImpl.kt` — add `private val AuthSession.userKey get() = UserScopeKey(tokens.refreshToken)` extension; update all internal calls to local data source
- Modify: `app/src/debug/.../data/source/local/LocalSettingsDataSourceFake.kt` — use `UserScopeKey`
- Modify: `app/src/test/.../data/source/local/database/dao/SettingsDaoFake.kt` — rename parameter

- [x] Create `UserScopeKey(@JvmInline value class)` in `core/domain`
- [x] Migrate Room schema: rename `SettingEntity.userId` → `userKey`; bump version to 4; add destructive migration fallback
- [x] Update `SettingsDao` signatures to use `userKey: String`
- [x] Update `LocalSettingsDataSource` interface and impl to use `userKey: UserScopeKey`
- [x] Update `SettingsSyncScheduler` to use `userKey: UserScopeKey` (was incorrectly `userId: UserId`)
- [x] Add `private val AuthSession.userKey` extension in `SettingsRepositoryImpl`; update all calls to local data source
- [x] Update `LocalSettingsDataSourceFake`, `SettingsDaoFake`, and related test doubles

### Task 2b: Surface UserScopeKey at SettingsRepository domain boundary

`SettingsRepository` is a domain interface; its operations are scoped per user. After Task 2a the impl derived `UserScopeKey` internally from `AuthSession`, but the interface still exposed `AuthSession` — leaking an auth concept into what should be a pure user-scope parameter. This task moves the derivation to the use-case layer and expresses the boundary cleanly.

**What was fixed / why it was needed:**
- The domain interface `SettingsRepository` should not know about `AuthSession`; it only needs a stable user-scope key
- Use cases already held `AuthState.Authenticated` and were the right place to derive the key
- Centralising the derivation rule (`tokens.refreshToken → UserScopeKey`) in one extension function removes duplication

**Files:**
- Modify: `core/domain/src/main/kotlin/.../domain/UserScopeKey.kt` — add `fun AuthSession.toUserScopeKey()` extension
- Modify: `core/domain/src/main/kotlin/.../usecase/settings/repository/SettingsRepository.kt` — `session: AuthSession` → `userKey: UserScopeKey` in all 4 methods
- Modify: 5 use cases (`ObserveSettingsUseCaseImpl`, `ObserveUiLanguageUseCase`, `ChangeUiLanguageUseCase`, `SyncSettingUseCase`, `SyncAllPendingSettingsUseCase`) — call `state.session.toUserScopeKey()` before repository calls
- Modify: `app/.../data/repository/SettingsRepositoryImpl.kt` — accept `UserScopeKey` directly; remove `private val AuthSession.userKey` extension; remove `AuthSession` import
- Modify: `core/domain/src/testFixtures/.../SettingsRepositoryFake.kt`, `SettingsRepositoryStub.kt` — swap signatures
- Modify: `app/src/androidTest/.../test/AndroidTestSettingsRepository.kt`, `SettingsRepositoryStub.kt` — swap signatures
- Modify: `app/src/androidTest/.../test/SettingsSyncSchedulerStub.kt` — fix pre-existing bug (`userId: UserId` → `userKey: UserScopeKey`)
- Modify: `app/src/androidTest/.../test/AndroidTestSettingsHelper.kt` — remove leftover `userId = testUserId` from `AuthSession` construction
- Modify: `app/src/test/.../data/repository/SettingsRepositoryImplTest.kt` — use `testUserKey` instead of `session` in all repository calls; remove unused `session`/`AuthSession` declarations
- Modify: `app/src/test/.../data/repository/SettingsRepositoryIntegrationTest.kt` — add `testUserKey = testSession.toUserScopeKey()`; replace `testSession` in repository calls

- [x] Add `fun AuthSession.toUserScopeKey(): UserScopeKey` extension to `UserScopeKey.kt`
- [x] Update `SettingsRepository` interface — all 4 methods take `userKey: UserScopeKey`; remove `AuthSession` import; update KDoc `@param` lines
- [x] Update 5 use cases to derive key with `state.session.toUserScopeKey()`
- [x] Update `SettingsRepositoryImpl` — remove `AuthSession` extension and import; accept `UserScopeKey` in all public and private methods
- [x] Update all test doubles in `core/domain` testFixtures and `app/src/androidTest`
- [x] Fix `SettingsSyncSchedulerStub` bug (wrong parameter type)
- [x] Fix `AndroidTestSettingsHelper` leftover `userId` construction
- [x] Update test call sites in `SettingsRepositoryImplTest` and `SettingsRepositoryIntegrationTest`

### Task 3: Refactor ChatViewModel and ChatScreen — inject AuthRepository, use isCurrentUser

**Files:**
- Modify: `app/src/main/java/.../ui/screen/chat/ChatViewModel.kt`
- Modify: `app/src/main/java/.../ui/screen/chat/ChatScreen.kt`
- Modify: `app/src/main/java/.../ui/activity/MainActivity.kt`

- [x] Remove `@Assisted("currentUserId") currentUserIdUuid: UUID` from `ChatViewModel`; inject `AuthRepository`; remove `KEY_CURRENT_USER_ID` and the `currentUserId: ParticipantId` field
- [x] In `ChatViewModel.init`: collect `authRepository.authState.first()` — `Authenticated` → launch `observeChatUpdates()`; `Unauthenticated` → send `ChatSideEffect.Unauthorized`
- [x] Replace `participants.first { it.id == currentUserId }` with `participants.first { it.isCurrentUser }` and `participants.first { it.id != currentUserId }` with `participants.first { !it.isCurrentUser }` throughout `ChatViewModel`
- [x] Update `ChatViewModelFactory`: remove `currentUserId` param
- [x] In `ChatScreen`: remove `currentUserId: ParticipantId` param; simplify hiltViewModel key to `chatId.id.toString()`; add `onAuthFailure: () -> Unit`; handle `ChatSideEffect.Unauthorized` → call `onAuthFailure`
- [x] In `MainActivity`/`MessengerApp`: remove hardcoded `currentUserId`; update `ChatScreen` call to pass `onAuthFailure`
- [x] Write/update unit tests for `ChatViewModel`: authenticated → chat loads; unauthenticated → Unauthorized side effect

### Task 4: Auth-gated startup navigation

**Files:**
- Modify: `app/src/main/java/.../ui/activity/MainActivityViewModel.kt`
- Modify: `app/src/main/java/.../ui/activity/MainActivity.kt`

- [x] Inject `AuthRepository` into `MainActivityViewModel`; add `private val _initialDestination = MutableStateFlow<Any?>(null)`; expose as `val initialDestination`; in `init`, launch a coroutine that collects `authRepository.authState.first()` and sets destination (`Authenticated` → Main, `Unauthenticated` → Login); keep existing locale observation
- [x] In `MainActivity`/`MessengerApp`: collect `initialDestination` via `collectAsStateWithLifecycle`; show empty `Box` while null; once resolved, use `LaunchedEffect(destination)` to build the correct initial back stack
- [x] Write unit tests for `MainActivityViewModel`: Authenticated → Main; Unauthenticated → Login; initial state is null until authState emits

### Task 5: End-to-end AuthApplicationTest — login → chat → logout → login

Covers the full auth lifecycle: app boots unauthenticated → user logs in → navigates to a chat → logs out → login screen is shown again.

**Files:**
- Create: `app/src/androidTest/java/timur/gilfanov/messenger/application/AuthApplicationTest.kt`
- Create (if not already shared): `app/src/androidTest/java/timur/gilfanov/messenger/test/AuthRepositoryStub.kt`

- [x] Create `AuthRepositoryStub` in androidTest helpers: used `AuthRepositoryFake()` (starts Unauthenticated; login defaults to Success/Authenticated; logout defaults to Success/Unauthenticated) — no separate stub needed
- [x] Create `AuthApplicationTest` using `@HiltAndroidTest`, `@UninstallModules(RepositoryModule::class, AuthDataModule::class)`, `createAndroidComposeRule<MainActivity>()` following the pattern from `NavigationApplicationTest`; inject `AuthRepositoryFake` as `AuthRepository` (Singleton) in the inner Hilt module alongside `ChatRepository`, `MessageRepository`, `SettingsRepository`, and `LocaleRepository` stubs
- [x] `authFlow_loginThenNavigateToChatThenLogoutThenLoginScreenShown`: start with stub in `Unauthenticated` state → assert `login_screen` displayed; fill `login_email_field` and `login_password_field`; click `login_sign_in_button` → stub transitions to `Authenticated` → assert `chat_list` displayed; tap a chat item → wait for `message_input` to confirm Chat screen rendered → back-navigate to chat list → navigate to Settings via `bottom_nav_settings` → tap `settings_logout_item` → stub transitions to `Unauthenticated` → assert `login_screen` displayed
- [x] run `./gradlew connectedMockDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class="timur.gilfanov.messenger.application.AuthApplicationTest"` to verify — PASSED

### Task 6: Verify acceptance criteria

- [x] Confirm no `IdentityRepository`, `DefaultIdentityRepository`, `Identity`, or `DeviceId` references in `app/src/main` and `core/domain/src/main` — only error-case names (`IdentityNotAvailable`) and KDoc comments remain; no entity/interface classes
- [x] Confirm no hardcoded `550e8400` UUID in `app/src/main` — UUID appears only in `@Preview` composables (fake data); removed from functional code (`MainActivity`)
- [x] move this plan to `docs/plans/completed/`
