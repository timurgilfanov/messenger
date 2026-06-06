# Add application tests for chronological message order in chat screen

## Overview
Add two instrumented application tests (no production code changes) that prove the chat screen renders messages oldest-at-top, newest-at-bottom, splitting the two code paths the issue conflates:

1. Initial paging→render order (preset-only): open a chat already seeded with many messages at distinct timestamps and assert visible messages render in chronological order. Catches the "newest-first on initial load" regression that prompted the issue.
2. Live-edge append order: open the Bob chat (one seeded message), inject a received message and send a reply, then assert all three render oldest→newest top-to-bottom. Catches a new message appending to the wrong end after PagingSource invalidation — a path preset-only messages can never exercise.

Both go through MainActivity + chat-list navigation so they assert true application-level behavior.

## Context
- Files involved:
  - Create: `app/src/androidTest/java/timur/gilfanov/messenger/application/ChatMessageOrderApplicationTest.kt` (preset-only, initial render)
  - Create: `app/src/androidTest/java/timur/gilfanov/messenger/application/ChatLiveAppendOrderApplicationTest.kt` (received + sent append)
- Related patterns / references:
  - `app/src/androidTest/java/timur/gilfanov/messenger/application/MessageReplyApplicationTest.kt` — Hilt scaffolding to mirror (`@HiltAndroidTest`, `@UninstallModules(RepositoryModule, AuthDataModule)`, `@ApplicationTest`, `@RunWith(AndroidJUnit4)`, hilt/compose/cleanup rules, in-class repository module), plus `simulateBobSendingMessage` + `message_input`/`send_button` reply flow
  - `app/src/androidTest/java/timur/gilfanov/messenger/feature/chat/ChatScrollPreservationFeatureTest.kt` — uses the `LONG_CONVERSATION` scenario and message-ordering assertions
  - `app/src/androidTest/java/timur/gilfanov/messenger/test/AndroidTestDataHelper.kt` — `DataScenario.{NON_EMPTY, LONG_CONVERSATION}`, `BOB_CHAT_ID`, `BOB_TEXT_1`, `MESSAGE_3_TIME`, `LONG_CHAT_ID`, `LONG_CONVERSATION_SIZE` (40), `longConversationMessageText(index)` → "Conversation message N"
  - `app/src/androidTest/java/timur/gilfanov/messenger/test/AndroidTestRepositoryWithRealImplementation.kt` — `simulateBobSendingMessage(text, createdAt)`
  - `app/src/main/java/timur/gilfanov/messenger/ui/screen/chat/ChatScreen.kt` — `message_list` LazyColumn uses `reverseLayout = true`; message bubbles have NO per-message test tag, so visual order is verified by comparing each message text node's `getBoundsInRoot().top` (a `Dp`, Comparable)
  - Annotation: `timur.gilfanov.messenger.annotations.ApplicationTest`
- Dependencies: none (test-only; uses existing test infrastructure and seed data)

## Key design decisions
- Two test classes, not one: each `@HiltAndroidTest` class carries a single in-class repository module, and the two tests need different `DataScenario` seedings (`LONG_CONVERSATION` vs `NON_EMPTY`). Both modules copy MessageReplyApplicationTest's providers verbatim (`AndroidTestRepositoryWithRealImplementation(scenario)`, `SettingsRepositoryStub`, `AuthRepositoryFake`, `LocaleRepositoryImpl`, repository `CoroutineScope`) and the three rules.
- Preset-only test (`ChatMessageOrderApplicationTest`, `LONG_CONVERSATION`):
  - Reuses existing seed data — no changes to `AndroidTestDataHelper`. The long Alice chat has 40 messages at strictly ascending timestamps.
  - Open `chat_item_$LONG_CHAT_ID` from `chat_list`; wait for `message_list`.
  - On initial load with `reverseLayout`, the newest messages sit at the live edge (bottom) and are visible. Assert the three newest — `longConversationMessageText(38/39/40)` — render with strictly ascending `getBoundsInRoot().top` (38 above 39 above 40 = oldest above newest). Three short messages are guaranteed visible at the bottom on any emulator screen.
  - This directly fails on a newest-first initial render regardless of `reverseLayout` internals.
