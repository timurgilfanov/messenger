---
name: commit-message
description: commit message rules for this codebase
allowed-tools: Bash(git status *), Bash(git diff *)
---

Use `conventional-commits-1-0-0.md` to write consistent and meaningful commit messages. This makes your work easier to review, track, and maintain for everyone involved in the project.

## Commit context
- Commit diff: `!git diff`
- Changed files: `!git status`

## Commit message format
```
<type>(<scope>): <description>

<body>

<footer(s)>
```

Components:
- <type>: The type of change being made (e.g., feat, fix, docs).
- <scope> (optional): The scope indicates the area of the codebase affected by the change (e.g., auth, ui).
- <description>: Short description of the change (50 characters or less)
- <body> (optional): Explain what changed and why, include context if helpful.
- <footer(s)> (optional): Include issue references, breaking changes, etc.

### Commit Types
| Type | Use for... | Example |
|------|-----------|---------|
| feat | New features | feat(camera): add zoom support |
| fix | Bug fixes | fix(auth): handle empty username crash |
| docs | Documentation only | docs(readme): update setup instructions |
| style | Code style (no logic changes) | style: reformat settings screen |
| refactor | Code changes (no features/fixes) | refactor(nav): simplify stack setup |
| test | Adding/editing tests | test(api): add unit test for login |
| chore | Tooling, CI, dependencies | chore(ci): update GitHub Actions config |
| revert | Reverting previous commits | revert: remove feature flag |

### Scope
Scope is optional but recommended for clarity. This list of scopes is not comprehansive:

| Scope | Use for... | Example |
|-------|-----------|---------|
| auth | Authentication | feat(auth): add login functionality |
| chat | Chat feature | test(chat): add tests for image sending |
| settings | User settings | feat(settings): add dark mode toggle |
| build | Build system | fix(build): improve build performance |
| ui | UI/theme | refactor(ui): split theme into modules |
| deps | Dependencies | chore(deps): bump Kotlin to 2.0.0 |

## Best practices
### One Commit, One Purpose
- Each commit should represent a single logical change or addition to the codebase.
- Don’t mix unrelated changes together (e.g., fixing a bug and updating docs, or changing a style and ) adding a feature).

### Think About Reviewers (and Future You)
- Write messages for your teammates and future self, assuming they have no context.
- Explain non-obvious changes or decisions in the message body.
- Consider the commit as a documentation tool.
- Avoid jargon, acronyms, or vague messages like "update stuff".
