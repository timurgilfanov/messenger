# Finish newest-first chat paging UX and tests

## Context

Phase 2 follow-up task for scalable chat history rendering. The app specification requires opening chats quickly, loading older messages, preserving scroll position, and handling conversations with large message histories without loading all messages into memory.

Local paging foundation already exists: messages are exposed through Paging 3 from local storage. This issue tracks the remaining chat-screen UX, retry/error handling, scroll preservation, and test coverage needed to make that paging behavior production-ready.

This issue is the canonical Phase 2 pagination ticket and supersedes #67.

## Scope

- Complete paging-backed message loading UX for the chat screen using the existing repository/data architecture.
- Keep message pages loaded from local storage newest-to-oldest, with bounded page sizes.
- Open the chat screen at the end of the conversation so the newest messages are visible first.
- Render the conversation like a long chronological chat list: older messages above newer messages, newer messages at the bottom/current viewport, and scrolling up loads older history.
- Show loading and error states for initial load and older-message pagination loads.
- Expose retryable UI/state for failed page loads.
- Preserve scroll position across configuration changes and paging refreshes where the same message window remains available.
- Restore the ignored ChatViewModel paging/update tests that were blocked by collecting paged data in tests, or replace them with equivalent coverage.

## Out of scope

- WebSocket/live delivery foundation beyond existing update flows.
- Message edit/delete/reply features.
- Push notifications and read receipt UI.
- Remote message pagination beyond the existing update flows.

## Acceptance criteria

- [x] Existing local Paging 3 foundation remains in use instead of loading the full conversation at once.
- [x] Messages page newest-to-oldest from local storage in bounded pages.
- [x] Opening a chat shows the newest messages first at the end/bottom of the conversation.
- [x] Older messages can be loaded by scrolling up without changing the current visual scroll position.
- [x] Pagination loading indicators are shown for initial and older-message loads.
- [x] Failed page loads expose retryable error UI/state.
- [x] Visual ordering is consistent: older messages are above newer messages, newer messages are at the bottom/current viewport.
- [x] Scroll position survives configuration changes for the loaded window.
- [x] Previously ignored ChatViewModel paging/update tests are restored or replaced with equivalent coverage.
- [x] Existing message send/receive/read-marking flows continue to work with paging-backed data.