# Mozhi — Android app

Offline English–Malayalam dictionary for Android, adapting the logic and data
from https://github.com/ShuhaibNC/mozhi.

## Data pipeline

- Source: `enml.json` from the mozhi repo (59,040 English words, 125k+ Malayalam
  definitions).
- `tools/mkdb.py` converts it to a prebuilt SQLite database:
  `entries(word TEXT PRIMARY KEY, lword TEXT, meanings TEXT)` where `meanings`
  is a JSON array. `lword` (lowercased) is indexed for case-insensitive prefix
  search — the same `startsWith` behavior as the website, capped at 60
  suggestions.
- The DB ships in `app/src/main/assets/mozhi.db` and is copied to internal
  storage on first launch.

## App

- `com.shuhaibnc.mozhi`, v1.5 (versionCode 6), minSdk 21.
- Single activity: live prefix suggestions with the matched part highlighted
  (like the website's `<mark>`), tap/Enter to open the word detail with
  numbered Malayalam meanings, Back returns to suggestions.
- Material 3 dynamic colors (wallpaper accent on Android 12+, static blue
  fallback), System/Light/Dark theme switcher persisted in SharedPreferences,
  About dialog, offline word-count status.
- Bold മൊഴി title in Manjari, custom launcher icon, AMOLED black dark mode.
- No network, no dependencies beyond the platform.

## Versioning

`version.properties` at the repo root holds `versionCode` and `versionName`.
Both the local build and CI read it — bump it there before a release.

## Build

Manual aapt2 pipeline (no Gradle), via the canonical script:

    bash build.sh

Output: `Mozhi-v<versionName>.apk` (debug-signed).

## Auto build release

Pushing a GitHub **Release** (e.g. tag `v1.6`) triggers
`.github/workflows/build.yml`: it installs the Android SDK (platform
android-29, build-tools 29.0.3), runs `build.sh`, and attaches the resulting
`Mozhi-v<versionName>.apk` to the release automatically. You can also run the
workflow manually from the Actions tab (APK lands in artifacts).
