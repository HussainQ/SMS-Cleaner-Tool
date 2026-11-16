# CleanSMS `v1.7.2-pre.1` Pre-Release Notes (Non-Production Local Testing)

> **Important:** This pre-release build is intended strictly for **local device testing**. It **is** a pre-release (`prerelease: true`) and does **not** promote to production.
>
> **In-Place Upgrades from `v1.6.1+` / `v1.7.0-pre.1` / `v1.7.1-pre.1`:** Installs directly over prior builds using Application ID `com.aistudio.cleansms.jrvxpq` and the persistent signing keystore.

---

## 📋 Release Metadata

| Field | Value |
| :--- | :--- |
| **Release Tag** | `v1.7.2-pre.1` |
| **Release Type** | `Pre-Release` (`prerelease: true`, `make_latest: false`) |
| **Application ID** | `com.aistudio.cleansms.jrvxpq` |
| **Version Name** | `1.7.2-pre.1` |
| **Version Code** | `10702` |
| **Room Schema Version** | `v6` (automatic non-destructive migrations `v1 → v6`) |
| **Min / Target SDK** | API 24 (Android 7.0) / API 36 (Android 16) |
| **Artifact Name** | `CleanSMS-v1.7.2-pre.1-nonprod-local-test.apk` |

---

## ✨ What's New in `v1.7.2-pre.1`

### 1. Device Material UI & Dynamic Material You Theming
- **Follow Device UI Settings Automatically:** CleanSMS now picks up the device's system Dark/Light mode (`isSystemInDarkTheme()`) and Android 12+ (API 31+) Material You dynamic color palette (`dynamicDarkColorScheme` / `dynamicLightColorScheme`), with tailored Light & Dark Material 3 fallback palettes on Android 7–11.
- **Settings Toggle (`Use Device Material UI`):** Added an explicit **Use Device Material UI** switch inside **Rules & Cloud > Settings** and synced it in **Messages > Display Settings**. Toggle it OFF anytime to switch back to the Classic CleanSMS Navy & Cyan theme.
- **Full-App Adaptive Surfaces & Edge-to-Edge System Bars:** Migrated screens, dialogs, badges, cards, buttons, and top/bottom navigation bars across Messages, Scanner, Review, Vault, and Rules & Cloud to `MaterialTheme.colorScheme` tokens paired with responsive `SystemBarStyle` edge-to-edge updates.

### 2. Includes All Prior `v1.7.x` & `v1.6.x` Enhancements
- Full **SMS / MMS / RCS Messaging** landing tab with conversation search bar, protocol filters, Archived & Spam cross-app folder sync, and 1-tap Compact UI density toggle.
- Room `v6` background `@Query` + Coroutine `Flow` contact filtering, Vault contact grouping, Recently Deleted batch restoration, and in-app APK updates with SHA-256 verification.

---

## 📲 Local Testing & Verification Instructions

1. **Direct In-App Update:**
   - Open **Updates** in CleanSMS, ensure pre-releases are enabled, check `HussainQ/SMS-Cleaner-Tool`, and tap **Download & Install** directly inside the app.
2. **Manual Download & Verify the APK:**
   - Download `CleanSMS-v1.7.2-pre.1-nonprod-local-test.apk` and `CleanSMS-v1.7.2-pre.1-nonprod-local-test.apk.sha256` from the GitHub Pre-Release assets.
   ```bash
   sha256sum -c CleanSMS-v1.7.2-pre.1-nonprod-local-test.apk.sha256
   ```
3. **Install / Upgrade on Local Android Test Device:**
   ```bash
   adb install -r CleanSMS-v1.7.2-pre.1-nonprod-local-test.apk
   ```
