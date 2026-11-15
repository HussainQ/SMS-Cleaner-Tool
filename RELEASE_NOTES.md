# ⚠️ CleanSMS `v1.6.0-pre.2` — Non-Production Pre-Release

> [!WARNING]
> **NON-PRODUCTION BUILD — FOR LOCAL DEVICE TESTING PURPOSES ONLY**
>
> This release is strictly a **Pre-Release (`prerelease: true`)** intended exclusively for **local device evaluation, QA verification, and sandbox testing**.
> - **DO NOT** deploy or distribute this build to production environments or commercial app stores.
> - **Upgrading from a previous release (One-Time Uninstall Required):** If your device has an earlier CleanSMS release installed under a **different package name (`applicationId`)** or signed with an **earlier ephemeral CI keystore**, Android will reject in-place installation with `"App not installed as package conflicts with an existing package"`. **Export/back up any custom rules if needed and uninstall the previous CleanSMS app before installing `v1.6.0-pre.2`.** Going forward, the Application ID remains locked at `com.aistudio.cleansms.jrvxpq` with persistent CI signing (and optional `DEBUG_KEYSTORE_BASE64` / `RELEASE_KEYSTORE_BASE64` secrets) so future updates install in-place without uninstalling.

---

## 📋 Release Metadata

| Field | Value |
| :--- | :--- |
| **Release Tag** | `v1.6.0-pre.2` |
| **Release Type** | `Pre-Release` (`prerelease: true`, `make_latest: false`) |
| **Target Environment** | Non-Production • Local Device Testing Only |
| **Application ID** | `com.aistudio.cleansms.jrvxpq` |
| **Version Name** | `1.6.0-pre.2` |
| **Version Code** | `10601` |
| **Min / Target SDK** | API 24 (Android 7.0) / API 36 (Android 16) |
| **Artifact Name** | `CleanSMS-v1.6.0-pre.2-nonprod-local-test.apk` |

---

## ✨ What's New in `v1.6.0-pre.2`

### 1. Fixed Package Conflict Errors Across GitHub Releases
- Updated the GitHub Actions release pipeline to reuse a persistent keystore (`DEBUG_KEYSTORE_BASE64` / `RELEASE_KEYSTORE_BASE64` repository secrets or persistent CI keystore cache) instead of regenerating a random keypair on every build.
- Removed the step that re-uploaded newly signed APKs onto older release tags and incremented `versionCode` to `10601`.

### 2. Refined Default SMS Role Detection & Status Indicator
- Added `inspectDefaultSmsRoleStatus` in `TelephonyManagerHelper` and instant state syncing across `onWindowFocusChanged`, `ON_START`/`ON_RESUME`, tab navigation, and `ActivityResult` callbacks.
- Replaced the **"Set as Default SMS App"** request button with a dedicated **"Default SMS App"** (`ACTIVE`) status indicator whenever CleanSMS holds the Default SMS role, with status indicators across Scanner, Review, and Permanent Delete confirmation.

### 3. GitHub Releases Update Checker & Multi-Version Upgrade Path
- Checks `HussainQ/SMS-Cleaner-Tool` releases using SemVer 2.0.0 comparison, computes direct multi-version upgrade chains, verifies `.sha256` assets, and executes non-destructive Room (`v1→v4`) + preferences migrations across arbitrary version jumps.

---

## 📲 Local Device Testing Instructions

1. **If upgrading from an older ephemerally-signed build (one-time step):**
   - Back up any custom rules/messages if needed, then uninstall the old CleanSMS build so Android accepts the persistent signing key.
2. **Download & Verify the APK:**
   - Download `CleanSMS-v1.6.0-pre.2-nonprod-local-test.apk` and its `.sha256` file from the GitHub Release assets.
   ```bash
   sha256sum -c CleanSMS-v1.6.0-pre.2-nonprod-local-test.apk.sha256
   ```
3. **Install on Local Android Device:**
   ```bash
   adb install -r CleanSMS-v1.6.0-pre.2-nonprod-local-test.apk
   ```
4. **Maintaining Identical Signing Between AI Studio & GitHub Actions (Recommended):**
   - In GitHub **Repository Settings → Secrets and variables → Actions**, add `DEBUG_KEYSTORE_BASE64` containing the base64-encoded `debug.keystore` so APKs exported from AI Studio and APKs built by GitHub Actions share the exact same certificate.
