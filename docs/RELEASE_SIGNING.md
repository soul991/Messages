# Release signing

## Where things live

| Thing | Path | Committed? |
|---|---|---|
| Release keystore | `~/keystores/messages-release.jks` (outside the repo) | **Never** |
| Passwords + alias | `keystore.properties` at the repo root | **Never** (gitignored) |
| Signing config | `app/build.gradle.kts` (reads `keystore.properties`) | Yes |

The keystore was generated 2026-07-24 (RSA 4096, alias `messages`, validity
10,000 days, self-signed). Store password and key password are identical and
live only in `keystore.properties`:

```properties
storeFile=/Users/<you>/keystores/messages-release.jks
storePassword=<password>
keyAlias=messages
keyPassword=<password>
```

## Password handling

- The password exists in exactly one place: `keystore.properties`. It is in
  `.gitignore` (along with `*.jks` and `*.keystore`) — a `git add -A` can
  never pick it up.
- **Back up both the keystore file and the password** somewhere durable (a
  password manager plus a copy of the `.jks` file). Losing either means you
  can never update the installed app again — a signature change forces an
  uninstall, which wipes app data (Room index, settings, rules, drafts).
- If `keystore.properties` is absent (fresh clone, CI), `assembleRelease`
  still builds — the APK just comes out unsigned. Nothing fails.

## Building

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew :app:assembleRelease
# → app/build/outputs/apk/release/app-release.apk (signed when keystore.properties present)
```

Release builds run R8 (minify + resource shrinking; rules in
`app/proguard-rules.pro`) and embed the baseline profile from
`app/src/main/baseline-prof.txt`. R8's obfuscation map for each release is at
`app/build/outputs/mapping/release/mapping.txt` — archive it alongside any
APK you keep, or stack traces from that build are unreadable.

## The device install caveat (personal-use note)

The phone's install lineage is signed with the **debug** key (all development
installs). A release-key APK cannot install over it without an uninstall,
which wipes local app state. For on-device release testing this repo's
workflow re-signs the release APK with the debug key:

```bash
apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android \
  --out /tmp/app-release-debugkey.apk app/build/outputs/apk/release/app-release.apk
adb install -r /tmp/app-release-debugkey.apk
```

Runtime behavior (R8, shrinking, baseline profile) is identical — only the
signature differs. Use the real release-key APK for any fresh install that
should be updateable long-term (e.g. a new phone, Play distribution).
