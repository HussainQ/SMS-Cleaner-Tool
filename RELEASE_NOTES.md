# CleanSMS `v1.0.0-alpha.1` Release Notes

Alpha pre-release introducing a decluttered 4-tab navigation architecture, unified Cleanup workspace, persistent Material 3 Dark Mode, lightweight vector icons, and the Minimal Cyan Sweep Envelope app icon.

---

## ✨ Highlights

### 1. Decluttered 4-Tab Architecture
- **Messages • Cleanup • Vault • Settings**: Consolidated navigation into four focused tabs and moved update indicators to the **Settings** tab badge.
- **Unified Cleanup Workspace**: Grouped **Scan**, **Review**, and **Rules** under one segmented workspace with hierarchical Back navigation.
- **On-Demand Setup & Permissions Sheet**: Compact status pill opens a modal bottom sheet for switching between Demo Mode and Device SMS, granting permissions, or managing the Default SMS role.

### 2. Settings Hub & Persistent Dark Mode
- **Material 3 Dark Mode**: Toggle dark theme across the entire app with Jetpack DataStore persistence.
- **Consolidated Controls**: Appearance toggles, cleanup safety switches, collapsible **Cloud Backup** accounts/snapshots, and **App Updates** now live in **Settings**.

### 3. Visual & Performance Polish
- **Minimal Cyan Sweep Envelope Icon**: Updated adaptive launcher icon across all screen densities.
- **Lightweight Binary & Smooth UI**: Replaced heavy icon libraries with local vector drawables, added an indeterminate scan progress indicator, and moved contact filtering into an on-demand popup picker.

---

## 📲 Installation & Verification

1. **In-App Update**: Open **Settings > App Updates** and pull `v1.0.0-alpha.1` from `HussainQ/SMS-Cleaner-Tool`.
2. **Checksum Verification**:
   ```bash
   sha256sum -c CleanSMS-v1.0.0-alpha.1-nonprod-local-test.apk.sha256
   ```
3. **Install via ADB**:
   ```bash
   adb install -r CleanSMS-v1.0.0-alpha.1-nonprod-local-test.apk
   ```
