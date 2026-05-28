<!-- SPDX-License-Identifier: LicenseRef-DIFF -->

<p align="center">
  <img src="https://raw.githubusercontent.com/bniladridas/diff-android/main/docs/assets/icon-transparent.png" alt="DIFF Android icon" width="96" height="96">
</p>

# DIFF Android

DIFF Android is the mobile app for DIFF, a small pull request workspace for GitHub.

It mirrors the DIFF web experience across pull requests, files, discussions, checks, branches, and repository history so work can move naturally between desktop and mobile.

The app focuses on reading and reviewing comfortably on a phone while still supporting authenticated repository actions when a GitHub token is configured. Local preferences can optionally sync through Supabase, and Draft Fix generation can use Gemini when an API key is provided.

## Build

Build the debug app with:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/diff-debug.apk
```

## Install

```bash
adb install -r app/build/outputs/apk/debug/diff-debug.apk
```

## Credentials

Entered GitHub, Gemini, and Supabase credentials remain stored locally on the device. Do not commit real tokens or API keys.

DIFF is an independent project and is not affiliated with GitHub.
