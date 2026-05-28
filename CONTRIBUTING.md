# Contributing

## Branch Rules

The `main` branch must remain stable.

Permanent rule:

* all changes must go through pull requests
* merge only after checks pass
* do not push directly to main
* follow repository merge settings and commit style automatically

Rules:

* no direct push to main
* all changes go through pull requests
* pull requests should stay focused on one purpose
* commit messages must follow repository commit style
* force push to main is not allowed

Recommended flow:

```text
feature branch -> pull request -> squash merge -> main
```

In GitHub repository settings, enable:

* require pull request before merging
* require status checks to pass
* allow squash merge
* disable rebase merge
* disable merge commits
* require linear history
* block force pushes
* require branches to be up to date

## Repository Habits

Consistency matters more than sophistication.

Trust-building rules:

* keep PRs small
* never leave broken `main`
* write calm release notes
* close stale branches
* avoid huge final-cleanup commits
* keep filenames predictable
* avoid random abbreviations
* use draft PRs while work is still in progress
* squash merge PRs into one calm commit on `main`
* keep screenshots consistent
* tag releases properly
* use semantic versions calmly

Good doc filenames:

```text
docs/architecture.md
docs/message-flow.md
docs/offline-behavior.md
```

Good release tags:

```text
v0.4.1
v0.5.0
v0.6.0-beta1
```

Write PR descriptions in this shape:

```text
why:
message retries could duplicate after reconnect

what:
adds request id tracking for pending sends

result:
delivery becomes idempotent during reconnect
```

PR title and final squash commit should match:

```text
[sync] improve reconnect retry
```

Keep deeper reasoning in the PR description, not in the squash commit body.
The squash commit on `main` should be one clean title-style line.

## Continuous Integration

Pull requests run a small Android check:

```text
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

Keep CI simple and fast. Only use Gradle tasks that currently exist in the
project. Do not add placeholder CI steps for ktlint, detekt, dependency review,
Firebase validation, or any other tool until the plugin, task, or integration is
actually configured.

Rules:

* no fake automation
* no speculative checks
* CI must reflect real project state

Later additions should happen in two steps:

```text
[core] add ktlint configuration
[ci] run ktlint in pull requests
```

Use the same pattern for detekt, dependency review, secret scanning, Firebase
emulator tests, or Firebase rules validation.

## Release Rules

Start with manual releases and automated checks.

Release flow:

```text
feature work -> PR -> squash merge -> stable main -> manual version bump -> tag -> GitHub release
```

Version tags should stay semantic and calm:

```text
v0.1.0
v0.2.0
v0.3.0-beta1
```

Rules:

* keep release decisions manual for now
* use CI for checks and debug APK builds
* avoid full auto-release until signing and release assets are mature
* write calm prose release notes
* avoid generated bullet dumps

Good release note style:

```text
DIFF Android 0.3.0 improves reconnect handling, draft preservation, and pull request navigation stability.
```

Later, tags may trigger release asset builds:

```text
git tag v0.4.0
git push origin v0.4.0
```

## Issue Rules

Use GitHub Issues for tracked work and problems. Keep the workflow minimal.

Good labels:

```text
bug
enhancement
design
android
sync
discussion
```

Good issue titles are calm and specific:

```text
draft can disappear after process death
reconnect may duplicate pending send
review comments jump during refresh
```

Avoid noisy titles:

```text
urgent bug please fix
```

Use this structure:

* issues for actual tracked work or problems
* discussions for ideas and thoughts
* PRs for implementation history
* `docs/roadmap.md` for product direction

Avoid Jira-style process, large project boards, and many templates early on.

## Commit Rules

Format:

```text
[scope] short summary
```

Rules:

* lowercase only
* no emojis
* no exclamation marks
* short calm wording
* one purpose per commit
* avoid generic summaries
* summaries must be readable without opening code
* prefer specific engineering verbs over repetitive generic verbs

Allowed scopes:

* chat
* sync
* media
* session
* thread
* ui
* core
* ci
* notification
* auth
* storage

Examples:

```text
[session] restore after restart
[chat] preserve unsent text
[sync] avoid duplicate delivery
```

Avoid overusing generic verbs:

* add
* update
* fix

Prefer precise intent verbs:

* refine
* stabilize
* preserve
* reduce
* simplify
* tighten
* align
* defer
* isolate
* harden
* soften
* normalize
* cache
* streamline
* prevent

Good precise summaries:

```text
[sync] stabilize reconnect retry
[media] reduce preview memory
[session] preserve auth restore
[thread] normalize message ordering
```

## Language Style

Prefer calm and concise wording.

Use:

* short phrases
* restrained wording
* clear intent
* product language when possible

Avoid:

* noisy technical phrasing
* excessive detail in summaries
* repeated engineering filler words

Good product-leaning summaries:

```text
[sync] steadier reconnect
[media] lighter previews
[thread] keeps your place
[session] ready after restart
```

## Review Comments

Small review comments are useful when they preserve reasoning.

Good comments explain architecture, tradeoffs, edge cases, or future cleanup:

```text
consider reconnect edge case here
might move this into session manager later
keeping this separate for retry safety
avoids duplicate notification on restore
```

Rules:

* comment only when useful context exists
* keep comments calm and short
* focus on architecture or tradeoffs
* avoid fake approval chatter
* do not create conversations just to make activity

Preferred prefixes:

```text
observation:
question:
note:
follow-up:
later:
```

Examples:

```text
note:
keeping this separate avoids duplicate reconnect work

question:
should session restore own this state instead?

later:
might move retry tracking into sync manager

observation:
this becomes easier to test independently
```

Avoid loud reviewer language unless truly needed:

```text
nit:
must fix
blocking
urgent
```
