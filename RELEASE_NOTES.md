# ⚠️ CleanSMS `v1.6.2-pre.1` — Non-Production Pre-Release

> [!WARNING]
> **NON-PRODUCTION BUILD — FOR LOCAL DEVICE TESTING PURPOSES ONLY**
>
> This release is strictly a **Pre-Release (`prerelease: true`)** intended exclusively for **local device evaluation, QA verification, and sandbox testing**.
> - **DO NOT** deploy or distribute this build to production environments or commercial app stores.
> - **In-Place Upgrades from `v1.6.1-pre.1`:** Installs directly over `v1.6.1-pre.1` (using `com.aistudio.cleansms.jrvxpq` and the persistent CI/debug signing keystore). If you still have an older release under a different package ID or an earlier ephemeral keystore, uninstall it first before installing `v1.6.2-pre.1` to avoid Android's *"App not installed as package conflicts with an existing package"* error.

---

## 📋 Release Metadata

| Field | Value |
| :--- | :--- |
| **Release Tag** | `v1.6.2-pre.1` |
| **Release Type** | `Pre-Release` (`prerelease: true`, `make_latest: false`) |
| **Target Environment** | Non-Production • Local Device Testing Only |
| **Application ID** | `com.aistudio.cleansms.jrvxpq` |
| **Version Name** | `1.6.2-pre.1` |
| **Version Code** | `10602` |
| **Room Schema Version** | `v5` (automatic non-destructive migrations `v1 → v5`) |
| **Min / Target SDK** | API 24 (Android 7.0) / API 36 (Android 16) |
| **Artifact Name** | `CleanSMS-v1.6.2-pre.1-nonprod-local-test.apk` |

---

## ✨ What's New in `v1.6.2-pre.1`

### 1. Background Threading via Room `@Query` + Coroutine `Flow` for Filter By Contact
- Added Room v5 indexed tables (`inbox_message_cache`, `contact_filter_selections`) and SQLite indices (`threadId`, `address`, `addressKey`) on `quarantine_messages` and `deleted_test_snapshots` with automatic `MIGRATION_4_5`.
- Moved **Filter By Contact** aggregation (`GROUP BY`), debounced live contact search (`LIKE`), Scanner thread ID resolution (`INNER JOIN`), and Vault/Recently Deleted contact filtering (`EXISTS`) into asynchronous Room `@Query` `Flow`s running on `Dispatchers.IO`, eliminating UI hitches during large 2,000–5,000+ message processing cycles.

### 2. Batch Restoration System in the Recently Deleted Folder
- Upgraded the Vault's recovery tab into a dedicated **Recently Deleted** folder with automatic 30-day expiration pruning, remaining-days badges (`Xd left`), multi-select checkboxes, per-contact group selection, and **Select All**.
- Added chunked (100-item) background batch restoration (**Restore (N) to Inbox**) with live progress indicator to move selected messages cleanly back to the Primary Inbox (and device Telephony SMS store when set as Default SMS App), plus **Purge (N)** and **Empty Trash**.

### 3. Vault Contact Group Filter & Direct In-App APK Downloader
- Included contact-checklist filtering & **Group by Contact** review in Vault alongside direct in-app GitHub release `.apk` downloading, SHA-256 verification, and Android `FileProvider` package installation (`v1.6.1-pre.1 → v1.6.2-pre.1`).

---

## 📲 Local Device Testing Instructions

1. **Direct In-App Update (from `v1.6.1-pre.1`):**
   - Open **Updates** in CleanSMS (`v1.6.1-pre.1`), ensure **Include Pre-Releases** is ON, check `HussainQ/SMS-Cleaner-Tool`, and tap **Download & Install** directly inside the app.
2. **Manual Download & Verify the APK:**
   - Download `CleanSMS-v1.6.2-pre.1-nonprod-local-test.apk` and its `.sha256` file from the GitHub Release assets.
   ```bash
   sha256sum -c CleanSMS-v1.6.2-pre.1-nonprod-local-test.apk.sha256
   ```
3. **Install / Upgrade on Local Android Device:**
   ```bash
   adb install -r CleanSMS-v1.6.2-pre.1-nonprod-local-test.apk
   ```
