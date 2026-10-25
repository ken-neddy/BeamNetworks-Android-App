---
name: update-changelog
description: Update CHANGELOG.md and publish it to the public Vercel page whenever a user-facing change to the Beam Networks app is made, finished or committed (new feature, fix, behaviour change, removal, release). Use it before every commit or push that changes how the app looks or behaves, and when cutting a release.
---

# Update the changelog and its public page

`CHANGELOG.md` (repo root) is the source. It is published at
**https://beam-networks-changelog.vercel.app** (raw text: `/CHANGELOG.md`).
Vercel builds the page from the repo on every push that touches `CHANGELOG.md`,
`changelog-site/` or `vercel.json` (see `vercel.json`; other pushes are skipped on purpose).

Do this every time app behaviour changes. A change is not finished until its changelog entry is pushed.

## 1. Write the entry

Open `CHANGELOG.md` and add to the top `## [Unreleased] - YYYY-MM-DD` section (create it if it is missing, using today's date).
Use the existing headings, in this order, and only the ones you need:

- `### Added` new features or screens
- `### Changed` behaviour or layout that already existed
- `### Fixed` bugs
- `### Removed` things taken out

Style rules, because the page is public and read by staff, not developers:

- One bullet per change, plain language, starting with what the user sees. Bold the feature name.
  Good: `**Payments register.** Search and filter every payment…`. Bad: `Refactored RegisterViewModel`.
- Describe the app only. Never include passwords, tokens, keystore details, customer names or phone
  numbers, the business's money figures, account or billing-system findings, or internal file paths.
- Fold small follow-ups into the existing bullet instead of adding near-duplicates.
- Do not list pure refactors, tests or build-tool changes unless users would notice them
  (the one exception is a build/Gradle upgrade, which has a line under Changed).

## 2. Releases

When `versionCode` / `versionName` in `app/build.gradle.kts` is bumped, rename `[Unreleased]` to
`## [x.y] - YYYY-MM-DD` (matching `versionName`) and add a fresh empty `## [Unreleased]` above it.
Do not bump the version yourself unless the user asked for a release.

## 3. Commit and push

- Commit `CHANGELOG.md` with the code change it describes (or straight after it), then push to `master`.
- Never commit `handoff/`, `keystore.properties`, `*.jks`, `app/google-services.json`, `.env*` or `.vercel/`.

## 4. Publish and check

Pushing is the publish step: Vercel builds on push.

1. Wait about a minute, then check the live text contains the new entry:
   `curl -s https://beam-networks-changelog.vercel.app/CHANGELOG.md | head -30`
2. If it is still the old text, the GitHub link on the Vercel project is probably missing.
   Deploy by hand instead, from the repo root, then say so to the user:
   ```
   cp CHANGELOG.md changelog-site/CHANGELOG.md
   cd changelog-site
   npx.cmd vercel deploy --prod --yes
   ```
3. Tell the user the page is updated (or that it needs the manual deploy and why).

## Gotchas on this machine

- PowerShell blocks `npx`; use `npx.cmd` there. In the Bash tool plain `npx` works.
- The Vercel project is `neddy/beam-networks-changelog`. Only the public alias above is open to everyone;
  the longer `…-neddy.vercel.app` addresses redirect to a Vercel sign-in page by design.
- `changelog-site/CHANGELOG.md` is a generated copy and is git-ignored. Never edit it; edit the root file.
