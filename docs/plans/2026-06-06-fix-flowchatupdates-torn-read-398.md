# Fix torn-read race in flowChatUpdates with verify-path discriminator (#398)

## Overview

`LocalChatDataSourceImpl.flowChatUpdates` joins two independently-invalidated Room flows: the outer `flowChatWithParticipants` (a captured participant snapshot) and the inner `flowLastMessageInChat`. After a sync transaction that atomically adds a participant and a message from that participant, the inner message flow can re-emit a fresh last-message against a still-stale captured participant set. `EntityMappers.toMessage` then throws "Sender not found", and the `.catch` only handles `SQLiteException`, so it rethrows and permanently terminates the per-chat updates flow.

The fix mirrors the approach accepted in PR #399 for the chat-list flow: a last message whose sender is absent from the captured participant set has two indistinguishable causes at flow level — a transient torn read (the DB is actually consistent) or a real source-of-truth orphan (the DB genuinely holds the orphan). A blanket skip would silence both, and a real orphan would silently blank the chat forever. The discriminator is a single committed snapshot:

- **Fast path**: when the captured participant snapshot already contains the last message's sender, map directly (no extra DB read).
- **Verify path**: when an apparent orphan is seen, re-read participants + last message inside one `withTransaction`. If the orphan is gone it was a transient torn read → emit `Success`; if it persists it is a real inconsistency → emit a handled `Failure(InvalidData)`, keeping the flow alive and the error visible.

This is consistent with the persistence-boundary fail-fast contract (CLAUDE.md): that contract governs persisted data integrity, checked here against a consistent committed snapshot rather than a torn cross-flow join.

## Context

- Files involved:
  - Modify: `app/src/main/java/timur/gilfanov/messenger/data/source/local/LocalChatDataSourceImpl.kt` (flowChatUpdates inner map, lines 167-176; add two private helpers)
  - Modify: `app/src/test/java/timur/gilfanov/messenger/data/source/local/LocalChatDataSourceImplTest.kt` (add regression tests)
  - Reference only (no change needed):
    - `app/src/main/java/timur/gilfanov/messenger/data/source/local/database/mapper/EntityMappers.kt` (toChat / toMessage; toMessage "Sender not found" error)
    - `app/src/main/java/timur/gilfanov/messenger/data/source/local/database/entity/ChatWithParticipants.kt`, `entity/MessageEntity.kt`
    - `app/src/main/java/timur/gilfanov/messenger/data/source/local/database/dao/ChatDao.kt` (`suspend getChatWithParticipants` exists), `dao/MessageDao.kt` (`suspend getLastMessageInChat` exists) — no new DAO methods required; `database.withTransaction` already imported and used in this file
- Related patterns:
  - PR #399 (chat-list flow): combine + orphan detection + consistent re-read verify-path; `InvalidData("messages", ...)` message format
  - Existing flowChatUpdates tests use Robolectric `AndroidJUnit4` + `InMemoryDatabaseRule` + Turbine; `createTestChat` / `DomainTestFixtures` helpers; `@Category(Component::class)`
  - This change is independent of whether PR #399 merges first: it adds single-chat helpers and touches only `flowChatUpdates`; no overlap with #399's list-flow changes
- Dependencies: none new

## Development Approach

- **Testing approach**: Regular (apply fix, then add regression tests).
- Per the resolved scope decision (mirror #399 exactly): the verify path re-reads the same single in-memory Room DB, so with the real-DB test harness a genuinely consistent DB always takes the fast path (no orphan ever observed) and the only way to reach the verify path is a real persisting orphan, which escalates. The transient-recovery branch (message from a new participant observed before the participant snapshot catches up, then recovering to `Success`) is therefore NOT deterministically forceable through the public flow with a single real DB. This is documented as a known limitation in a KDoc/comment note on the verify helper, exactly as #399 accepted — no extra test seam or delegating fake is added.
- Tests cover: the escalation path (real persisting orphan → `Failure(InvalidData)`) through the public flow, plus regression guards for unchanged normal / `ChatNotFound` / exactly-one-current-user behavior.
- Complete each task fully before the next; every task includes new/updated tests; all tests must pass before starting the next task.
- **CRITICAL: every task MUST include new/updated tests**
- **CRITICAL: all tests must pass before starting next task**

## Implementation Steps

### Task 1: Add verify-path discriminator to flowChatUpdates

**Files:**
- Modify: `app/src/main/java/timur/gilfanov/messenger/data/source/local/LocalChatDataSourceImpl.kt`
- Modify: `app/src/test/java/timur/gilfanov/messenger/data/source/local/LocalChatDataSourceImplTest.kt`

- [x] Add a private helper `findOrphanLastMessage(chatWithParticipants: ChatWithParticipants, lastMessage: MessageEntity?): MessageEntity?` returning the last message when it is non-null and its `senderId` matches none of `chatWithParticipants.participants`, else null
- [x] Add a private suspend helper `readChatUpdateConsistently(chatId: String): ResultWithError<Chat, LocalDataSourceError>` wrapping `database.withTransaction`: re-read `chatDao.getChatWithParticipants(chatId)` (`Failure(ChatNotFound)` if null) and `messageDao.getLastMessageInChat(chatId)`; if `findOrphanLastMessage` still returns non-null emit `Failure(InvalidData("messages", "Sender <senderId> of message <id> is not a participant of chat <chatId>"))`; else build via `EntityMappers.toChat(listOfNotNull(lastMessage))` and emit `Success`
- [x] Change the inner `messageDao.flowLastMessageInChat(...).distinctUntilChanged().map { ... }`: when `findOrphanLastMessage(chatWithParticipants, lastMessageEntity)` is non-null call `readChatUpdateConsistently(chatId)` (verify path), otherwise map directly via `EntityMappers.toChat(listOfNotNull(lastMessageEntity))` as `Success` (fast path); keep the existing outer null-chat and exactly-one-current-user checks unchanged
- [x] Add a KDoc note on `flowChatUpdates` (and/or the verify helper) explaining the fast-path/verify-path discriminator, why escalation (not silent skip) is correct for a persisting orphan, and that the transient-recovery branch is not unit-forceable with a single real DB
- [x] Add imports for `ChatWithParticipants` and `MessageEntity` if missing
- [x] Write regression tests in `LocalChatDataSourceImplTest`:
  - `flowChatUpdates` escalates to `Failure(InvalidData)` when the last message sender is genuinely not a participant of the chat (insert orphan message via `databaseRule.messageDao`; sender exists globally as a member of another chat so it is a real orphan, not a missing-row case); assert the flow stays alive and emits `Failure` with `LocalDataSourceError.InvalidData`
  - `flowChatUpdates` still emits a consistent `Success` snapshot for a normal chat whose last message sender is a participant (fast-path regression guard)
- [x] Run `./gradlew :app:testMockDebugUnitTest --tests "timur.gilfanov.messenger.data.source.local.LocalChatDataSourceImplTest"` — must pass before Task 2

### Task 2: Verify acceptance criteria

- [ ] run `./gradlew ktlintFormat detekt --auto-correct`
- [ ] run `./gradlew :app:testMockDebugUnitTest --tests "*LocalChatDataSourceImplTest"`
- [ ] verify the touched flow keeps 80%+ coverage (fast path, verify-path escalation, `ChatNotFound`, exactly-one-current-user branches all exercised)

### Task 3: Update documentation

- [ ] no README/CLAUDE.md change expected (behavior fix following the existing #399 verify-path pattern, no new public API); revisit only if the discriminator warrants a shared note
- [ ] move this plan to `docs/plans/completed/`
