# Release signing

## Where things live

| Thing | Path | Committed? |
|---|---|---|
| Release keystore | `~/keystores/messages-release.jks` (outside the repo) | **Never** |
| Passwords + alias | `keystore.properties` at the repo root | **Never** (gitignored) |
| Signing config | `app/build.gradle.kts` (reads `keystore.properties`) | Yes |

There is **exactly one** authoritative keystore file:
`~/keystores/messages-release.jks`, plus the owner's off-machine backup. No
copy lives inside the repo directory. A stale pre-rotation copy that had been
sitting at the repo root was deleted on 2026-07-27 after its certificate
identity was confirmed to match (see *Certificate identity* below).

The keystore was generated 2026-07-24 (RSA 4096, alias `messages`, validity
10,000 days, self-signed). Store password and key password are identical and
live only in `keystore.properties`:

```properties
storeFile=/Users/<you>/keystores/messages-release.jks
storePassword=<password>
keyAlias=messages
keyPassword=<password>
```

### File mode is enforced (V2-04)

`keystore.properties` holds passwords in plaintext, so the build **refuses to
read it** unless it is owner-only:

```bash
chmod 600 keystore.properties
```

A group- or world-readable file fails configuration with an actionable message
rather than quietly handing your signing password to every account on the box.

### Preferred on CI and shared machines: environment variables

The build reads the environment **first** and only falls back to the file. When
all four are set, no plaintext credentials file needs to exist at all:

| Variable | Maps to |
|---|---|
| `MESSAGES_STOREFILE` | `storeFile` |
| `MESSAGES_STOREPASSWORD` | `storePassword` |
| `MESSAGES_KEYALIAS` | `keyAlias` |
| `MESSAGES_KEYPASSWORD` | `keyPassword` |

All four must be non-blank or the build falls back to the file. With
`-PrequireSigning=true` the build also verifies the keystore actually exists at
`storeFile` before starting, so a dangling path fails immediately with a clear
message instead of deep inside AGP.

## Certificate identity

The signing identity — not the password — is what Android enforces on update.
It has never changed:

```
Owner:  CN=Messages, OU=Personal, O=Personal, C=IN
Alias:  messages
SHA-256: 8F:06:A5:76:A0:5F:50:89:2B:35:F6:B4:E5:98:3D:E5:
         18:26:D9:83:96:6B:D7:1C:48:0D:73:D0:D7:91:49:15
SHA-1:   79:E2:F9:51:44:81:4F:30:30:9D:AF:56:98:04:D3:CC:A7:9C:2C:62
Signature algorithm: SHA384withRSA
Valid:  2026-07-24 → 2053-12-09
```

Verify at any time with:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
keytool -list -v -keystore ~/keystores/messages-release.jks | grep -A1 'Certificate fingerprints'
# and, for an APK you already built:
apksigner verify --print-certs app/build/outputs/apk/release/Messages.apk
```

The SHA-1 above is the value Google Cloud Console needs for the release
OAuth client (see [`DRIVE_BACKUP_SETUP.md`](DRIVE_BACKUP_SETUP.md)).

## Password rotation history

| Date | Event | Effect on signing identity |
|---|---|---|
| 2026-07-24 | Keystore generated | — |
| 2026-07-27 | Store password rotated via `keytool -storepasswd` | **None** — certificate fingerprint unchanged |

`keytool -storepasswd` re-encrypts the private key under the new password, so
the *file bytes change* while the certificate stays identical. Two copies of
this keystore with different checksums are therefore not necessarily two
different keys — compare the SHA-256 fingerprint above, never the file hash.
After any rotation, update `storePassword`/`keyPassword` in
`keystore.properties` and your password manager together.

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
