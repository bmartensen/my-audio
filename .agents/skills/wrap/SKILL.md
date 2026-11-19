---
name: wrap
description: Save a repository work session safely, mid-session or at the end. Use when the user invokes /wrap or $wrap, or asks the assistant to wrap up, save or back up the work by checking local wiki consistency, verifying the working tree, committing all intended changes, and pushing only after repository remote safety rules pass.
---

# Wrap

Save the session's work: reconcile the wiki, verify, commit and push, each step only within the user's current authorization. Repository rules in `AGENTS.md` take precedence over these steps.

## Workflow

1. **Scope.** Read `AGENTS.md`. Invoking `wrap`, or an explicit request to save, is the permission to commit for this one run; it does not carry over to later work. A no-commit, no-push or preparation-only instruction limits this workflow to wiki reconciliation and verification. Never initialize Git or rewrite history as part of a wrap.
2. **Wiki.** Compare the session's changes with `.wiki/`:
   - agreed behavior in `app-requirements.md`, durable choices in the newest dated group of `decisions.md`, implemented state and evidence in `current-work.md`;
   - delete completed items from `backlog.md`; append new open work at the bottom of its section;
   - add user-visible changes to `CHANGELOG.md`;
   - fix contradictions, such as a page calling an implemented feature pending.
3. **Verify.** Run the documented checks that cover the changes (`.wiki/repository-rules.md`). If a check cannot run, say exactly why.
4. **Review.** Inspect `git status --short` and the full diff. Mention unrelated pre-existing changes; do not revert them. Stage only intended files and check the staged list for credentials, personal data and build artifacts.
5. **Commit.** Write a concise English message describing the change. Include wiki updates in the same commit as the implementation they describe. Add no agent/model authorship credits or trailers. If the commit introduces a new `myAudio.versionName`, create the annotated tag `v<versionName>` on it.
6. **Push, if authorized.** Identify the intended remote and destination branch from the checkout and task; forks are supported, and `origin` or `main` are not assumed. Inspect every URL from `git remote get-url --push --all <remote>`; each must match the intended destination. Push with an explicit refspec, using `gh` where practical, and push a new version tag explicitly as well (`refs/tags/v<version>`). If the destination is unclear, stop after the local commit and ask only about the push target. Never change remotes or switch branches automatically.
7. **Report.** Summarize what was committed, the verification result and its scope, the push target (or that nothing was pushed), and remaining caveats such as unpushed commits.

## Safety

- Never push to an unverified destination or override a no-push instruction.
- Never run destructive Git commands unless the user explicitly asks for them.
- Keep planning in `.wiki/backlog.md`, not in an external issue tracker.
