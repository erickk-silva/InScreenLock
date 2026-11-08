# InScreenLock - FROZEN SPEC v1.1 (Product, Platform & Harness Lite)

**Version:** 1.1.0 | **Frozen:** 2026-10-18 | **Status:** FROZEN | **Project:** Independent personal project
**Repository:** `InScreenLock` (PRIVATE, owned by the user's personal GitHub account)
**Local workspace:** directory named `InScreenLock` supplied by the user; work only inside this workspace, except installing required development tools.
**Timebox:** Android-first internal MVP in 5-7 days of development; iOS intentionally deferred.
**Product language:** pt-BR. **Baseline:** Android 9+ (`minSdk=28`); validate on current physical Android releases.

## 0. Authority and decisions

1. This v1.1 frozen SPEC and explicit user instructions outrank v1.0 and public examples, agent preferences, and generated plans.
2. Live Android SDK documentation and real-device evidence outrank open-source READMEs.
3. Existing files and Git state in the designated workspace must be inspected before initialization; do not overwrite anything silently.
4. Technical implementation details not dictated below are delegated to the Luna 6 orchestrator, subject to testable acceptance criteria.
5. The user has already authorized installation of needed developer tooling and autonomous execution inside the workspace. No additional approval is needed for this project. Never edit, delete, reset, or otherwise modify unrelated projects or their files.
6. OS-protected permissions and device pairing still require the user to act in Android's own settings; they cannot be silently granted by the application or agent.

**Product decision:** Preflight and build a minimum functional local Harness Lite FIRST, then a new minimal Kotlin Android application, **not a fork** of an existing feature-heavy app. Study open-source detector patterns; reuse source code only if license, compatibility, quality, and attribution have been independently verified.

## 1. Goal and explicit scope

Build a friendly, visually refined, offline Android application for daily self-control of Instagram and TikTok consumption. Once the user has opted in and enabled the necessary Android permissions, opening those apps automatically activates an unobtrusive counter over them. The user can choose a combined daily budget measured by **total time in the two apps** or **estimated transitions into short-form videos**. At the limit, show a warning or a reversible full-screen pause.

**Important semantic distinction:** Time mode measures **whole-app foreground, screen-interactive time** in Instagram and TikTok, including messages and other sections; it must never be described as time exclusively watching Reels. Video mode counts only positively detected **short-video presentations/transitions**, not proven full video watches or unique videos. Label it approximate (`~`, or `aprox.`) throughout.

No server, account, cloud storage, analytics, ads, backend, monetization, or other social networks for the MVP. Primary deliverable is an installable debug APK and source in a private GitHub repo. Store publication is NOT in scope.

## 2. Frozen product defaults

| Item | Contract |
|---|---|
| Name | InScreenLock |
| Android app ID | `com.inscreenlock.app` if not already occupied in this new workspace |
| Stack | Kotlin, Jetpack Compose, Material 3, Coroutines/Flow, Android framework APIs |
| Project structure | Single Gradle Android app module; layered folders, no premature module split |
| Config and state | Jetpack DataStore; no Room/SQLite unless measured data requirements force it |
| Default mode | TIME, combined daily budget for Instagram + TikTok |
| Default time limit | 30 minutes/day, user editable; minimum 1 minute |
| Video-mode default if enabled | 20 estimated short videos/day, user editable; minimum 1 |
| Reaction | WARNING by default; optional PAUSE (user enabled) |
| Monitoring | ON only after clear disclosure and explicit opt-in + OS permissions |
| Data timezone | Device local calendar day; use actual zone boundaries, not 24h increments |
| Display | Dark theme by default, pt-BR; graceful response to large font and TalkBack |
| Packages | Instagram `com.instagram.android`; TikTok candidate `com.zhiliaoapp.musically`; verify installed package IDs on device and narrow scope to known genuine packages |
| Counters | Daily combined budget with individual per-app breakdown |
| Reset | On next interaction/event/UI refresh after crossing local midnight; no exact alarm |
| Code tests | Version controlled product JUnit/instrumentation tests; local private datasets/harness not versioned |
| Remote | PRIVATE only, create/reuse safely via authenticated `gh`, never a public repo |

## 3. Functional acceptance criteria

### AC-01 - Transparent setup and consent (REQUIRED)

First launch explains the actual scope, what time vs estimated video mode measures, and what sensitive data Android Accessibility *could* access. The app collects and stores only narrow non-content event metadata and numeric aggregates. Obtain a distinct affirmative in-app consent before guiding the user to enable Accessibility in system settings. Provide individual status indicators and settings shortcuts for Usage Access and Accessibility. Do not claim protection is active if either essential capability is missing. User can disable monitoring at any time.

No Accessibility permission is auto-granted. Android 13+ sideloaded APKs may show "restricted settings" that require an explicit on-device system flow. Never bypass the OS restrictions. Declare `isAccessibilityTool=false` / do not claim disability-support status.

### AC-02 - Contextual auto activation (REQUIRED)

After onboarding, the system-managed `AccessibilityService` sees supported foreground app changes and activates/hides the badge when Instagram/TikTok enters/leaves the foreground, without launching an InScreenLock Activity every time. Badge must disappear when screen becomes noninteractive, on lock screen, in system settings, and in unrelated apps; it must never obstruct login, system settings, emergency, or accessibility system interactions. No continuous foreground service or polling loop by default.

Time stats remain reviewable in InScreenLock when Usage Access is allowed even if the Accessibility service has been disabled; in that degraded state, explicitly mark badge and pause as unavailable.

### AC-03 - Correct daily time budget (REQUIRED)

Use `UsageStatsManager.queryEvents` for foreground and screen/keyguard events. In API 28 use `MOVE_TO_FOREGROUND`/`MOVE_TO_BACKGROUND`; for API 29+ prefer `ACTIVITY_RESUMED`/`ACTIVITY_PAUSED` (API-29-only constants); prevent double counting across aliases and multiple Activity changes. Handle same-app Activity changes, duplicate/out-of-order events, screen lock, process death, restart, missing system events, and pauses to avoid double counting. Use a single canonical stream/derived daily result; do not independently sum an Accessibility stopwatch and UsageStats data. Refresh the visible total periodically while monitored app is active without excessive battery cost (target 5-15 seconds). Threshold alarm latency target <= 15 seconds in the tested device under normal use.

On resume/restart, reconstruct the current day from Android usage events where available and reconcile cached display data. Stored summaries do not override contradictory system truth. `UsageStatsManager` may return unavailable/null and only preserves events for a limited time: show a clear unavailable state instead of fabricating zero. Day rollover uses `LocalDate` plus device ZoneId; handle timezone/DST changes defensively.

### AC-04 - Video transition estimation, explicitly gated (OPTIONAL RELEASE FEATURE)

Implement a small, isolated per-package `ShortVideoDetector` behind a feature flag initially OFF. Input is only permitted Accessibility event metadata for the two target packages, primarily `TYPE_VIEW_SCROLLED`, `TYPE_WINDOW_CONTENT_CHANGED`, and `TYPE_WINDOW_STATE_CHANGED`. Require credible evidence that the user is within the short-video viewer (Instagram Reels, TikTok video feed) before accepting any transition. **Never count every scroll in the whole Instagram app.** Never claim viewing duration, completion, or unique video identity.

Proposed detector workflow, to be validated on-device:

1. Foreground gate: recognized target package and interactive unlocked screen.
2. Context gate: short-video pager/viewer indicated by actual observed source class/view resource ID/layout metadata; maintain app-specific, optional, bounded rules. Prefer source/event metadata before any broader node inspection.
3. Transition candidate: vertical scroll/pager index change or observed item transition; content-change events alone do not increment, but may confirm state.
4. Deduplication: coalesce event bursts, use an empirically tuned short settle/debounce window (start with 600-1000 ms), avoid consecutive increments for the same transition. Do not dedupe genuinely distinct rapid video transitions just because they are fast.
5. Entry: optionally count the initially shown video ONLY when reliably confirmed by an app-specific signal; otherwise label the metric as transitions and do not invent an opening count.
6. Unknown/ambiguous context: do NOT increment. New app versions or changed IDs fail closed to time mode.

Do not persist texts, captions, usernames, thumbnails, URLs, DOM-like dumps, screenshots, video frames, message content, or hashes derived from private content. No OCR, ML, MediaProjection, injected gestures, root, or traffic interception.

**Release gate per app:** execute an evidence-recorded physical-device run with at least (a) 30 manually verified transitions, (b) 15 negative actions outside short-video feeds, (c) back navigation, comments, share sheet, repeated/rapid swipes, and re-entry. Require >=27/30 correctly matched transitions, <=3 spurious increments during the 30-transition run and <=1 false positive across the 15 off-feed actions. Include device model, Android version, app version, detector settings, observed count and expected count. Video-mode as a **combined Instagram+TikTok limit** is enabled in release only when BOTH apps pass. If either fails or physical evidence is unavailable, ship TIME mode only; detector may remain compiled but hidden/disabled. This is acceptable completion, not a hidden product failure.

### AC-05 - Daily budget and reaction (REQUIRED)

User can choose TIME or, if gated, ESTIMATED_VIDEOS; choose a positive daily combined limit; choose WARNING or PAUSE; enable/disable monitoring. Defaults: 30 min/day, WARNING. Changing mode/limit applies immediately, without deleting today's metric or rewriting historical totals. Validate inputs (no zero, negative, malformed values). Do not let unavailable data trigger a misleading hard lock.

Warning happens once at the first threshold crossing per local day/configuration state (re-arm only for a meaningful newly chosen higher threshold or next day). The dashboard and small badge show used/limit and clear status (normal >= 0%, attention >= 80%, exceeded >= 100%). When exceeding 100%, do not silently clamp the reported amount.

### AC-06 - Nonintrusive counter overlay (REQUIRED)

Preferred implementation: a view attached **from the enabled AccessibilityService** using `WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY`. First try this system-specific overlay to avoid `SYSTEM_ALERT_WINDOW` permission and a redundant foreground service. Verify experimentally on physical devices. If OEM/Android behavior blocks it, document evidence and add a narrowly scoped fallback only if objectively necessary, with explicit user consent for any newly needed special permission.

Badge: compact pill, muted dark surface, translucent, high contrast, default top right inside window insets, e.g. `18 / 30 min` or `~12 / 20 videos`. Outside the badge touches pass through (`FLAG_NOT_TOUCHABLE` on passive badge). Provide an alternate corner in Settings if the default conflicts with Instagram/TikTok controls. Hide on every unrelated window and as soon as protection is off. No fullscreen window for normal counting.

### AC-07 - Reversible pause overlay (REQUIRED)

When PAUSE is enabled and threshold exceeded, show a full-window overlay only over the currently monitored target app, with a short human-centered explanation and buttons to return to the home screen and open InScreenLock settings (or disable this feature). Block taps to the underlying social app while the pause is shown, but never block OS-level Back/Home, notification shade, system settings, emergency screens, or Accessibility controls. Hide immediately when leaving the target app. Re-show if reopened later that day while over budget. This is a **voluntary and reversible friction mechanism**, not device-owner security or tamper-proof enforcement.

Never intercept attempts to uninstall, modify system settings, disable Accessibility or stop the app. Do not add Device Admin, kiosk mode, system setting interception, automatic global-back loops, or anti-bypass protections.

### AC-08 - Dashboard, settings, statuses (REQUIRED)

A minimalist 3-view navigation flow: onboarding/permission setup, one dashboard, one settings screen. Dashboard shows today's combined consumption, remaining/exceeded amount, daily progress indicator, Instagram/TikTok breakdown, current budget and action mode, enable status, and an obvious settings CTA. In TIME-only builds show TIME prominently; estimated counts are absent/disabled, not displayed as fake zero. Provide an in-app privacy explanation and one-tap clear local history/settings reset with confirmation. No unnecessary charts, sign-in, gamification, streaks, or guilt/shame content.

### AC-09 - Resilience and offline operation (REQUIRED)

Keep monitoring logic within a single Android app process/service structure; no internet permission in the APK. Refresh when Accessibility is re-enabled, user returns from settings, target package changes, device rotates, app is killed, date changes, device reboots, or Usage Access is revoked. Do not request exact alarm or battery-optimization exemption by default. Battery usage must remain reasonable: scope Accessibility event types/packages, batch bursts, avoid full tree rescans, and run no continuous 1-second background polling. A system-enabled Accessibility service may restart after process death; verify instead of promising universal behavior across OEMs.

### AC-10 - Privacy, policy, licensing (REQUIRED)

Minimal permissions: Usage Access (`PACKAGE_USAGE_STATS`, user grants via special access) and appropriately declared system-bound Accessibility service (`BIND_ACCESSIBILITY_SERVICE` protection). No `INTERNET`, `QUERY_ALL_PACKAGES`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `READ_SMS`, notification listener, `SYSTEM_ALERT_WINDOW`, or foreground-service declarations unless a proven requirement and documented architecture change justify them. Inspect merged manifest for transitive permissions.

The Accessibility disclosure must be separate, prominent, affirmative, and accurate. Google Play deployment later requires Accessibility API declaration/review for a non-accessibility-tool app; APK-only MVP does not imply store approval. Explain limitations in README and privacy screen. Do not collect/replay/debug personal content. Restrict operational diagnostics to anonymous technical event fields (package, type, event timestamp, optional view ID/class) in debug builds, off by default, kept locally and never committed.

## 4. Quality design system (small, real)

**UX objective:** calm, mature, easy-to-read mobile UI; avoid alarmist or cyberpunk visuals. Use Material 3 Compose, a deliberate dark color palette, and compact motion. No external icon libraries required if Material Symbols suffice.

- **Background:** `#101319`; **surface/card:** `#1B2029`; **border:** `#303845`.
- **Accent/primary:** `#69DDBB`; **text primary:** `#F2F5F7`; **muted text:** `#A5AFBC`.
- **Attention:** `#FFBD75`; **exceeded:** `#EE8791`. Use colors with labels/icons; color alone must not convey state.
- **Typography:** native Roboto / Material 3; hero usage figure 36-40sp, screen title 24sp, body 14-16sp, supportive label >=12sp; respect OS font scale.
- **Spacing:** 4/8/12/16/24/32 dp; phone screen horizontal gutters ~20dp (16dp on narrow widths); rounded cards 20dp; buttons 14dp; minimum interactive hit area 48dp.
- **Screen layout:** top status label -> dominant daily budget ring/progress card -> two app breakdown cards -> settings/action section -> permissions summary. Dark surfaces, restrained depth, readable progress.
- **Microinteraction:** 150-250 ms changes on progress/status/settings when not restricted by reduced-motion; avoid blocking responsiveness or running infinite animations.
- **Overlay:** <= ~160dp width when feasible; passive translucent pill with 14sp readable text, inset >=12dp from safe edges; tone-neutral; red only for exceeded state. Fullscreen pause has clear hierarchy, short plain-language copy, and large actions.
- **Accessibility:** TalkBack semantics, content descriptions, contrast appropriate to small overlay text, font scale, edge-to-edge/inset respect, touch targets, small-device landscape handling, no reliance on color alone.

Minimum visual gate: manually review actual Android screenshots for onboarding, normal progress, attention, exceeded, no permission, and pause on a physical device (or emulator for layout only). Fix visible overlap/clipping before acceptance; do not call a successful Gradle build visual proof.

## 5. Architecture and single source of truth

```text
Android system (UsageStats events, Accessibility events)
     |                    |
     |                    +--> AppContextGate -> ShortVideoDetector (optional, flagged)
     |                                             |
     +--> TimeUsageCalculator                    |
                    \                           /
                     -> serial DailyUsageEngine -> DataStore (settings, daily aggregates)
                                             |          |
                                             v          v
                                   Accessibility Overlay  Compose Dashboard
```

- One Android application module with folders approximating `monitoring`, `detection`, `domain`, `data`, `ui`, `overlay`. Keep classes small, not a ceremonial Clean Architecture template.
- `DailyUsageEngine`: state and crossed-threshold decisions in pure Kotlin, tested without Android.
- `TimeUsageCalculator`: deduplicated daily screen-interactive foreground spans derived from `UsageEvents`; no duplicate simultaneous clocks.
- `ShortVideoDetector`: app-specific rule + event-settle state machine, only for enabled/verified mode.
- `AccessibilityMonitorService`: owned by OS lifecycle; scoped events, current context, overlay; offloads persistence from callback thread.
- `DataStore`: small settings and per-day number summaries only; atomic serialized updates and bounded retention (e.g., last 30 days). No images/text/event archives.
- `MainActivity`: Compose UI only; never a constant companion activity behind Instagram/TikTok.

No custom backend, DB migration, DI framework, network client, complex architecture harness, per-app plugin registry, or new module unless a specific testable need appears.

## 6. Feasibility checkpoint and fallback policy

On day 1, test real Accessibility events in current Instagram Reels and TikTok (candidate package names are not proof) and verify `TYPE_ACCESSIBILITY_OVERLAY` visibility/touch behavior, UsageStats access, and lock-screen safety. Record only technical fields: event types, scrollDeltaY, fromIndex/toIndex where present, source class/view ID when necessary; do not persist arbitrary text or full UI hierarchies. Assess whether event source metadata distinguishes video feed from Instagram Home/Explore/DMs/Stories.

Evidence matrix should include positive cases, negative cases, rapid scroll bursts, app version, Android version, and coverage. If context cannot be reliably identified, **do not substitute every-scroll counting**; keep time monitoring, badge, and pause, and mark video mode disabled. Never add OCR, screen-recording, or root as a workaround.

## 7. Preflight (P0) - environment + local harness, BEFORE implementation

**Mandatory first vertical slice: P0-HARNESS.** Prior design/build cannot start until the scripts and their failure tests operate. P0 is an independent bootstrap phase authorized by the user; after it freezes policy and creates capsule ISL-001, normal product work is subject to that capsule.

### 7.1 Workspace and toolchain preflight

1. Resolve `pwd -P` and compare with the explicitly opened local directory `InScreenLock`. Inspect contents and `git rev-parse --show-toplevel`; stop if this directory sits inside an unrelated repository or existing project. Never overwrite unexpected files.
2. Confirm frozen SPEC SHA-256 and its exact name; inspect a pre-existing Git repository, branches, local changes, remotes, owners, and secret-like files without printing secrets. A nonempty repo must be reconciled, not reset.
3. Inventory WSL version, `python3`, Git, `gh`, JDK, Android SDK cmdline-tools, `sdkmanager`, `adb`, platform-tools, Gradle wrapper, Android target API, emulator availability, USB/Wi-Fi ADB paths and physical-device model/OS only if accessible. Record installed and missing capabilities as FACT/UNKNOWN/BLOCKED; do not claim Android device online from `adb` process existence.
4. Install missing trusted packages (user explicitly authorized). Prefer user-scoped Android SDK, official Google repositories, a compatible stable JDK/AGP/Kotlin/Compose toolchain and project-scoped wrapper; choose actual versions after checking compatibility. Record versions and checksum/source where practical. Do not install Node, NestJS, Jest or an extra orchestration framework for this Kotlin project.
5. If an authenticated `gh` account exists, initialize/reuse exactly one private `InScreenLock` repository and verify owner/name/visibility before pushing. Otherwise continue local and record REMOTE_AUTH_BLOCKED. Scope permissions to the project; never alter unrelated repos, global firewall or third-party credentials.
6. Device probe: adb transport state, whether physical device can install APK, target app package IDs/versions, expected manual Android permissions, Android 13+ restricted-settings flow. Do NOT grant protected consents with adb/root or bypass OS dialogs. Save no social-app content.
7. Create `.gitignore` up front and prove local directories `.agent-harness/`, `.local-tests/`, and `artifacts/` are ignored/untracked. No SDKs, keystores, tokens, private screenshots, logs or device traces may be versioned.

### 7.2 Bootstrap gates (must be executed, not written-only)

The agent creates the minimum Python 3 stdlib harness under `.agent-harness/bin/harness` (a functioning optional starter seed accompanies this SPEC), a local policy, a capsule, and persistent local artifact folders. Execute `selftest`, `doctor`, `task-new`, `validate`, a deliberately failing out-of-scope fixture test, a passing in-scope fixture test, and one deterministic smoke `eval` plus `compare`. Fix actual faults before applying the harness to product work. Test that a modified SPEC SHA, wrong branch, forbidden path, missing gate and untracked-secret candidate are rejected. Keep the control-plane intentionally small.

Expected commands once bootstrapped (the script path is a contract; syntax must match live `--help`):

```bash
python3 .agent-harness/bin/harness selftest
python3 .agent-harness/bin/harness doctor
python3 .agent-harness/bin/harness task-new --task ISL-001 --writer luna-implementer --branch feat/android-mvp
python3 .agent-harness/bin/harness validate
python3 .agent-harness/bin/harness gate-run --gate UNIT -- ./gradlew --no-daemon testDebugUnitTest
python3 .agent-harness/bin/harness gate-run --gate LINT -- ./gradlew --no-daemon lintDebug
python3 .agent-harness/bin/harness gate-run --gate BUILD -- ./gradlew --no-daemon assembleDebug
python3 .agent-harness/bin/harness eval --input .local-tests/video-synthetic-b.jsonl
python3 .agent-harness/bin/harness compare --a .local-tests/video-synthetic-a.jsonl --b .local-tests/video-synthetic-b.jsonl
python3 .agent-harness/bin/harness verify
python3 .agent-harness/bin/harness close --reviewed-sha <exact-reviewed-head-sha>
```

`gate-run` (if present) must capture actual Gradle command, exit status, candidate SHA and log into a local evidence file; `verify` must reject mismatched command gate/SHA or a nonzero exit. `verify` should BLOCK until all required gates have evidence. `close` MUST NOT close a capsule merely because scripts return exit code 0. During work, `validate` checks capsule and scope, while `verify` is an acceptance gate. The optional local pre-commit hook may run `validate` and reject any forbidden staged file; never auto-disable a failing hook.

**P0 exit evidence:** `00-preflight.json`, `01-harness-selftest.json`, actual stable toolchain versions, verified `.gitignore`, capsule hash, branch/base SHA, gate registry initialized PENDING, list of unverified device-specific capabilities. If no physical device is present, P0 still passes with `DEVICE_QA_BLOCKED` and the product implementation proceeds; runtime-related gates stay pending.

## 8. Harness Lite layout and artifact authority

```text
InScreenLock/
  InScreenLock_SPEC_FROZEN_v1.1.md     # tracked, immutable during task
  AGENTS.md                            # tracked, stable agent rules
  README.md                            # tracked, install/use/privacy docs
  docs/adr/ADR-0001-*.md               # tracked; few material ADRs
  app/src/main/...                     # Kotlin product
  app/src/test/...                     # tracked deterministic unit tests
  app/src/androidTest/...              # tracked Compose/UI Automator E2E tests
  gradle/, gradlew, *.gradle.kts       # pinned Gradle toolchain
  .gitignore                           # tracked
  .agent-harness/                     # LOCAL-ONLY, ignored
    bin/harness                        # Python 3 stdlib CLI
    policy.json                        # allow/deny; frozen for task
    task/current.json                  # active capsule ISL-001
    task/gates.json                    # gate states + evidence refs
    artifacts/ISL-001/<run-id>/        # local run artifacts
    hooks/pre-commit                   # optional local hook
  .local-tests/                        # LOCAL-ONLY; device datasets, captures, private fixtures
  artifacts/                           # optional local debug outputs
```

Do not copy the CenterPlatform harness wholesale. Do not add a daemon, state machine server, SQLite database, Node harness, vector store, remote observability stack, custom agent framework, or large MCP collection. **Harness exists to save tokens and reduce rework.** Keep the core to one executable CLI + small JSON policy/capsule and at most a small shell wrapper for Gradle gates. The product itself remains one Android application module.

Versioning decision specific to this PERSONAL greenfield repository: **tracked** deterministic JUnit/unit/instrumentation test source and sanitized synthetic fixtures needed to reproduce app logic; **untracked** harness engine, private event traces, screen content, evidence, device labels, SDK installations and temporary A/B runs. This differs intentionally from the employer-specific `portal-franqueado` local-only testing rule; never import employer code or internal files.

Artifact files are factual, timestamped, SHA-linked and minimal; writers return paths and summaries, not 5,000-line transcripts. At minimum:

| Artifact | Owner | Content |
|---|---|---|
| `00-preflight.json` | orchestrator | root, tool versions, device, Git safety, blockers |
| `01-harness-selftest.json` | harness/investigator | commands, negative guards, exit codes |
| `02-discovery.md` | investigator | Android APIs, OSS license/SHA, factual feasibility |
| `03-red-green.md` | writer | AC, failing contract test, patch, passing causal test |
| `04-gates.json` | harness | unit/lint/build/manifest commands, exit code, SHA |
| `05-evals.json` + `05-ab.json` | eval engine | dataset hash, confusion matrix, scores, A/B decision |
| `06-e2e.md` | device tester | model/OS/app versions, permission steps, observed flows |
| `07-visual.md` | UI tester | app-only screenshots, density/font scale, findings |
| `08-reviewer.md` | reviewer | findings with severity, path, evidence on fixed SHA |
| `09-challenger.md` | challenger | security/accessibility/false positive counterexamples |
| `10-judge.md` | judge | AC -> evidence map, HEAD SHA, ACCEPT/BLOCK |
| `11-handoff.md` | orchestrator | run state, exact SHA, status, unblock instructions |

Artifact paths are illustrative within the current run root, not separate frameworks. `status` should show capsule status, last reviewed SHA, PASS/BLOCK/PENDING gates and a weighted progress percentage **that never equals release approval by itself**.

## 9. Capsule schema, guards and state

Freeze a local schema v1 once P0 selftests pass. Minimum required keys:

```json
{
  "schema_version": 1,
  "task_id": "ISL-001",
  "status": "AUTHORIZED",
  "branch": "feat/android-mvp",
  "base_sha": "<actual git base sha>",
  "spec_sha256": "<actual SHA256>",
  "risk": "HIGH",
  "writer": "luna-implementer",
  "scope": {
    "edit_paths": ["app", "gradle", "docs/adr", "README.md", ".gitignore", "AGENTS.md", "build.gradle.kts", "settings.gradle.kts", "gradle.properties", "gradlew", "gradlew.bat"],
    "forbidden_paths": ["other-projects", "secrets", ".github/workflows"]
  },
  "effects": {
    "tool_install": true,
    "private_git": true,
    "physical_device_smoke": true,
    "production_write": false,
    "bypass_os_consent": false
  },
  "required_gates": ["PREFLIGHT", "HARNESS", "UNIT", "LINT", "BUILD", "PRIVACY", "VISUAL", "E2E", "REVIEW", "JUDGE"],
  "artifact_root": ".agent-harness/artifacts/ISL-001/<real run id>",
  "created_at": "<UTC timestamp>",
  "expires_at": null
}
```

Replace all placeholders with actual live values. This JSON is a normative example; do not claim pre-existing capsule bytes. The capsule and policy belong to the local bootstrap and are not committed; policy changes after bootstrap require a new distinct preflight record and a new/updated capsule, never silent loosening by a writer to make a gate green.

Rules: exactly one product writer, investigators/read-only reviewers, bound root/worktree, exact branch and valid base SHA, frozen SPEC hash, scoped edits and ignored local roots; deny secret-like paths, license violations, unexpected file changes, destructive Git commands, unrelated directories and OS consent bypass. A script alone cannot sandbox shell access; role instructions, worktree scoping, Git hook checks and human-access boundaries complement verification. Always differentiate **write-block prevention** from **post-write detection**.

Lifecycle: `NO_CAPSULE -> AUTHORIZED -> IMPLEMENTING -> REVIEWING -> VERIFIED -> CLOSED`. Missing device proof remains `DEVICE_QA_BLOCKED`/PARTIAL, not CLOSED. `close` must require all mandatory ACs, required gates with genuine artifact paths, JUDGE APPROVE for the exact final SHA and no P0/P1. Never auto-close on branch push or green Gradle alone. A new task gets a distinct capsule; no rewriting history to recycle a capsule.

## 10. Evals - numerical algorithm with hard release gates

Two independent tracks: **TIME** (mandatory, whole target app) and **VIDEO** (optional, estimated short-video transitions). A single combined score must never hide mandatory blockers. All progress numbers show both `observed` and `not_yet_proven` status. Synthetic fixtures can test logic but never authorize VIDEO release.

### 10.1 Canonical video eval dataset

Private JSONL, one row per labelled observation/action, no screen content:

```json
{"case_id":"IG-001","app":"instagram","source":"device","expected":1,"predicted":1,"critical_negative":false}
{"case_id":"IG-DM-001","app":"instagram","source":"device","expected":0,"predicted":0,"critical_negative":true}
```

`source`: `synthetic` or `device`; `expected` and `predicted` are integer increments (not account identifiers); stable `case_id`, independent manual annotation, explicit app version/OS/model in a separate QA manifest. Never persist texts/captions/creators/media/screen dumps. For algorithm A/B, both predictions MUST be replayed against exact same case IDs, labels and fixture SHA, without changing annotations to favor B.

For each app, compute:

```text
TP = SUM(min(expected_i, predicted_i))
FP = SUM(max(predicted_i - expected_i, 0))
FN = SUM(max(expected_i - predicted_i, 0))
NEGATIVE_CASES = COUNT(expected_i == 0)
FP_NEGATIVE_ACTIONS = COUNT(expected_i == 0 AND predicted_i > 0)
CRITICAL_FP = COUNT(critical_negative_i AND predicted_i > 0)
Precision = TP / (TP + FP), 0 if denominator=0
Recall = TP / (TP + FN), 0 if denominator=0
FPR_actions = FP_NEGATIVE_ACTIONS / NEGATIVE_CASES, 1 if denominator=0
EvalScore = 100 * (0.50*Precision + 0.35*Recall + 0.15*(1-FPR_actions))
```

Score is **diagnostic**, not certification. Gate VIDEO per app requires real-device evidence with >=30 manually labeled true transitions, >=15 negative actions including DMs/Home/Explore/comments/share/back, `TP>=27`, `FN<=3`, positive-case spurious increments `<=3`, off-feed negative actions with false positives `<=1`, and `CRITICAL_FP=0` for system/DM/login/settings. Record app version and sample provenance. Both apps must pass for the combined video-limit mode; otherwise feature flag stays OFF and a TIME-only MVP is a legitimate outcome. No statistics are inferred from synthetic cases.

### 10.2 Paired A/B offline comparison

- **A:** simple scroll/debounce detector baseline in a *test-only* implementation (NOT a product fallback).
- **B:** contextual source/window gate + transition confirmation + burst deduplication; the candidate for the MVP.
- Replay A and B on identical frozen labeled cases; output each confusion matrix, score, `delta_score=B-A`, false positives, and decision.
- B cannot replace the accepted candidate if it introduces critical false positives, fails absolute release thresholds or demonstrably reduces the eval score against the same case set. Do not use a low-quality A to claim generalized superiority. Keep at least a small held-out subset not used for tuning. Do not repeatedly tune against the final test set.
- A/B tests here are **offline algorithm evaluations**, not online behavioral experiments or traffic split of real users. They require NO backend, analytics or personal-usage tracking.

### 10.3 Time meter, pause and global progress

TIME eval must include a deterministic table of at least 12 scenarios: two apps sequential/alternating, same-app Activity switches, duplicate/out-of-order events, screen-off, keyguard, timezone/day rollover (23:59:59), process restart, permission lost, unavailable events, threshold crossing once and app exit. Require exact expected durations on synthetic traces (tolerance <= 1 second of pure calculation), no double count, idempotent replay, no false PAUSE when data is unavailable. Real-device telemetry must label clock interval uncertainty and measured threshold latency, target <=15 seconds under normal use; only a real test may claim that SLA. Tests prove observable contract, not merely method invocation.

Progress % is a dashboard for engineering, not a release score. Example weighted implementation: `PREFLIGHT=10, HARNESS=10, UNIT=20, LINT=5, BUILD=5, PRIVACY=10, VISUAL=10, E2E=15, REVIEW=5, JUDGE=10` (sum=100). Only PASS with genuine evidence earns weight; BLOCK/PENDING/UNKNOWN=0. Feature VIDEO has a separate `video_release_gate` and never inflates mandatory progress. State `E2E_PENDING` explicitly if no physical device; 85% progress does not equal release approval.

## 11. Testing standard: causal TDD, Android E2E and sensitivity

**Do not use Jest, NestJS, Playwright-browser or Node for this app by default.** Native ecosystem is more direct and lighter:

| Layer | Tool | Contract | Artifact |
|---|---|---|---|
| Fast pure domain | Kotlin JUnit4 + kotlinx-coroutines-test | usage reconciliation, days, thresholds, detector burst/state | RED/GREEN log |
| Android local if necessary | Robolectric (optional, only for Android API boundary) | lifecycle/integration without emulator | unit result |
| Compose semantics | `androidx.compose.ui:ui-test-junit4` + AndroidJUnitRunner | buttons, labels, state, min 48dp targets, dark UI | instrumentation report |
| Multi-app E2E | AndroidX UI Automator **2.4.0 stable** | Settings navigation, foreground changes, overlays, pause | E2E report |
| Physical app-specific | opt-in Android phone + real Instagram/TikTok | real feed context, false positives, overlay safety | app versions + private labels |
| Offline detector A/B | Python 3 stdlib local harness | identical labeled trace, score, non-regression | eval/compare JSON |

Prefer JUnit4/Gradle Android test defaults to keep compatibility, no new dependency injection framework. Use stable versions vetted against actual AGP/Kotlin/Compose toolchain. Default commands, adjust only to tasks present in live project:

```bash
./gradlew --no-daemon testDebugUnitTest
./gradlew --no-daemon lintDebug
./gradlew --no-daemon assembleDebug
./gradlew --no-daemon connectedDebugAndroidTest
```

`connectedDebugAndroidTest` is executable ONLY with a verified authorized device or emulator; if absent it is BLOCKED, not PASS. Emulator may prove InScreenLock onboarding/UI and synthetic feed simulator states; it does NOT replace physical Instagram/TikTok detector proof. UI Automator can interact across apps but must not bypass Android permissions or collect screen content. Test app-specific flows on physically controlled accounts/devices only, without automation that posts messages, alters profiles, harvests content or bulk interacts with third-party platforms.

Per AC, require positive + negative + boundary where causal. Mandatory RED before behavioral patch and matched GREEN; reviewer must explain a concrete mutation that would make a critical test fail. At least one isolated temporary mutation probe must fail one central time/threshold test and then be fully reverted. Never accept "exit 0" as proof that user-visible functionality works. Capture Gradle command, exit code, total test count and failures, path to report, final SHA and physical test state.

E2E minimum: first-run consent denied/granted, correct shortcut to special-access settings, app reopen, target application enter/exit, badge show/hide and touch pass-through, threshold warning once, reversible PAUSE via home/settings, screen lock and unlock, permission revocation, local midnight synthetic rollover, narrow layout/high font scale and TalkBack basics. Full physical workflow may be blocked by device absence; document with explicit gate state.

## 12. Evaluation harness correctness and guard tests

Harness is tested like production gating logic, with **negative assertions**:

1. Baseline happy path: `selftest`, `doctor`, task creation and `validate` work on a new isolated local repository.
2. Unexpected workspace root, another repository's Git root, symlink/path traversal and wrong branch are rejected.
3. Capsule missing, invalid schema, expired capsule, SPEC SHA modified and policy/scope mismatch are rejected.
4. Secret-like `.env`/keystore/credentials, ignored dirs accidentally tracked and out-of-scope product changes are rejected. The scope verifier checks both staged and unstaged changes and untracked nonignored files (not only `git diff --cached`).
5. `eval` recomputes TP/FP/FN/precision/recall/FPR on an exact golden fixture; invalid counts, duplicates or missing labels block. A/B datasets with different cases/labels block.
6. `verify` rejects any mandatory gate PENDING, BLOCKED, PASS lacking evidence path or mismatched final SHA. `close` rejects a stale reviewed SHA even if Gradle passes.
7. `git status --porcelain` and `git ls-files` prove `.agent-harness`, `.local-tests`, raw artifacts and secrets never entered the commit.

P0 requirement is functional fail-closed behavior, not a policy file that says it exists. If a supplied starter script has a bug, the agent may fix it within its **local harness bootstrap scope** and record the change, never downgrade scope to pass a gate.

## 13. Privacy, Android API semantics and sources of truth

`UsageStatsManager` foreground events need API-specific handling: API 28 `MOVE_TO_FOREGROUND/BACKGROUND` vs API 29+ `ACTIVITY_RESUMED/PAUSED`. Do not access API29 constants unguarded in a 28 device. `TYPE_ACCESSIBILITY_OVERLAY` via WindowManager is supported in older Android; the newer `AccessibilityService.attachAccessibilityOverlayToDisplay` API is API34+, not the universal default. The platform may suppress or restart accessibility services; test actual OEM behavior. Source scope includes only authorized Instagram/TikTok, with correct package visibility checks and no `QUERY_ALL_PACKAGES` by default.

Accessibility is a sensitive permission, so in-app prominent disclosure and affirmative consent are required. Do not label this digital-wellbeing product `isAccessibilityTool=true`. No hidden capture of usernames, messages, creators, frame pixels, traffic, contacts, audio, screen recordings or secrets. Do not bypass restricted settings, accessibility disable/uninstall, security prompts, or otherwise misrepresent compliance. Play Store approval is separate and outside the 5-7 day private APK goal.

OSS source/reference audit: AwayDoomscrollin' GPL-3.0-only **read-only patterns**, ReelLock MIT only if code/license verified and attribution retained, ReeTCode as reference only until license evidence exists. Record URL, inspected commit SHA, relevant source paths, actual license and why adopted/rejected. Open-source code must never silently expand required permissions or telemetry.

## 14. Phase plan and timebox

| Phase | Target | Required exit artifact |
|---|---|---|
| D1 P0 | Preflight WSL/Git/Android tools + functional Harness Lite, capsule, tests | `00-preflight`, selftest, scope negative checks |
| D1 Spike | Tiny accessibility/UsageStats/overlay probe, context feasibility | `02-discovery`, API/device matrix |
| D2 | Domain time accounting and threshold engine, DataStore | RED/GREEN + TIME eval |
| D3 | Design system and Compose onboarding/dashboard/settings | UI semantics + visual states |
| D4 | Context badge and reversible PAUSE, optional flagged detector | E2E partial + video A/B if feasible |
| D5 | Unit/lint/build/e2e, app privacy audit, screenshots, independent triple review | exact-SHA gates + APK |
| D6-7 | Fix only evidence-driven defects; retest affected gates; finalize private repo | `11-handoff`, closed capsule if fully proven |

If timebox tightens, keep the required time/overlay/pause experience and privacy; do not trim causal tests or claim speculative video-mode success. Avoid recurrent review of an unchanged SHA, needless refactoring, unnecessary module splitting, and endless tool installation. Continue nonblocked work when Android device or GitHub authentication is missing.

## 15. Multiagent execution contract

The main Codex **Luna 6** agent is ORCHESTRATOR only when subagents are supported. It owns the plan, capsule status, Git integration, gate decisions, concise communication and final result; product code written by the **single implementer**. Parallel read-only investigators: Android runtime/API + third-party detector/license, plus test/eval/UI research only when nonoverlapping. On first real feature branch candidate SHA, run independent reviewer, security/false-positive challenger and judge against exactly the same SHA, without each other's conclusions. Judge maps ACs to evidence, not adjectives. Consolidate findings into one deduplicated minimal repair packet; let the same writer repair, rerun affected gates and resubmit a changed SHA. Limit to <=2 corrective cycles before explicit `NON_CONVERGED` block.

Each worker reports <=5 factual findings, file/symbol references, commands/tests, open risks, and artifact paths. Store deep logs locally; keep main thread bounded. If CLI has no subagent support, execute equivalent roles sequentially in independent contexts, maintaining one writer. No external agent framework installation unless the CLI proves one is missing for a necessary task; never fake subagent execution.

## 16. Git, ADR and spec change strategy

- Initialize new local repository safely, create a reviewed baseline commit with frozen SPEC/AGENTS/ADR and `.gitignore`, then create `feat/android-mvp` (or reconcile existing repo without force/rebase). No broad staging; paths explicit.
- Private GitHub remote only under authenticated user's own account and verified PRIVATE before push. No public repository, no unknown existing remote overwritten; if offline/no auth, local commits + concrete blocker.
- SPEC v1.1 frozen: no autonomous feature-contract edits; discrepancies go into `docs/adr` if purely technical, or report `SPEC_CHANGE_REQUIRED` for human-controlled new revision. Agents MAY autonomously resolve implementation details not affecting frozen ACs.
- `AGENTS.md` contains stable concise rules; `.agent-harness` controls current run; ADRs record only material architectural decisions and consequences; do not create ADR per component/commit.
- Use Git-reviewed changes for Kotlin product and its *reproducible* tests. Keep local harness/artifacts/device traces under `.gitignore` (supported by explicit Git gates). No source from private employer repos.
- Verify `git diff --check`, strict path allowlist, merged Android manifest, no secrets, unit/lint/build commands and APK artifact. Record exact HEAD reviewed SHA before final private push. Do not silently mutate source after independent reviews; any code mutation invalidates prior SHA approval.

## 17. Required gates, terminal states and DoD

| Gate | Acceptable evidence | Required? |
|---|---|---|
| PREFLIGHT | root/tooling/Git + device classification | YES |
| HARNESS | selftest+negative cases+capsule/hash/scope | YES |
| UNIT | critical time+threshold+detector RED/GREEN + 12 scenarios | YES |
| LINT | `lintDebug` and documented non-suppressed diagnostics | YES |
| BUILD | `assembleDebug`, real generated APK + manifest | YES |
| PRIVACY | explicit consent, permissions, source/manifest/license checks | YES |
| VISUAL | app-only UI state checks incl. small/font scale | YES (emulator acceptable for UI) |
| E2E | cross-app real-device open/exit overlay and pause | YES for `ANDROID_MVP_APPROVED` |
| REVIEW | independent same-SHA diff review, P0/P1=0 | YES |
| JUDGE | exact SHA, AC->evidence, no blocks | YES |
| VIDEO_RELEASE | physical device per-app metrics + paired A/B | CONDITIONAL; if not proven, OFF and show exact reason |

Terminal states:

- `ANDROID_MVP_APPROVED`: all required gates PASS, physical E2E PASS, reviewer and judge approve exact SHA, private source/APK deliverable verified, capsule CLOSED.
- `DEVICE_QA_BLOCKED`: build/tests/harness may be green, but Android physical consent/ADB or target apps not accessible; E2E remains PENDING, capsule cannot close approved.
- `PARTIAL`: one or more nondevice mandatory gates fail; list exact failure and next repair.
- `REMOTE_AUTH_BLOCKED`: remote is not created/pushed, development may be otherwise sound. Do not falsely claim remote delivery.
- `VIDEO_DISABLED_UNVERIFIED`: TIME MVP may be approved; flagged VIDEO release OFF with rationale and no fabricated score.
- `NON_CONVERGED`: after <=2 causal repair cycles for same issue, stop repeated loops and report primary evidence.

Final report (pt-BR, concise): outcome; working root; branch/base/HEAD/reviewed SHA; private remote visibility/push if verified; versioned source tests; toolchain; gradle command results; harness selftest, path/capsule/eval outputs; TIME eval; A/B VIDEO per app or disabled; app-only visual and real E2E evidence; P0/P1/P2; manifest privacy; APK relative path; artifacts and exact external blockers/user action when inevitable.

## 18. Primary official sources (verified 2026-10-18)

- Android AccessibilityService and scoped events: https://developer.android.com/guide/topics/ui/accessibility/service
- Android Accessibility policy, user disclosure: https://support.google.com/googleplay/android-developer/answer/10964491?hl=en
- UsageEvents API28/API29 compatibility: https://developer.android.com/reference/android/app/usage/UsageEvents.Event
- UsageStatsManager: https://developer.android.com/reference/android/app/usage/UsageStatsManager
- Accessibility overlay window: https://developer.android.com/reference/android/view/WindowManager.LayoutParams
- Compose-first Android guidance: https://android-developers.googleblog.com/2026/05/android-ui-development-is-compose-first.html
- Compose UI test: https://developer.android.com/develop/ui/compose/testing
- UI Automator 2.4.0 stable: https://developer.android.com/jetpack/androidx/releases/test-uiautomator
- UI Automator cross-app guides: https://developer.android.com/training/testing/other-components/ui-automator
- Android sdkmanager installation: https://developer.android.com/tools/sdkmanager
- WSL USB documentation: https://learn.microsoft.com/en-us/windows/wsl/connect-usb
- Android DataStore: https://developer.android.com/topic/libraries/architecture/datastore
- OSS AwayDoomscrollin' (GPL): https://github.com/ResolveCommunity/AwayDoomscrollin
- OSS ReelLock (verify exact MIT code before reuse): https://github.com/SohamTilekar/ReelLock
- OSS ReeTCode (read-only until license proved): https://github.com/prince-721/ReeTCode

**Frozen final rule:** The user has authorized autonomous project-scoped execution, not unrelated file changes or Android security bypass. Product quality is proven by actual measurable contracts; synthetic evaluations and green builds do not replace on-device observations. Harness itself must stay smaller and simpler than the app it is validating.
