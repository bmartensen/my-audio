# Agents Guide

Shared policy for every contributor and every assistant client. `AGENTS.md`, `CLAUDE.md` and `GEMINI.md` only point here and to `PROJECT_GUIDE.md`. This file belongs to the [harness](.agents/harness.md) and is the same in every repository that uses it; what is specific to this project (product direction, build commands, extra rules) is in `PROJECT_GUIDE.md`. Project knowledge lives in `.wiki/` (start at `.wiki/README.md`); collaboration principles are in `.wiki/collaboration.md`.

## Hard Rules
- Forks and other repository owners are supported; there is no owner or remote-URL allowlist.
- Branches: `main` is the default branch and holds the latest release, so visitors always see the released state. It is protected and changes only through a release pull request from `develop`, merged with a merge commit (see `.wiki/repository-rules.md#branches-and-releases`). All work is committed to `develop`; no feature branches unless `PROJECT_GUIDE.md` says otherwise. Never commit to or push `main` directly. Forks use the branch and remote intended for their checkout; do not assume direct access to the upstream repository.
- Never commit or push on your own initiative. Save to Git only when the user explicitly asks for it or invokes `wrap`; that permission covers the one request, not later work. Finishing a task, a passing build or a user's "ok" is not a request to commit. Before an authorized push, inspect all actual push URLs of the selected remote and the source/destination branches. Confirm they match the intended destination; do not silently change remotes or push to another target.
- Use GitHub CLI (`gh`) for repository-facing commit/push workflows where practical.
- Open work, feature requests and bug reports are GitHub Issues, never a backlog page in the wiki; `start` gives an overview of the open ones. Creating, labelling or closing an issue is repository-facing: do it when the user asks or a workflow says so (`grill-me` opens issues for agreed work). A commit that completes an issue says `Closes #<n>`; GitHub closes it when the release reaches `main`.
- Ticket first: a larger change starts from a GitHub issue that says why it is made, so the history shows what was done and for what reason. Larger means new or changed behavior, a new dependency or license, a change to a data format, the build or the release process, or work across several modules. Small things need no issue: wording and typos, comments, documentation fixes, minor UI polish and obvious one-line fixes. If larger work has no issue yet, propose one before implementing it (see `.wiki/repository-rules.md#issues`).

## Wiki-First Workflow
- Treat `.wiki/` as the source of project knowledge: current state, requirements, decisions and documentation; GitHub Issues hold open work.
- The repository and its wiki are public. The wiki describes the product and the repository, never the people working on it: no personal test runs, device or installation state of a contributor, troubleshooting diaries, session logs or private paths. Record what is true of the product (behavior, verification approach, known limits); results of a single run belong in the conversation or the pull request, not in the wiki.
- Update the wiki before or alongside meaningful implementation changes; document newly agreed behavior in `.wiki/app-requirements.md`.
- `.wiki/decisions.md` is a **reverse-chronological log: newest entries at the top**, grouped under a dated heading. Keep entries terse: the durable decision and its rationale, not implementation minutiae that live in code. Fold a superseded decision into its replacement rather than leaving a dead entry behind.
- Describe the current state in code, wiki and descriptions. User-visible version history belongs in `CHANGELOG.md`; change narratives belong in Git history.

## Project Guide
- `PROJECT_GUIDE.md` holds what the harness cannot know: the product and its direction, platforms and technology, issue labels of the project, where build and test commands are documented, and any additional rules.
- It adds to this guide. A deliberate deviation from a rule here is stated there explicitly, with its reason, and then applies to this repository.

## Collaboration, Credits and Language
- Treat people with dignity, curiosity and respect. Make collaboration understandable and welcoming to contributors with different experience and backgrounds.
- When using someone else's work, credit its authors with appreciation, preserve required copyright/license notices, and keep the project's license inventory and credits up to date together with the code.
- Assistants are tools; the human contributor reviews and is responsible for every change. Keep product code, documentation and commit messages free of agent/model authorship credits or trailers, and never replace human/community attribution with them.
- Keep source comments, repository documentation, wiki pages, skill instructions and commit messages in English. Users may converse in any language; translate agreed outcomes into English when recording them.
- Preserve deliberate product/store localization and verbatim upstream notices in their original language. Public author/contact attribution is intentional, not private data to erase.

## Engineering Defaults
- Keep changes small, intentional, and documented. Prefer repository-local documentation over scattered notes.
- Interface text is clean and sparse: before writing any label, menu entry or dialog text, draft all siblings in every interface language and check them against `.wiki/interface-text.md`; propose new texts as a table before implementing them.
- Preserve user changes; do not rewrite unrelated work.
- Report verification with its scope: builds, tests, manual checks, signature checks and secret scans answer different questions.

## Shared Skills and Harness
- `AGENTS_GUIDE.md` is the common policy; `.wiki/` is the only durable project memory and GitHub Issues the only list of open work, regardless of the tool in use.
- The canonical workflows are `.agents/skills/start/SKILL.md`, `.agents/skills/wrap/SKILL.md` and `.agents/skills/grill-me/SKILL.md`. Edit workflows only there; client-specific entrypoints only delegate. Setup and discovery: `.agents/README.md`.
- `start` alone is read-only and summarizes open issues; `grill-me` records agreed decisions and opens issues without implementing them; `wrap` verifies and saves intended work on `develop` subject to the user's current commit/push restrictions. Release pull requests into `main` happen only on explicit request.
- Harness-owned files are listed in `.agents/harness.md` together with the harness version in use. They change only through a harness update, never by local edits; an improvement is proposed as an issue in the harness repository. Project-owned files are never overwritten by an update.

## Publication Safety
- Never version signing keys, certificates, passwords, tokens, local configuration, personal data, build artifacts or session transcripts. Signing credentials stay outside the checkout.
- Before publishing a candidate (an initial export or release source), inspect the exact files and scan them for secrets; `.gitignore` alone does not protect already tracked files. See `.wiki/publication-checks.md`.
- Do not infer publication permission from permission to prepare files.

## Builds, Signing and Devices
- Build and test commands are documented in `.wiki/development.md`. Do not assume tools are on PATH or use workstation-specific paths; installing missing tools and accepting licenses are explicit, separate steps.
- Ordinary development needs no publisher credentials. Do not request or copy the publisher's release keys or signing settings just to build or test.
- Keep release-signature verification intact. Own keys stay outside the checkout; do not overwrite existing user signing settings. A self-signed build is not an official release.
- Install on a device only when authorized for that device. Never uninstall a user's app or change its identity to make an installation pass.
