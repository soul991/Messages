# Play Protect & Sideloading Guide

This guide explains two Android security behaviors encountered when testing and sideloading the Messages APK:
1. **Google Play Protect Financial Fraud Detection** (`App blocked to protect your device`)
2. **Android 13/14+ Restricted Settings** (`App was denied access to be default SMS app`)

---

## 1. Google Play Protect: "App blocked to protect your device"

### What happened
When installing `Messages-v1.2.0.apk` directly from WhatsApp, Google Play Protect showed:
> **Google Play Protect**  
> **App blocked to protect your device**  
> *Messages*  
> *This app can request access to sensitive data. This can increase the risk of identity theft or financial fraud.*  
> `[OK]`

### Why this happens
In 2024, Google introduced an **enhanced financial fraud protection pilot** in several regions (India, Singapore, Thailand, Brazil). Under this policy:
- Play Protect automatically blocks the installation of any app sideloaded from **Internet-browsing sources** (e.g. WhatsApp, Telegram, web browsers, third-party file downloaders).
- This block triggers if the app declares any of 4 sensitive permissions:
  1. `RECEIVE_SMS`
  2. `READ_SMS`
  3. `BIND_Notifications` (`BIND_NOTIFICATION_LISTENER_SERVICE`)
  4. `Accessibility` (`BIND_ACCESSIBILITY_SERVICE`)
- Because banking malware frequently uses WhatsApp APK sharing to steal SMS OTPs, Play Protect hard-blocks these APKs when received through messaging apps.
- Since `Messages` is an SMS app, it legitimately requires SMS intake permissions (`RECEIVE_SMS`, `READ_SMS`).

### How to install for testing

#### Method A: Temporarily toggle Play Protect scanning (Recommended for on-phone testing)
1. Open **Google Play Store**.
2. Tap your **Profile Picture** in the top right.
3. Tap **Play Protect**.
4. Tap the **Settings (gear icon)** in the top right.
5. Toggle **OFF** "Scan apps with Play Protect" (or "Improve harmful app detection").
6. Return to WhatsApp / Downloads and install `Messages.apk`.
7. Once installed, you can re-enable Play Protect.

#### Method B: Install via ADB (Recommended for developers)
ADB installs do not originate from an Internet-browsing source, so Play Protect's financial fraud heuristic does not block them:
```sh
adb install -r Messages.apk
```

#### Method C: Play Protect Developer Appeal
If distributing pre-release APKs outside the Play Store, submit the APK hash and package name to Google for verification:
- [Google Play Protect Appeals Form](https://support.google.com/googleplay/android-developer/contact/protectappeals)

---

## 2. Android 13/14+: "App was denied access to be default SMS app"

### What happened
When opening the app or tapping "Set as default", Android showed:
> **App was denied access to be default SMS app**  
> *The app requested access to sensitive permissions which can put your personal and financial info at risk. It's possible that the app won't work properly without these restricted permissions. Learn how to allow access.*  
> `[Close]`

### Root Cause
1. **Premature onCreate invocation (Fixed)**: On cold start, `MainActivity` was immediately calling `requestDefaultRole()` and `requestCorePermissions()` before the user even advanced past the Intro onboarding screen. This triggered the denial dialog over the first screen.
2. **Restricted Settings on Sideloaded Apps**: On Android 13 (API 33) and above, apps installed outside of app stores (sideloaded from WhatsApp/browser) are placed in "Restricted Settings" mode. Android blocks setting them as Default SMS or granting Accessibility/SMS permissions until the user explicitly unlocks the app in System Settings.

### The Solution in Code
- **No premature prompts**: The app now waits until the user explicitly taps "Set as default" on the onboarding screen or home gate.
- **Restricted settings detection**: The app checks `AppOpsManager.unsafeCheckOpNoThrow("android:access_restricted_settings", ...)` and tracks role request refusals.
- **Guided UI card**: When restricted, `OnboardingScreen` and `HomeScreen` render an in-app guidance card with direct action buttons:
  - **Open App Settings**: Directly opens App Info where the user taps `⋮` -> "Allow restricted settings" -> verifies PIN/fingerprint.
  - **Default Apps**: Opens system Default Apps settings to choose Messages directly.
  - **Retry Set as Default**: Re-triggers the system role chooser once restricted settings have been allowed.

---

## 3. Manifest Optimizations Applied
- **Removed `RECEIVE_WAP_PUSH`**: Default MMS intake only requires `RECEIVE_MMS` and the broadcast receiver `WAP_PUSH_DELIVER`. The dangerous uses-permission `RECEIVE_WAP_PUSH` is no longer declared, removing a major security scanner flag.
- **Scoped `READ_PHONE_STATE`**: Added `READ_BASIC_PHONE_STATE` for API 33+ (normal permission for multi-SIM subscription info) and capped `READ_PHONE_STATE` to `maxSdkVersion="32"`, ensuring Android 13+ devices do not request high-risk phone state permissions.
