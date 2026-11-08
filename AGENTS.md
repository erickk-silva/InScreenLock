# InScreenLock - local agent rules (V1.1)

Authority order: explicit user request > `docs/spec/InScreenLock_SPEC_FROZEN_v1.1.md` > accepted ADRs > valid local capsule and policy > live Android docs and real test evidence > existing source > open-source references.

Workspace scope: only the `InScreenLock` checkout and required user-scoped development tool installations. Never mutate unrelated projects, credentials, OS security protections, other repositories, or the user's other applications.

Start: verify `pwd -P`, Git ownership and status, SPEC SHA, installed toolchain, local harness `selftest` / `doctor` / capsule `validate`. No product writes without an authorized capsule. Capsule is a local control-plane check, not an OS sandbox.

Orchestration: Luna 6 is the main orchestrator; investigators are read-only; exactly one logical product writer; independently run reviewer, security challenger and judge against the same candidate SHA. Preserve compact evidence, not full agent transcripts. Keep writer-only mutations, causal RED -> GREEN, YAGNI and clean single-module Kotlin.

Testing: product unit tests and instrumentation tests ARE versioned for durability in this independent new project. Private device traces, labels, screenshots, diagnostics, SDKs, generated artifacts, `.agent-harness`, and `.local-tests` are always local-only. Android gates use JUnit, Coroutines Test, Compose Test, UI Automator; Evals/A-B use Python 3 stdlib, not NestJS/Jest. Build PASS is not Android runtime proof.

Safety: disabled until clear consent; no special-permission bypass; never store app content; threshold locking requires the owner's credential and must never block system navigation, settings, uninstall, or disabling Accessibility. Do not claim Reels viewed when only transition candidates were inferred. Only enable video mode on physical validation for both supported target apps.

Git: private remote only after verification; avoid reset --hard, clean, force-push, rebase, broad staging and unrelated writes. Do not commit local harness, device evidence, secrets or test user data. Stop and report blockers without inventing results.
