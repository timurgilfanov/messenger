---
# Fix Stale User-Scoped State on Logout

## Overview
On logout, cancel all WorkManager jobs tagged with the outgoing `UserScopeKey`
and delete all settings rows keyed to that `UserScopeKey` from the local
database. The fix is threaded through five layers: DAO ->
LocalSettingsDataSource -> SettingsSyncScheduler -> SettingsRepository ->
LogoutUseCaseImpl.

## Context
- Files involved: `SettingsDao`, `SettingsDaoFake`,
  `SettingsDataSourceError`, `LocalSettingsDataSource`,
  `LocalSettingsDataSourceImpl`, `LocalSettingsDataSourceFake`,
  `SettingsSyncScheduler`, `SettingsSyncSchedulerImpl`,
  `SettingsSyncSchedulerStub`, `SettingsRepository`, `SettingsRepositoryImpl`,
  `SettingsRepositoryFake`, `SettingsRepositoryStub`, `LogoutUseCaseImpl`,
  `AuthViewModelModule`, and related tests
- Related patterns: error mapping pattern (SQLiteException -> sealed error ->
  LocalStorageError -> repository error), best-effort cleanup (log error,
  continue), WorkManager tag-based cancellation, `retryOrFail` helper for
  transient SQLite errors
- Dependencies: `core:domain` (SettingsRepository interface, error types),
  `app` (implementations)

## Development Approach
- **Testing approach**: Regular (implement each layer, then write tests)
- Complete each task fully before moving to the next
- **CRITICAL: every task MUST include new/updated tests**
- **CRITICAL: all tests must pass before starting next task**

## Implementation Steps

### Task 1: DAO — add deleteAllByUser

**Files:**
- Modify: `app/src/main/java/timur/gilfanov/messenger/data/source/local/database/dao/SettingsDao.kt`
- Modify: `app/src/test/java/timur/gilfanov/messenger/data/source/local/database/dao/SettingsDaoFake.kt`
- Modify: `app/src/test/java/timur/gilfanov/messenger/data/source/local/database/dao/SettingsDaoTest.kt`

- [x] Add `@Query("DELETE FROM settings WHERE userKey = :userKey") suspend fun deleteAllByUser(userKey: String)` to `SettingsDao`
- [x] Add `deleteAllByUser(userKey)` implementation to `SettingsDaoFake` (delegates to realDao, subject to `checkDatabaseHealth()`)
- [x] Add test in `SettingsDaoTest`: insert rows for two user keys, call `deleteAllByUser` for one, verify only the targeted rows are gone
- [x] Run `./gradlew app:testDebugUnitTest --tests "*.SettingsDaoTest"` — must pass

### Task 2: LocalSettingsDataSource — add deleteAllForUser

**Files:**
- Modify: `app/src/main/java/timur/gilfanov/messenger/data/source/local/SettingsDataSourceError.kt`
- Modify: `app/src/main/java/timur/gilfanov/messenger/data/source/local/LocalSettingsDataSource.kt`
- Modify: `app/src/main/java/timur/gilfanov/messenger/data/source/local/LocalSettingsDataSourceImpl.kt`
- Modify: `app/src/debug/java/timur/gilfanov/messenger/data/source/local/LocalSettingsDataSourceFake.kt`
- Modify: `app/src/test/java/timur/gilfanov/messenger/data/source/local/LocalSettingsDataSourceImplTest.kt`

- [x] Add `DeleteAllForUserError` sealed interface to `SettingsDataSourceError.kt` (cases: `ConcurrentModificationError`, `DiskIOError`, `DatabaseCorrupted`, `AccessDenied`, `ReadOnlyDatabase`, `UnknownError(val cause: Throwable)`)
- [x] Add `suspend fun deleteAllForUser(userKey: UserScopeKey): ResultWithError<Unit, DeleteAllForUserError>` to `LocalSettingsDataSource` interface (with KDoc)
- [x] Implement in `LocalSettingsDataSourceImpl` using the same retry loop pattern as `getSetting`/`upsert`: retry `SQLiteDatabaseLockedException` and `SQLiteDiskIOException` via existing `retryOrFail` helper (up to `MAX_RETRIES` with exponential backoff); fail immediately on `SQLiteFullException`, `SQLiteDatabaseCorruptException`, `SQLiteAccessPermException`, `SQLiteReadOnlyDatabaseException`, and unknown errors; update the class-level KDoc to document the new method's retry behaviour
- [x] Add `deleteAllForUser` to `LocalSettingsDataSourceFake`: remove entries matching `userKey` from `settings` map, support `deleteAllForUserError` configuration field
- [x] Add tests in `LocalSettingsDataSourceImplTest`: success path removes rows for that user only; `SQLiteDatabaseLockedException` retries and succeeds on second attempt; `SQLiteDatabaseLockedException` after max retries maps to `ConcurrentModificationError`; `SQLiteDiskIOException` maps similarly; permanent errors (`DatabaseCorrupted`, `AccessDenied`, `ReadOnlyDatabase`) map to correct cases with no retry
- [x] Run `./gradlew app:testDebugUnitTest --tests "*.LocalSettingsDataSourceImpl*"` — must pass

### Task 3: SettingsSyncScheduler — add cancelUserScopedJobs + tag work requests

