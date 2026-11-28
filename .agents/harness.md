# Harness

**Harness version: 1.1**

This repository uses the shared assistant harness from [github.com/bmartensen/harness](https://github.com/bmartensen/harness): one policy for several assistant clients, canonical skills, a public wiki as the only project memory, GitHub Issues as the only list of open work, and releases from `develop` to `main` through pull requests. The harness is copied in at a tagged version; the line above records which one. This file is harness-owned, so an update replaces it and with it the recorded version.

## Who owns which file

**Harness-owned** files are the same in every repository at a given version. An update replaces them; local edits would be lost, so they are not edited locally. An improvement is proposed as an issue in the harness repository (or in the fork the project adopts from).

```text
AGENTS_GUIDE.md AGENTS.md CLAUDE.md GEMINI.md .agents/README.md .agents/harness.md .agents/skills/start .agents/skills/wrap .agents/skills/grill-me .claude/skills/start .claude/skills/wrap .claude/skills/grill-me .wiki/collaboration.md .wiki/interface-text.md .wiki/repository-rules.md .wiki/publication-checks.md .wiki/store-listings.md
```

**Project-owned** files describe the project. Adoption copies starters for them from the harness's `skeleton/` folder only where the file does not exist yet; an update never touches them.

| File | Holds |
|---|---|
| `PROJECT_GUIDE.md` | Product and direction, platforms and technology, issue labels, extra rules and deliberate deviations from `AGENTS_GUIDE.md` |
| `CHANGELOG.md` | User-visible changes per released version |
| `README.md` | The repository's front page |
| `.wiki/README.md` | Index of the wiki, including the harness-owned pages |
| `.wiki/current-work.md`, `.wiki/app-requirements.md`, `.wiki/decisions.md` | Current state, agreed behavior, durable decisions |
| `.wiki/development.md` | Build, test and verification commands, publishing steps |
| `.wiki/microsoft-store.md`, `.wiki/google-play.md` | Store listings, only for a project that publishes there |
| `.github/ISSUE_TEMPLATE/*` | Issue forms worded for the project |
| everything else | Code, other wiki pages, the project's own skills and tools |

## Adopting the harness

Run these steps on `develop` in a clean working tree, from Git Bash, a POSIX shell or PowerShell. Replace the URL by a fork's when adopting from a fork, and `v1.1` by the version to adopt. Nothing is committed until the result has been reviewed.

1. Fetch the tagged version without adding a remote or tags:

   ```sh
   git fetch https://github.com/bmartensen/harness refs/tags/v1.1
   ```

2. Copy the harness-owned files (the list above) from it. This overwrites files of the same name, such as an existing `AGENTS.md` or `.claude/skills/start`, so move their project-specific rules into `PROJECT_GUIDE.md` in step 4:

   ```sh
   git checkout FETCH_HEAD -- AGENTS_GUIDE.md AGENTS.md CLAUDE.md GEMINI.md .agents/README.md .agents/harness.md .agents/skills/start .agents/skills/wrap .agents/skills/grill-me .claude/skills/start .claude/skills/wrap .claude/skills/grill-me .wiki/collaboration.md .wiki/interface-text.md .wiki/repository-rules.md .wiki/publication-checks.md .wiki/store-listings.md
   ```

3. Copy the project-owned starters, keeping every file that already exists (`-k`). The `tar` of Windows keeps them silently; GNU tar (Git Bash, Linux) names each kept file as an error and exits with status 2, which is expected:

   ```sh
   git archive -o harness-skeleton.tar FETCH_HEAD skeleton
   tar -xkf harness-skeleton.tar --strip-components=1
   ```

   Then delete `harness-skeleton.tar`, and the store pages the project does not need.

4. Fill in the project-owned files, replacing every `<placeholder>`:
   - `PROJECT_GUIDE.md`: product direction, platforms, labels and the rules of the old `AGENTS.md`/`CLAUDE.md` that still apply. A rule that contradicts the harness (such as committing directly to `main`) is either dropped or kept as an explicit deviation with its reason.
   - `.wiki/development.md`: the existing build and test commands.
   - `.wiki/README.md`: the existing wiki pages next to the harness pages.
   - An existing page that covers a starter under another name (e.g. `product-requirements.md` next to the new `app-requirements.md`) is merged into the starter's name, because the harness rules and skills refer to it; delete the other one.
   - Move a backlog page in the wiki to GitHub Issues (one issue per change still worth making) and delete the page. Remove skills the harness replaces, e.g. old `start`/`quit` copies in `.codex/skills/` or `.claude/skills/`.
5. Set up GitHub as in [repository rules](../.wiki/repository-rules.md#repository-settings): `develop`, the protection of `main`, merge commits only and milestones. Then run the [label sync](../.wiki/repository-rules.md#label-sync) for the harness labels and the project labels from `PROJECT_GUIDE.md`.
6. Review everything with `git status` and `git diff --staged`, stage the project-owned files, and commit on `develop` (through `wrap` or on explicit request), e.g. "Adopt harness 1.1".

## Updating to a newer version

1. Read the harness's `CHANGELOG.md` for the versions in between. It names changes that need project action, such as a new skeleton page or a removed harness file.
2. Fetch the new version (here `v1.2`) and see what an update would change, including any local edits to harness-owned files that it would discard:

   ```sh
   git fetch https://github.com/bmartensen/harness refs/tags/v1.2
   git diff HEAD FETCH_HEAD -- <harness-owned list of the new version>
   ```

   The list is in `.agents/harness.md` of the new version: `git show FETCH_HEAD:.agents/harness.md`.
3. Replace the harness-owned files with `git checkout FETCH_HEAD -- <the same list>`. Remove files the changelog says the new version no longer has (`git rm <path>`).
4. Apply project actions named in the changelog by hand, such as the [label sync](../.wiki/repository-rules.md#label-sync); skeleton changes never overwrite project-owned files.
5. Review with `git diff --staged` and commit on `develop`, e.g. "Update harness to 1.2".

## Credits

The harness was extracted from [My Audio](https://github.com/bmartensen/my-audio) by Björn Martensen, where it grew, and is published under the MIT license.
