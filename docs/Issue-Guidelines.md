# Issue Guidelines

How to write effective issues for this project.

## Choosing the Right Template

| Template | When to use |
|----------|-------------|
| **Bug Report** | Something is broken or behaves unexpectedly |
| **Feature Request** | A new capability or improvement to an existing one |
| **Refactoring** | Structural code changes that don't alter observable behavior |
| **AI Agent Task** | Mechanical, bounded work that can be executed autonomously by an AI agent |

## Writing Effective Issues

### Bug Reports

- Provide **exact reproduction steps** — assume the reader has never seen the bug.
- Always include **expected vs actual** behavior.
- Specify **API level and device** when the bug is device-specific or involves platform APIs.
- Paste logcat output or stack traces in the logs field — it renders as a code block.
- Select the **build flavor** where you observed the bug. Choose "All" only if you have verified it across flavors.

### Feature Requests

- Lead with the **problem**, not the solution. Describe what is painful or missing today.
- Describe the feature **from the user's perspective** before diving into implementation.
- Use **estimated effort** to help with prioritization — refer to the scale: XS (hours), S (1–2 days), M (3–5 days), L (1–2 weeks), XL (> 2 weeks).
- Mention alternatives you considered so reviewers understand tradeoffs.

### Refactoring

- Explain **why** the current state is problematic — readability, testability, performance, coupling, etc.
- Confirm whether the change **alters observable behavior**. If it does, a Feature Request or Bug Report may be more appropriate.
- For mechanical, bounded refactoring (rename, extract, move), consider the **AI Agent Task** template instead.
- List affected files or modules to help estimate blast radius.

## Labeling and Prioritization

Each template auto-assigns a type label (`bug`, `enhancement`, or `developer-experience`). During triage (within 48 hours), maintainers add:

- **Priority label** — `priority:critical`, `priority:high`, `priority:medium`, or `priority:low`
- **Layer label** — `ui-layer`, `domain-layer`, `use-case-layer`, or `data-layer`
- **Additional labels** as appropriate (`performance`, `agent`, etc.)

See [Issue Prioritization](Issue-Prioritization.md) for priority definitions and response times.

## Linking Issues

- Reference related issues with `#<number>`.
- Link to relevant ADRs in `docs/architecture/` when applicable.
- Reference specific files with their path (e.g., `domain/usecase/chat/GetChatsUseCase.kt`).

## Related Docs

- [Issue Prioritization](Issue-Prioritization.md)
- [AI Agent Tasks](AI-Agent-Tasks.md)
- [Testing Strategy](Testing%20Strategy.md)
