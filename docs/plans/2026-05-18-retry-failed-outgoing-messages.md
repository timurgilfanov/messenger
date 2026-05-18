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
- Dependencies identified: none new. `SendMessageUseCase`, `MessageRepository`, and the
  `Message` entity already support retry unchanged.

## Development Approach

- **Testing approach**: Regular (code first, then tests).
- Complete each task fully (its tests passing) before the next.
- Make small, focused changes; no refactor of the existing send path.
- **Every task includes its tests**; all tests must pass before the next task.
- No KDoc except where the contract is non-obvious — add a short KDoc on `retryMessage`
  documenting the no-op contract.
- After editing any source file: `./gradlew ktlintFormat detekt --auto-correct`.
- Do not modify static-analysis config or add `@Suppress`.

## Testing Strategy

- **Unit tests**: required (Task 3) — retry success, retry local failure (dialog), retry
  remote failure (silent), no-op for unknown/non-failed id.
- **E2E tests**: none — this is a ViewModel-only change; UI wiring of a retry tap is tracked
  separately under #213.

## Progress Tracking

- Mark completed items `[x]` immediately when done.
- `➕` prefix for newly discovered tasks; `⚠️` for blockers.
- Keep this file in sync with actual work.

## Implementation Steps

### Task 1: Add `retryMessage` + `handleRetryFailure` to ChatViewModel

- [x] Added public `fun retryMessage(messageId, now)`: single guard
  `currentChat.takeIf { _state.value is Ready } ?: return`, then
  `messages.filterIsInstance<TextMessage>().firstOrNull { id match && deliveryStatus is Failed } ?: return`,
  `failed.copy(deliveryStatus = null)`, launch `sendMessageUseCase(...).collect { Success -> Unit; Failure -> ... }`.
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

- [x] `retry re-sends same id and text with cleared status and no dialog` — retry flow
  `Success(Sending(0)), Success(Sent)`; asserts `sentMessages.single()` has id ==
  FAILED_MESSAGE_ID, text equal, `deliveryStatus == null`, final `Ready`,
  `dialogError == null`, no side effects from retry.
- [x] `retry local failure shows dialog and message stays retryable` — flow ends
  `Failure(LocalOperationFailed(TemporarilyUnavailable))`; asserts `dialogError` wraps
  `SendMessageError.LocalOperationFailed`; second `retryMessage` → `sentMessages.size == 2`.
- [x] `retry remote failure stays silent and message stays retryable` — flow ends
  `Failure(RemoteOperationFailed(NetworkNotAvailable))`; asserts `dialogError == null`;
  second `retryMessage` grows `sentMessages` to 2.
- [x] `retry is a no-op for unknown message id` — no-failed-match → no send, no dialog.
- [x] ➕ `retry is a no-op for a non-failed message` — message present but `Sent` → no send.
- [x] `./gradlew :app:testMockDebugUnitTest --tests "...ChatViewModelRetryTest"` — 5/5 pass
- [x] `./gradlew ktlintFormat detekt --auto-correct` — green

### Task 4: Verify acceptance criteria & regressions

- [x] Re-checked issue #377 ACs — all satisfied (action exists; reuses id; reuses text;
  success timeline-driven, input not cleared; failure keeps message Failed & retryable;
  tests cover success & failure).
- [x] `./gradlew :app:testMockDebugUnitTest --tests "timur.gilfanov.messenger.ui.screen.chat.*"` — pass, no regressions
- [x] `./gradlew ktlintFormat detekt --auto-correct` — clean
- [x] `./gradlew :app:compileMockDebugAndroidTestKotlin` — green

## Technical Details

- `retryMessage` reuses `SendMessageUseCase` unchanged: rules re-validated with `now`,
  `validate()` still passes (same text), `deliveryStatus == null` clears the
  `DeliveryStatusAlreadySet` guard.
- Original `createdAt` preserved via `copy` so the retried message keeps its timeline
  position; `now` is used only for rule evaluation (matches `sendMessage`).
- Success branch is intentionally a no-op: the timeline is the source of truth and
  `currentChat` is refreshed by `receiveChatUpdatesUseCase`; the bubble transitions
  Sending → Sent without ViewModel state mutation.
- `handleRetryFailure` treats the message as already accepted (it is in the timeline), so
  it applies only the post-acceptance dialog rule (`!RemoteOperationFailed → dialog`) and
  never resets `isSending`.

## Post-Completion

**Manual verification** (optional, on-device):
- Force-fail a send, confirm the failed bubble re-enters Sending then Sent on retry, input
  is untouched, a logical failure shows the existing error dialog while a network failure
  stays silent but remains retryable.

**External system updates**: none. UI wiring of a retry tap on the failed bubble is tracked
separately under #213.
