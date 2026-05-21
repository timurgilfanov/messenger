# Retry failed outgoing messages (issue #377)

## Overview

- Add a `ChatViewModel.retryMessage(messageId)` action that re-sends a failed outgoing
  message that is already in the chat timeline.
- Solves issue #377 ("ChatViewModel retries failed outgoing messages", part of #213,
  effort:S): today `ChatViewModel` only has `sendMessage()`, which builds a brand-new
  message from composer input — there is no way to re-send a message that failed.
- A message that reached an accepted delivery status is added to the timeline; if its send
  later fails it is persisted as `DeliveryStatus.Failed` and stays visible (commit
  `cf351c8b` made the message timeline the source of truth for send status). Retry finds
  that message, re-sends it with the **same id and text** (delivery status reset to `null`
  so `SendMessageUseCase` accepts it — it rejects non-null status with
  `DeliveryStatusAlreadySet`), and lets the timeline drive the bubble back through
  Sending → Sent/Failed.

## Context (from discovery)

- Files/components involved:
  - `app/src/main/java/timur/gilfanov/messenger/ui/screen/chat/ChatViewModel.kt`
    (`sendMessage`, `handleSendSuccess`, `handleSendFailure`, `isAcceptedStatus`)
  - `app/src/main/java/timur/gilfanov/messenger/ui/screen/chat/ChatUiState.kt`
    (`ChatUiState.Ready`, `ReadyError.SendMessageError`)
  - `core/domain/.../usecase/message/SendMessageUseCase.kt` (rejects non-null
    `deliveryStatus`; emits `Success` progress / terminal `Failure`)
  - `core/domain/.../entity/message/Message.kt`, `DeliveryStatus.kt`, `TextMessage`
  - `app/src/test/.../ui/screen/chat/ChatViewModelTestFixtures.kt`,
    `ChatViewModelMessageSendingTest.kt`
- Related patterns found: MVI (`StateFlow` + `Channel` effects), `ResultWithError`,
  fixed-UUID/Instant test constants, Turbine `test {}`, `MainDispatcherRule`,
  `@Category(Component::class)`.
- Dependencies identified: none new. `MessageRepository` and the `Message` entity support
  retry unchanged. `SendMessageUseCase` required one minimal change: its debounce baseline
  must exclude the message being (re)sent (a first send is unaffected since the id is not
  yet in the timeline) — see Task 1 / Technical Details.

## Development Approach

- **Testing approach**: Regular (code first, then tests).
- Complete each task fully (its tests passing) before the next.
- Make small, focused changes. The only send-path change is a minimal, in-scope domain
  fix: `SendMessageUseCase` now excludes the (re)sent message from its own debounce
  baseline (see Task 1 / Technical Details). No other refactor of the send path.
- **Every task includes its tests**; all tests must pass before the next task.
- No KDoc except where the contract is non-obvious — add a short KDoc on `retryMessage`
  documenting the no-op contract.
- After editing any source file: `./gradlew ktlintFormat detekt --auto-correct`.
- Do not modify static-analysis config or add `@Suppress`.

## Testing Strategy

- **Unit tests**: required (Task 3) — retry success (timeline reaches Sent), retry local
  failure (dialog), retry remote failure (silent), debounce-baseline regression,
  same-id/distinct-id concurrent retry, no-op for unknown/non-failed/in-flight id and
  not-ready chat, plus a domain-layer debounce self-exclusion test.
- **E2E tests**: none — this is a ViewModel-only change; UI wiring of a retry tap is tracked
  separately under #213.

## Progress Tracking

- Mark completed items `[x]` immediately when done.
- `➕` prefix for newly discovered tasks; `⚠️` for blockers.
- Keep this file in sync with actual work.

## Implementation Steps

### Task 1: Add `retryMessage` + `handleRetryFailure` to ChatViewModel

- [x] Added public `fun retryMessage(messageId, now)`: guard
  `currentChat.takeIf { _state.value is Ready } ?: return`, then
  `messages.filterIsInstance<TextMessage>().firstOrNull { id match && deliveryStatus is Failed }`,
  `failed.copy(deliveryStatus = null)`, launch `sendMessageUseCase(...).collect { Success -> Unit; Failure -> ... }`.
