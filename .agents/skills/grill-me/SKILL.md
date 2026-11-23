---
name: grill-me
description: Interview the user to settle decisions on a named question. Use when the user invokes /grill-me or $grill-me with a topic (e.g. "$grill-me which effects do I still need") or asks to be grilled or interviewed about a decision. Builds an informed overview from AGENTS.md, the local wiki, the code and, where useful, comparable products; then asks focused decision questions as radio or checkbox lists with the recommended option first, marked with a localized " (recommended)" label, records agreed decisions in the wiki and opens GitHub issues for the resulting work.
---

# Grill Me

The argument names the question. If none is given, ask for it in one sentence and wait. Answer in the user's language. Record repository outcomes in English.

## Overview (read-only)

1. Read `AGENTS.md`, then the wiki pages relevant to the question: `.wiki/app-requirements.md`, `.wiki/decisions.md`, `.wiki/current-work.md`, and technical pages when they matter. Check open GitHub issues on the topic (`gh issue list --search <terms>`).
2. Inspect the code only as far as needed to know what exists and what an option would cost.
3. For features, UX or technology choices, research comparable apps or established practice when web access is available. Keep facts and guesses apart.
4. Do not edit files, build, change Git state or touch issues in this phase.

Then give a short briefing (at most about ten lines): current state, constraints, findings, and the decisions to make, most consequential first.

## Decision Rounds

Respect the available question tool's limits; ask at most four questions per round, each with two to four options. Prefer the client's structured question tool when one is available. Otherwise use this Markdown format so the user can answer compactly (e.g. `1a, 2b+c`):

```markdown
**1. Which zoom controls should the editor use?** (single choice)
- (a) Overview strip with magnifiers (recommended) – visible range and familiar controls
- (b) Pinch zoom – fast, without a visible range overview

**2. Which effects should be added?** (multiple choices)
- [ ] (a) Compressor (recommended) – evens out vocals
- [ ] (b) Equalizer (recommended) – track tone adjustment
- [ ] (c) Chorus
```

Rules:

- If you recommend an option, list it first and end its label with a localized ` (recommended)`; with checkbox lists, mark every recommended option and list them first.
- Radio list `( )`/"single choice" for mutually exclusive options; checkbox list `[ ]`/"multiple choices" when several can be combined.
- One short trade-off per option. Free-text answers are always welcome; say so once.
- Make each question self-contained. Do not ask what has an obvious default or can be verified; decide it and mention it in the summary.
- Adapt later rounds to the answers. Stop when the question is settled or the user wants to stop.

## Record and Close

- Summarize decisions, open points and deliberately deferred options.
- Wiki-first: write agreed scope to `.wiki/app-requirements.md` and durable decisions with rationale to the newest dated group in `.wiki/decisions.md` (create a group for today if needed); remove questions that are now answered.
- Resulting work becomes GitHub issues (`gh issue create`): one per independently deliverable piece, with labels from `.wiki/repository-rules.md#issues` and the target version's milestone when one was agreed. The issue states the goal, links the requirements/decisions instead of repeating them, and lists acceptance checks. Update an existing issue instead of opening a duplicate; close issues the decisions made obsolete, with a short reason.
- Do not implement, commit or push from this skill alone; offer the first concrete implementation step.
