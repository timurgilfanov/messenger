---
# Fix CI race in concurrent 401 coalescing test

## Overview
`AuthInterceptorTest.when concurrent 401 responses then tokenRefreshUseCase is invoked only once`
fails on GitHub Actions with `expected:<1> but was:<2>`. Production code is correct.

Root cause: `MockEngine.execute()` runs `withContext(dispatcher + callContext)` where `dispatcher`
defaults to `Dispatchers.IO` (`HttpClientEngineBase.dispatcher = config.dispatcher ?: ioDispatcher()`).
`advanceUntilIdle()` only drains the test scheduler queue, not real IO threads. So it returns
"idle" while IO threads are still executing the mock handler. `releaseRefresh.complete(Unit)` fires
immediately. When IO threads eventually dispatch back, `deferred1` is already complete, subsequent
requests see `deferred1.isActive == false`, each create their own deferred, count becomes 2+.

Fix: set `mockEngine.config.dispatcher = Dispatchers.Unconfined` in the concurrent test body before
calling `buildClient` (which constructs `HttpClient`). `withContext(Unconfined + callContext)` runs the handler synchronously on the
calling thread. No real-thread dispatch — all work stays on the test scheduler.
Concurrency is preserved: `runTest` uses `StandardTestDispatcher` (cooperative, single-threaded).
When `advanceUntilIdle()` runs the 10 request coroutines FIFO, each suspends at `deferred1.await()`
before the refresh-coroutine gets its turn (it was enqueued after the 10 requests). All 10 callers
see `deferred1.isActive == true` and reuse it — exactly the concurrent coalescing scenario.
`Dispatchers.Unconfined` removes the IO indirection, making the interleaving deterministic.
The existing KDoc already states the intent: "does not depend on Ktor engine scheduling details."
The fix is what finally makes that claim true.

## Context
- Files involved:
  - `feature/auth/src/test/kotlin/timur/gilfanov/messenger/auth/AuthInterceptorTest.kt`
- Related patterns: none — isolated test fix, production code `AuthInterceptor.kt` is correct
- Dependencies: none

## Development Approach
- **Testing approach**: fix test directly (production code is correct)
- Single task, no architectural change, no new dependencies
- **CRITICAL: every task MUST include new/updated tests**
- **CRITICAL: all tests must pass before starting next task**

## Implementation Steps

### Task 1: Apply the dispatcher fix and update the KDoc

**Files:**
- Modify: `feature/auth/src/test/kotlin/timur/gilfanov/messenger/auth/AuthInterceptorTest.kt`

- [x] In the concurrent test body, add `mockEngine.config.dispatcher = Dispatchers.Unconfined` before calling `buildClient` (before `HttpClient` is created, so the lazy `dispatcher` property on the engine picks up the override). Scoped to this test only so other tests run with the default engine dispatcher.
- [x] Add `import kotlinx.coroutines.Dispatchers` to imports
- [x] Update the KDoc on the concurrent-401 test to explain the `Dispatchers.Unconfined` constraint: MockEngine defaults to `Dispatchers.IO`, which defeats `advanceUntilIdle()` — Unconfined keeps the handler on the test scheduler so all 10 request coroutines reach `deferred1.await()` before the refresh-coroutine runs, preserving deterministic concurrent coalescing verification
- [x] Run `./gradlew ktlintFormat detekt --auto-correct`
- [x] Run the isolated failing test: `./gradlew :feature:auth:testDebugUnitTest --tests "timur.gilfanov.messenger.auth.AuthInterceptorTest.when concurrent 401 responses then tokenRefreshUseCase is invoked only once"` — must pass

### Task 2: Verify acceptance criteria

- [x] Run the full `AuthInterceptorTest` suite: `./gradlew :feature:auth:testDebugUnitTest --tests "timur.gilfanov.messenger.auth.AuthInterceptorTest"`
- [x] Run the full `:feature:auth` unit test suite: `./gradlew :feature:auth:testDebugUnitTest`

### Task 3: Update documentation

- [x] Move this plan to `docs/plans/completed/`
