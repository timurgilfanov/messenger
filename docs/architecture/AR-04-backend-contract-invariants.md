# Architecture Rule 4: Backend-contract invariants — enforce at the persistence boundary, trust downstream

## Status
Accepted

## Context
The backend guarantees certain structural invariants on the payloads it returns — for example, every chat participant list contains exactly one participant with `isCurrentUser=true`. Downstream code (use cases, ViewModels, UI) depends on these invariants and reads cleaner when it can assume they hold: `participants.first { it.isCurrentUser }` instead of `firstOrNull` + null handling at every call site.

A malformed payload can still arrive (backend bug, partial rollout, corrupted response). Two questions must be answered consistently: **where** is the invariant checked, and **what happens** when it fails — a recoverable error, or a crash?

Three placement options exist:
1. Re-check the invariant defensively at every consumer that depends on it.
2. Validate when the remote response is deserialized, before it becomes a domain delta.
3. Validate once at the persistence boundary, inside the database transaction that commits the payload.

## Applicability
This rule applies whenever a backend payload carries a structural invariant that downstream code relies on without re-deriving it.

## Rule
**Validate each backend-contract invariant exactly once, at the persistence boundary, inside the write transaction. A violation is rejected as a handled data-source error and the transaction is rolled back, so invalid data never reaches the local store. Because only valid data is persisted, downstream consumers trust the invariant and must not add defensive guards. If an unguarded consumer lookup ever fails, the invariant was broken *after* the boundary — an internal bug — and the resulting crash is the correct signal.**

The signal for placement: the persistence boundary is the single chokepoint where untrusted backend data becomes the local source of truth. Validate there and every reader downstream inherits the guarantee; validate per-consumer and the same check is duplicated and can drift.

## Rationale
- Validating once at the boundary keeps invalid data out of the source of truth entirely, so the many readers downstream need no defensive code — the guarantee is centralized rather than re-derived.
- Performing the check **inside the write transaction** couples rejection to rollback: a violation aborts the whole delta atomically, never leaving a half-applied record. This is why the check throws and is mapped at the method boundary rather than returning a `ResultWithError` inline — the throw is what triggers the transaction rollback.
- A violating backend payload is therefore a **handled error** (a data-source error variant), not a crash — it is rejected and rolled back, and the caller decides how to surface it.
- The consumer-side crash is reserved for the case the boundary is supposed to make impossible. If it happens, the cause is an internal write path that skipped validation, not the backend; masking it with a guard would hide that bug rather than fix it. This is consistent with [AR-02](AR-02-error-hierarchy-openness.md): the handled rejection is a control-flow error, while the post-boundary lookup failure is a programming error that should fail loudly.

## Implications
- Each backend-contract invariant has exactly one enforcement site, at the persistence boundary, not scattered across consumers.
- Data-source error hierarchies carry a variant for rejected payloads (e.g. `LocalDataSourceError.InvalidData`) so violations are surfaced, not swallowed.
- Consumers that look up data guaranteed by the contract use total operations (`first {}`, `single {}`), not their `…OrNull` defensive counterparts.
- Adding a new write path into the local store means routing it through the same validation, or the downstream trust guarantee is silently broken.

## Enforcement
- Validation of a backend-contract invariant lives in the data-source write path, executed inside the database transaction, before any DAO write.
- A violation is converted to a handled data-source error at the public method boundary; it does not propagate as an exception to callers.
- Code review rejects defensive `firstOrNull` / null-handling guards added around lookups the contract already guarantees.
- Code review rejects validation placed only at remote-parse time when it would lose the transactional-rollback coupling, unless the boundary check is also present.

## Worked example: the current-user participant contract
- **Invariant:** every chat participant list contains exactly one participant with `isCurrentUser=true`; zero or multiple is an invalid payload.
- **Enforcement:** `LocalSyncDataSourceImpl.validateParticipantContract` runs at the top of `applyChatCreatedDelta` / `applyChatUpdatedDelta`, inside `database.withTransaction`. On violation it throws a private `InvalidParticipantContractException`, which `applyChatDelta` / `applyChatListDelta` catch and map to `LocalDataSourceError.InvalidData`; the transaction rolls back.
- **Consumer trust:** `ChatViewModel` looks up participants with unguarded `participants.first { it.isCurrentUser }` and `participants.first { !it.isCurrentUser }`. These never throw in practice because only validated chats reach Room; if one ever did, it would mean a write path bypassed validation.

## Anti-patterns
- `participants.firstOrNull { it.isCurrentUser }` + null handling in a consumer — re-guards an invariant the boundary already guarantees, masking internal bugs.
- Returning a `ResultWithError.Failure` from the in-transaction validation instead of throwing — does not trigger the rollback, leaving a partially applied delta committed.
- Validating only when the remote response is parsed, with no boundary check — loses atomic rollback and is bypassed by any future write path that does not go through the parser.
- Swallowing a contract violation (logging and continuing) instead of rejecting the payload — lets invalid data into the source of truth.
