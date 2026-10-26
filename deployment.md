# Deploying BeamOps to production

This is the runbook for shipping a change to staff phones. It is written for a developer or
another Claude session starting from a clean checkout. BeamOps is **not on the Play Store**: a
signed APK is hosted on Vercel, and installed phones update themselves from it.

> **Keep this file current.** If the build, signing, scripts, Vercel projects, addresses or any
> step below changes, update this file in the same piece of work. See `CLAUDE.md`.

## 1. What "production" means here

| Thing | Where it lives | How it is deployed |
|---|---|---|
| **The app (APK + download page + `version.json`)** | Vercel project `neddy/beam-ops-downloads`, alias `https://beam-ops-downloads.vercel.app/<token>/` (the token is in `UPDATE_BASE_URL` in `app/build.gradle.kts`) | `scripts/publish-release.ps1` (builds, then deploys with the Vercel CLI) |
| **The public changelog page** | Vercel project `neddy/beam-networks-changelog`, alias `https://beam-networks-changelog.vercel.app` | Copy `CHANGELOG.md` into `changelog-site/` and run `vercel deploy --prod` there |
| **Source code** | GitHub `ken-neddy/BeamNetworks-Android-App`, branch `master` | `git push` |

Pushing to GitHub does **not** publish anything on its own. Both Vercel sites are deployed by hand
from the CLI. (Vercel is linked to the GitHub repo, but pushes only create *Preview* builds, not
Production, so they never change the live sites. Do not rely on them.)

Phones check `<UPDATE_BASE_URL>version.json` each time the app is opened. If its `versionCode` is
higher than the installed one, they offer the update. So **publishing a higher `versionCode` is
what releases the app.**

## 2. What you need installed (one-time)

Download or install these on the machine that deploys:

| Tool | Why | Notes |
|---|---|---|
| **Git** | Commit and push | Remote is `https://github.com/ken-neddy/BeamNetworks-Android-App.git` |
| **Android Studio** | Provides the JDK, Android SDK and the Gradle setup | Use its bundled JDK: `JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"` |
| **Android SDK Platform 36 and Build-Tools 36.0.0** | Compile, and `apksigner` for verifying | Install through Android Studio's SDK Manager. Gradle 9.2.1 downloads itself via the wrapper |
| **Node.js** (24.x was used) | Runs the Vercel CLI | `npx` downloads the CLI on demand; no global install needed |
| **Vercel CLI** (65.x via `npx`) | Deploys the two sites | First time: `npx.cmd vercel login`, with the account that owns team `neddy` |
| **Windows PowerShell 5.1** | The publish script is PowerShell | Already on Windows 10/11 |

Files that are **not in git** and must be present locally:

| File | What it is | How to get it |
|---|---|---|
| `keystore.properties` (repo root) | Four lines: `storeFile`, `storePassword`, `keyAlias`, `keyPassword` for release signing | From the owner. The keystore file itself is kept outside the repo (`C:\Users\Ken.Neddy\.android-keystores`). **Never commit either.** |
| the release keystore (`.jks`) | The signing key. Android only installs an update signed with the *same* key | From the owner. **Back it up: if it is lost, every phone must uninstall and reinstall** |
| `app/google-services.json` | Firebase config (the app will not build without it) | Firebase console → project settings → the Android app |
| `local.properties` (repo root) | `sdk.dir=…` path to the Android SDK | Android Studio creates it on first open |
| `handoff/` | Design reference, intentionally not committed | Not needed to build |

The release key's certificate SHA-256, to check a published APK against:
`35a643c8cb10969673202821797ce88ac42a933f4ae512259321fa8b34ca8f42`.

## 3. Releasing a new version: step by step

Run everything from the repository root in **PowerShell**, not Git Bash, unless noted.

### 3.1 Make and test the change

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Both must pass. (Android lint crashes on `MapScreen.kt` because of an Accompanist lint-rule bug,
so it is not part of the check.)

### 3.2 Update the changelog first

Add an entry for every user-visible change to `CHANGELOG.md` (follow
`.claude/skills/update-changelog/SKILL.md`). The release notes on the download page and in the
phone's update pop-up are **generated from the changelog section for the version being
published**, and the script stops with an error if that section is empty or missing.

### 3.3 Bump the version

In `app/build.gradle.kts`:

- raise `versionCode` by 1 (an integer; phones compare this);
- set `versionName` to the new `x.y`.

In `CHANGELOG.md`, rename the finished `## [Unreleased]` entries to `## [x.y] - YYYY-MM-DD`
(matching `versionName`) and leave a fresh empty `## [Unreleased]` above it.

> **Never publish different content under the same `versionCode`.** Phones that already have
> that code see "latest version" and are never offered the fix. Bump again instead.

### 3.4 Commit and push

```powershell
git add -A ':!handoff'
git commit -m "Release x.y: <summary>"
git push origin master
```

