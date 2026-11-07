# ADR-0004 - UI redesign, Haze dependency and on-demand weekly usage

Status: ACCEPTED (2026-11-06). Project: InScreenLock. Feature: ISL-001.

## Context

The owner explicitly decided, in the 2026-11-06 session, to redesign the whole app UI from an owner-provided canvas and to add weekly usage analysis. These decisions touch SPEC items (AC-08 "3 views", SPEC §4 palette) and ADR-0001 (dependency policy), so they are recorded here.

## Decision

1. **Full UI redesign** following the owner canvas: dark palette (background #12161C, surface #1F232B, primary #22E3B0 and the canvas accents), card layouts, and a bottom navigation with four destinations: Início, Análises, Limites, Mais. This supersedes the "3 views" wording of AC-08 and the SPEC §4 palette values; accessibility rules (48dp targets, TalkBack semantics, contrast) still apply.
2. **Splash and onboarding screens** are added before the consent flow. Consent still requires an explicit action; nothing is enabled by onboarding. Decorative infinite animations (splash progress, onboarding float) are static when the system animator duration scale is 0 (reduced motion).
3. **Haze 2.1.0** (`dev.chrisbanes.haze:haze` and `haze-blur`, Apache-2.0) is an approved third-party UI dependency for the frosted-glass effect. It declares no permissions, performs no network access and collects nothing.
4. **Weekly usage** (Análises) is computed on demand from Android UsageStats for the last seven local days and held in memory only; nothing is persisted to DataStore, files or logs (owner chose option 1). A day with no recognised events is shown as unavailable, never as zero. The query runs off the main thread when the tab is opened (and on retry or when Usage Access changes), not in the lock path.
5. **Package visibility**: `<queries>` also names `com.instagram.android` and `com.zhiliaoapp.musically` so their launcher icons and labels resolve, in addition to the MAIN/LAUNCHER intent from ADR-0003. `QUERY_ALL_PACKAGES` is still not used.

## Consequences

The new navigation and palette are the UI contract going forward. Haze must be re-reviewed on version changes. Weekly data depends on the retention of Android's UsageEvents and may be partial on some devices. Material accessibility and the privacy texts must keep matching the on-demand, nothing-persisted behavior.
