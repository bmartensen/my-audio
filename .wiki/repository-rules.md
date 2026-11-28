# Repository rules

How work moves through this repository: branches, releases, issues, changelog and verification. Policy is in [AGENTS_GUIDE.md](../AGENTS_GUIDE.md), project specifics in [PROJECT_GUIDE.md](../PROJECT_GUIDE.md), commands in [development](development.md). This page belongs to the [harness](../.agents/harness.md).

## Git and project memory

Commit and push permission rules are in [AGENTS_GUIDE.md](../AGENTS_GUIDE.md). Update the wiki alongside meaningful changes: decisions newest-first, current work as the state of the product with its verification approach and known limits, never a log of individual test runs. User-visible changes go into `CHANGELOG.md`. Shared workflows: [.agents](../.agents/README.md).

## Branches and releases

- `main` is the default branch and the latest release, so visitors (and a site published from `main`) always see the released state. It is protected: changes only through a pull request (no required approval), no direct or force pushes, no deletion, also for admins. Only merge commits are allowed in the repository.
- `develop` is the working branch. All commits and wiki updates land there and are pushed there; no feature branches unless `PROJECT_GUIDE.md` says otherwise. Pull requests from contributors target `develop`.
- **Release:** on `develop`, raise the version where the project keeps it, update `CHANGELOG.md`, [current work](current-work.md) and, for a store listing, its *What's new* text ([store listings](store-listings.md)), then build and check the candidate as [development](development.md) describes. Complete the [publication checks](publication-checks.md) and push `develop`. Open a pull request `develop` → `main` titled `Release <version>` whose body is the changelog entry (`gh pr create --base main --head develop --title "Release <version>"`), merge it with a merge commit (`gh pr merge --merge`), and put the annotated tag `v<version>` on that merge commit, whose tree equals the `develop` commit it was built from. Publishing a GitHub release, store submissions or a site follow the project's steps in [development](development.md). Each step needs the user's explicit request.
- After a merge into `main`, bring the merge commit back into `develop` (`git merge --ff-only origin/main`, or a plain merge if `develop` moved on) and push `develop`. `main` then never holds anything `develop` lacks, so the next release pull request has no conflicts.

## Issues

Open work, feature requests and bug reports are GitHub Issues, never a backlog page in the wiki. Issue forms for bug reports and feature requests are in `.github/ISSUE_TEMPLATE/`. Issues describe changes to the codebase and its state (features, bugs, technical work), never tests or measurements to perform; tests belong to the work on an issue, and lasting findings (coverage, known limits) go to [current work](current-work.md), never logs of single runs. A deviation from agreed behavior noticed while using the product is a bug issue.

- **Labels:** every repository has the harness labels below, with these colors and descriptions, so issues look and filter the same everywhere. `PROJECT_GUIDE.md` lists the project's own labels (e.g. per platform or area) in the same form. GitHub's other default labels may stay; the harness does not rely on them.

  | Label | Color | Description |
  |---|---|---|
  | `bug` | `d73a4a` | Something isn't working |
  | `enhancement` | `a2eeef` | New feature or request |
  | `documentation` | `0075ca` | Improvements or additions to documentation |
  | `development` | `bfd4f2` | Build, tests, tooling and repository |
  | `accessibility` | `f143ab` | Barrier affecting people with disabilities |

  The [label sync](#label-sync) creates missing labels and corrects color and description of existing ones; it never deletes a label.
- **Milestones:** one per planned version (e.g. `1.0`, or `0.10.x` when every build raises the patch version). It groups the version's issues and summarizes its agreed decisions in its description.
- Agreed behavior still goes to [requirements](app-requirements.md) and decisions to [decisions](decisions.md); an issue links them instead of repeating them.

**Ticket first.** Every larger change is motivated by an issue before it is implemented: new or changed behavior, a new dependency or license, a change to a data format, the build or the release process, or work across several modules. The issue says why the change is made and what it covers; its commits reference it (`Closes #<n>` when they complete it). Small things go without an issue: wording and typos, comments, documentation fixes, minor UI polish and obvious one-line fixes. When in doubt, open the issue; it costs a minute and keeps the history traceable.

## Changelog

`CHANGELOG.md` lists user-visible changes by released version, newest first.

- Headings are plain version numbers (`## 1.2.0`), one per saved (committed and tagged) version, without labels or dates.
- Intermediate builds without their own release are folded into the next listed version, describing only the net result.
- Entries name new features and changed behavior briefly, in the style of the user guide; they also become the release notes. Interface polish is summarized as UI improvements, without layout details or former labels. Internal refactoring, tests and repository work are left out.

## Verification

Automated tests are the verification; the commands that run them are in [development](development.md), and [current work](current-work.md) states what they cover and their known limits. Contributors use the product, and any deviation from agreed behavior becomes an issue. The wiki keeps no acceptance tickets, acceptance procedures or lists of open checks. Report each check with its scope; a check that could not run is reported as such.

## Repository settings

The harness expects these GitHub settings. Setting them changes the repository, so it needs the maintainer's explicit request. The commands work in a POSIX shell and in PowerShell; `{owner}/{repo}` is filled in by `gh` from the current checkout.

```sh
# develop as the working branch, created from main
git switch -c develop
git push -u origin develop

# merge commits only, main as default branch
gh repo edit --default-branch main --enable-merge-commit --enable-squash-merge=false --enable-rebase-merge=false

# protect main: pull requests without required approval, also for admins, no force pushes or deletion
gh api -X PUT repos/{owner}/{repo}/branches/main/protection -F enforce_admins=true -F "required_pull_request_reviews[required_approving_review_count]=0" -F required_status_checks=null -F restrictions=null -F allow_force_pushes=false -F allow_deletions=false

# a milestone per planned version
gh api repos/{owner}/{repo}/milestones -f title=1.0 -f description="<goal of the version>"
```

### Label sync

Run after adopting the harness, after an update whose changelog asks for it, and after changing the project labels in `PROJECT_GUIDE.md`. Each line creates the label or updates an existing one, in a POSIX shell and in PowerShell alike:

```sh
gh label create bug --color d73a4a --description "Something isn't working" --force
gh label create enhancement --color a2eeef --description "New feature or request" --force
gh label create documentation --color 0075ca --description "Improvements or additions to documentation" --force
gh label create development --color bfd4f2 --description "Build, tests, tooling and repository" --force
gh label create accessibility --color f143ab --description "Barrier affecting people with disabilities" --force
```

Then the same line for every project label from `PROJECT_GUIDE.md`. `gh label list` shows the result; a label that is in neither list is removed only by hand, after checking the issues that use it.
