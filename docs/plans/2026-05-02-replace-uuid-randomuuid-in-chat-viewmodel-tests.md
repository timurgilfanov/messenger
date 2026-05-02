# Replace UUID.randomUUID() with constants in ChatViewModel test fixtures

## Overview
Refactor five `ChatViewModel*Test` files in `app/src/test/.../ui/screen/chat/` to replace `UUID.randomUUID()` calls with named companion-object constants (`TEST_CHAT_ID`, `TEST_CURRENT_USER_ID`, `TEST_OTHER_USER_ID`, etc.). Resolves issue #337. Follows the existing convention already established in `ChatViewModelMessageSendingTest.kt` and `ChatViewModelProcessDeathTest.kt` for reproducibility per the project Testing Strategy.

## Context
- Files involved:
  - `app/src/test/java/timur/gilfanov/messenger/ui/screen/chat/ChatViewModelUpdatesTest.kt`
  - `app/src/test/java/timur/gilfanov/messenger/ui/screen/chat/ChatViewModelTextInputTest.kt`
  - `app/src/test/java/timur/gilfanov/messenger/ui/screen/chat/ChatViewModelLoadingTest.kt`
  - `app/src/test/java/timur/gilfanov/messenger/ui/screen/chat/ChatViewModelAuthTest.kt` (already uses inline `UUID.fromString` — extract to constants)
  - `app/src/test/java/timur/gilfanov/messenger/ui/screen/chat/ChatViewModelErrorHandlingTest.kt` (added per Q&A — uses `UUID.randomUUID()`)
- Related patterns:
  - `app/src/test/java/timur/gilfanov/messenger/ui/screen/chat/ChatViewModelMessageSendingTest.kt` — established companion-object constant pattern (lines 42-51)
  - `app/src/test/java/timur/gilfanov/messenger/ui/screen/chat/ChatViewModelProcessDeathTest.kt` — same pattern
  - CLAUDE.md Testing rule: "Use constants for time and IDs instead of current time or randomly generated IDs"
- Dependencies: none (pure test refactor)

## Development Approach
- Testing approach: refactor only — preserve existing test behavior; each test class already has tests. No new tests added; existing tests act as the regression check.
- Each file is mechanically transformed: add a `private companion object` with `TEST_CHAT_ID`, `TEST_CURRENT_USER_ID`, `TEST_OTHER_USER_ID` (and any extra IDs the file needs, e.g. `TEST_NEW_PARTICIPANT_ID` in UpdatesTest), then replace inline assignments inside test methods.
- Use the same UUID literals as `ChatViewModelMessageSendingTest`: `00000000-0000-0000-0000-000000000001` (chat), `...000000002` (current user), `...000000003` (other user). Additional IDs continue the sequence (`...000000004`, etc.).
- Remove `import java.util.UUID` only if no remaining usage in the file.
- CRITICAL: existing tests must pass after each task before moving on.

## Implementation Steps

### Task 1: Refactor ChatViewModelLoadingTest

**Files:**
- Modify: `app/src/test/java/timur/gilfanov/messenger/ui/screen/chat/ChatViewModelLoadingTest.kt`

- [x] Add `private companion object` with `TEST_CHAT_ID`, `TEST_CURRENT_USER_ID`, `TEST_OTHER_USER_ID` constants using fixed UUID literals
- [x] Replace 8 `UUID.randomUUID()` call sites across 3 test methods with the constants
- [x] Run `./gradlew :app:testMockDebugUnitTest --tests "timur.gilfanov.messenger.ui.screen.chat.ChatViewModelLoadingTest"` — must pass before task 2

### Task 2: Refactor ChatViewModelTextInputTest

**Files:**
- Modify: `app/src/test/java/timur/gilfanov/messenger/ui/screen/chat/ChatViewModelTextInputTest.kt`

- [x] Add `private companion object` with `TEST_CHAT_ID`, `TEST_CURRENT_USER_ID`, `TEST_OTHER_USER_ID` constants
- [x] Replace 3 `UUID.randomUUID()` call sites with the constants
- [x] Run `./gradlew :app:testMockDebugUnitTest --tests "timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTextInputTest"` — must pass before task 3

### Task 3: Refactor ChatViewModelUpdatesTest

**Files:**
- Modify: `app/src/test/java/timur/gilfanov/messenger/ui/screen/chat/ChatViewModelUpdatesTest.kt`

- [x] Add `private companion object` with `TEST_CHAT_ID`, `TEST_CURRENT_USER_ID`, `TEST_OTHER_USER_ID`, `TEST_NEW_PARTICIPANT_ID` constants
- [x] Replace 10 `UUID.randomUUID()` call sites across 3 test methods with the constants
- [x] Run `./gradlew :app:testMockDebugUnitTest --tests "timur.gilfanov.messenger.ui.screen.chat.ChatViewModelUpdatesTest"` — must pass before task 4

### Task 4: Refactor ChatViewModelAuthTest

**Files:**
- Modify: `app/src/test/java/timur/gilfanov/messenger/ui/screen/chat/ChatViewModelAuthTest.kt`

- [x] Add `private companion object` with `TEST_CHAT_ID`, `TEST_CURRENT_USER_ID`, `TEST_OTHER_USER_ID` constants (file already uses fixed `UUID.fromString` literals inline — just extract them)
- [x] Replace inline `UUID.fromString(...)` constructions in the test method with the constants
- [x] Run `./gradlew :app:testMockDebugUnitTest --tests "timur.gilfanov.messenger.ui.screen.chat.ChatViewModelAuthTest"` — must pass before task 5

### Task 5: Refactor ChatViewModelErrorHandlingTest

**Files:**
- Modify: `app/src/test/java/timur/gilfanov/messenger/ui/screen/chat/ChatViewModelErrorHandlingTest.kt`

- [x] Add `private companion object` with `TEST_CHAT_ID`, `TEST_CURRENT_USER_ID`, `TEST_OTHER_USER_ID` constants
- [x] Replace 11 `UUID.randomUUID()` call sites across 4 test methods with the constants
- [x] Run `./gradlew :app:testMockDebugUnitTest --tests "timur.gilfanov.messenger.ui.screen.chat.ChatViewModelErrorHandlingTest"` — must pass before task 6

### Task 6: Verify acceptance criteria

- [ ] run `./gradlew ktlintFormat detekt --auto-correct`
- [ ] run `./gradlew :app:testMockDebugUnitTest --tests "timur.gilfanov.messenger.ui.screen.chat.*"` to confirm all chat ViewModel tests pass together
- [ ] verify no `UUID.randomUUID()` remains in the 5 refactored files via grep

### Task 7: Update documentation

- [ ] move this plan to `docs/plans/completed/`
