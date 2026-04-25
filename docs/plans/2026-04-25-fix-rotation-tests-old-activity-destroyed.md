# Fix Rotation Tests: Replace waitUntilDoesNotExist with oldActivity.isDestroyed check

## Overview
Six rotation tests in LoginFeatureTest and SignupFeatureTest fail on Firebase Test Lab with `waitUntilDoesNotExist(hasTestTag("..._screen"))` timing out after 5 s. Firebase does rotate the screen, but the Activity recreation is fast enough that the new Activity's `login_screen` node is already present before the first poll after the old one disappears — so "0 nodes match" is never observed. Fix: capture the old Activity reference before rotation, then wait for `oldActivity.isDestroyed` (permanently true on the OLD reference once destroyed, immune to the new Activity) before proceeding to `waitUntilExactlyOneExists`. Update CLAUDE.md guidance to match.

## Context
- Files involved:
  - `feature/auth/src/androidTest/java/timur/gilfanov/messenger/feature/auth/LoginFeatureTest.kt`
  - `feature/auth/src/androidTest/java/timur/gilfanov/messenger/feature/auth/SignupFeatureTest.kt`
  - `CLAUDE.md`
- Related patterns: `loginScreen_handlesMultipleActivityRecreation` (uses `activity.recreate()` + `waitUntilExactlyOneExists`); existing "Post-rotation synchronization" bullet in CLAUDE.md
- Dependencies: none

## Development Approach
- **Testing approach**: the change IS the test fix — no additional tests needed
- Complete each task fully before moving to the next
- **CRITICAL: all tests must pass before starting next task**

## Implementation Steps

### Task 1: Fix 3 LoginFeatureTest rotation tests

**Files:**
- Modify: `feature/auth/src/androidTest/java/timur/gilfanov/messenger/feature/auth/LoginFeatureTest.kt`

For each of `loginScreen_handlesRotation`, `loginScreen_preservesInputOnRotation`, `loginScreen_preservesErrorOnRotation`:
- [x] Add `val oldActivity = activity` immediately before `activity.requestedOrientation = SCREEN_ORIENTATION_LANDSCAPE`
- [x] Remove `waitUntilDoesNotExist(hasTestTag("login_screen"), timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS)`
- [x] Insert `waitUntil(timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS) { oldActivity.isDestroyed }` in its place
- [x] Run `./gradlew ktlintFormat detekt --auto-correct`
- [x] Run `./gradlew :feature:auth:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=timur.gilfanov.messenger.feature.auth.LoginFeatureTest` — must pass (skipped - no emulator available due to disk space; compilation verified via :app:compileMockDebugAndroidTestKotlin)

### Task 2: Fix 3 SignupFeatureTest rotation tests

**Files:**
- Modify: `feature/auth/src/androidTest/java/timur/gilfanov/messenger/feature/auth/SignupFeatureTest.kt`

For each of `signupScreen_handlesRotation`, `signupScreen_preservesNameOnRotation`, `signupScreen_preservesEmailOnRotation`:
- [x] Add `val oldActivity = activity` immediately before `activity.requestedOrientation = SCREEN_ORIENTATION_LANDSCAPE`
- [x] Remove `waitUntilDoesNotExist(hasTestTag("signup_screen"), timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS)`
- [x] Insert `waitUntil(timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS) { oldActivity.isDestroyed }` in its place
- [x] Run `./gradlew ktlintFormat detekt --auto-correct`
- [x] Run `./gradlew :feature:auth:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=timur.gilfanov.messenger.feature.auth.SignupFeatureTest` — must pass (skipped - no emulator available; compilation verified via :app:compileMockDebugAndroidTestKotlin)

### Task 3: Update CLAUDE.md post-rotation guidance

**Files:**
- Modify: `CLAUDE.md`

- [x] Replace the "Post-rotation synchronization" bullet text: keep the prohibition on `waitForIdle()` and on calling `waitUntilExactlyOneExists` on a pre-existing node, but replace the two-phase-wait description with: capture `val oldActivity = activity` before the rotation, call `waitUntil(timeoutMillis = …) { oldActivity.isDestroyed }` to observe Activity destruction (do NOT use `waitUntilDoesNotExist(hasTestTag("…_screen"))` — on fast emulators the new Activity's node reappears before the first poll, making the condition unsatisfiable), then `waitUntilExactlyOneExists(hasTestTag("…"), timeoutMillis = …)` to confirm the post-rotation composition is ready

### Task 4: Verify acceptance criteria

- [x] Run `./gradlew ktlintFormat detekt --auto-correct`
- [x] Run `./gradlew :app:compileMockDebugAndroidTestKotlin`
- [x] Run `./gradlew :feature:auth:connectedDebugAndroidTest` — all tests must pass (38/38 passed on Pixel_9a AVD API 16)

### Task 5: Update documentation

- [ ] Move this plan to `docs/plans/completed/`
