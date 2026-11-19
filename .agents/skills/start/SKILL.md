---
name: start
description: Start a repository work session with a local, wiki-first orientation. Use when the user invokes /start or $start, asks to begin a session, asks the assistant to get oriented before work, or wants a concise overview from AGENTS.md, repository-local wiki/project memory, backlog, decisions, and git status without task-specific arguments.
---

# Start

Give the user a short working picture of the repository from local instructions and project memory. Answer in the user's language.

## Workflow

1. Read `AGENTS.md` in full. Its rules override the generic steps below.
2. Read `.wiki/README.md`, then `.wiki/current-work.md`, `.wiki/backlog.md` and the newest group of `.wiki/decisions.md`. Open other wiki pages only when they matter for an obvious next task.
3. Inspect the repository shape and Git state: `git status --short --branch`, the current branch and recent commits if any. Mention uncommitted files briefly; never revert or rewrite them.
4. Report concisely: product purpose, current version and state, operating rules that matter for this session (commit/push limits, wiki-first), open work most likely to come next, open questions, and the documented build/test commands.
5. Stop there unless the user also asked for a concrete task. Do not edit files, build, commit or push from `start` alone.

If the repository has no wiki, use the README, build files, tests and Git history instead. Prefer facts in the repository over remembered conversation context.
