# Detective Dialer — Android App

Kotlin · MVVM · Hilt · Room · Coroutines · Jetpack Compose (Material 3).
The on-device half of the system: it intercepts incoming calls, decides
ring/reject locally in milliseconds, receives call summaries from the backend
via Firebase Cloud Messaging, and presents everything in a clean dashboard.

Covers Phases 3, 4, and 6 of the development plan. Target: **minSdk 29,
compileSdk 34**, tuned for the **iQOO Neo 9 Pro (FuntouchOS 15 / Android 15)**.

## What the app does

- **`CallScreeningService`** (`service/ScreeningService.kt`): on every incoming
  call it checks, in order — allowlist/contacts → ring; blocklist → silent
  reject; unknown → `POST /screen` to the backend, which classifies the number
  with Gemini and returns `ALLOW | REJECT | SPAM`. The app rejects the call
  silently only on **SPAM**; `ALLOW`/`REJECT` ring through. Either way it raises
  a notification with the AI's decision + reason.
- **Room database** (`data/local`): `blocked_numbers`, `allowed_numbers`,
  `call_log`.
- **FCM** (`service/AppFirebaseMessagingService.kt`): receives the backend's
  data-only push, caches a `CallLogEntry`, and raises a deep-linking
  notification (`detectivedialer://call/{id}`).
- **Foreground service + BootReceiver**: keep screening alive across FuntouchOS
  battery kills and reboots.
- **Compose UI** (`ui/`): Onboarding (backend URL + iQOO battery guide),
  Dashboard (today's stats + recent calls), Call Detail (call-back/block/allow),
  Settings (name, address, language, persona, backend URL), Blocklist +
  Allowlist managers.

## Project layout

```
android/
├── settings.gradle.kts, build.gradle.kts, gradle.properties
├── gradle/libs.versions.toml          # version catalog
├── gradlew, gradlew.bat, gradle/wrapper/…   # Gradle wrapper (8.9)
└── app/
    ├── build.gradle.kts, proguard-rules.pro
    ├── google-services.json           # PLACEHOLDER — replace with your own
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/                       # theme, icons, strings
        └── java/com/personal/detectivedialer/
            ├── DetectiveDialerApp.kt    # @HiltAndroidApp + notif channels
            ├── di/AppModule.kt
            ├── data/{local,remote,prefs,repository}
            ├── service/               # screening, FCM, foreground, boot
            └── ui/                    # Compose screens + viewmodels
```

## Before you build

1. **Install the Android SDK** (one-time). With Android Studio installed, the SDK
   lands at `~/Library/Android/sdk`. Or via command line:
   ```bash
   sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
   ```
   Then create `android/local.properties`:
   ```
   sdk.dir=/Users/<you>/Library/Android/sdk
   ```
   (Android Studio writes this automatically when you open the project.)

2. **Add your Firebase config.** Create a Firebase project, register an Android
   app with package `com.personal.detectivedialer`, download the real
   `google-services.json`, and replace `app/google-services.json` (a placeholder
   is committed so the project configures out of the box). See
   `app/google-services.json.template`.

3. **Set the backend URL** in the app's onboarding/Settings once installed
   (or enter it at first launch).

## Build

See **[BUILD_AND_SIDELOAD.md](BUILD_AND_SIDELOAD.md)** for the full sideload
guide, ADB permission-grant steps, and the end-to-end test checklist.

Quick version:
```bash
cd android
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```
