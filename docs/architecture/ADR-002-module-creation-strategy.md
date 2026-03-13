# ADR-002: Module creation strategy

## Status
Accepted

## Context
ADR-001 introduced multi-module architecture but doesn't specify how modules are created. Two kinds of modules exist: layer modules (`:core:*`) that group code by architectural layer, and feature modules (`:feature:*`) that group code by business feature. These have different design constraints — layer boundaries are stable abstractions (domain never depends on UI), while feature boundaries depend on dependency graphs that only emerge as code is written. Multiple engineers need clear guidance on when and how to create each type to work in parallel without conflicts.

## Decision
Layer modules (`:core:domain`, `:core:ui`, `:core:test`, `:core:data`) are extracted from existing package structure that already maps 1:1 to module boundaries. Feature modules (`:feature:*`) are created when feature development starts — the engineer creates the module and builds inside it from day one. Feature module boundaries are shaped by actual dependencies as the feature is built. New feature work always goes into a dedicated `:feature:*` module, not into `:app` or another feature's module.

## Consequences
Positive:
- Layer modules can be extracted mechanically with low risk
- Each engineer works in their own feature module, avoiding merge conflicts
- Feature boundaries reflect real dependency graphs

Negative:
- Feature module boundaries may need adjustment as cross-cutting concerns emerge (e.g., blocking affects chat, chatlist, and profile)
- Layer modules must be extracted before feature modules can depend on them

## Alternatives Considered
1. Full upfront modularization (design all modules before building features) — rejected because feature boundaries designed before code exists are wrong (e.g., proposed `:feature:profile` containing blocking+privacy fell apart because blocking cross-cuts multiple features).
2. Build in monolith, extract later (add features to `:app`, modularize after) — rejected because multiple engineers in a single module causes merge conflicts in DI, navigation, and build files.
