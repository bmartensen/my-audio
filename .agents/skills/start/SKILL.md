---
name: start
description: Start a repository work session with a local, wiki-first orientation. Use when the user invokes /start or $start, asks to begin a session, asks the assistant to get oriented before work, or wants a concise overview from AGENTS.md, the repository wiki, decisions, open GitHub issues and git status, to plan the session.
---

# Start

Give the user a short working picture of the repository and its open issues, so the session can be planned. Answer in the user's language.

## Workflow

1. Read `AGENTS.md` in full. Its rules override the generic steps below.
2. Read `.wiki/README.md`, then `.wiki/current-work.md` and the newest group of `.wiki/decisions.md`. Open other wiki pages only when they matter for an obvious next task.
3. Inspect Git state: `git status --short --branch`, recent commits, and `develop` versus `main` (`git fetch` is fine; `git log --oneline origin/main..origin/develop` shows unreleased work). Work belongs on `develop`; if the checkout is on `main`, say so and offer to switch, but do not switch on your own. Mention uncommitted files briefly; never revert or rewrite them.
4. List open issues and pull requests with `gh issue list --state open --limit 100 --json number,title,labels,milestone,updatedAt` and `gh pr list --state open`. Group issues by milestone, then by label (`bug` first). Name new or recently updated ones. If `gh` is missing, unauthenticated or offline, say so and continue.
5. Report concisely: product purpose, current version and state, rules that matter for this session (commit/push limits, `develop`/`main`, wiki-first), unreleased work on `develop`, the open issues as a short overview with a suggested focus for the session, open questions, and the documented build/test commands.
6. Stop there unless the user also asked for a concrete task. Do not edit files, build, commit, push or change issues from `start` alone.

If the repository has no wiki, use the README, build files, tests and Git history instead. Prefer facts in the repository over remembered conversation context.
