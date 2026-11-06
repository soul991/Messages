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
| Vault (Locked Space) encrypted on-device | Encrypted with AES-256-GCM; key derived via PBKDF2-HMAC-SHA256, 600,000 iterations | `com.messages.core.secret.SecretCrypto.kt:15, 27` |
| Android Cloud Backup disabled | `android:allowBackup="false"`, `dataExtractionRules`, and `fullBackupContent` default-deny cloud and D2D backups | `app/src/main/AndroidManifest.xml:60-62`, `app/src/main/res/xml/backup_rules.xml`, `app/src/main/res/xml/data_extraction_rules.xml` |
| On-device heuristic scoring | Rule-based heuristics evaluate senders and patterns on-device | `protection-engine/src/main/kotlin/com/messages/protection/` |
| On-device AI scoring status | Machine learning scoring model is currently disabled | `AiScorer.kt:227` (`const val AI_LAYER_ENABLED = false`) |

---

## 3. Network Outbound Destinations & Data Flow

| Outbound Feature | Protocol / Restrictions | Destination / Provider | Code Reference |
|---|---|---|---|
| **Google Drive Backup** | HTTPS only; TLS version is chosen by the Android platform; hardcoded `https://www.googleapis.com` endpoints | Google LLC (`googleapis.com`) | `app/src/main/kotlin/com/messages/app/drive/DriveClient.kt:93, 140, 158, 173, 179` |
| **Link Previews** | HTTPS port 443 only (`SafeHttp.kt:94`); public IP check (`SafeHttp.kt:64`); private IP/SSRF blocked (`SafeHttp.kt:107-129`); cookies disabled (`SafeHttp.kt:79`); automatic redirects disabled with manual re-validation up to 3 hops (`SafeHttp.kt:77-78, 154`) | Target URL web server | `app/src/main/kotlin/com/messages/app/net/SafeHttp.kt:64, 77-79, 94, 107-129, 154` |
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

---

## 6. Claims that no code enforces

1. **"HTTPS TLS 1.3 only"**:
   - **Verification**: `grep -rn -E -i "TLSv1|TLS1|SSLContext|sslSocketFactory|TlsVersion" --include=*.kt app/src/main core-messaging/src/main` returned 0 matches.
   - **Finding**: No code or network configuration enforces TLS 1.3 exclusively. The code enforces HTTPS only (`SafeHttp.kt:94` requires `url.isHttps` and `url.port == 443`; `DriveClient.kt:93, 140, 158, 173, 179` uses hardcoded `https://www.googleapis.com/...` URLs; `network_security_config.xml` sets `cleartextTrafficPermitted="false"`). The specific TLS protocol version is negotiated and chosen by the Android platform runtime and BoringSSL/Conscrypt provider.
   - **Correction**: Section 3 table was updated from "HTTPS TLS 1.3 only" to "HTTPS only; TLS version is chosen by the Android platform".

2. **"Argon2"**:
   - **Verification**: `grep -n -E -i "PBKDF2|Argon" core-messaging/src/main/kotlin/com/messages/core/secret/SecretCrypto.kt` returned matches for PBKDF2 only (lines 15, 38, 42, 56) with `const val ITERATIONS = 600_000` (line 27). Zero references to Argon2 exist in the codebase.
   - **Finding**: Key derivation is performed using PBKDF2-HMAC-SHA256 with 600,000 iterations. Argon2 is not implemented.
   - **Correction**: Section 2 table was updated from "PBKDF2/Argon2" to "PBKDF2-HMAC-SHA256, 600,000 iterations".

3. **Verification of other specific claims against code**:
   - **"cookies and redirects disabled" for link previews**: Enforced by code in `SafeHttp.kt:77-79` (`.followRedirects(false).followSslRedirects(false).cookieJar(CookieJar.NO_COOKIES)`) and `SafeHttp.kt:151-155` (manual loop re-validating every redirect hop up to 3 hops via `validateUrl`).
   - **"private IP / SSRF blocked"**: Enforced by code in `SafeHttp.kt:64` (`PublicOnlyDns` throws `UnknownHostException` if any resolved address is not public) and `SafeHttp.kt:107-129` (`isPublicAddress` blocks loopback, link-local, site-local, multicast, IPv6 unique-local `fc00::/7`, CGNAT `100.64/10`, `192.0.0/24`, `198.18/15` benchmark, and `240/4` reserved/broadcast).
   - **"default-deny cloud backup"**: Enforced in manifest and XML rules (`AndroidManifest.xml:60-62` sets `android:allowBackup="false"`, `android:fullBackupContent="@xml/backup_rules"`, and `android:dataExtractionRules="@xml/data_extraction_rules"`; both XML rule files specify empty inclusion elements blocking cloud backup and device-to-device transfers).
   - **Other claims**: None found unbacked by code. All other claims in Sections 1 through 5 correspond directly to code, manifest entries, or gradle dependencies as verified by command output.

