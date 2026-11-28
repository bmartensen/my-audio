# Store listings

How a project keeps its store listings in the wiki, so every submission is reproducible and the listing never drifts from the released product. This page belongs to the [harness](../.agents/harness.md); a project without store listings can ignore it. The starters `microsoft-store.md` and `google-play.md` come from the harness skeleton; delete the ones the project does not use.

## One page per store

- A store page records **every field the store console asks for, in the order of its pages**, with the value the project submits, so a new submission is copied from the page, not reconstructed.
- Values that are not secret (package identity, publisher display name, store ID, URLs) are written down. Credentials, signing material and account details are not.
- **Localized listing texts** are complete per language (one section per locale, e.g. `de-DE`, `en-US`). They are deliberate localizations, not machine translations, and are kept in their language.
- Images are named by file and path, in listing order, with captions. The page states how they are produced; screenshots use demo data, never personal data or third-party brands.
- Limits of the store (text lengths, image sizes, counts) are listed with the date they were checked against the store's documentation.

## Style

- Description and features name the core of the product, not the refinements of the latest version. Plain sentences, what the product does, no filler and no semicolons.
- **What's new** names only the few relevant changes since the previous store version, as short phrases (e.g. "PDF export in addition to CSV"), never the full changelog. It follows [interface text](interface-text.md).
- The listing describes current behavior, like the user guide.

## In every release

Part of the release on `develop` ([branches and releases](repository-rules.md#branches-and-releases)):

1. Write *What's new* for every locale from the changelog entries since the previous store version.
2. Check description, features and screenshots against the release; a feature that changed its name or behavior changes in the listing too.
3. Update the package/version fields of the page.
4. After the release is merged and tagged, submit the package built from the tagged merge commit, with the texts from the page. The project's publishing steps ([development](development.md)) say how the package is built and when it is published.
