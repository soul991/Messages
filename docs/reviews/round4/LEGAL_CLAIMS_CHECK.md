# Legal Claims Verification Matrix

This document provides line-by-line verification for every claim in the Privacy Policy and Terms and Conditions against the codebase, `AndroidManifest.xml`, and dependency declarations.

## 1. Permissions Verification

| Claimed Permission | AndroidManifest.xml Line | Purpose in Code | Implementation File / Evidence |
|---|---|---|---|
| `RECEIVE_SMS` | `app/src/main/AndroidManifest.xml:15` | Intake inbound SMS messages | `com.messages.app.receiver.SmsReceiver` |
| `READ_SMS` | `app/src/main/AndroidManifest.xml:16` | Synchronize system telephony message store | `com.messages.core.sync.TelephonySync` |
| `SEND_SMS` | `app/src/main/AndroidManifest.xml:17` | Dispatch outbound user messages | `com.messages.core.telephony.SmsSender` |
| `RECEIVE_MMS` | `app/src/main/AndroidManifest.xml:18` | Intake inbound multimedia messages | `com.messages.app.receiver.MmsReceiver` |
| `READ_CONTACTS` | `app/src/main/AndroidManifest.xml:19` | Query contact names and local avatar URIs | `com.messages.core.contacts.ContactRepository` |
| `POST_NOTIFICATIONS` | `app/src/main/AndroidManifest.xml:20` | Surface notifications for incoming messages | `com.messages.app.notification.NotificationHelper` |
| `READ_BASIC_PHONE_STATE` | `app/src/main/AndroidManifest.xml:21` | Identify multi-SIM subscriptions for message routing | `com.messages.app.telephony.SubscriptionManager` |
| `READ_PHONE_STATE` | `app/src/main/AndroidManifest.xml:23` | Legacy multi-SIM detection (maxSdkVersion 32) | `app/src/main/AndroidManifest.xml:24` |
| `USE_BIOMETRIC` | `app/src/main/AndroidManifest.xml:25` | BiometricPrompt authentication for App Lock and Vault | `com.messages.app.lock.AppLock:224` |
| `INTERNET` | `app/src/main/AndroidManifest.xml:27` | Encrypted Drive backup, link preview, GitHub updates | `SafeHttp.kt:43`, `DriveClient.kt:93`, `UpdateCheck.kt:36` |
| `REQUEST_INSTALL_PACKAGES` | `app/src/main/AndroidManifest.xml:29` | Prompt user to install downloaded APK update | `com.messages.app.update.AppUpdateManager:188` |

---

## 2. On-Device Storage & Local Processing

| Privacy Claim | Code Verification | File Reference |
|---|---|---|
| Message history stored locally in private SQLite database | Messages stored in Room database `messages_db` in app sandbox | `com.messages.core.db.MessagesDatabase` |
| Vault (Locked Space) encrypted on-device | Encrypted with AES-256-GCM; key derived via PBKDF2/Argon2 locally | `com.messages.core.secret.SecretCrypto.kt:25` |
| Android Cloud Backup disabled | `android:dataExtractionRules` and `android:fullBackupContent` default-deny cloud and D2D backups | `app/src/main/AndroidManifest.xml:38-45` |
| On-device heuristic scoring | Rule-based heuristics evaluate senders and patterns on-device | `protection-engine/src/main/kotlin/com/messages/protection/` |
| On-device AI scoring status | Machine learning scoring model is currently disabled | `AiScorer.kt:227` (`const val AI_LAYER_ENABLED = false`) |

---

## 3. Network Outbound Destinations & Data Flow

| Outbound Feature | Protocol / Restrictions | Destination / Provider | Code Reference |
|---|---|---|---|
| **Google Drive Backup** | HTTPS TLS 1.3 only; hardcoded `https://www.googleapis.com` endpoints | Google LLC (`googleapis.com`) | `app/src/main/kotlin/com/messages/app/drive/DriveClient.kt:93, 140` |
| **Link Previews** | HTTPS port 443 only; public IP check; private IP/SSRF blocked; cookies & redirects disabled | Target URL web server | `app/src/main/kotlin/com/messages/app/net/SafeHttp.kt:43`, `LinkPreview.kt:81` |
| **Update Check** | HTTPS GET; public DNS check | GitHub, Inc. (`api.github.com/repos/soul991/Messages/releases/latest`) | `app/src/main/kotlin/com/messages/app/update/UpdateCheck.kt:36` |
| **Spam Reporting** | Cellular SMS dispatch to carrier/regulator short code (`1909` or `7726`); no HTTP traffic | User's cellular carrier / TRAI regulator | `app/src/main/kotlin/com/messages/app/report/CarrierReportFormat.kt:16` |

---

## 4. Negative Claims (What We Never Do)

| Claim | Verification Method | Result |
|---|---|---|
| **No User Accounts** | Inspected `MainActivity.kt` and `OnboardingScreen.kt` | No account registration or server authentication exists. |
| **No Analytics SDKs** | Grepped `app/build.gradle.kts` and `gradle/libs.versions.toml` | Zero analytics dependencies (no Firebase Analytics, Google Analytics, Mixpanel, Segment, Flurry). |
| **No Crash Tracking SDKs** | Grepped dependency list | Zero crash reporting dependencies (no Crashlytics, Sentry, Bugsnag). |
| **No Advertising SDKs** | Grepped dependency list | Zero ad networks (no AdMob, Facebook Audience Network, Unity Ads). |
| **No Data Sale / Transfer** | Inspected all network calls (`git grep 'https://'`) | Only 3 outbound destinations exist: `googleapis.com` (user backup), `github.com` (updates), and target preview URLs. |
| **No Cloud AI Upload** | Grepped all AI/protection code | Message bodies never leave device for classification; model runs or refuses locally. |

---

## 5. Terms & Conditions Claims

| Claim | Verification | Reference |
|---|---|---|
| Open Source License | App is open source under GNU GPL v3 | `LICENSE` file in repository root |
| Public Source Code | Public GitHub repository | `https://github.com/soul991/Messages` |
| No Warranty / As-Is | Best-effort spam and scam heuristics | Heuristic and regex matching cannot guarantee 100% scam interception. |
| Issue Reporting | GitHub Issues tracker | `https://github.com/soul991/Messages/issues` |
