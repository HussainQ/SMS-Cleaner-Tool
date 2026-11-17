# CleanSMS Release Version Schema

This document defines CleanSMS's internal semantic versioning, Android `versionCode` continuity rules, Room database migration guarantees, and GitHub CI release conventions.

---

## 1. Semantic Versioning Format

CleanSMS follows **Semantic Versioning 2.0.0** (`MAJOR.MINOR.PATCH` with optional pre-release tags):

```text
vMAJOR.MINOR.PATCH[-prerelease.N]
```

- **Stable releases:** `MAJOR.MINOR.PATCH` (for example `1.0.0`, GitHub tag `v1.0.0`).
- **Pre-releases / Alphas / Betas:** `MAJOR.MINOR.PATCH-alpha.N`, `-beta.N`, `-pre.N`, or `-rc.N` (for example `1.0.0-alpha.1`, GitHub tag `v1.0.0-alpha.1`).
- In `SemanticVersion.kt`, a final release `X.Y.Z` always sorts **higher** than any pre-release `X.Y.Z-*` with the same numeric triad, and numeric pre-release identifiers compare numerically rather than lexicographically.

---

## 2. Android `versionCode` Continuity Rules

Android's package installer strictly enforces monotonically increasing `versionCode` values for in-place upgrades on device (`adb install -r` or in-app APK updater).

### Standard Formula

For sequential releases:

```kotlin
versionCode = MAJOR * 10000 + MINOR * 100 + PATCH
```

Examples under this formula:

| Release Version (`versionName`) | Computed Formula Value | Active `versionCode` | Notes |
| :--- | :--- | :--- | :--- |
| `1.7.0-pre.1` | `1 * 10000 + 7 * 100 + 0` | `10700` | Sequential feature milestone |
| `1.7.1-pre.1` | `1 * 10000 + 7 * 100 + 1` | `10701` | Archived & Spam folders |
| `1.7.2-pre.1` | `1 * 10000 + 7 * 100 + 2` | `10702` | Device Material UI theming |
| `1.7.3-pre.1` | `1 * 10000 + 7 * 100 + 3` | `10703` | Lightweight vector & progress update |
| `1.0.0-alpha.1` | Override (monotonic floor) | `10800` | **Must stay `> 10703`** so `1.7.x` test devices upgrade in place |

### Monotonic Floor Rule for Version Resets

When resetting `versionName` downward (such as transitioning from `1.7.3-pre.1` to `1.0.0-alpha.1`), `versionCode` **must never decrease**:
- Keep `versionCode` strictly greater than all previously shipped APKs (e.g. `10800` > `10703`), incrementing `versionCode` for each subsequent `1.0.0-alpha.N`, `1.0.0-beta.N`, `1.0.0-rc.N`, and `1.0.0` release until `versionName` naturally surpasses the numeric code again.
- `GitHubUpdateRepository` compares releases by `SemanticVersion` and handles tag transitions cleanly without requiring users to uninstall previous local test builds.

---

## 3. Data & Schema Continuity

Upgrading directly across any multi-version jump runs idempotent state and storage migrations automatically:
- **App Preferences (`AppUpgradeManager`):** Evaluates stored `last_migrated_version_code` against `BuildConfig.VERSION_CODE` and executes only applicable milestone initializers.
- **Room Database (`AppDatabase`):** Currently at **Schema v6**, providing explicit non-destructive `Migration(from, to)` paths (`v1 -> v2 -> v3 -> v4 -> v5 -> v6`) so quarantined SMS records, custom filter rules, OAuth vault entries, and thread folder states persist seamlessly across installs.
- **Jetpack DataStore:** Theme overrides (`cleansms_theme_prefs`) are stored independently of transient UI state and remain intact across app upgrades.

---

## 4. Automated GitHub Release & Artifact Naming

The CI workflow in `.github/workflows/prerelease.yml` derives release behavior automatically from the Git tag or `app/build.gradle.kts` `versionName`:

1. **Pre-release tags (containing `-`, e.g. `v1.0.0-alpha.1`):**
   - Publishes with `prerelease: true`, `make_latest: false`
   - Outputs `CleanSMS-<tag>-nonprod-local-test.apk` + `.sha256` checksum
2. **Stable tags (no `-`, e.g. `v1.0.0`):**
   - Publishes with `prerelease: false`, `make_latest: true`
   - Outputs `CleanSMS-<tag>.apk` + `.sha256` checksum
3. **Signing Key Continuity:**
   - Locked Application ID `com.aistudio.cleansms.jrvxpq` paired with persistent CI signing secrets (`RELEASE_KEYSTORE_BASE64` or `DEBUG_KEYSTORE_BASE64`) ensures seamless signature verification for in-app updates and `adb install -r`.
