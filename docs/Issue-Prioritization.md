# Issue Prioritization Guide

This document defines the priority system for GitHub issues in this project.

## Priority Levels

| Priority | Description | Response Time |
|----------|-------------|---------------|
| `priority:critical` | Blocks core functionality, security issues, data loss risk | Work on immediately |
| `priority:high` | Significant impact on user experience, important features | This week |
| `priority:medium` | Moderate impact, improvements, non-blocking issues | This month |
| `priority:low` | Nice-to-have, minor improvements, cosmetic issues | When time permits |

## Priority Definitions

### Critical
- App crashes or becomes unusable
- Security vulnerabilities
- Data loss or corruption
- Authentication/authorization failures
- Blocking production deployments

### High
- Major bugs affecting user experience
- Key feature requests
- Technical debt blocking other work
- Performance issues significantly impacting users
- Security concerns (non-critical)

### Medium
- Enhancements to existing features
- Code refactoring for maintainability
- Test coverage improvements
- Non-critical performance optimizations
- Architecture improvements

### Low
- UI polish (icons, colors, spacing)
- Minor optimizations
- Code cleanup
- Documentation improvements
- Developer experience enhancements

## Labeling Guidelines

1. **Every issue should have a priority label** - Triage new issues within 48 hours
2. **Combine with layer labels** - Use `ui-layer`, `data-layer`, `domain-layer`, or `use-case-layer` to indicate affected area
3. **Use additional context labels** - `bug`, `enhancement`, `performance`, `developer-experience` as appropriate

## Filtering Issues

```bash
# See what to work on next
gh issue list --label "priority:critical"
gh issue list --label "priority:high"

# Filter by priority and layer
gh issue list --label "priority:high" --label "data-layer"

# See all prioritized issues
gh issue list --label "priority:critical,priority:high,priority:medium,priority:low"
```

## Updating Priorities

Priorities can change based on:
- New information about impact
- User feedback
- Business requirements
- Dependencies with other issues

When changing priority, add a comment explaining the reason.
