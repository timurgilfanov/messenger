# Architecture Rule 2: Error hierarchy openness

## Status
Accepted

## Context
Repository error types are sealed interfaces. When the backend evolves it can introduce new error codes the client has not modelled yet. Two options exist: keep the hierarchy closed (only known variants) or add an escape-hatch variant (e.g., `UnknownRuleViolation(reason: String)`) so new backend errors can be forwarded without a client release.

## Applicability
This rule applies whenever a new sealed error hierarchy is added to a repository or data source layer.

## Rule
**Control-flow errors must be closed. Display-only errors may carry an escape hatch.**

The signal: ask "does a use case or ViewModel branch on this variant to decide what to do next?" If yes — closed. If the variant is only ever forwarded to the UI to render a message — escape hatch is acceptable.

## Rationale
An escape hatch on a control-flow error means an unhandled backend case silently falls through and produces wrong behaviour until manually discovered. An escape hatch on a display-only error lets validation rules evolve on the backend without requiring a client release.

## Implications
- Display-only validation errors (e.g., name/email rules) survive backend rule changes without a client release
- Control-flow errors stay exhaustively handled by the compiler — a new backend variant forces a deliberate client update
- `UnknownRuleViolation` branches in `when` expressions are not compiler-checked; developers must not make control-flow decisions on the `reason` string

## Enforcement
- Control-flow error hierarchies have no `Unknown*` or catch-all variant
- Display-only detail types (e.g., `EmailValidationError`, `ProfileNameValidationError`) are carried as a `reason` field inside the parent control-flow variant, not as the variant itself
- Code review rejects escape hatches added to control-flow errors

## Patterns
- Control-flow variant carries display detail as a typed field: `data class InvalidName(val reason: ProfileNameValidationError) : SignupRepositoryError`
- Display-only hierarchy has `data class UnknownRuleViolation(val reason: String)` as terminal escape hatch

## Anti-patterns
- `data object InvalidName : SignupRepositoryError, ProfileNameValidationError` — opaque marker; can't carry detail
- `UnknownRuleViolation` used as a branch condition in use case logic
- Catch-all variant added to a control-flow error "just in case"