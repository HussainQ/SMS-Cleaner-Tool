# ⚠️ CleanSMS `v1.5.0-pre.1` — Non-Production Pre-Release

> [!WARNING]
> **NON-PRODUCTION BUILD — FOR LOCAL DEVICE TESTING PURPOSES ONLY**
>
> This release is strictly a **Pre-Release (`prerelease: true`)** intended exclusively for **local device evaluation, QA verification, and sandbox testing**.
> - **DO NOT** deploy or distribute this build to production environments or commercial app stores.
> - Signed with local development testing credentials (`debugConfig`).
> - Includes a built-in **30+ message Demo Mode** so all deduplication, quarantine, and custom rule workflows can be tested safely before granting live SMS permissions.

---

## 📋 Release Metadata

| Field | Value |
| :--- | :--- |
| **Release Tag** | `v1.5.0-pre.1` |
| **Release Type** | `Pre-Release` (`prerelease: true`, `make_latest: false`) |
| **Target Environment** | Non-Production • Local Device Testing Only |
| **Application ID** | `com.aistudio.cleansms.jrvxpq` |
| **Version Name** | `1.5.0-pre.1` |
| **Version Code** | `10500` |
| **Min / Target SDK** | API 24 (Android 7.0) / API 36 (Android 16) |
| **Artifact Name** | `CleanSMS-v1.5.0-pre.1-nonprod-local-test.apk` (`app-debug.apk`) |
| **APK Size** | ~22 MB |
| **SHA-256 Checksum** | `8cbf4fe80690b1b53a0fecfd231303778ed61457713ec2505c4dc4dfabbc8994` |

---

## ✨ What's Included in `v1.5.0-pre.1`

### 1. Android Runtime & Manifest SMS Permissions (`READ_SMS`, `RECEIVE_SMS`, `WRITE_SMS`)
- Declared `READ_SMS`, `RECEIVE_SMS`, `WRITE_SMS`, and `RECEIVE_MMS` in `AndroidManifest.xml` alongside Default SMS App receivers (`SmsReceiver`, `MmsReceiver`, `HeadlessSmsSendService`).
- Implemented the full Jetpack Compose runtime permissions flow (`ActivityResultContracts.RequestMultiplePermissions`) with:
  - Educational rationale modal (`SmsPermissionRationaleDialog`)
  - Permanent denial recovery & system settings redirect (`SmsPermissionSettingsDialog`)
  - Safe fallback to **Demo Mode** if permissions are declined

### 2. Smart Duplicate Thread & RCS Chat Deduplication
- Detects duplicate messages within existing SMS and RCS threads (`threadId` grouping).
- Preserves original conversation thread anchors and RCS delivery/read statuses (`READ`, `DELIVERED`, `SENT`).
- Supports `KEEP_OLDEST` and `KEEP_NEWEST` retention strategies.

### 3. Local-Only On-Device OAuth Secrets Vault
- Pulls on-device OAuth tokens (`Google Drive`, `Dropbox`) **if and only if** the user explicitly authorizes the connection via `AuthorizeServiceDialog`.
- Stores credentials strictly inside the local Room database (`oauth_credentials`) with UI token masking and one-tap revocation/purge.
- Zero hardcoded or repository-tracked secrets.

### 4. Bug Fixes in This Pre-Release
- **Fixed `CleanSmsViewModel` Instantiation Crash**: Added `@JvmOverloads` and `CleanSmsViewModel.Factory` companion builder to resolve `RuntimeException: Cannot create an instance of class com.example.ui.CleanSmsViewModel` on startup.
- **TopAppBar Pre-Release Indicator**: Added a visible `PRE-RELEASE v1.5.0-pre.1 • LOCAL TEST ONLY` badge in both phone and tablet/foldable layouts so testers can immediately distinguish non-production builds on local hardware.

---

## 📲 Local Device Testing Instructions

1. **Download or Export the APK**:
   - Download `CleanSMS-v1.5.0-pre.1-nonprod-local-test.apk` from the GitHub Pre-Release assets (or export the APK directly from AI Studio's Settings menu).
2. **Verify Checksum (Optional)**:
   ```bash
   sha256sum -c CleanSMS-v1.5.0-pre.1-nonprod-local-test.apk.sha256
   ```
3. **Install on Local Android Device**:
   - Transfer the APK to your local Android phone/tablet and open it (allow "Install unknown apps" if prompted), or install via USB debugging:
     ```bash
     adb install -r CleanSMS-v1.5.0-pre.1-nonprod-local-test.apk
     ```
4. **Testing Modes**:
   - **Safe Sandbox Testing (Default)**: Launch the app and keep **Demo Mode** enabled to test thread deduplication, OTP filtering, Quarantine Vault restore, and Custom Rules on 30+ simulated SMS/RCS messages without touching personal messages.
   - **Live Inbox Testing**: Tap **Grant Permissions** on the scanner banner to grant `READ_SMS` and `WRITE_SMS` and test live inbox scanning on your local device.
