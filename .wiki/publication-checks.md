# Publication checks

Run these checks before publishing source, for example a fork's first export, a new public repository or the source for a release. Publishing requires explicit authorization; preparing files does not grant it. This page belongs to the [harness](../.agents/harness.md); a project may add its own audit tooling in [development](development.md).

## What may be published

Source, build scripts, tests, public artwork, license texts, wiki and harness files. Never local IDE/SDK settings, signing material, credentials, personal data or recordings, analysis results, session transcripts or build artifacts. Public author/contact details and public product identifiers (application or package IDs) are intentional.

## Audit

1. **Inventory.** List what would be published: `git ls-files` for tracked files and `git status --short --ignored` for anything else in the tree. Every file must be intended; look in particular for local configuration, keys and certificates, archives, executables, logs, databases and symlinks.
2. **Secret scan.** Run [Gitleaks](https://github.com/gitleaks/gitleaks) on the candidate, both on the history and on the working tree:

   ```sh
   gitleaks git --redact .
   gitleaks dir --redact .
   ```

   A finding is fixed in the candidate, never suppressed without review. A secret that was ever pushed is revoked and replaced, not only deleted.
3. **Review.** Read the documentation and wiki for personal state (private paths, devices, test runs) and check image metadata of new images. `.gitignore` does not protect files that are already tracked, and no scanner proves the absence of every secret. Repeat the checks after every change to the candidate.

## Before pushing

1. Stage only the reviewed files and inspect the staged list.
2. A new public history starts from reviewed source; never import or force-push an older history to create it.
3. Inspect every push URL of the intended remote and the destination branch.

Binary distribution can carry further obligations, such as providing the source of bundled components; the project documents them in [development](development.md).
