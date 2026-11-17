# CleanSMS `v2.0.0-alpha.1` Release Notes

Alpha pre-release on the `2.0.0` line introducing the new dual-tone cyan and teal speech bubble app icon alongside the decluttered 4-tab UI, unified Cleanup workspace, and persistent Material 3 Dark Mode.

---

## ✨ Highlights

### 1. New Speech Bubble App Icon & `2.0.0-alpha.1` Versioning
- **Dual-Tone Cyan & Teal Speech Bubble Icon**: Recreated the adaptive and density launcher icons around a layered cyan and teal chat bubble on dark slate-navy (`#1A2537`).
- **Clean 2.0.0 Alpha Versioning**: Bumped to `v2.0.0-alpha.1` (`versionCode = 20000`) for clean semantic version continuity.

### 2. Decluttered 4-Tab Architecture
- **Messages • Cleanup • Vault • Settings**: Consolidated navigation into four focused tabs and moved update indicators to the **Settings** tab badge.
- **Unified Cleanup Workspace**: Grouped **Scan**, **Review**, and **Rules** under one segmented workspace with hierarchical Back navigation.
- **On-Demand Setup & Permissions Sheet**: Compact status pill opens a modal bottom sheet for switching between Demo Mode and Device SMS, granting permissions, or managing the Default SMS role.

### 3. Settings Hub, Persistent Dark Mode & Performance
- **Material 3 Dark Mode**: Toggle dark theme across the entire app with Jetpack DataStore persistence, consolidated alongside **Cloud Backup** and **App Updates** inside **Settings**.
- **Lightweight Binary & Smooth UI**: Uses local vector drawables, an indeterminate scan progress indicator, and an on-demand popup contact filter picker.

---

## 📲 Installation & Verification

1. **In-App Update**: Open **Settings > App Updates** and pull `v2.0.0-alpha.1` from `HussainQ/SMS-Cleaner-Tool`.
2. **Checksum Verification**:
   ```bash
   sha256sum -c CleanSMS-v2.0.0-alpha.1-nonprod-local-test.apk.sha256
   ```
3. **Install via ADB**:
   ```bash
   adb install -r CleanSMS-v2.0.0-alpha.1-nonprod-local-test.apk
   ```
