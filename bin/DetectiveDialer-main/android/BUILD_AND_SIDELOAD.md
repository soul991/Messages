# Build & Sideload Guide — iQOO Neo 9 Pro

This guide takes you from source to a working install on your phone, then
verifies the whole system end to end. (Phase 6.)

---

## 0. Prerequisites (one-time)

| Need | How |
|---|---|
| **JDK 17** | `java -version` should show 17. Android Studio bundles one. ⚠️ If a terminal build fails with *"JAVA_HOME is set to an invalid directory"*, export a valid one, e.g. `export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`. |
| **Android SDK** | Install via Android Studio, or `sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"` |
| **`local.properties`** | In `android/`, add `sdk.dir=/Users/<you>/Library/Android/sdk` (Android Studio writes this for you). |
| **Real `google-services.json`** | From your Firebase project → replace `app/google-services.json`. |
| **Backend deployed** | See `backend/README.md` (Railway). Note its public URL. |

---

## 1. Build the debug APK

> ✅ A debug APK has already been built once at
> `app/build/outputs/apk/debug/app-debug.apk` (~65 MB). Rebuild any time with the
> command below.

From the `android/` directory:

```bash
./gradlew assembleDebug
```

Output:
```
app/build/outputs/apk/debug/app-debug.apk
```

> First build downloads Gradle 8.9 and all dependencies — give it a few minutes.

To rebuild cleanly: `./gradlew clean assembleDebug`.

---

## 2. Enable installation on the iQOO Neo 9 Pro

1. **Settings → Apps → Install unknown apps** → enable for your file manager /
   browser / ADB.
2. **Enable Developer Options:** Settings → About phone → tap **Software version**
   7 times.
3. **Settings → System → Developer options** → enable **USB debugging**
   (and **Install via USB** + **USB debugging (Security settings)** on iQOO).

---

## 3. Install via ADB

Connect the phone over USB and accept the debugging prompt, then:

```bash
adb devices                      # confirm your phone is listed
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

(Or copy the APK to the phone and tap it in a file manager.)

---

## 4. Grant permissions (in-app onboarding)

Open the app — onboarding walks you through everything:

1. **Step 2 (Connect):** enter your **Backend URL** (validated; `https://` is
   assumed if you omit the scheme) and the optional **API key** if your backend
   sets `API_KEY`.
2. **Step 3 (Permissions):** tap **Set as call screening app** (system role
   dialog) and **Grant permissions** (Contacts + Notifications). The checklist
   ticks turn green as each one lands.
3. **Step 4 (Battery):** whitelist the app (see Step 5 below).

The FCM token is fetched and registered with the backend automatically on
startup — no manual copying.

<details>
<summary>ADB fallback (only if the in-app dialogs fail on your OEM build)</summary>

```bash
PKG=com.personal.detectivedialer
adb shell pm grant $PKG android.permission.READ_PHONE_STATE
adb shell pm grant $PKG android.permission.READ_CONTACTS
adb shell pm grant $PKG android.permission.READ_CALL_LOG
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
# ANSWER_PHONE_CALLS is signature/role-gated; set the call-screening role:
adb shell cmd role add-role-holder android.app.role.CALL_SCREENING $PKG
```

Or manually: **Settings → Apps → Default apps → Caller ID & spam app →
Detective Dialer.**
</details>

---

## 5. iQOO / FuntouchOS battery whitelist (critical)

FuntouchOS will kill background apps aggressively. In the app's **Onboarding →
Step 3** tap the buttons, or manually:

- **Settings → Battery → High background power consumption / Background power** →
  set **Detective Dialer** to **Allowed / Unrestricted**.
- **Settings → Apps → Detective Dialer → Battery → Unrestricted**.
- **Settings → Apps → Auto-start manager** → enable **Detective Dialer**.

---

## 6. Configure the backend connection

Done during onboarding (Step 2 above); editable later in **Settings →
Connection**. No call forwarding or carrier USSD codes are needed — the app
screens calls on the device and asks the backend to classify them.

The **FCM token** registers itself with the backend (`POST /api/device`) on app
start and whenever Firebase rotates it. Settings still displays the token as a
fallback in case you ever want to set `FCM_DEFAULT_DEVICE_TOKEN` manually.

---

## 7. End-to-end test checklist

| # | Test | Expected |
|---|------|----------|
| 1 | Call from a **saved contact** | Phone rings normally ✓ |
| 2 | Call from a **blocked number** | Rejected silently, no notification ✓ |
| 3 | Call from an **unknown number** | App posts to `/screen`; decision shown as a notification ✓ |
| 4 | Unknown number the AI flags as **spam** | Call rejected silently on device; "SPAM" notification with reason ✓ |
| 4b | Unknown number the AI marks **REJECT** | Phone does **not audibly ring** (silenced), call still reaches the call log; "REJECT" notification ✓ |
| 4c | Backend stopped/unreachable, unknown number calls | Phone rings normally within ~4s (fail-open timeout) ✓ |
| 5 | After a screened call | **Notification** arrives with the decision + reason ✓ |
| 6 | Reboot the phone, then receive a call | Screening still works (BootReceiver + foreground service) ✓ |
| 7 | Leave app idle overnight, then receive a call | Survives FuntouchOS battery kill (whitelist) ✓ |
| 8 | Tap a notification | Opens the **Call Detail** screen (deep link) ✓ |
| 9 | Same spammer calls 3× | Auto-added to blocklist; "auto-blocked" reason in the notification ✓ |

### Quick checks without a real call

- Simulate an FCM push from the Firebase console (Cloud Messaging → "Send test
  message" to your device token) → a notification should appear and a call row
  should show up on the Dashboard.
- Add/remove numbers in **Blocklist**/**Allowlist** → they persist (Room) and
  sync to the backend if a Backend URL is set.

---

## Troubleshooting

- **App isn't screening calls:** confirm it's the default *Caller ID & spam app*
  (Step 4) and that `READ_PHONE_STATE` is granted.
- **No notifications:** check `POST_NOTIFICATIONS` is granted, the FCM token is set
  in the backend env, and `google-services.json` is your real file.
- **Killed in background:** re-check the battery whitelist + auto-start (Step 5).
- **Gradle can't find the SDK:** create `android/local.properties` with `sdk.dir`.
