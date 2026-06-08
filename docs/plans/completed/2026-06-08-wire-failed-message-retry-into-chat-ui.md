# Wire failed outgoing-message retry into chat UI

## Overview

Issue #405. `ChatViewModel.retryMessage(messageId)` already exists (added in #377) but is unreachable from the UI. This wires a discoverable, accessible retry action through the Compose layer: failed outgoing messages (sent by the current user) expose a Retry control that invokes `ChatViewModel.retryMessage` with the failed message's existing ID. Timeline updates remain the source of truth for the Sending → Sent/Failed transition, and the composer input is never touched on retry.

## Context

- Files involved:
  - `app/src/main/java/timur/gilfanov/messenger/ui/screen/chat/MessageBubble.kt` (add `onRetry` callback + Retry control in the failed-message branch; update failed preview)
  - `app/src/main/java/timur/gilfanov/messenger/ui/screen/chat/ChatScreen.kt` (thread `onRetryMessage: (MessageId) -> Unit` through ChatScreen → ChatScreenContent → ChatContent → MessageList; bind `message.id` at MessageList; no-op for static/preview paths)
  - `app/src/main/res/values/strings.xml` (retry label + accessibility content description)
  - `app/src/test/java/timur/gilfanov/messenger/ui/screen/chat/MessageBubbleComponentTest.kt` (retry availability, callback dispatch, accessibility, negative cases)
  - `app/src/test/java/timur/gilfanov/messenger/ui/screen/chat/ChatScreenComponentTest.kt` (screen-level wiring of retry to the callback)
- Key facts:
  - `MessageBubble` receives a `MessageUiModel` (string id); `MessageList` has the domain `Message` with the real `MessageId`. Bind the callback at `MessageList` via `onRetryMessage(message.id)`; `MessageBubble` only needs a parameterless `onRetry: () -> Unit`.
  - `ChatViewModel.retryMessage` already guards on Failed status + current-user sender; the UI must still only *show* retry for failed current-user messages (per acceptance criteria), gated on `deliveryStatus is DeliveryStatus.Failed && isFromCurrentUser`.
  - Existing paging retry uses a Material3 `Button` with `R.string.chat_paging_retry` and testTag `paging_retry_button` — mirror that idiom for the message retry control.
- Related patterns: MVI property-drilling of callbacks (existing `onSendMessage`, `onMarkMessagesAsReadUpTo`); stateless `ChatScreenContent` tested directly (no real ViewModel in component tests). Component tests use Robolectric `@Category(Component::class)` with `createComposeRule`.
- Dependencies: none new.

## Development Approach

- **Testing approach**: Regular (code first, then tests), matching the existing component-test style (Robolectric `@Category(Component::class)`, `createComposeRule`).
- Use `assertTrue` from `kotlin.test` for boolean assertions.
- Reuse constant ids/timestamps in tests for reproducibility.
- Do not generate inline/code comments; add KDoc only where a contract is non-obvious.
- Complete each task fully before moving to the next.
- **CRITICAL: every task MUST include new/updated tests.**
- **CRITICAL: all tests must pass before starting the next task.**

## Implementation Steps

### Task 1: Add retry control to MessageBubble

**Files:**
- Modify: `app/src/main/java/timur/gilfanov/messenger/ui/screen/chat/MessageBubble.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/test/java/timur/gilfanov/messenger/ui/screen/chat/MessageBubbleComponentTest.kt`

- [x] Add string resources: `chat_message_retry` ("Retry") and `chat_message_retry_content_description` ("Retry sending message").
- [x] Add `onRetry: () -> Unit = {}` parameter to `MessageBubble`.
- [x] In the `DeliveryStatus.Failed` branch, render a discoverable Retry action (Material3 `Button` mirroring the paging-retry idiom) only when `message.isFromCurrentUser`; give it `testTag("message_retry_button")` and a `contentDescription` from the new string for accessibility; wire its `onClick` to `onRetry`.
- [x] Confirm incoming failed messages and non-failed messages render no retry control.
- [x] Update `MessageBubbleFailedPreview` to pass an `onRetry` lambda so the preview displays the retry UI.
- [x] Add MessageBubble component tests: retry visible+enabled for failed current-user message; retry absent for failed incoming message; retry absent for non-failed current-user message; clicking retry invokes `onRetry`; retry control exposes the accessibility content description.
- [x] Run `./gradlew :app:testMockDebugUnitTest --tests "timur.gilfanov.messenger.ui.screen.chat.MessageBubbleComponentTest"` — must pass before Task 2.

### Task 2: Thread onRetryMessage through ChatScreen and bind to the ViewModel

**Files:**
- Modify: `app/src/main/java/timur/gilfanov/messenger/ui/screen/chat/ChatScreen.kt`
- Modify: `app/src/test/java/timur/gilfanov/messenger/ui/screen/chat/ChatScreenComponentTest.kt`

- [x] Add `onRetryMessage: (MessageId) -> Unit` parameter to `ChatScreenContent`, `ChatContent`, and `MessageList`; default to `{}` where it eases previews/tests.
- [x] In `MessageList`, pass `onRetry = { onRetryMessage(message.id) }` to each `MessageBubble`, using the domain `Message.id` (real `MessageId`), not the string UI-model id.
- [x] In `ChatScreen`, bind `onRetryMessage = viewModel::retryMessage`.
- [x] Leave static/preview paths with a no-op retry (previews don't run the ViewModel).
- [x] Do not alter composer/input handling on retry (no `ClearInputText`/input-reset effect).
- [x] Add a screen-level component test on `ChatScreenContent`: render a Ready state containing a failed outgoing message, activate the retry control, and assert the `onRetryMessage` callback receives that message's `MessageId` — verifying the UI is wired to the action.
- [x] Run `./gradlew :app:testMockDebugUnitTest --tests "timur.gilfanov.messenger.ui.screen.chat.ChatScreenComponentTest"` — must pass before Task 3.

### Task 3: Verify acceptance criteria

- [x] Run `./gradlew ktlintFormat detekt --auto-correct`.
- [x] Run `./gradlew :app:testMockDebugUnitTest` for the full app unit-test suite.
- [x] Run `./gradlew :app:compileMockDebugAndroidTestKotlin` (signature changes to `ChatScreenContent` may affect androidTest sources).
- [x] Verify coverage for changed UI code meets 80%+. (MessageBubble.kt: 100% line / 98% instruction; ChatScreen.kt changed retry-threading lines all covered — including the `onRetry = { onRetryMessage(message.id) }` lambda. The file's overall 74.5% reflects pre-existing untested code, not the changed lines.)

### Task 4: Update documentation

- [x] Update README.md only if user-facing behavior documentation requires it (no change needed; README documents no per-feature UI behavior).
- [x] No CLAUDE.md change expected (no new internal pattern); update only if one emerges (none emerged).
- [x] Move this plan to `docs/plans/completed/`.
