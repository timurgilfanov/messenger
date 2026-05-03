# Fix Settings Offline-Fallback Without Persisting Defaults

## Overview

Bug from issue #333: when the settings table is empty and remote recovery fails (offline), `SettingsRepositoryImpl.upsertDefaultSettings()` writes a row with `localVersion=1, syncedVersion=0, serverVersion=0`. The mismatch tags it as pending, so the next online sync pushes placeholder defaults and overwrites the user's real server preferences.

Simpler fix (per user direction): do not persist any row when the offline-fallback fires. The repository emits transient defaults so the UI still has something to display, and the next observe-collection cycle (e.g. next app launch / ViewModel re-init) re-attempts `recoverSettings()` against the server. Because no row is ever written, `syncAllPendingSettings` cannot pick up placeholder defaults, so the overwrite cannot happen.

The one tricky path is `changeUiLanguage()` on an empty DB: it currently relies on `upsertDefaultSettings()` having created a row so the recursive transform succeeds. With the simpler approach, on offline + empty DB we instead directly persist the user's chosen language as a fresh row (`localVersion=1, syncedVersion=0, serverVersion=0`) and schedule sync — this is a real user choice, not a placeholder, so pushing it up (with LWW conflict handling on the server side) is correct.

## Context

- Files involved:
  - Modify: `app/src/main/java/timur/gilfanov/messenger/data/repository/SettingsRepositoryImpl.kt` (drop `upsertDefaultSettings()`; change `recoverSettings()` failure branch to return `SettingsResetToDefaults` without persisting; change `observeSettings()` to follow `SettingsResetToDefaults` with a transient `Success(defaultSettings)` emission; change `handleRecoverSettingsError()` so the `changeUiLanguage` `SettingsResetToDefaults` branch directly persists the user's choice instead of recursing)
  - Modify: `app/src/test/java/timur/gilfanov/messenger/data/repository/SettingsRepositoryImplTest.kt` (add regression tests; update existing offline-fallback test to assert no row is persisted)
  - Modify (only if assertions break): `app/src/test/java/timur/gilfanov/messenger/data/repository/SettingsRepositoryIntegrationTest.kt`
- Related patterns:
  - `LocalSetting` invariants: `app/src/main/java/timur/gilfanov/messenger/data/source/local/LocalSetting.kt`
  - Existing `SettingsResetToDefaults` consumer behavior: `SettingsViewModel.kt:80` and `LanguageViewModel.kt:109` only log on this error and otherwise wait for a `Success` emission — so emitting a follow-up `Success(defaultSettings)` keeps the UX working without changing consumer logic
  - Test fakes already exercise the offline path: the default `remoteDataSourceStub` in `SettingsRepositoryImplTest.kt:97` returns `NetworkNotAvailable`, and the existing `observeSettings returns default settings when no entities exist` test (line 129) covers the recovery path
- Dependencies: none

## Development Approach

- Testing approach: Regular (code first, then tests)
- Keep the change strictly to `SettingsRepositoryImpl.kt` plus its tests — no new files, no interface changes, no worker/scheduler additions
- Preserve the existing `SettingsResetToDefaults` error contract (still emitted once) so `ObserveSettingsError` / `ObserveUiLanguageError` mapping and consumer logging stay untouched
- Follow each `SettingsResetToDefaults` emission with a `Success(defaultSettings)` so the UI still receives values (replaces the previous "DB write triggers re-emit" mechanism)
- The `changeUiLanguage` offline + empty DB case directly persists the user's chosen language (`localVersion=1, syncedVersion=0, serverVersion=0`) and schedules sync — it is not a placeholder
- Trade-off accepted: server values are pulled only on the next observe-collection cycle (next app launch / VM re-init), not via a background worker. This is the user-chosen scope for this fix
- **CRITICAL: every task MUST include new/updated tests**
- **CRITICAL: all tests must pass before starting next task**

## Implementation Steps

### Task 1: Stop persisting offline-fallback defaults in recoverSettings

**Files:**
- Modify: `app/src/main/java/timur/gilfanov/messenger/data/repository/SettingsRepositoryImpl.kt`

- [x] Change `recoverSettings()` failure branch (currently calls `upsertDefaultSettings(userKey)`) to log the remote error and return `ResultWithError.Failure(GetSettingsRepositoryError.SettingsResetToDefaults)` without touching the local DB
- [x] Delete the now-unused `upsertDefaultSettings()` private method
- [x] Remove the `defaultLocalSetting` import if it becomes unused
- [x] Change `observeSettings()` to use `flatMapConcat` (or equivalent) instead of `map` so that, when `handleObserveFailure` returns `Failure(SettingsResetToDefaults)`, the flow emits both `Failure(SettingsResetToDefaults)` and a follow-up `Success(defaultSettings.toLocalSettings()-equivalent transient value)` — for non-`SettingsResetToDefaults` failures, emit a single value as before; keep `.distinctUntilChanged()`
- [x] Run `./gradlew :app:compileMockDebugKotlin` to confirm compilation

### Task 2: Handle changeUiLanguage on empty DB without recursion

**Files:**
- Modify: `app/src/main/java/timur/gilfanov/messenger/data/repository/SettingsRepositoryImpl.kt`

- [x] In `handleRecoverSettingsError()`, change the `GetSettingsRepositoryError.SettingsResetToDefaults` branch: instead of recursively calling `changeUiLanguage(userKey, language)` (which would now infinite-loop because no default row is ever persisted), directly upsert a fresh `LocalSetting(value=language, localVersion=1, syncedVersion=0, serverVersion=0, modifiedAt=now())` via `localDataSource.upsert(userKey, TypedLocalSetting.UiLanguage(...))`, and on success call `syncScheduler.scheduleSettingSync(userKey, SettingKey.UI_LANGUAGE)` and return `Success(Unit)`; on upsert failure map via the existing `UpsertSettingError → ChangeLanguageRepositoryError.LocalOperationFailed` pattern
- [x] Run `./gradlew :app:compileMockDebugKotlin` to confirm compilation

### Task 3: Update and add regression tests in SettingsRepositoryImplTest

**Files:**
- Modify: `app/src/test/java/timur/gilfanov/messenger/data/repository/SettingsRepositoryImplTest.kt`

- [x] Update `observeSettings returns default settings when no entities exist` (line 129): keep the assertion that the first emission is `Failure(SettingsResetToDefaults)` and the second is `Success(defaultSettings)`, then ALSO assert the local DB still has no row (`localDataSource.getSetting(testUserKey, SettingKey.UI_LANGUAGE)` returns `Failure(GetSettingError.NotFound)` or equivalent)
- [x] Add test `observeSettings does not persist offline fallback defaults`: collect `observeSettings`, await both emissions, then assert `localDataSource.getSetting(testUserKey, SettingKey.UI_LANGUAGE)` is still NotFound
- [x] Add test `syncAllPendingSettings is no-op after offline fallback`: trigger the offline-fallback path, then call `repository.syncAllPendingSettings(testUserKey)` against a recording remote stub and assert (a) it returns `Success(Unit)` and (b) neither `syncBatch` nor `syncSingleSetting` was invoked
- [x] Add test `changeUiLanguage on empty DB while offline persists user choice with versions 1, 0, 0`: with no local row and the default offline `remoteDataSourceStub`, call `repository.changeUiLanguage(testUserKey, UiLanguage.German)`, assert `Success(Unit)`, then read back the persisted setting and assert `value=German`, `localVersion=1`, `syncedVersion=0`, `serverVersion=0`, and that `syncSchedulerStub` recorded a `scheduleSettingSync(UI_LANGUAGE)` call
- [x] Add test `changeUiLanguage on empty DB while offline does not infinite-loop`: same setup, ensure the call returns within the test (would hang or stack-overflow under regression)
- [x] Run `./gradlew :app:testMockDebugUnitTest --tests "timur.gilfanov.messenger.data.repository.SettingsRepositoryImplTest"` — must pass before next task

### Task 4: Verify integration test still passes (and update assertions if needed)

**Files:**
- Modify (only if necessary): `app/src/test/java/timur/gilfanov/messenger/data/repository/SettingsRepositoryIntegrationTest.kt`

- [x] Run `./gradlew :app:testMockDebugUnitTest --tests "timur.gilfanov.messenger.data.repository.SettingsRepositoryIntegrationTest"`
- [x] If the existing offline-fallback assertion at lines 98-104 (which currently accepts either a `SettingsResetToDefaults` failure or a `Success` with defaults) breaks, update only the assertion to match the new two-emission sequence; do NOT change production semantics

### Task 5: Verify acceptance criteria

- [x] Run `./gradlew ktlintFormat detekt --auto-correct`
- [x] Run `./gradlew :app:testMockDebugUnitTest --tests "timur.gilfanov.messenger.data.repository.*"` to cover both repository-level test classes
- [x] Run `./gradlew :app:compileMockDebugAndroidTestKotlin` (no interface changes, but ensures nothing else compiled against `upsertDefaultSettings` indirectly)
- [x] Verify the new tests cover: (a) no row persisted on offline fallback, (b) `syncAllPendingSettings` is a no-op after offline fallback, (c) `changeUiLanguage` on offline + empty DB persists the user's actual choice and does not loop
- [x] Verify test coverage meets 80%+ (skipped - manual verification, not automatable in this loop)

### Task 6: Update documentation

- [x] No README changes needed
- [x] Update the KDoc on `SettingsRepositoryImpl` (lines 47-73) `On-Demand Recovery` bullet that says "Falls back to default settings if remote fetch fails" to clarify that offline fallback emits transient defaults and does NOT persist (so the next observe cycle retries against the server)
- [x] Move this plan to `docs/plans/completed/`
