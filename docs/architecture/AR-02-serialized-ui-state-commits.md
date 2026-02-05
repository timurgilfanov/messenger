# Architecture Rule 2: Serialized UI State Commits (Single Writer)

## Status
Accepted

## Context
On many screens, multiple coroutines may be active at the same time (UI events, background refresh, repository callbacks, retries, etc.). If more than one coroutine can mutate UI state directly, updates may interleave in timing-dependent ways.

This can cause:
- lost updates (read-modify-write overwrite)
- non-repeatable reads (state changes “under” an update)
- duplicated or missed one-off transitions/effects derived from state
- nondeterministic final state (“last writer wins” depends on timing)
- flaky tests that pass/fail depending on scheduling

To make state transitions deterministic and testable, UI state must have a single writer and all commits must be applied sequentially.

## Applicability
This rule applies to any feature/screen where UI state can be updated from more than one coroutine or callback (including concurrent async work, multiple event sources, or background refresh).

If a screen has exactly one update source and updates never overlap, this rule is still recommended for consistency but is less critical.

## Rule
UI state MUST be mutated only through a single serialized commit pipeline.

- Exactly one mechanism (e.g., `reduce`, an actor, a channel/queue) is allowed to apply state commits.
- Commits MUST be processed sequentially (no two commits execute concurrently).
- Any async work MAY run concurrently, but it MUST NOT mutate UI state directly; it must produce a commit request to the serialized pipeline.

## Rationale
Coroutine scheduling, thread execution, and IO timing are nondeterministic. If state can be mutated by multiple concurrent writers, the observed state sequence becomes timing-dependent.

Serializing commits ensures:
- deterministic state transitions
- elimination of concurrency races in state mutation
- simpler reasoning about updates
- stable, reproducible tests

## Enforcement
- Only the serialized commit function/mechanism (e.g., `reduce`) may mutate UI state.
- Direct state mutation from async callbacks is forbidden.
- All state writes go through the same pipeline (no “side” writers).

## Anti-patterns
- Writing UI state directly from multiple coroutines.
- Updating UI state in repository callbacks, collectors, or `onEach` blocks without passing through the commit pipeline.
- Multiple independent “writers” (e.g., both View and ViewModel, or multiple reducers) mutating the same UI state.

## Related
- AR-01: Single authority for ordering rules and UI state commits