# ⚠️ CleanSMS `v1.7.1-pre.1` — Non-Production Pre-Release

> [!WARNING]
> **NON-PRODUCTION BUILD — FOR LOCAL DEVICE TESTING PURPOSES ONLY**
>
> This release is strictly a **Pre-Release (`prerelease: true`)** intended exclusively for **local device evaluation, QA verification, and sandbox testing**.
> - **DO NOT** deploy or distribute this build to production environments or commercial app stores.
> - **In-Place Upgrades from `v1.6.1-pre.1` / `v1.6.2-pre.1` / `v1.7.0-pre.1`:** Installs directly over prior `v1.6.1+` builds (using `com.aistudio.cleansms.jrvxpq` and the persistent CI/debug signing keystore).

---

## 📋 Release Metadata

| Field | Value |
| :--- | :--- |
| **Release Tag** | `v1.7.1-pre.1` |
| **Release Type** | `Pre-Release` (`prerelease: true`, `make_latest: false`) |
| **Target Environment** | Non-Production • Local Device Testing Only |
| **Application ID** | `com.aistudio.cleansms.jrvxpq` |
| **Version Name** | `1.7.1-pre.1` |
| **Version Code** | `10701` |
| **Room Schema Version** | `v6` (automatic non-destructive migrations `v1 → v6`) |
| **Min / Target SDK** | API 24 (Android 7.0) / API 36 (Android 16) |
| **Artifact Name** | `CleanSMS-v1.7.1-pre.1-nonprod-local-test.apk` |

---

## ✨ What's New in `v1.7.1-pre.1`

### 1. Cross-App Archived & Spam Folders (`Messages` Tab)
- Added dedicated **Inbox**, **Archived**, and **Spam** folder tabs directly inside **Messages**, isolating archived threads and spam/blocked conversations out of the main inbox.
- **Provider & Third-Party App Preservation:** Reads thread-level archive/spam flags from Android Telephony (`conversations?simple=true` / `archived`, `is_archived`, `spam`, `is_spam`, `spam_type`, `junk`, `blocked`, `folder`) and Android's system `BlockedNumberContract`, automatically persisting observed states in Room `v6` (`thread_folder_states`).
- **Stays Categorized Across Apps & Rescans:** Conversations archived or flagged as spam in Google Messages, CleanSMS, or another SMS client stay categorized in **Archived** / **Spam** across refreshes and app switches until you explicitly tap **Unarchive** or **Not Spam (Move to Inbox)** (saved with an explicit user-override guard).
- **Best-Effort Two-Way Telephony Sync:** Archiving/unarchiving in CleanSMS also writes back `archived` on standard Telephony conversation threads.

### 2. Direct One-Tap Compact UI Toggle
- Added a one-tap **Compact** toggle button directly beside the top conversation search bar in **Messages**, synced bidirectionally with **Messages Display Settings** and **Rules & Cloud > Settings**.

### 3. Hierarchical Back Button Support
- Extended Messages tab Back navigation to cleanly unwind: Overlay Dialog → Active Conversation → Search Query → Protocol Filter (`SMS`/`MMS`/`RCS`) → Folder Filter (`Archived`/`Spam` → `Inbox`) → Tab History Stack.

---

## 📲 Local Device Testing Instructions

1. **Direct In-App Update (from `v1.6.1-pre.1` / `v1.6.2-pre.1` / `v1.7.0-pre.1`):**
   - Open **Updates** in CleanSMS, ensure **Include Pre-Releases** is ON, check `HussainQ/SMS-Cleaner-Tool`, and tap **Download & Install** directly inside the app.
2. **Manual Download & Verify the APK:**
   - Download `CleanSMS-v1.7.1-pre.1-nonprod-local-test.apk` and its `.sha256` file from the GitHub Release assets.
   ```bash
   sha256sum -c CleanSMS-v1.7.1-pre.1-nonprod-local-test.apk.sha256
   ```
3. **Install / Upgrade on Local Android Device:**
   ```bash
   adb install -r CleanSMS-v1.7.1-pre.1-nonprod-local-test.apk
   ```
