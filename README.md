<!-- SPDX-License-Identifier: LicenseRef-DIFF -->

# DIFF Android

DIFF Android is the mobile app for DIFF, a small pull request workspace for GitHub. It follows the DIFF web app so pull requests, files, discussion, checks, history, branches, and repository code feel like one product across web and Android.

The web app is the behavior and design reference:

- https://github.com/bniladridas/diff

## Features

- Browse pull requests, branches, changed files, and repository files.
- Read discussion, review comments, checks, commits, and timeline history.
- Save pull requests locally and sync preferences with Supabase when configured.
- Use a GitHub token for write actions such as comments, reviews, file edits, branches, pull requests, PR metadata, merge actions, and branch cleanup.
- Use an optional Gemini API key for Draft Fix generation from review comments.

## Build

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

DIFF Android stores entered GitHub, Gemini, and Supabase credentials locally on the device. Do not commit real tokens, API keys, access tokens, or refresh tokens.

DIFF is not an official GitHub product or an official product of any repository owner whose data it reads.
