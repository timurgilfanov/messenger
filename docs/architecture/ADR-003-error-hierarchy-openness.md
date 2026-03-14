# ADR-003: Error hierarchy openness

## Status
Accepted

## Context
Repository error types are sealed interfaces with explicit variants for each known failure. When the backend evolves it can introduce new error codes the client has not modelled yet. Two design options exist: keep the hierarchy closed (only known variants) or add an escape-hatch variant (e.g., `UnknownRuleViolation(reason: String)`) so new backend errors can be forwarded without a client release.

The decision surfaced during auth domain design (`SignupRepositoryError` gained detailed validation sub-hierarchies with `UnknownRuleViolation`, while `LoginRepositoryError` and `RefreshRepositoryError` remained closed).

## Decision
Apply the escape-hatch pattern only to errors whose sole purpose is **display** — i.e., the use case passes the error to the UI without branching on it. An `UnknownRuleViolation(val reason: String)` variant is acceptable there because control flow never depends on it; the UI simply renders the message.

Keep hierarchies **closed** for errors that drive **control flow** — where a use case or ViewModel must branch on the specific variant to decide what to do next (retry, redirect, surface a specific screen, log out). An escape hatch here means an unhandled backend case silently falls through and produces wrong behaviour until manually discovered.

## Consequences
Positive:
- Display-only validation errors (e.g., name/email rules) survive backend rule changes without a client release
- Control-flow errors stay exhaustively handled by the compiler — a new backend variant forces a deliberate client update

Negative:
- `UnknownRuleViolation` branches in `when` expressions are not compiler-checked; developers must resist making control-flow decisions on the `reason` string

## Alternatives Considered
1. All hierarchies open — rejected because control-flow errors silently become unreachable when the backend adds a case that needs distinct handling (e.g., `AccountSuspended` vs `InvalidCredentials` require different UX responses).
2. All hierarchies closed — rejected because display-only validation errors would require a client release every time the backend adds or renames a rule, coupling release cycles unnecessarily.