### 3.5 Build and publish the app

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\publish-release.ps1 -Notes "One-line summary shown above the release notes"
```

`-ExecutionPolicy Bypass` is needed because script execution is disabled on this machine. The
script:

1. reads `versionCode`, `versionName` and `UPDATE_BASE_URL` from `app/build.gradle.kts`;
2. runs `gradlew :app:assembleRelease` (signed with `keystore.properties`);
3. copies the APK to `release-site/<token>/BeamOps-<versionName>.apk` and removes the old APK;
4. writes `version.json` (versionCode, versionName, apk name, SHA-256, `-Notes`, and a `history`
   of every release after 1.0, parsed from `CHANGELOG.md`);
5. fills `scripts/download-page.html` into `release-site/<token>/index.html` and copies the logo;
6. links `release-site/` to the Vercel project `beam-ops-downloads` (created on first run);
7. deploys to production with `vercel deploy --prod --yes --archive=tgz`.

It ends by printing the page address to send to people. The build takes a few minutes and the
upload a minute or two.

### 3.6 Publish the changelog page

```powershell
Copy-Item CHANGELOG.md changelog-site/CHANGELOG.md; Set-Location changelog-site; npx.cmd vercel deploy --prod --yes; Set-Location ..
```

Run it from the repository root. (If your shell is already in `changelog-site`, the copy fails
and the **old** changelog is redeployed. Check the prompt first.)

### 3.7 Verify

Replace `<token>` and `<x.y>`; run in Git Bash or any shell with `curl`:

```bash
B=https://beam-ops-downloads.vercel.app/<token>
curl -s $B/version.json                       # versionCode/versionName are the new ones
curl -s -o /tmp/app.apk $B/BeamOps-<x.y>.apk
sha256sum /tmp/app.apk                        # must equal "sha256" in version.json
"$LOCALAPPDATA/Android/Sdk/build-tools/36.0.0/apksigner.bat" verify --print-certs /tmp/app.apk
                                              # certificate SHA-256 must match section 2
curl -s https://beam-networks-changelog.vercel.app/CHANGELOG.md | grep "## \[<x.y>\]"
```

Also confirm the old APK now returns 404 and the download page shows the new version and notes.
Finally open BeamOps on a phone with the previous version: an **Update available** pop-up should
appear (fully close and reopen the app, or tap Settings › App version).

## 4. How the pieces fit

- `version.json` and the APK sit under a random folder name (`<token>`) in the downloads project.
  The same address is compiled into the app as `BuildConfig.UPDATE_BASE_URL`. Changing the token
  or project name means shipping a new APK that contains the new address, and phones on the old
  build will never see updates, so avoid it.
- `release-site/` and its `.vercel` link are generated and gitignored. The script recreates them.
- The script copies `app/src/main/res/drawable-nodpi/beam_logo.png` to the page as `logo.png`.

## 5. Pitfalls we have already hit

| Symptom | Cause and fix |
|---|---|
| `&&` "is not a valid statement separator" | Windows PowerShell 5.1 has no `&&`. Use `;` or separate lines |
| "running scripts is disabled on this system" | Run through `powershell -ExecutionPolicy Bypass -File …` |
| `npx` blocked in PowerShell | Use `npx.cmd` |
| Vercel: "Couldn't parse JSON file …\vercel.json" | PowerShell 5.1 `-Encoding UTF8` adds a byte-order mark. The script writes files with a BOM-free writer; keep it that way |
| Vercel upload: "Upload aborted" repeated | File-by-file upload of the large APK fails. The script uses `--archive=tgz` |
| Deploy goes to `beam-networks-changelog` and takes 5 min | The repo root has a `.vercel` link, and a deploy from a subfolder without its own link picks it up and uploads the whole repo. `release-site/` must have its own `.vercel` link (the script creates it); never deploy it without |
| "App not installed" / update refused | The installed app was signed with a different key (for example an Android Studio debug build). Uninstall it, then install from the download page |
| Phone says "latest version" but has old code | Same `versionCode` was published twice. Bump the version |
| Changelog page shows old text | Redeploy step 3.6 from the **repository root**; pushes do not update it |
| Download page still shows the old name/logo | Page is only rebuilt by running the publish script again |
| New project connected itself to GitHub | Vercel auto-connects the repo when creating a project. In `beam-ops-downloads` → Settings → Git, **Disconnect** so a push can never overwrite the download site |

## 6. Notes for Claude sessions

- Claude Code's auto-mode permission check has **blocked production deploys** and
  `-ExecutionPolicy Bypass`. Do the build, tests, commit and push, then give the user the exact
  commands in sections 3.5 and 3.6 to run in their own terminal, and use `mcp__terminal__read_terminal`
  (or ask for the output) to see the result. Then run the checks in 3.7 yourself with `curl`.
- Do not work around a denial. State what you were trying to do and let the user run it.
- Do not commit `release-site/`, `.vercel`, `.env*`, `keystore.properties`, `*.jks`,
  `google-services.json` or `handoff/`.
- Mention in the final message that the changelog was updated, and never put secrets, customer
  data or internal file paths in it (the page is public).
