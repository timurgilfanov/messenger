# Avoid loading all messages in flowChatList preview query (#396)

## Overview
`LocalChatDataSourceImpl.flowChatList()` is backed by `ChatDao.flowAllChatsWithParticipantsAndMessages()`, whose `@Relation` eagerly loads every `MessageEntity` of every chat just so `toChatPreview()` can derive the single latest message. This rewires the preview flow to load only chat + participants plus the one most-recent message per chat, mirroring the bounded pattern already used by `flowChatUpdates()`. Then it removes the now-dead `ChatWithParticipantsAndMessages` entity and its DAO methods (full cleanup, per prior Q&A). No externally observable behavior changes.

## Context
- Files involved:
  - `app/src/main/java/timur/gilfanov/messenger/data/source/local/LocalChatDataSourceImpl.kt` — `flowChatList()`
  - `app/src/main/java/timur/gilfanov/messenger/data/source/local/database/dao/ChatDao.kt` — add `flowAllChatsWithParticipants`; remove the three `…AndMessages` methods
  - `app/src/main/java/timur/gilfanov/messenger/data/source/local/database/dao/MessageDao.kt` — add `flowLastMessagesPerChat`
  - `app/src/main/java/timur/gilfanov/messenger/data/source/local/database/mapper/EntityMappers.kt` — `toChatPreview()`
  - `app/src/main/java/timur/gilfanov/messenger/data/source/local/database/entity/ChatWithParticipantsAndMessages.kt` — delete
  - `app/src/main/java/timur/gilfanov/messenger/data/source/local/database/entity/ChatWithParticipants.kt` — reference for `toChat(lastMessages)` pattern
  - Tests:
    - `app/src/test/java/timur/gilfanov/messenger/data/source/local/LocalChatDataSourceImplTest.kt`
    - `app/src/test/java/timur/gilfanov/messenger/data/source/local/database/mapper/EntityMappersTest.kt`
    - `app/src/test/java/timur/gilfanov/messenger/data/source/local/database/dao/ChatDaoTest.kt`
    - `app/src/test/java/timur/gilfanov/messenger/data/source/local/database/dao/ParticipantDaoTest.kt`
    - `app/src/test/java/timur/gilfanov/messenger/data/source/local/database/dao/MessageDaoTest.kt`
- Related patterns: `flowChatUpdates()` + `MessageDao.flowLastMessageInChat` (createdAt DESC, id DESC LIMIT 1); existing `ChatWithParticipants` entity and `ChatWithParticipants.toChat(lastMessages)` mapper.
- Note: `flowChatList` currently adds no current-user participant guard; per the persistence-boundary contract (CLAUDE.md Backend Chat Contract) none will be added (behavior preserved).
- Dependencies: none (internal data-layer refactor; no new external dependencies).

## Development Approach
- **Testing approach**: Regular (code first, then tests), matching the existing data-layer test style.
- Complete each task fully before moving to the next.
- Use Fakes/in-memory Room over mocks; use constant time and IDs for reproducibility.
- **CRITICAL: every task MUST include new/updated tests.**
- **CRITICAL: all tests must pass before starting next task.**

## Implementation Steps

### Task 1: Add efficient DAO queries

**Files:**
- Modify: `app/src/main/java/timur/gilfanov/messenger/data/source/local/database/dao/ChatDao.kt`
- Modify: `app/src/main/java/timur/gilfanov/messenger/data/source/local/database/dao/MessageDao.kt`
- Modify: `app/src/test/java/timur/gilfanov/messenger/data/source/local/database/dao/ChatDaoTest.kt`
- Modify: `app/src/test/java/timur/gilfanov/messenger/data/source/local/database/dao/MessageDaoTest.kt`

- [x] Add `ChatDao.flowAllChatsWithParticipants(): Flow<List<ChatWithParticipants>>` (`@Transaction`, `SELECT * FROM chats ORDER BY updatedAt DESC`), mirroring the existing all-chats ordering
- [x] Add `MessageDao.flowLastMessagesPerChat(): Flow<List<MessageEntity>>` using a correlated subquery returning one row per chat with the same ordering as `flowLastMessageInChat` (createdAt DESC, id DESC)
- [x] Add ChatDaoTest case verifying participants load without messages
- [x] Add MessageDaoTest case verifying exactly one latest message per chat (including tie-break by id and empty-chat exclusion)
- [x] Run `./gradlew :app:testMockDebugUnitTest --tests "*ChatDaoTest" --tests "*MessageDaoTest"` — must pass before Task 2

