---
# Fix flaky concurrent 401 coalescing test in AuthInterceptor

## Overview
Replace a fragile `while { yield() } + runCurrent()` synchronization pattern with `advanceUntilIdle()` in `AuthInterceptorTest`, eliminating the race condition that causes `tokenRefreshUseCase` to be invoked twice on CI.

## Context
- Files involved: `feature/auth/src/test/kotlin/timur/gilfanov/messenger/auth/AuthInterceptorTest.kt`
- Root cause: `runCurrent()` only processes tasks that are "already in the queue at call time" — it explicitly does not re-run tasks added concurrently (e.g. by Ktor's pipeline resuming on a different dispatcher). After the while loop exits (one refresh invocation counted), coroutines 2-10 may still be mid-pipeline and haven't called `getOrCreateRefresh()` yet. `releaseRefresh.complete(Unit)` then fires while `ongoingRefresh.isActive == false`, so lagging coroutines each create a second refresh. `advanceUntilIdle()` keeps draining until nothing is runnable, ensuring all 10 are suspended at `deferred.await()` before the refresh is released.
- Production code `AuthInterceptor.kt` is correct; no changes needed there.
- Related patterns: none (isolated test fix)
- Dependencies: none

## Development Approach
- **Testing approach**: fix test directly (the production code is correct)
- Single task; no architectural change, no new dependencies
- **CRITICAL: all tests must pass before starting next task**

## Implementation Steps

### Task 1: Fix the flaky concurrent coalescing test

**Files:**
- Modify: `feature/auth/src/test/kotlin/timur/gilfanov/messenger/auth/AuthInterceptorTest.kt`

- [x] Remove the `while (refreshInvocationCount.get() == 0) { yield() }` loop from the concurrent test
- [x] Replace `runCurrent()` with `advanceUntilIdle()` (this processes all pending work until everything is suspended, guaranteeing all 10 coroutines are at `deferred.await()` before the refresh is released)
- [x] Update imports: remove `import kotlinx.coroutines.yield` and `import kotlinx.coroutines.test.runCurrent`; add `import kotlinx.coroutines.test.advanceUntilIdle`
- [x] Run `./gradlew ktlintFormat detekt --auto-correct`
- [x] Run the isolated test to confirm it passes: `./gradlew :feature:auth:testDebugUnitTest --tests "timur.gilfanov.messenger.auth.AuthInterceptorTest.when concurrent 401 responses then tokenRefreshUseCase is invoked only once"`

### Task 2: Verify acceptance criteria

- [ ] Run `./gradlew ktlintFormat detekt --auto-correct`
- [ ] Run full unit test suite for the module: `./gradlew :feature:auth:testDebugUnitTest`
- [ ] Confirm no regressions in the other `AuthInterceptorTest` cases

### Task 3: Update documentation

- [ ] Move this plan to `docs/plans/completed/`
