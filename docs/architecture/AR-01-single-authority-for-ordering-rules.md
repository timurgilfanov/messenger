# Architecture Rule 1: Single authority for ordering rules and UI state commits

## Status
Accepted

## Context
UI usually has async work to handle actions that update UI state. Sometimes UI has business ordering invariants which require coordination of async work (e.g., "search clears paging"). If this coordination relies on guarding state updates (e.g., tokens, coroutine cancellation checks) then coordination logic is scattered across the class leading to hard-to-find bugs and increasing change, debugging, and onboarding costs.

## Applicability
This rule applies to screens that:
1. Have overlapping async operations, and
2. Require business ordering invariants across those actions (e.g., “search clears paging”).

Screens without these pressures may use simpler state management approaches.

## Rule
All ordering rules (e.g., "last write wins", "paging does not overlap", "search clears paging") MUST be implemented exclusively inside the actor. The actor is the only authority to coordinate async work and to commit UI state updates (e.g., call `reduce`).

## Rationale
Centralization in `actor` reduces hidden coupling between actions and makes ordering rules explicit. If components other than `actor` can commit UI state updates, then ordering rules can be bypassed.

## Implications
- more boilerplate to establish `actor`
- one place to change business ordering invariants

## Enforcement
- state is private mutable, exposed as read-only
- only `actor` can commit UI state updates (e.g., call `reduce`)
- tests exist for all ordering invariants (e.g., "search clears paging")

## Recommended patterns
- MVI actor+reducer satisfies AR-01; serialized processing satisfies AR-02.

## Anti-patterns
- ordering is enforced implicitly via scattered guards across multiple writers
- commit UI state updates (e.g., call `reduce`) outside `actor`

## Related
- AR-02: Deterministic state commits 