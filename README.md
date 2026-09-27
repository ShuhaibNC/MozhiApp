# Mozhi — Android app

Offline English–Malayalam dictionary for Android, adapting the logic and data
from https://github.com/ShuhaibNC/mozhi.

## Data pipeline

- Source: `data/enml.json` (vendored from the mozhi repo: 59,040 English words,
  125k+ Malayalam definitions).
- `tools/mkdb.py` converts it to a SQLite database at **build time**:
  `entries(word TEXT PRIMARY KEY, lword TEXT, meanings TEXT)` where `meanings`
  is a JSON array. `lword` (lowercased) is indexed for case-insensitive prefix
  search — the same `startsWith` behavior as the website, capped at 60
  suggestions.
- The prebuilt `mozhi.db` binary is intentionally **not** committed. The Gradle
  build generates it into `build/generated/mozhiAssets` via the `generateMozhiDb`
  task; the manual `build.sh` generates it into `app/src/main/assets` if missing.
- The DB is copied to internal storage on first launch.

## App

- `com.shuhaibnc.mozhi`, v1.6 (versionCode 7), minSdk 21.
- Single activity: live prefix suggestions with the matched part highlighted
  (like the website's `<mark>`), tap/Enter to open the word detail with
  numbered Malayalam meanings, Back returns to suggestions.
- Material 3 dynamic colors (wallpaper accent on Android 12+, static blue
  fallback), System/Light/Dark theme switcher persisted in SharedPreferences,
  About dialog, offline word-count status.
- Bold മൊഴി title in Manjari, custom launcher icon, AMOLED black dark mode.
- No network, no permissions, no dependencies beyond the platform.
  Manjari font is under the SIL Open Font License 1.1.

## Versioning

`versionCode` and `versionName` are literals in `app/build.gradle` — the single
source of truth (F-Droid reads them with a regex, so they must not be computed).
Bump them there and tag the release commit `v<versionName>` (e.g. `v1.6`).
The manual `build.sh` reads the same values.

## Build

Standard Gradle build (used by F-Droid):

    ./gradlew assembleRelease

Needs JDK 8 and the Android SDK with platform `android-29` and build-tools
`29.0.3` (`ANDROID_HOME` env var, or `local.properties` with `sdk.dir=`).
Output: `app/build/outputs/apk/release/app-release.apk`.

Manual aapt2 pipeline (no Gradle), via the canonical script:

    bash build.sh

Output: `Mozhi-v<versionName>.apk` (debug-signed).

## Auto build release

Pushing a GitHub **Release** (e.g. tag `v1.6`) triggers
`.github/workflows/build.yml`: it installs the Android SDK (platform
android-29, build-tools 29.0.3), runs `build.sh`, and attaches the resulting
`Mozhi-v<versionName>.apk` to the release automatically. You can also run the
workflow manually from the Actions tab (APK lands in artifacts).

## F-Droid

The repo is set up for F-Droid submission:

- `LICENSE` — FOSS license file
- `app/build.gradle` — standard Gradle build, versions as plain literals
- `data/enml.json` vendored; `mozhi.db` generated at build time (no prebuilt
  binaries, no network access needed during build)
- `fastlane/metadata/android/en-US/` — descriptions, 512px icon, changelog
  (add real phone screenshots under `images/phoneScreenshots/` before submitting)
- `fdroid/com.shuhaibnc.mozhi.yml` — draft build metadata for the fdroiddata
  merge request

To submit: fork https://gitlab.com/fdroid/fdroiddata, copy the draft to
`metadata/com.shuhaibnc.mozhi.yml`, set the `License` field, and open a merge
request following the
[Quick Start Guide](https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide).
