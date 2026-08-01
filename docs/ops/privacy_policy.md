# Privacy Policy

**Effective date:** _[set to the date this policy is first published]_
**Applies to:** Messages for Android, version 1.0 (`com.messages.app`)
**Operator:** _[legal or individual name of the publisher]_
**Contact:** _[a monitored address you control]_

> **Before publishing:** the four bracketed fields above must be filled in with
> real values. A policy that names no accountable operator and gives no working
> contact channel is not a publishable policy, and app stores will reject it.

This policy describes exactly what Messages does with your data. Where the app
sends something off your device, this document says so plainly, says what is
sent, and says who receives it.

## 1. What stays on your device

Everything, unless you switch on one of the optional features in section 3.

- **Message content.** SMS and MMS live in the Android Telephony provider (the
  system message store) and in a local database the app maintains alongside it.
- **The protection engine.** Spam filtering, fraud detection and categorisation
  run entirely on your device against a pattern library bundled with the app.
  No message text is sent anywhere to be classified, and there is no server-side
  model.
- **Derived data.** Categories, protection labels, matched pattern IDs, the
  explanations shown on the "Why?" screen, sender reputation counters, your
  custom rules, and a normalised copy of message text used for local search.
- **Contacts.** Read from the device to show names and photos. Matched contact
  names are cached in the local database so the conversation list can render
  without re-reading contacts each time.
- **Locked space.** Conversations you move into the secret space are gated by a
  separate credential and excluded from every normal-surface query,
  notification, widget and shortcut. Their **message text is encrypted at rest**
  (AES-256-GCM) under a key held in the Android Keystore, which never leaves the
  device, and the copy the system message store held is **deleted** when a
  conversation is locked — so a locked chat's words are not readable from a
  device image, a filesystem dump, or another SMS app. What is *not* encrypted,
  because the app has to join on it: the correspondent's phone number, message
  timestamps, and read/sent state. Locking is therefore a claim about *what was
  said*, not about *who you said it to or when*. Two consequences follow and are
  stated on screen when you set the space up: a locked chat no longer exists
  outside this app, so it will not appear in another messaging app and reaches a
  new phone only via section 3.1; and if you forget the credential there is no
  recovery path.

There is **no analytics SDK, no crash reporter, no advertising identifier and no
telemetry** of any kind in this app.

## 2. Permissions and why they are needed

| Permission | Why |
|---|---|
| SMS / MMS (read, send, receive, WAP push) | Required of any app that acts as your default messaging app |
| Contacts (read) | Match phone numbers to saved contacts for names and photos |
| Notifications | Alert you to new messages |
| Phone state (read) | Identify SIM subscriptions on dual-SIM devices |
| Biometric | The optional app lock and the locked space |
| Internet | **Only** for the optional features in section 3. With both switched off, the app makes no network requests |

## 3. What leaves your device, and only if you turn it on

### 3.1 Google Drive backup — off by default

If you enable Drive backup:

- **What is sent:** an encrypted snapshot containing your message text and
  metadata (addresses, timestamps, read/sent state), conversation records
  including cached contact names, your categories and protection labels, your
  custom rules and sender reputation. Media attachments are included only if you
  enable that option; spam is included according to your spam setting; locked
  space content is included inside a separately encrypted sub-envelope.
- **Where it goes:** the application-private `appDataFolder` of **your own**
  Google Drive account. The app requests the `drive.appdata` scope only, which
  means it cannot see, read or modify any other file in your Drive.
- **Encryption:** each snapshot is encrypted with a fresh random data key using
  AES-256-GCM. That data key is wrapped under a random master key.
