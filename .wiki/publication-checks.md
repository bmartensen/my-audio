# Publication checks

Run these checks before publishing source, for example a fork's first export or the source for a release. Publishing requires explicit authorization; preparing files does not grant it.

## What may be published

Source, build scripts, tests, public artwork, license texts, wiki and harness files. Never local IDE/SDK settings, signing material, credentials, recordings, analysis results, session transcripts or build artifacts. Public author/contact details and the application ID are intentional.

## Audit

`tools/prepare_public_repo.py` (Python 3.10+, Git) inventories tracked and non-ignored files, rejects private names, unexpected binaries, symlinks/junctions and common secret patterns, and writes a SHA-256 manifest. With `--destination` it copies the reviewed files into an empty directory.

```powershell
python tools/prepare_public_repo.py --manifest .work/public-manifest.json
python tools/prepare_public_repo.py --manifest .work/public-manifest.json --destination .work/public-candidate
```

Also run a dedicated secret scanner such as Gitleaks on the candidate, then review the manifest, documentation and image metadata. `.gitignore` does not protect files that are already tracked, and no scanner proves the absence of every secret. Repeat the checks after every change to the candidate.

## Before pushing

1. Stage only the reviewed files and inspect the staged list.
2. A new public history starts from reviewed source; never import or force-push an older history to create it.
3. Inspect every push URL of the intended remote and the destination branch.

Binary distribution has further obligations, notably the Java runtime's corresponding source; see [developer builds](developer-builds.md).
