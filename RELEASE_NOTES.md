# ⚠️ CleanSMS `v1.6.0-pre.1` — Non-Production Pre-Release

> [!WARNING]
> **NON-PRODUCTION BUILD — FOR LOCAL DEVICE TESTING PURPOSES ONLY**
>
> This release is strictly a **Pre-Release (`prerelease: true`)** intended exclusively for **local device evaluation, QA verification, and sandbox testing**.
> - **DO NOT** deploy or distribute this build to production environments or commercial app stores.
> - Signed with local development testing credentials (`debugConfig`).
> - Includes a built-in **Demo Mode** and a **Testing Deleted Message Recovery Toggle** so all deduplication, contact filtering, rule editing, and deletion workflows can be tested safely on a local device.

---

## 📋 Release Metadata

| Field | Value |
| :--- | :--- |
| **Release Tag** | `v1.6.0-pre.1` |
| **Release Type** | `Pre-Release` (`prerelease: true`, `make_latest: false`) |
| **Target Environment** | Non-Production • Local Device Testing Only |
| **Application ID** | `com.aistudio.cleansms.jrvxpq` |
| **Version Name** | `1.6.0-pre.1` |
| **Version Code** | `10600` |
| **Min / Target SDK** | API 24 (Android 7.0) / API 36 (Android 16) |
| **Artifact Name** | `CleanSMS-v1.6.0-pre.1-nonprod-local-test.apk` (`app-debug.apk`) |
| **APK Size** | 22 MB |
| **SHA-256 Checksum** | `8d688441c59c223e064adb8736c3ef0ace701866ff6211d29e4d89228e755f9f` |

---

## ✨ What's New in `v1.6.0-pre.1`

### 1. Per-Contact Filter & Google Messages Thread Linking (`READ_CONTACTS`)
- Added `READ_CONTACTS` manifest declaration and runtime permission flow to read device address-book contacts and link them with existing SMS/RCS threads in Google Messages using normalized 10-digit/7-digit phone matching (`PhoneNormalizer`).
- **Main Detection Rules**: Toggle **"Filter by Contact"** to switch between **Target Selected** (restrict cleaning only to chosen contacts) and **Protect (VIP)** (shield important contacts from all automatic cleaning flags).
- **Custom Cleaning Rules**: Pick one or more contacts/message threads inside any custom rule (`ContactPickerDialog`).

### 2. Seamless Default SMS Role Takeover & Hand-Back Workflow
- Tracks the previously active Default SMS handler (e.g., **Google Messages**) in `TelephonyManagerHelper` before CleanSMS assumes `RoleManager.ROLE_SMS`.
- Animated `TelephonyBanner` removes the yellow **"Set as Default SMS App"** button as soon as CleanSMS holds the role and replaces it with a Teal active banner + **"Hand Back Default SMS Role to [Previous App]"** action (`Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS` on Android 10+ / `ACTION_CHANGE_DEFAULT` on Android 9 and below), plus a quick hand-back bar in Review.

### 3. Testing Feature — Restore Permanently Deleted Messages (Toggle)
- Added a **Testing: Recover Deleted Messages** toggle in both **Scanner** and **Vault**.
- When enabled, permanent deletions record a recovery snapshot in Room (`deleted_test_snapshots`, DB v4) and display them in **Vault > Deleted Test** so testers can restore deleted messages back into the inbox (and re-insert into `Telephony.Sms` on local devices when Default SMS Role is active) to re-test cleaning behavior repeatedly.

### 4. Edit Existing Custom Rules
- Tap any existing custom rule card or its **Edit** (`Icons.Default.Edit`) action on the **Rules & Cloud** screen to open `AddCustomRuleSheet` pre-populated with its saved name, contacts, sender filter, keywords, OR/AND toggle, age threshold, and enabled state while preserving its `id` and `createdAt`.

### 5. Large-Inbox Performance & ANR Optimization
- Moved SMS loading, contact linking, classification, and Room persistence off the Main thread onto `Dispatchers.IO` and `Dispatchers.Default` with cancellable jobs.
- Eliminated per-message regex compilation (`normalizeWhitespaceLower` linear pass + precompiled `CompiledRule`s) and per-row `SimpleDateFormat` allocations (`DateFormatterUtil` ThreadLocal cache).
- Added single-pass `ClassificationSummary` category counts and debounced background search filtering (`150ms` + `produceState`) to prevent frame drops and "Force Close" ANR dialogs on inboxes with thousands of messages.

---

## 📲 Local Device Testing Instructions

1. **Download or Export the APK**:
   - Download `CleanSMS-v1.6.0-pre.1-nonprod-local-test.apk` from the GitHub Pre-Release assets (or build locally via `gradle :app:assembleDebug`).
2. **Verify Checksum (Optional)**:
   ```bash
   sha256sum -c CleanSMS-v1.6.0-pre.1-nonprod-local-test.apk.sha256
   ```
3. **Install on Local Android Device**:
   ```bash
   adb install -r CleanSMS-v1.6.0-pre.1-nonprod-local-test.apk
   ```
4. **Testing Checklist on Local Device**:
   - Grant `READ_SMS`, `WRITE_SMS`, and `READ_CONTACTS` from the Scanner runtime permissions banner (or evaluate safely in Demo Mode).
   - Enable **Testing: Recover Deleted Messages** to test permanent deletion + one-tap recovery in **Vault > Deleted Test**.
   - Test **Set as Default SMS App** -> verify the yellow button transitions to **Hand Back Default SMS Role to Google Messages**.
