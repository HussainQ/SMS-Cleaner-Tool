# ⚠️ CleanSMS `v1.6.1-pre.1` — Non-Production Pre-Release

> [!WARNING]
> **NON-PRODUCTION BUILD — FOR LOCAL DEVICE TESTING PURPOSES ONLY**
>
> This release is strictly a **Pre-Release (`prerelease: true`)** intended exclusively for **local device evaluation, QA verification, and sandbox testing**.
> - **DO NOT** deploy or distribute this build to production environments or commercial app stores.
> - **Upgrading from a previous release (One-Time Uninstall Notice):** If your device has an earlier CleanSMS release installed under a **different package name (`applicationId`)** or signed with an **earlier ephemeral CI keystore**, Android will reject in-place installation with `"App not installed as package conflicts with an existing package"`. **Export/back up any custom rules if needed and uninstall the previous CleanSMS app before installing `v1.6.1-pre.1`.** Going forward, the Application ID remains locked at `com.aistudio.cleansms.jrvxpq` with persistent CI signing (and optional `DEBUG_KEYSTORE_BASE64` / `RELEASE_KEYSTORE_BASE64` secrets) so future updates install in-place cleanly.

---

## 📋 Release Metadata

| Field | Value |
| :--- | :--- |
| **Release Tag** | `v1.6.1-pre.1` |
| **Release Type** | `Pre-Release` (`prerelease: true`, `make_latest: false`) |
| **Target Environment** | Non-Production • Local Device Testing Only |
| **Application ID** | `com.aistudio.cleansms.jrvxpq` |
| **Version Name** | `1.6.1-pre.1` |
| **Version Code** | `10601` |
| **Min / Target SDK** | API 24 (Android 7.0) / API 36 (Android 16) |
| **Artifact Name** | `CleanSMS-v1.6.1-pre.1-nonprod-local-test.apk` |

---

## ✨ What's New in `v1.6.1-pre.1`

### 1. Fixed Filter By Contact + De-duplicate Text Messages Bug
- Fixed `SmsClassifier` so enabling **Filter By Contact (`Target Selected`)** with **De-duplicate Text Messages** (or other rules) runs active rules across **all threads belonging to the selected contact(s)** rather than flagging all messages of the contact outright.
- Preserves the original/master message copy and all non-duplicate normal messages for targeted contacts while removing only redundant duplicate copies (and leaves unselected contacts untouched in both message selection and duplicate thread clusters).

### 2. Refined Default SMS Role Detection & Status Indicator
- Added `inspectDefaultSmsRoleStatus` in `TelephonyManagerHelper` and synced role state on window focus changes, `ON_START`/`ON_RESUME`, tab navigation, and activity results.
- Replaced the **"Set as Default SMS App"** request button with a dedicated **"Default SMS App"** (`ACTIVE`) status indicator whenever CleanSMS holds the Default SMS role, with clear indicators across Scanner, Review, and Permanent Delete confirmation.

### 3. Consistent Multi-Level Back Button Navigation
- Replaced immediate app-exit behavior with hierarchical `BackHandler` dispatch factoring in window levels, in-screen sub-levels, and exact tab click order:
  1. **Window & Nested Sub-Window Level:** Nested dialogs/pickers (`ContactPickerDialog` inside `AddCustomRuleSheet`, inline repo editor inside `AppUpdatesDialog`) collapse before dismissing top-level dialogs/bottom sheets.
  2. **In-Screen Sub-Level:** Active Review search clears before resetting category filter chips to `All`; Vault **Deleted Test** returns to **Quarantine**; compact **Rules & Cloud** switches from **Cloud Backup** back to **Rules**.
  3. **Tab History (Order of Clicks):** Bottom/Rail navigation maintains visited tab order so Back steps backward through clicked tabs down to **Scanner** before closing the app.

### 4. Persistent CI Signing & In-App GitHub Release Updater
- Updated GitHub Actions to persist/reuse the signing keystore (`DEBUG_KEYSTORE_BASE64` / `RELEASE_KEYSTORE_BASE64` or persistent CI cache) and avoid clobbering older release tags.
- In-app GitHub Releases updater verifies versions via SemVer 2.0.0 (`HussainQ/SMS-Cleaner-Tool`), aggregates skipped changelogs, fetches `.sha256` digests, and runs non-destructive Room (`v1→v4`) + preferences migrations.

---

## 📲 Local Device Testing Instructions

1. **If upgrading from an earlier conflicting package/ephemeral build (one-time step):**
   - Back up any custom rules/messages if needed, then uninstall the old CleanSMS build so Android accepts the persistent signing key and `com.aistudio.cleansms.jrvxpq`.
2. **Download & Verify the APK:**
   - Download `CleanSMS-v1.6.1-pre.1-nonprod-local-test.apk` and its `.sha256` file from the GitHub Release assets (or pull directly via the in-app **Updates** button).
   ```bash
   sha256sum -c CleanSMS-v1.6.1-pre.1-nonprod-local-test.apk.sha256
   ```
3. **Install on Local Android Device:**
   ```bash
   adb install -r CleanSMS-v1.6.1-pre.1-nonprod-local-test.apk
   ```
4. **Maintaining Identical Signing Between AI Studio & GitHub Actions (Recommended):**
   - In GitHub **Repository Settings → Secrets and variables → Actions**, add `DEBUG_KEYSTORE_BASE64` containing the base64-encoded `debug.keystore` so APKs exported from AI Studio and APKs built by GitHub Actions share the exact same certificate.
