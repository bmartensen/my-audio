# Shared skills

`AGENTS_GUIDE.md` is the shared policy (`AGENTS.md`, `CLAUDE.md` and `GEMINI.md` only point to it), `.wiki/` the project memory and `skills/<name>/SKILL.md` the canonical workflows. Edit workflows only here.

| Client | Policy | Skill discovery | Invocation |
|---|---|---|---|
| Antigravity | Reads `AGENTS.md` (and `GEMINI.md`), which point to `AGENTS_GUIDE.md` | `.agents/skills/` | `/start`, `/wrap`, `/grill-me <question>` |
| Gemini CLI | `GEMINI.md` imports `AGENTS_GUIDE.md` | `.agents/skills/` (workspace alias) | Ask for the skill by name; `/skills list` shows discovery |
| OpenAI Codex | Reads `AGENTS.md`, which points to `AGENTS_GUIDE.md` | `.agents/skills/` | `$start`, `$wrap`, `$grill-me <question>` or `/skills` |
| Claude Code | `CLAUDE.md` imports `AGENTS_GUIDE.md` | `.claude/skills/` adapters delegate here | `/start`, `/wrap`, `/grill-me <question>` |
| Other clients | Read `AGENTS_GUIDE.md` explicitly | — | Read and follow the desired `SKILL.md` |

Claude Code adapters repeat the canonical `name` and `description`; update them when a description changes. No symlinks, credentials or global configuration are required.

| Skill | Does | Does not |
|---|---|---|
| `start` | Reads policy, wiki, Git state and open issues; proposes a session focus | Implement anything or change issues |
| `grill-me` | Investigates a named question, asks decision questions, records agreed outcomes in the wiki and opens issues for the work | Implement the decisions |
| `wrap` | Reconciles wiki and code, verifies, commits to `develop` and pushes within the user's authorization | Commit to `main`, open release pull requests or override a no-commit/no-push instruction |

Discovery paths verified against the client documentation on 2026-11-19: [Antigravity skills](https://antigravity.google/docs/skills), [Antigravity rules](https://antigravity.google/docs/rules), [Gemini CLI skills](https://geminicli.com/docs/cli/skills/), [Gemini CLI context](https://geminicli.com/docs/cli/gemini-md/), [Codex skills](https://developers.openai.com/codex/skills). An interactive invocation in each client Problems found in use become issues.