**Files:**
- Modify: `app/src/main/java/timur/gilfanov/messenger/data/repository/SettingsSyncScheduler.kt`
- Modify: `app/src/main/java/timur/gilfanov/messenger/data/repository/SettingsSyncSchedulerImpl.kt`
- Modify: `app/src/androidTest/java/timur/gilfanov/messenger/test/SettingsSyncSchedulerStub.kt`

- [x] Add `fun cancelUserScopedJobs(userKey: UserScopeKey)` to `SettingsSyncScheduler` interface (with KDoc)
- [x] In `SettingsSyncSchedulerImpl.scheduleSettingSync`: add `.addTag(userKey.key)` to `OneTimeWorkRequestBuilder` so each job is discoverable by user key
- [x] Implement `cancelUserScopedJobs`: `workManager.cancelAllWorkByTag(userKey.key)`
- [x] Add `cancelUserScopedJobs(userKey: UserScopeKey) = Unit` to `SettingsSyncSchedulerStub`
- [x] Run `./gradlew ktlintFormat detekt --auto-correct` to verify no lint issues

### Task 4: SettingsRepository — add deleteUserData

**Files:**
- Create: `core/domain/src/main/kotlin/timur/gilfanov/messenger/domain/usecase/settings/repository/DeleteUserDataRepositoryError.kt`
- Modify: `core/domain/src/main/kotlin/timur/gilfanov/messenger/domain/usecase/settings/repository/SettingsRepository.kt`
- Modify: `app/src/main/java/timur/gilfanov/messenger/data/repository/SettingsRepositoryImpl.kt`
- Modify: `core/domain/src/testFixtures/kotlin/timur/gilfanov/messenger/domain/usecase/settings/SettingsRepositoryFake.kt`
- Modify: `core/domain/src/testFixtures/kotlin/timur/gilfanov/messenger/domain/usecase/settings/SettingsRepositoryStub.kt`
- Modify: `app/src/test/java/timur/gilfanov/messenger/data/repository/SettingsRepositoryImplTest.kt`

- [x] Create `DeleteUserDataRepositoryError` sealed interface: `data class LocalOperationFailed(val error: LocalStorageError) : DeleteUserDataRepositoryError`
- [x] Add `suspend fun deleteUserData(userKey: UserScopeKey): ResultWithError<Unit, DeleteUserDataRepositoryError>` to `SettingsRepository` interface (with KDoc)
- [x] Implement in `SettingsRepositoryImpl`: call `syncScheduler.cancelUserScopedJobs(userKey)` first, then `localDataSource.deleteAllForUser(userKey)`, map `DeleteAllForUserError` to `DeleteUserDataRepositoryError.LocalOperationFailed(localStorageError)`
- [x] Add `deleteUserData` to `SettingsRepositoryFake` with configurable result (default: `ResultWithError.Success(Unit)`) and a field to inspect the `userKey` passed in
- [x] Add `deleteUserData` to `SettingsRepositoryStub` returning success by default
- [x] Add tests in `SettingsRepositoryImplTest`: success path calls both cancel and delete; on delete error, error is propagated; cancel is always called regardless of delete result
- [x] Run `./gradlew app:testDebugUnitTest --tests "*.SettingsRepositoryImplTest"` — must pass

### Task 5: LogoutUseCaseImpl — add settings cleanup before logout

**Files:**
- Modify: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/domain/usecase/LogoutUseCaseImpl.kt`
- Modify: `feature/auth/src/main/kotlin/timur/gilfanov/messenger/auth/di/AuthViewModelModule.kt`
- Modify: `feature/auth/src/test/kotlin/timur/gilfanov/messenger/auth/domain/usecase/LogoutUseCaseImplTest.kt`
- Modify: `app/src/test/java/timur/gilfanov/messenger/ui/screen/settings/SettingsViewModelLogoutTest.kt`

- [x] Add `SettingsRepository` constructor parameter to `LogoutUseCaseImpl` (between `authRepository` and `logger`)
- [x] In `LogoutUseCaseImpl.invoke()`: read `authRepository.authState.first()`, extract `UserScopeKey` if `Authenticated`, call `settingsRepository.deleteUserData(userKey)`, log any failure, then call `authRepository.logout()`
- [x] Update `AuthViewModelModule.provideLogoutUseCase` to inject `SettingsRepository`
- [x] Update `LogoutUseCaseImplTest.createUseCase` to accept `SettingsRepository` (default: `SettingsRepositoryStub()`); add test: cleanup called with correct key on successful logout; add test: cleanup failure does not block logout
- [x] Update `SettingsViewModelLogoutTest.createViewModel`: add `SettingsRepositoryStub()` to `LogoutUseCaseImpl` constructor
- [x] Run `./gradlew :feature:auth:testDebugUnitTest` and `./gradlew app:testDebugUnitTest` — must pass

### Task 6: Verify acceptance criteria

- [x] Run `./gradlew ktlintFormat detekt --auto-correct`
- [x] Run `./gradlew test` (all JVM tests) — Debug variants pass; Release KSP failures in TestChatModule/TestChatId are pre-existing and unrelated to this issue
- [x] Run `./gradlew :app:testMockDebugUnitTest :feature:auth:testDebugUnitTest`

### Task 7: Update documentation

- [x] Move this plan to `docs/plans/completed/`