- **Key custody — read this carefully.** You choose who holds the master key.
  Both choices encrypt the snapshots identically; what differs is what an
  attacker needs in order to open them.

  **Your Google Account (the default).** The master key is stored **in the same
  Drive `appDataFolder` as the snapshots**. This is deliberate: it is what lets
  you restore on a new phone by signing in, with no password to remember. The
  direct consequence is that **anyone who can sign in to your Google account can
  restore your backup.** Your backups are protected from an attacker who
  obtains the files alone; they are **not** protected from someone who controls
  your Google account. We do not claim otherwise. Protect the Google account
  with a strong password and two-factor authentication.

  **A code only you have (opt-in).** In backup settings you can lock the master
  key with a recovery code the app generates, or with a password you choose.
  The key is then stored on Drive only in locked form, and **the unprotected
  copy is deleted**. The code itself never leaves your phone and is never sent
  anywhere, so signing in to your Google account is no longer enough to open a
  backup. Three things follow, and all three are on screen when you turn it on:
  - **Nobody can reset the code.** We have no copy and no server. If you lose
    it and no phone still holds the unlocked key, the backups cannot be opened
    by anyone, including us.
  - **A password is weaker than the generated code.** Because we run no server,
    there is nothing to rate-limit guesses: someone holding a backup file can
    try passwords offline as fast as their hardware allows. The key-stretching
    used (PBKDF2-HMAC-SHA256, 600,000 iterations) raises that cost but does not
    remove it. The generated recovery code is 160 random bits and is not
    guessable.
  - **Changing the code does not re-encrypt anything.** It re-locks the same
    master key, so existing backups keep working and the old code stops working.
    Turning the protection off puts the key back into Drive in the clear.

  Older backups created with a password are still restored with that password.
- **Retention:** the app keeps the **two most recent** snapshots in your Drive
  and deletes older ones automatically. The master key file — locked or not — is
  never pruned.
- **Who else is involved:** Google, as the provider of Google Sign-In and Google
  Drive. Their handling of the data is governed by Google's own privacy policy.
  We operate no server and receive nothing.

### 3.2 Link previews — off by default

If you enable link previews, the app fetches the page a link points to in order
to show its title, description and image.

- That request goes **directly to the third-party website named in the link**,
  not through us. That site will see your IP address, the approximate time, and
  the fact that someone opened a conversation containing its link. If the page
  declares a preview image, the image host sees the same.
- Previews are fetched over HTTPS only, carry no cookies and no identifying
  headers, and are never fetched for messages classified as spam, dangerous or
  fraudulent, nor for anything outside your Inbox.
- If this trade-off is not one you want, leave the setting off. It is off unless
  you turn it on.

### 3.3 Reporting spam to your carrier — you initiate each one

When you choose to report a message to your mobile operator, the app composes an
SMS **you send** to an industry short code — `1909` (TRAI, India) or `7726`
(GSMA, international). That message contains **the reported message's text and
the sender's number**, because that is what the reporting scheme requires. It
goes to your mobile carrier over the cellular network. Nothing is reported
automatically and nothing is sent without your explicit action.

## 4. Pattern library updates

The bundled pattern library ships inside the app and updates only when you
install a new version. You may also import a pattern pack file yourself. Neither
path sends any information about your messages anywhere, and imported packs are
validated and size-limited before use.

## 5. Features this app does **not** have

- **RCS is not supported.** The app handles SMS and MMS only. Any earlier
  statement that RCS messages are stored was incorrect.
- No cloud sync, no web client, no companion service, no account with us — there
  is no "us" to hold an account with.

## 6. Deleting your data

- **On the device:** deleting a conversation removes it from the app's database
  and from the system message store. Deleted items sit in Trash for 60 days and
  are then purged permanently. Uninstalling the app removes its local database
  and all its settings.
- **In Drive:** deleting the app does **not** delete your Drive backups. To
  remove them, either turn off backup in the app and choose to delete existing
  backups, or revoke the app's access in your Google account settings at
  <https://myaccount.google.com/permissions>, which removes its `appDataFolder`
  and everything in it.
- **Android's own backup:** the app opts out of Android Auto Backup and
  device-to-device transfer, so its private data is not copied into a Google
  account by the platform.

## 7. Children

This app is not directed at children and collects nothing to direct at anyone.

## 8. Your rights

Because the operator receives no personal data, there is no data held about you
to request, correct, export or erase. All of your data is in your hands: on your
device, and — if you enabled backup — in your own Google account. Rights you
hold under the GDPR, the UK GDPR, the CCPA/CPRA or India's DPDP Act attach to
the copies held by Google under their policy, and to your device.

## 9. Changes to this policy

Material changes will be reflected here with an updated effective date. Because
the app has no network channel back to us, please check this document after
updates.

## 10. Contact

_[the address named at the top of this document]_
