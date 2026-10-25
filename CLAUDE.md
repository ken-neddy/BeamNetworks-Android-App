# Beam Networks Android app

## Always keep the changelog current

Any change that users of the app would notice (feature, fix, behaviour change, removal) must update
`CHANGELOG.md` in the same piece of work, before it is committed or pushed. Follow the
`update-changelog` skill (`.claude/skills/update-changelog/SKILL.md`): it covers how to write the
entry, what must never appear in it (the page is public), and how the Vercel page auto-deploys on push.
Mention in your final message that the changelog was updated.

## Other project facts

- Release signing uses `keystore.properties` and the key in `C:\Users\Ken.Neddy\.android-keystores`.
  Neither is ever committed.
- `handoff/` (design reference) is intentionally not committed.
- Build with Android Studio's JDK: set `JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"`.