- [x] ➕ Added a per-ViewModel `retryingMessageIds: MutableSet<MessageId>` in-flight guard:
  the id is added before `launch` (`if (failed == null || !retryingMessageIds.add(messageId)) return`)
  and removed in a `finally`, so a double-invoke cannot launch concurrent sends for the same
  message; distinct messages may still retry concurrently. Relies on ViewModel actions being
  main-thread-confined (no extra synchronization).
- [x] ➕ Minimal in-scope domain change: `SendMessageUseCase.checkDebounceRule` now excludes
  the message being (re)sent from the debounce baseline (`Chat.lastMessageBy` takes
  `excluding: MessageId`). Without it a retry under an active `Debounce` rule spuriously
  fails with `WaitDebounce` against its own failed timeline entry. A first send is
  unaffected (id not yet in the timeline).
- [x] Reused existing `handleSendFailure(error, SendProgress(acceptedLocally = true))` for the
  failure branch instead of a new `handleRetryFailure` — identical policy (dialog unless
  `RemoteOperationFailed`, `isSending` left unchanged) and avoids the `TooManyFunctions`
  detekt limit. ➕ Inlined the single-use private `textMessage` helper into `sendMessage` to
  offset the new function (detekt `thresholdInClasses` = 11, fails at 11).
- [x] Added a KDoc on `retryMessage` documenting the no-op contract.
- [x] `./gradlew ktlintFormat detekt --auto-correct` — green
- [x] `./gradlew :app:compileMockDebugAndroidTestKotlin` — green

### Task 2: Add `MessengerRepositoryFakeWithTimeline` test fixture

- [x] Added `MessengerRepositoryFakeWithTimeline(chat, perCallSendFlows)` to
  `ChatViewModelTestFixtures.kt`: `chatFlow` MutableStateFlow, `sentMessages`, per-call
  index; `sendMessage` records the message, selects `perCallSendFlows[index++]` (default
  single `Success(message Sending(0))`), `.onEach { delay(10); Success -> upsert(data);
  Failure -> markFailed(message.id) }`; `upsert` replaces-by-id or adds; `markFailed` sets
  `DeliveryStatus.Failed(DeliveryError.NetworkUnavailable)`; `receiveChatUpdates =
  chatFlow.map { Success(it) }`; `getPagedMessages = chatFlow.map { PagingData.from(...) }`;
  other members `error("Not implemented")`, `markMessagesAsRead -> Success(Unit)`,
  `isChatListUpdateApplying -> flowOf(false)`.
- [x] `./gradlew ktlintFormat detekt --auto-correct` — green

### Task 3: Retry success + failure unit tests

Created `ChatViewModelRetryTest.kt`, `@Category(Component::class)`, `MainDispatcherRule`,
fixed constants. ➕ Simpler seed than planned: pre-populate the chat with the failed
message via `createTestChat(...).copy(messages = persistentListOf(failed))` instead of a
seed `sendMessage` call — removes the call-0 flow and debounce-timing dependency. A
`backgroundScope` collector keeps `repeatOnSubscription`/`observeChatUpdates` alive while
asserting on `state.value`.

- [x] `retry re-sends same id and text, clears status, and reaches Sent` — retry flow
  `Success(Sending(0)), Success(Sent)`; asserts the captured sent message has id ==
  FAILED_MESSAGE_ID, text equal, `deliveryStatus == null`, original `createdAt` preserved,
  final `Ready`, `dialogError == null`, `isSending == false`, no side effects, timeline Sent.
- [x] `retry local failure shows dialog and message stays failed and retryable` — flow ends
  `Failure(LocalOperationFailed(TemporarilyUnavailable))`; asserts `dialogError` wraps
  `SendMessageError.LocalOperationFailed`, timeline back to `Failed`; second `retryMessage`
  → `sentMessages.size == 2`. Pins the captured sent message id/status.
- [x] `retry remote failure stays silent and message stays failed and retryable` — flow ends
  `Failure(RemoteOperationFailed(NetworkNotAvailable))`; asserts `dialogError == null`,
  timeline back to `Failed`; second `retryMessage` grows `sentMessages` to 2. Pins the
  captured sent message id/status.