- Live-append test (`ChatLiveAppendOrderApplicationTest`, `NON_EMPTY`):
  - Three messages at ascending timestamps: seeded `BOB_TEXT_1` @ `MESSAGE_3_TIME` (oldest), received via `simulateBobSendingMessage(receivedText, MESSAGE_3_TIME + 1.minutes)` (middle), reply typed into `message_input` + `send_button` → real "now" timestamp (newest).
  - Open the Bob chat, wait for each text, then assert `BOB_TEXT_1.top < received.top < reply.top`.
  - Assert ordering ONLY — no unread-badge assertions, so it avoids the read-marking flakiness that keeps MessageReplyApplicationTest `@Ignore`d (#339). Do NOT add `@Ignore`.

## Development Approach
- **Testing approach**: Test-only change — these tests are the deliverable; no production code is modified.
- Complete each task fully before moving to the next.
- **CRITICAL: all tests must pass before starting next task.**

## Implementation Steps

### Task 1: Add ChatMessageOrderApplicationTest (preset-only initial render)

**Files:**
- Create: `app/src/androidTest/java/timur/gilfanov/messenger/application/ChatMessageOrderApplicationTest.kt`

- [x] Create the class with MessageReplyApplicationTest's Hilt/rule scaffolding (`@HiltAndroidTest`, `@UninstallModules(RepositoryModule, AuthDataModule)`, `@ApplicationTest`, `@RunWith(AndroidJUnit4)`, hilt/compose/cleanup rules, in-class repository module, `@Before` inject).
- [x] In-class repository module uses `AndroidTestRepositoryWithRealImplementation(LONG_CONVERSATION)`.
- [x] Test body: wait for `chat_list` → open `chat_item_$LONG_CHAT_ID` → wait for `message_list` → wait for the three newest message texts → read `onNodeWithText(text).getBoundsInRoot().top` for `longConversationMessageText(38/39/40)` and assert strictly ascending (`assertTrue` from kotlin.test).
- [x] Run `./gradlew :app:compileMockDebugAndroidTestKotlin` — must pass before Task 2.

### Task 2: Add ChatLiveAppendOrderApplicationTest (received + sent append)

**Files:**
- Create: `app/src/androidTest/java/timur/gilfanov/messenger/application/ChatLiveAppendOrderApplicationTest.kt`

- [x] Create the class with the same scaffolding; in-class repository module uses `AndroidTestRepositoryWithRealImplementation(NON_EMPTY)`.
- [x] Test body: wait for `chat_list` → open `chat_item_$BOB_CHAT_ID` → wait for `message_list` and `BOB_TEXT_1` → `simulateBobSendingMessage(receivedText, MESSAGE_3_TIME.plus(1.minutes))`, wait for `receivedText` → type `replyText` into `message_input`, click `send_button`, wait for `replyText` → assert `BOB_TEXT_1.top < receivedText.top < replyText.top` via `getBoundsInRoot().top` (`assertTrue`).
- [x] Run `./gradlew :app:compileMockDebugAndroidTestKotlin` — must pass before Task 3.

### Task 3: Format, static analysis, and run on emulator

- [x] Run `./gradlew ktlintFormat detekt --auto-correct`.
- [x] Launch an AVD if none running (`$ANDROID_HOME/emulator/emulator -list-avds`, then `-avd <name> -no-boot-anim` in background). (emulator-5554 already running)
- [x] Run both tests: `./gradlew :app:connectedMockDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=timur.gilfanov.messenger.application.ChatMessageOrderApplicationTest,timur.gilfanov.messenger.application.ChatLiveAppendOrderApplicationTest` — must pass. (2/2 passed)

### Task 4: Update documentation

- [x] No CLAUDE.md/README changes expected (test-only, no new pattern); update only if a non-obvious convention emerged. (no new convention emerged — no doc changes)
- [x] Move this plan to `docs/plans/completed/`.
