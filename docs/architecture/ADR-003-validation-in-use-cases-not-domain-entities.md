# ADR-003: Validation in use cases, not domain entities

## Status
Accepted

## Context
Field validation (email format, password length, profile name rules) currently lives in dedicated validator classes injected into use cases. An alternative is self-validating domain value objects: `Email`, `ProfileName`, `Password` types whose constructors reject invalid input and return a typed error, making it structurally impossible to hold an invalid value anywhere in the domain.

## Decision
Keep validation in use cases via injected validator interfaces. Do not introduce self-validating value objects unless the threshold conditions below are met.

**Threshold for revisiting:** introduce a self-validating value object when all three hold:
1. The same rule is **copied** (not reused via a shared validator) across three or more independent locations — multiple use cases injecting the same validator do not count, as the rule still lives in one place.
2. The field gains domain behavior beyond validation (e.g., `email.domain`, `name.initials()`).
3. The serialization cost (custom deserializers for Room/JSON) is justified by the reduction in duplicated logic.

## Consequences
Positive:
- Validators are `fun interface` — test doubles are one-liner lambdas; no need to construct valid domain objects in tests that don't care about the field.
- Cross-field validation (password == confirmPassword) fits naturally in a use case; it has no home in either individual value object.
- No serialization friction — DTOs and Room entities map directly to primitive types without custom deserializers.
- Validation rules are context-flexible — stricter rules for one screen do not affect another without creating separate types.

Negative:
- The compiler cannot distinguish a validated profile name from an arbitrary string; type safety relies on discipline rather than structure.
- Same invariant can drift across call sites if a new use case forgets to inject the validator.

## Alternatives Considered
1. Self-validating value objects — rejected at current complexity. The same invariant appears in at most two call sites per field (login + signup), no field has domain behavior beyond validation, and the serialization overhead is not offset by any meaningful reduction in duplication.