- [x] ➕ `retry succeeds under debounce rule despite its own failed timeline entry` — chat
  has `CreateMessageRule.Debounce(5s)`, retry within the window still sends and reaches
  `Sent` (ViewModel-level regression guard for the `SendMessageUseCase` debounce-baseline
  change). Pins the captured sent message id/status.
- [x] ➕ `concurrent retry of the same message launches only one send` — two `retryMessage`
  calls against a gated send → `sentMessages.size == 1`, stays 1 after the gate drains.
- [x] ➕ `concurrent retry of distinct messages launches a send for each` — two distinct
  failed messages, independently gated → `sentMessages.size == 2` with both ids (verifies
  the in-flight guard is id-scoped, not a single boolean).
- [x] `retry is a no-op for unknown message id` — no-failed-match → no send, no dialog.
- [x] ➕ `retry is a no-op for a non-failed message` — message present but `Sent` → no send.
- [x] ➕ `retry is a no-op for an in-flight Sending message` — message `Sending(0)` → no send.
- [x] ➕ `retry is a no-op when chat is not ready` — `Loading` state → no send.
- [x] ➕ Domain test `debounce excludes the message being re-sent from its own baseline` in
  `CreateMessageUseCaseTest` — pins the `SendMessageUseCase` change at its own layer; the
  existing `test debounce rule failure` (distinct ids) covers the unchanged first-send path.
- [x] `./gradlew :app:testMockDebugUnitTest --tests "...ChatViewModelRetryTest"` — all pass
- [x] `./gradlew ktlintFormat detekt --auto-correct` — green

### Task 4: Verify acceptance criteria & regressions

- [x] Re-checked issue #377 ACs — all satisfied (action exists; reuses id; reuses text;
  success timeline-driven, input not cleared; failure keeps message Failed & retryable;
  tests cover success & failure).
- [x] `./gradlew :app:testMockDebugUnitTest --tests "timur.gilfanov.messenger.ui.screen.chat.*"` — pass, no regressions
- [x] `./gradlew ktlintFormat detekt --auto-correct` — clean
- [x] `./gradlew :app:compileMockDebugAndroidTestKotlin` — green

## Technical Details

- `retryMessage` reuses the `SendMessageUseCase` flow: rules re-validated with `now`,
  `validate()` still passes (same text), `deliveryStatus == null` clears the
  `DeliveryStatusAlreadySet` guard. `SendMessageUseCase.checkDebounceRule` was changed to
  exclude the message being (re)sent from the debounce baseline (`Chat.lastMessageBy` now
  takes `excluding: MessageId`) — without this a retry under an active `Debounce` rule
  spuriously fails with `WaitDebounce` against its own failed timeline entry. A first send
  is unaffected (id not yet in the timeline). `CanNotWriteAfterJoining` needs no analogous
  change: `checkRules` runs before `repository.sendMessage`, so a message can only be in
  the timeline (incl. as `Failed`) if it already passed the join rule, and `now - joinedAt`
  only grows, so a later retry always passes it too.
- Original `createdAt` preserved via `copy` so the retried message keeps its timeline
  position; `now` is used only for rule evaluation (matches `sendMessage`).
- Success branch is intentionally a no-op: the timeline is the source of truth and
  `currentChat` is refreshed by `receiveChatUpdatesUseCase`; the bubble transitions
  Sending → Sent without ViewModel state mutation.
- The failure branch reuses `handleSendFailure(error, SendProgress(acceptedLocally = true))`:
  treating the message as already accepted (it is in the timeline) applies only the
  post-acceptance dialog rule (`!RemoteOperationFailed → dialog`) and never resets
  `isSending`.
- Concurrent-retry safety: `retryMessage` adds `messageId` to a private
  `retryingMessageIds` set before launching and removes it in a `finally`; a second invoke
  for an id already in the set is a no-op. Distinct messages may still retry concurrently.
  ViewModel actions are main-thread-confined so the set needs no extra synchronization.

## Post-Completion

**Manual verification** (optional, on-device):
- Force-fail a send, confirm the failed bubble re-enters Sending then Sent on retry, input
  is untouched, a logical failure shows the existing error dialog while a network failure
  stays silent but remains retryable.

**External system updates**: none. UI wiring of a retry tap on the failed bubble is tracked
separately under #213.
