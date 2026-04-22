# Fix Flaky Rotation Tests: Replace waitForIdle() with Explicit Node Waits

## Overview
Replace post-rotation `waitForIdle()` calls in `LoginFeatureTest` and `SignupFeatureTest` with `waitUntilExactlyOneExists(...)` targeting a concrete node. This makes the post-rotation synchronization point deterministic: the test waits for a visible node rather than relying on Compose/Espresso idling, which can hang indefinitely on Firebase Test Lab due to IME and window-redraw activity after orientation change.

## Context
- Files involved:
  - `feature/auth/src/androidTest/java/timur/gilfanov/messenger/feature/auth/LoginFeatureTest.kt`
  - `feature/auth/src/androidTest/java/timur/gilfanov/messenger/feature/auth/SignupFeatureTest.kt`
- Related patterns: existing use of `waitUntilExactlyOneExists` in pre-rotation setup in both files; `SCREEN_LOAD_TIMEOUT_MILLIS = 5_000L` already defined in both files
- Dependencies: none

## Development Approach
- **Testing approach**: the change IS the test fix — no additional tests needed
- Single task since all changes are parallel and non-interactive
- **CRITICAL: all tests must pass before task is considered complete**

## Implementation Steps

### Task 1: Fix post-rotation waitForIdle() in LoginFeatureTest

**Files:**
- Modify: `feature/auth/src/androidTest/java/timur/gilfanov/messenger/feature/auth/LoginFeatureTest.kt`

In `loginScreen_handlesRotation` (line 173):
- [x] Replace `waitForIdle()` with `waitUntilExactlyOneExists(hasTestTag("login_email_field"), timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS)`

In `loginScreen_preservesInputOnRotation` (line 206):
- [x] Replace `waitForIdle()` with `waitUntilExactlyOneExists(hasTestTag("login_email_field"), timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS)`

In `loginScreen_preservesErrorOnRotation` (line 232):
- [x] Replace `waitForIdle()` with `waitUntilExactlyOneExists(hasTestTag("login_general_error"), timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS)`

- [x] Run `./gradlew ktlintFormat detekt --auto-correct`
- [x] Run `./gradlew :feature:auth:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=timur.gilfanov.messenger.feature.auth.LoginFeatureTest` — must pass

### Task 2: Fix post-rotation waitForIdle() in SignupFeatureTest

**Files:**
- Modify: `feature/auth/src/androidTest/java/timur/gilfanov/messenger/feature/auth/SignupFeatureTest.kt`

In `signupScreen_handlesRotation` (line 177):
- [x] Replace `waitForIdle()` with `waitUntilExactlyOneExists(hasTestTag("signup_name_field"), timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS)`

In `signupScreen_preservesNameOnRotation` (line 193):
- [x] Replace `waitForIdle()` with `waitUntilExactlyOneExists(hasTestTag("signup_name_field"), timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS)`

In `signupScreen_preservesEmailOnRotation` (line 210):
- [x] Replace `waitForIdle()` with `waitUntilExactlyOneExists(hasTestTag("signup_email_field"), timeoutMillis = SCREEN_LOAD_TIMEOUT_MILLIS)`

- [x] Run `./gradlew ktlintFormat detekt --auto-correct`
- [x] Run `./gradlew :feature:auth:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=timur.gilfanov.messenger.feature.auth.SignupFeatureTest` — must pass

### Task 3: Verify acceptance criteria

- [ ] Run `./gradlew ktlintFormat detekt --auto-correct`
- [ ] Run `./gradlew :feature:auth:connectedDebugAndroidTest`

### Task 4: Update documentation

- [ ] Move this plan to `docs/plans/completed/`
