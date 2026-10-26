# Beam Networks Android app

## Always keep the changelog current

Any change that users of the app would notice (feature, fix, behaviour change, removal) must update
`CHANGELOG.md` in the same piece of work, before it is committed or pushed. Follow the
`update-changelog` skill (`.claude/skills/update-changelog/SKILL.md`): it covers how to write the
entry, what must never appear in it (the page is public), and how to publish the page (pushing does
not publish it; it is deployed by hand, see `deployment.md` section 3.6).
Never leave anything out: every change goes in, however small, and the log must cover every commit.
Mention in your final message that the changelog was updated.

## Always keep architecture.md and deployment.md current

- `architecture.md` describes how the app is built *now*. Any change that alters its structure
  (new or removed package, screen, route, data source, repository, background job, permission,
  dependency, build or hosting step) must update the matching section of `architecture.md` in the
  same piece of work, before it is committed or pushed.
- `deployment.md` is the production runbook. Any change to the build, signing, versioning,
  scripts, Vercel projects or addresses, or to a pitfall already listed there, must update it too.
- Read both before changing code or deploying. Mention in your final message when either file was
  updated (or that nothing in them needed to change).

## Releasing

Releasing means: tests pass, changelog entries added, `versionCode`/`versionName` bumped (never
reuse a `versionCode` for different content), commit and push, then run the publish script and the
changelog deploy from `deployment.md`. The release notes shown to staff are generated from the
changelog section for the version, so write those entries in plain language.

## Other project facts

- Release signing uses `keystore.properties` and the key in `C:\Users\Ken.Neddy\.android-keystores`.
  Neither is ever committed.
- `handoff/` (design reference) is intentionally not committed.
- Build with Android Studio's JDK: set `JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"`.
- The shell is Windows PowerShell 5.1: no `&&`, use `npx.cmd`, and run the publish script with
  `-ExecutionPolicy Bypass`.
- Production deploys to Vercel are run by the user in their own terminal; auto mode blocks them.