### Task 2: Rewire `toChatPreview` mapper

**Files:**
- Modify: `app/src/main/java/timur/gilfanov/messenger/data/source/local/database/mapper/EntityMappers.kt`
- Modify: `app/src/test/java/timur/gilfanov/messenger/data/source/local/database/mapper/EntityMappersTest.kt`

- [x] Change extension to `ChatWithParticipants.toChatPreview(lastMessage: MessageEntity?)`, mirroring `toChat(lastMessages)`; drop the `messages.maxByOrNull` logic and use the passed `lastMessage`
- [x] Update EntityMappersTest: rewrite the two `ChatWithParticipantsAndMessages` cases to build `ChatWithParticipants` and pass a `lastMessage`, plus a null-`lastMessage` case
- [x] Run `./gradlew :app:testMockDebugUnitTest --tests "*EntityMappersTest"` — must pass before Task 3

### Task 3: Rewire `flowChatList` in LocalChatDataSourceImpl

**Files:**
- Modify: `app/src/main/java/timur/gilfanov/messenger/data/source/local/LocalChatDataSourceImpl.kt`
- Modify: `app/src/test/java/timur/gilfanov/messenger/data/source/local/LocalChatDataSourceImplTest.kt`

- [x] Replace the `flowAllChatsWithParticipantsAndMessages` source with `combine(flowAllChatsWithParticipants(), flowLastMessagesPerChat())`; build a `chatId → lastMessage` map and map each chat via the new `toChatPreview(lastMessage)`; keep `distinctUntilChanged()` and the existing `SQLiteException` `catch`
- [x] Extend LocalChatDataSourceImplTest: keep the existing previews test and add one asserting `lastMessage`/`lastActivityAt` reflect the most-recent message and are null for an empty chat
- [x] Run `./gradlew :app:testMockDebugUnitTest --tests "*LocalChatDataSourceImplTest"` — must pass before Task 4

### Task 4: Delete dead code and repoint orphaned tests

**Files:**
- Delete: `app/src/main/java/timur/gilfanov/messenger/data/source/local/database/entity/ChatWithParticipantsAndMessages.kt`
- Modify: `app/src/main/java/timur/gilfanov/messenger/data/source/local/database/dao/ChatDao.kt`
- Modify: `app/src/test/java/timur/gilfanov/messenger/data/source/local/database/dao/ChatDaoTest.kt`
- Modify: `app/src/test/java/timur/gilfanov/messenger/data/source/local/database/dao/ParticipantDaoTest.kt`

- [x] Delete `ChatWithParticipantsAndMessages.kt`
- [x] Remove `ChatDao.flowAllChatsWithParticipantsAndMessages`, `getChatWithParticipantsAndMessages`, `flowChatWithParticipantsAndMessages` and the now-unused import
- [x] Repoint orphaned tests: ChatDaoTest `flow chat with participants and messages emits updates` → `flowChatWithParticipants`; ChatDaoTest `chat with participants and messages returns complete data` → `getChatWithParticipants` for participants plus `MessageDao.getLastMessageInChat` for the message assertion; ParticipantDaoTest two `getChatWithParticipantsAndMessages` call sites → `getChatWithParticipants`
- [x] Run `./gradlew :app:testMockDebugUnitTest --tests "*ChatDaoTest" --tests "*ParticipantDaoTest"` — must pass before Task 5

### Task 5: Verify acceptance criteria

- [x] Run `./gradlew ktlintFormat detekt --auto-correct`
- [x] Run `./gradlew :app:testMockDebugUnitTest`
- [x] Verify no remaining references to `ChatWithParticipantsAndMessages` or the removed DAO methods (grep)
- [x] Verify touched-code coverage meets 80%+ (LocalChatDataSourceImpl 81%, EntityMappers 86%, ChatDao_Impl 92%, MessageDao_Impl 87%)

### Task 6: Update documentation

- [ ] No README.md changes expected (no user-facing changes)
- [ ] No CLAUDE.md changes expected (internal data-layer refactor; no new patterns)
- [ ] Move this plan to `docs/plans/completed/`
