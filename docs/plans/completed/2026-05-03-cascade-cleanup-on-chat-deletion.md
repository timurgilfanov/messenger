# Cascade cleanup on chat deletion (issue #54)

## Overview

Issue #54 asks for proper cascade cleanup when a chat is deleted locally. Investigation shows Room already cascades messages (`MessageEntity.chatId → chats.id`) and junction rows (`ChatParticipantCrossRef.chatId → chats.id`) via existing `ForeignKey(onDelete = CASCADE)` declarations. The real gap is:

1. Orphaned global participants in the `participants` table are never cleaned up — `Participant` has no FK back to chats, so participants linger forever once their last chat is removed.
2. There are no tests proving the existing cascade behavior actually works (no regression coverage).
3. Both delete paths share the gap: `LocalSyncDataSourceImpl.applyChatDeletedDelta` (server-driven delete via sync) and `LocalChatDataSourceImpl.deleteChat` (locally-initiated delete via `DeleteChatUseCase`).

Per user decision, fix both paths consistently so cleanup behaves the same regardless of which path triggers the delete.

## Context

- Files involved:
  - `app/src/main/java/timur/gilfanov/messenger/data/source/local/database/dao/ParticipantDao.kt`
  - `app/src/main/java/timur/gilfanov/messenger/data/source/local/LocalSyncDataSourceImpl.kt`
  - `app/src/main/java/timur/gilfanov/messenger/data/source/local/LocalChatDataSourceImpl.kt`
  - `app/src/test/java/timur/gilfanov/messenger/data/source/local/database/dao/ParticipantDaoTest.kt`
  - `app/src/test/java/timur/gilfanov/messenger/data/source/local/LocalSyncDataSourceImplTest.kt`
  - `app/src/test/java/timur/gilfanov/messenger/data/source/local/LocalChatDataSourceImplTest.kt`
- Related patterns:
  - Room FK CASCADE already declared on `MessageEntity` and `ChatParticipantCrossRef` — leave those entity FK declarations alone; the orphan cleanup is a query, not a schema change, so no Room migration is needed.
  - All multi-statement DB writes wrap in `database.withTransaction { … }` (existing pattern in both data sources).
  - Tests use `InMemoryDatabaseRule` (Robolectric, `Room.inMemoryDatabaseBuilder` — FKs are enabled by default).
- Dependencies: none new.

## Development Approach

- Testing approach: Regular (code first, then tests) — narrow well-bounded change with existing test infrastructure.
- Complete each task fully before moving to the next.
- Cleanup must run inside the same transaction as the chat delete so the operation is atomic.
- CRITICAL: every task MUST include new/updated tests.
- CRITICAL: all tests must pass before starting the next task.

## Implementation Steps

### Task 1: Add orphan-cleanup query to ParticipantDao

Add a single query that deletes participants no longer referenced by any row in `chat_participants`. This is the building block both delete paths will reuse.

Files:
- Modify: `app/src/main/java/timur/gilfanov/messenger/data/source/local/database/dao/ParticipantDao.kt`
- Modify: `app/src/test/java/timur/gilfanov/messenger/data/source/local/database/dao/ParticipantDaoTest.kt`

- [x] Add `@Query("DELETE FROM participants WHERE id NOT IN (SELECT participantId FROM chat_participants)") suspend fun deleteOrphanedParticipants(): Int` to `ParticipantDao`. Returning the deleted-row count keeps the query observable in tests and aids future logging/metrics.
- [x] Add tests in `ParticipantDaoTest` covering: (a) returns 0 and deletes nothing when every participant is still referenced, (b) deletes only the participants whose only chat reference was removed, (c) leaves participants who remain referenced by at least one other chat, (d) returns 0 when the participants table is empty.
- [x] Run `./gradlew :app:testMockDebugUnitTest --tests "timur.gilfanov.messenger.data.source.local.database.dao.ParticipantDaoTest"` — must pass before Task 2.

### Task 2: Cleanup orphaned participants in applyChatDeletedDelta

Files:
- Modify: `app/src/main/java/timur/gilfanov/messenger/data/source/local/LocalSyncDataSourceImpl.kt`
- Modify: `app/src/test/java/timur/gilfanov/messenger/data/source/local/LocalSyncDataSourceImplTest.kt`

- [x] In `applyChatDeletedDelta` (around line 190), after the existing `chatDao.deleteChat(it)` call, invoke `participantDao.deleteOrphanedParticipants()`. The whole operation already runs inside `database.withTransaction { … }` via `applyChatDelta`, so atomicity is preserved.
- [x] Add tests to `LocalSyncDataSourceImplTest`:
  - cascade verification: after applying `ChatDeletedDelta`, the chat's messages and `chat_participants` junction rows are gone (proves the existing FK cascade is wired and continues to work).
  - orphan cleanup: a participant who only belonged to the deleted chat is removed from the `participants` table.
  - shared participant preservation: a participant who is also a member of another chat is preserved.
- [x] Run `./gradlew :app:testMockDebugUnitTest --tests "timur.gilfanov.messenger.data.source.local.LocalSyncDataSourceImplTest"` — must pass before Task 3.

### Task 3: Cleanup orphaned participants in LocalChatDataSourceImpl.deleteChat

Files:
- Modify: `app/src/main/java/timur/gilfanov/messenger/data/source/local/LocalChatDataSourceImpl.kt`
- Modify: `app/src/test/java/timur/gilfanov/messenger/data/source/local/LocalChatDataSourceImplTest.kt`

- [x] In `deleteChat` (around line 96), after `chatDao.deleteChat(chatEntity)` and inside the existing `database.withTransaction { … }`, call `participantDao.deleteOrphanedParticipants()`. Keep the `ChatNotFound` branch unchanged.
- [x] Update / add tests in `LocalChatDataSourceImplTest`:
  - cascade verification: after `deleteChat`, the chat's messages and junction rows are removed.
  - orphan cleanup: participants whose only chat was deleted are removed from `participants`.
  - shared participant preservation: participants who remain in another chat stay in `participants`.
  - the `ChatNotFound` test still passes and no participants are touched on that path.
- [x] Run `./gradlew :app:testMockDebugUnitTest --tests "timur.gilfanov.messenger.data.source.local.LocalChatDataSourceImplTest"` — must pass before Task 4.

### Task 4: Verify acceptance criteria

- [x] Run `./gradlew ktlintFormat detekt --auto-correct`.
- [x] Run `./gradlew :app:testMockDebugUnitTest` for the full app unit-test suite to catch any unexpected regression in repository or sync tests that exercise delete flows.
- [x] Run `./gradlew :app:compileMockDebugAndroidTestKotlin` (signature change on `ParticipantDao` adds a new method — all `:app` androidTest sources compile against it).
- [x] Verify test coverage for the touched files meets the 80% target (`./gradlew :app:koverHtmlReportMockDebug` or the project's standard coverage task).

### Task 5: Update documentation

- [x] No README/CLAUDE.md changes expected (internal data-source behavior, no new pattern). Confirm no doc updates are required and skip if so.
- [x] Move this plan to `docs/plans/completed/`.
