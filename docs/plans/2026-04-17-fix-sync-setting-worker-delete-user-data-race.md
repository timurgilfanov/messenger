# Fix SyncSettingWorker race with deleteUserData cleanup (#343)

## Overview
Eliminate the race where a mid-execution SyncSettingWorker can recreate settings rows after `deleteUserData` has cleared them. Combine two fixes: (1) make `cancelUserScopedJobs` a suspending function that awaits WorkManager's cancellation `Operation` before `deleteAllForUser` runs, and (2) add `isStopped` guards in `SyncSettingWorker` so it cooperates with cancellation.

## Context
- Files involved:
  - `app/src/main/java/timur/gilfanov/messenger/data/repository/SettingsSyncScheduler.kt`
  - `app/src/main/java/timur/gilfanov/messenger/data/repository/SettingsSyncSchedulerImpl.kt`
  - `app/src/main/java/timur/gilfanov/messenger/data/repository/SettingsRepositoryImpl.kt`
  - `app/src/main/java/timur/gilfanov/messenger/data/worker/SyncSettingWorker.kt`
  - `app/src/test/java/timur/gilfanov/messenger/data/repository/SettingsRepositoryImplTest.kt`
  - `app/src/test/java/timur/gilfanov/messenger/data/worker/SyncSettingWorkerTest.kt` (new)
  - `app/src/androidTest/java/timur/gilfanov/messenger/test/SettingsSyncSchedulerStub.kt`
- Related patterns: existing `CoroutineWorker` pattern, `Result/fold` error pattern, fakes-over-mocks test strategy, AR/ADR records under `docs/architecture/`.
- Dependencies: `androidx.work:work-runtime-ktx` already provides `Operation.await()` (via `kotlinx-coroutines-guava` transitive). Verify the artifact is on the `:app` classpath before use; otherwise add `androidx.work:work-runtime-ktx` (or `kotlinx-coroutines-guava`) explicitly.

## Development Approach
- Testing approach: Regular (code first, then tests) — small behavioral change, fakes already available.
- Complete each task fully before moving to the next.
- CRITICAL: every task MUST include new/updated tests.
- CRITICAL: all tests must pass before starting next task.

## Implementation Steps

### Task 1: Make `cancelUserScopedJobs` awaitable

**Files:**
- Modify: `app/src/main/java/timur/gilfanov/messenger/data/repository/SettingsSyncScheduler.kt`
- Modify: `app/src/main/java/timur/gilfanov/messenger/data/repository/SettingsSyncSchedulerImpl.kt`
- Modify: `app/src/main/java/timur/gilfanov/messenger/data/repository/SettingsRepositoryImpl.kt`
- Modify: `app/src/test/java/timur/gilfanov/messenger/data/repository/SettingsRepositoryImplTest.kt`
- Modify: `app/src/androidTest/java/timur/gilfanov/messenger/test/SettingsSyncSchedulerStub.kt`

- [x] Change `SettingsSyncScheduler.cancelUserScopedJobs` to `suspend fun`
- [x] In impl, call `workManager.cancelAllWorkByTag(userKey.key).await()` (from `kotlinx.coroutines.guava.await`) so cancellation is registered in WorkManager before return
- [x] Update `SettingsRepositoryImpl.deleteUserData` to call the suspending function directly inside `runCatching` (remove the now-unneeded wrapping if appropriate), preserving existing error-logging behavior
- [x] Update all test stubs/fakes of `SettingsSyncScheduler` to declare the function as `suspend`
- [x] Add a test verifying `deleteUserData` awaits cancellation before invoking `deleteAllForUser` (stub whose suspending `cancelUserScopedJobs` records order relative to the local data source delete)
- [x] Run `./gradlew :app:testMockDebugUnitTest --tests "*SettingsRepositoryImplTest*"` — must pass before Task 2

### Task 2: Cooperate with cancellation in `SyncSettingWorker`

**Files:**
- Modify: `app/src/main/java/timur/gilfanov/messenger/data/worker/SyncSettingWorker.kt`
- Create: `app/src/test/java/timur/gilfanov/messenger/data/worker/SyncSettingWorkerTest.kt`

- [ ] Early return `Result.failure()` at the start of `doWork` if `isStopped`
- [ ] After `syncSetting(settingKey)` returns, check `isStopped` before translating a `Success` into `Result.success()`; if stopped, return `Result.failure()` to avoid further side effects
- [ ] Ensure cooperative cancellation is respected (no `NonCancellable` wrappers around DB writes in the relied-on path — verify by reading `SettingsRepositoryImpl.syncSetting`)
- [ ] Add unit tests for the worker: verify that when `isStopped` is true, `doWork` returns `Result.failure()` without invoking `syncSetting` (use a `SyncSettingUseCase` fake that records invocations; simulate `isStopped` via a pre-stopped `WorkerParameters` or extract a testable seam)
- [ ] Run `./gradlew :app:testMockDebugUnitTest --tests "*SyncSettingWorkerTest*"` — must pass before Task 3

### Task 3: Verify acceptance criteria

- [ ] run `./gradlew ktlintFormat detekt --auto-correct`
- [ ] run `./gradlew :app:testMockDebugUnitTest`
- [ ] run `./gradlew :app:compileMockDebugAndroidTestKotlin` (the scheduler interface signature changed)
- [ ] verify test coverage for changed lines meets 80%+

### Task 4: Update documentation

- [ ] update KDoc on `SettingsSyncScheduler.cancelUserScopedJobs` to document the awaiting behavior
- [ ] move this plan to `docs/plans/completed/`
