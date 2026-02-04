# Architecture Rule 2: Deterministic UI State Commits (Serialization)

## Status
Accepted

## Context
On screens with concurrent async actions and business ordering invariants (e.g., “latest wins”, “paging cannot overlap”), multiple async operations may complete in arbitrary order due to coroutine scheduling, thread execution, or IO timing.

If UI state commits occur in completion order, business-defined ordering can be violated. This leads to:
- stale UI state
- non-deterministic behavior
- race conditions that are hard to reproduce
- flaky tests

To preserve correctness, state transitions must follow business order, not completion timing.

## Applicability
This rule applies to screens that:
1. Have overlapping async operations, and
2. Require business ordering invariants.

Screens without these pressures may use simpler state management approaches.

## Rule
UI state commits MUST be applied in a deterministic, sequential order (i.e., no two state transitions are applied concurrently), independent of coroutine scheduling and parallel execution.

State transitions MUST be processed sequentially (i.e., no concurrent mutation of UI state).

## Rationale
Completion order is determined by runtime timing and is non-deterministic.
Business order is defined by application requirements and must be preserved.

Serializing state commits guarantees:
- deterministic UI behavior
- enforcement of ordering invariants
- simpler reasoning about state transitions
- stable and reliable tests

## Enforcement
- Only a single sequential commit mechanism (e.g., `reduce`) may mutate UI state.
- No concurrent mutation of UI state is allowed.
- Tests exist for critical ordering invariants (e.g., LWW, search clears paging, no-overlap paging).

## Anti-patterns
- Concurrent mutation of UI state from multiple coroutines.
- Committing state directly from async callbacks without passing through the serialized commit pipeline.
- Relying on completion order to determine final UI state.

## Related
- AR-01: Business ordering and async coordination authority