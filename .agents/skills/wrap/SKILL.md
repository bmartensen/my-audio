---
name: wrap
description: Save a repository work session safely, mid-session or at the end. Use when the user invokes /wrap or $wrap, or asks the assistant to wrap up, save or back up the work by checking local wiki consistency, verifying the working tree, committing all intended changes to develop, and pushing only after repository remote safety rules pass.
---

# Wrap

Save the session's work on `develop`: reconcile the wiki, verify, commit and push, each step only within the user's current authorization. Repository rules in `AGENTS.md` and `.wiki/repository-rules.md#branches-and-releases` take precedence over these steps.

## Workflow

1. **Scope.** Read `AGENTS.md`. Invoking `wrap`, or an explicit request to save, is the permission to commit for this one run; it does not carry over to later work. A no-commit, no-push or preparation-only instruction limits this workflow to wiki reconciliation and verification. Never initialize Git or rewrite history as part of a wrap.
2. **Branch.** Commit on `develop`. If the checkout is on `main` or another branch, stop and ask; never commit to `main`, which changes only through a release pull request.
3. **Wiki and issues.** Compare the session's changes with `.wiki/` and the open issues:
   - agreed behavior in `app-requirements.md`, durable choices in the newest dated group of `decisions.md`, implemented state and evidence in `current-work.md`;
   - add user-visible changes to `CHANGELOG.md`;
   - fix contradictions, such as a page calling an implemented feature pending;
   - name the issues this work completes, so the commit message can close them (`Closes #<n>`); new open work found during the session becomes an issue only when the user agrees.
4. **Verify.** Run the documented checks that cover the changes (`.wiki/repository-rules.md`). If a check cannot run, say exactly why.
5. **Review.** Inspect `git status --short` and the full diff. Mention unrelated pre-existing changes; do not revert them. Stage only intended files and check the staged list for credentials, personal data and build artifacts.
6. **Commit.** Write a concise English message describing the change, with `Closes #<n>` lines for completed issues. Include wiki updates in the same commit as the implementation they describe. Add no agent/model authorship credits or trailers. Version tags are not created here; they belong on the release merge commit in `main`.
7. **Push, if authorized.** Push `develop` to the intended remote: inspect every URL from `git remote get-url --push --all <remote>`, each must match the intended destination, then push with an explicit refspec (`git push <remote> develop:develop`). Forks are supported; `origin` is not assumed. If the destination is unclear, stop after the local commit and ask only about the push target. Never change remotes or switch branches automatically.
8. **Report.** Summarize what was committed, the verification result and its scope, the push target (or that nothing was pushed), the issues it will close on release, and remaining caveats such as unpushed commits.

## Releases

A release pull request `develop` → `main`, its merge, the version tag and the GitHub release are separate steps, each only on the user's explicit request; follow `.wiki/repository-rules.md#branches-and-releases` and `.wiki/site-publication.md#publishing-a-version`. A wrap never opens or merges it on its own.

## Safety

- Never push to an unverified destination, push `main`, or override a no-push instruction.
- Never run destructive Git commands unless the user explicitly asks for them.
