# Google Drive Backup — One-Time Setup (Owner Action Required)

The app's Drive backup (§8.3) uses the `drive.appdata` scope: backups live in
an app-private Drive area, invisible to the user's normal Drive files and to
other apps. Android apps authenticate to Google **without any client secret in
the code** — Google Play services matches the app's *package name + signing
certificate SHA-1* against an OAuth client you register once in Google Cloud
Console. Until that registration exists, sign-in inside the app will fail with
error 10 (`DEVELOPER_ERROR`).

## 1. Create the Cloud project (once)

1. Go to <https://console.cloud.google.com/> → create a project (e.g.
   `messages-app`).
2. **APIs & Services → Library** → enable **Google Drive API**.

## 2. Configure the OAuth consent screen

1. **APIs & Services → OAuth consent screen**.
2. User type: **External** → fill in app name ("Messages"), support email.
3. **Scopes** → add `https://www.googleapis.com/auth/drive.appdata`.
4. While in *Testing* publishing status, add your Google account(s) under
   **Test users**. (Publish to production later; `drive.appdata` is a
   non-sensitive scope and does not require verification review.)

## 3. Register the Android OAuth client

1. **APIs & Services → Credentials → Create credentials → OAuth client ID**.
2. Application type: **Android**.
3. Package name: `com.messages.app`
4. SHA-1: for the debug build use

   ```sh
   keytool -list -v -alias androiddebugkey \
     -keystore ~/.android/debug.keystore -storepass android | grep SHA1
   ```

   For release builds, add a **second** Android OAuth client with the release
   keystore's SHA-1 (and the Play App Signing SHA-1 if Play distributes it).
5. Save. **No code change is needed** — nothing from this page is embedded in
   the app.

## 4. Verify on device

Settings → Google Drive backup → *Choose Google account*. The account chooser
should appear and complete without error. Then tap *Back up now* — no backup
password is needed by default: the Google account is the access control (the
encryption master key lives in a key file in the same app-private Drive area).

To exercise the opt-in mode, use *Backup key protection → Protect with a code*.
Enabling it seals the same master key into `messages-backup-key-vault.json`,
re-downloads and test-opens that object, and only then deletes
`messages-backup-key.bin`. A useful device check: after enabling, clear the
app's data (which drops the local Keystore cache) and confirm the settings
screen now offers *Enter recovery code* rather than backing up silently.

Troubleshooting:
- **Error 10 / DEVELOPER_ERROR** — package name or SHA-1 mismatch (wrong
  keystore, or the client isn't created yet).
- **HTTP 403 on upload** — Drive API not enabled on the project, or the scope
  wasn't added to the consent screen.

## 5. (Future) Passkey unlock for backups

The backup envelope format already reserves a `passkey-prf` key-wrap method
next to the password wrap. Producing it requires WebAuthn passkeys, which need
a **Relying Party domain you own** serving
`https://<domain>/.well-known/assetlinks.json` that lists this app's package
name + SHA-256 cert fingerprint. When such a domain exists:

1. Host the assetlinks.json.
2. Add the domain as the RP ID in a Credential Manager
   `CreatePublicKeyCredentialRequest` with the `prf` extension.
3. Wrap the same backup data key with the PRF output and append it to
   `wrappedKeys[]` — old backups stay readable, and either the passkey or the
   password can unlock new ones.

Today, backups use the `account-plain` wrap: the snapshot data key is wrapped
(AES-256-GCM) under a master key stored as a key file in the same
app-private `appDataFolder` — signing in to the Google account IS the access
control, WhatsApp-style. Legacy snapshots made under the earlier mandatory
password model (`password` wrap, PBKDF2-HMAC-SHA256 600k iterations) are
detected by their header and still prompt for that password on restore.

**Note where the user-held wrap sits (V2-5 / V2-46).** It is a layer *above*
this, not another entry in `wrappedKeys[]`: `MasterKeyVault` seals the master
key itself, so the snapshot format is untouched and every existing snapshot
stays readable when custody changes. That is also what makes rotation cheap —
reseal one small object, re-encrypt nothing. A passkey PRF secret, once the RP
domain above exists, drops in as a third `method` on the vault rather than as a
fourth snapshot wrap.

Restore UX: "Restore" lists the kept snapshots (the last 2). With one
snapshot it goes straight to the confirm dialog; with two, a chooser shows
date, source device, message count, size, and a "needs password" marker for
legacy snapshots — headers are read via a small Range request, so listing
never downloads full snapshots. Both backup and restore show live progress
(preparing n/m, encrypting, upload % / download %, decrypting, adding
messages).
