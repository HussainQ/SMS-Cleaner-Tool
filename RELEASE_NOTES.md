# ⚠️ CleanSMS `v1.7.0-pre.1` — Non-Production Pre-Release

> [!WARNING]
> **NON-PRODUCTION BUILD — FOR LOCAL DEVICE TESTING PURPOSES ONLY**
>
> This release is strictly a **Pre-Release (`prerelease: true`)** intended exclusively for **local device evaluation, QA verification, and sandbox testing**.
> - **DO NOT** deploy or distribute this build to production environments or commercial app stores.
> - **In-Place Upgrades from `v1.6.1-pre.1` / `v1.6.2-pre.1`:** Installs directly over `v1.6.1-pre.1` and `v1.6.2-pre.1` (using `com.aistudio.cleansms.jrvxpq` and the persistent CI/debug signing keystore). If you still have an older release under a different package ID or an earlier ephemeral keystore, uninstall it first before installing `v1.7.0-pre.1` to avoid Android's *"App not installed as package conflicts with an existing package"* error.

---

## 📋 Release Metadata

| Field | Value |
| :--- | :--- |
| **Release Tag** | `v1.7.0-pre.1` |
| **Release Type** | `Pre-Release` (`prerelease: true`, `make_latest: false`) |
| **Target Environment** | Non-Production • Local Device Testing Only |
| **Application ID** | `com.aistudio.cleansms.jrvxpq` |
| **Version Name** | `1.7.0-pre.1` |
| **Version Code** | `10700` |
| **Room Schema Version** | `v5` (automatic non-destructive migrations `v1 → v5`) |
| **Min / Target SDK** | API 24 (Android 7.0) / API 36 (Android 16) |
| **Artifact Name** | `CleanSMS-v1.7.0-pre.1-nonprod-local-test.apk` |

---

## ✨ What's New in `v1.7.0-pre.1`

### 1. Full SMS, MMS & RCS Messaging Landing Hub (`Messages` Tab)
- Added a dedicated **Messages** tab as the **default landing page** when CleanSMS opens, enabling users to view, read, compose, and send standard SMS, MMS (with Android Photo Picker image attachments & multipart PDU encoding), and RCS chat conversations seamlessly alongside inbox cleaning.
- Handles `SENDTO` / `SEND` (`sms:`, `smsto:`, `mms:`, `mmsto:`) external intents, incoming carrier SMS/MMS notifications, and adaptive wide-screen List-Detail split layouts.

### 2. Top Conversation Search Bar & Protocol Filters (`All` / `SMS` / `MMS` / `RCS`)
- Added a quick **top search bar** at the very top of the **Messages** tab to filter SMS, MMS, and RCS threads in real time by contact/sender name, phone number, message body content, or MMS subject.
- Added instant protocol filter chips (**All**, **SMS**, **MMS**, **RCS**) directly beneath the top search bar, with hierarchical Android Back button support (Overlay → Active Conversation → Search Query → Protocol Filter → Tab History).

### 3. Compact Messages UI Density & Synchronized Settings Option
- Streamlined the **Messages** tab layout with a cleaner **Compact UI** mode enabled by default (slimmer conversation rows, compact headers & message bubbles, 1-line previews, and compact FAB) so more threads fit on screen.
- Added a persisted **Compact Messages UI** setting (`Compact` vs `Comfortable`) accessible both via the **tune/settings icon** right next to the Messages top search bar and in **Rules & Cloud > Settings**.

---

## 📲 Local Device Testing Instructions

1. **Direct In-App Update (from `v1.6.1-pre.1` / `v1.6.2-pre.1`):**
   - Open **Updates** in CleanSMS, ensure **Include Pre-Releases** is ON, check `HussainQ/SMS-Cleaner-Tool`, and tap **Download & Install** directly inside the app.
2. **Manual Download & Verify the APK:**
   - Download `CleanSMS-v1.7.0-pre.1-nonprod-local-test.apk` and its `.sha256` file from the GitHub Release assets.
   ```bash
   sha256sum -c CleanSMS-v1.7.0-pre.1-nonprod-local-test.apk.sha256
   ```
3. **Install / Upgrade on Local Android Device:**
   ```bash
   adb install -r CleanSMS-v1.7.0-pre.1-nonprod-local-test.apk
   ```
