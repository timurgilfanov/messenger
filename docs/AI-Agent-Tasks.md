# AI Agent Tasks

This document defines which GitHub issues are safe to delegate to an autonomous AI coding agent (Claude Code) and how to scope them for success.

## Prerequisites

1. `ANTHROPIC_API_KEY` repository secret configured
2. [Claude GitHub App](https://github.com/apps/claude) installed on the repository

The agent's verification tool is `./gradlew preCommit`. It cannot inspect a screen, evaluate aesthetics, or make architectural judgment calls. Tasks must be scoped accordingly.

## Solvability Criteria

A task is AI-solvable when **all five** conditions hold:

| # | Criterion                                     | Rationale                                                                                                    |
|---|-----------------------------------------------|--------------------------------------------------------------------------------------------------------------|
| 1 | **Verifiable by `preCommit`**                 | The agent's verification tool. If correctness requires looking at a screen, the agent can't confirm it.      |
| 2 | **No open design decisions**                  | Agent replicates patterns, it doesn't make architectural calls. "Decide during implementation" = human task. |
| 3 | **Reference pattern exists**                  | A merged file or PR demonstrating the same pattern. Agent's primary success mode is pattern replication.     |
| 4 | **Bounded blast radius** (~10 files or fewer) | Beyond this, the agent misses integration points.                                                            |
| 5 | **Effort XS or S** (or purely mechanical M)   | Correlates with all above.                                                                                   |

## Task Type Reference

### Good fit

- Renames / mechanical refactors
- New use case following existing pattern (e.g., follows `SyncAllPendingSettingsUseCase`)
- New validator / validation rule
- Error type sealed interface hierarchy
- Adding tests to existing code
- Handling an ignored error branch
- KDoc additions

### Risky fit — needs very tight scoping

- ViewModel migration *without* cross-action ordering invariants
- SavedStateHandle integration (must pre-decide what to persist)
- Data layer mapping (DTO/entity mappers)

### Not suitable

- ViewModel work *with* cross-action ordering invariants / concurrency reasoning
- Cross-cutting architectural changes (effort L+)
- Tasks with open design alternatives
- UI visual judgment (spacing, colors, layout)
- External API integration
- Flaky test debugging

## Workflow

1. **Author** writes issue using the `ai-agent-task` template
2. **Author** confirms all five solvability criteria in the template checklist
3. **Author** applies the `agent` label
4. **GitHub Actions** triggers the `Claude Agent` workflow (`.github/workflows/claude-agent.yml`), which launches Claude Code to read the issue, implement the solution, run verification, and open a PR automatically
5. **Author** reviews the resulting PR on their schedule

## Related

- [Issue Prioritization Guide](Issue-Prioritization.md) for priority and labeling guidelines
- `.github/ISSUE_TEMPLATE/ai-agent-task.yml` for the issue template
