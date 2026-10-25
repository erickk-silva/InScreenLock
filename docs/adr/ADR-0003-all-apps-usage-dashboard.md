# ADR-0003 - All-apps daily usage dashboard

Status: ACCEPTED (2026-10-25). Project: InScreenLock. Feature: ISL-001.

## Context

The owner requests a same-screen view of today's foreground time for apps beyond Instagram and TikTok, while preserving their existing daily budget behavior. Usage history is sensitive and Android package visibility is restricted.

## Decision

Add an “Uso de hoje por app” card below the Instagram/TikTok breakdown on the dashboard. It ranks apps by today's interactive, unlocked foreground duration, shows the first ten, and expands in place. Each row shows the app label, launcher icon and duration. An unresolved or hidden package is shown by its package name. If Usage Access or its event query is unavailable, the card says “Indisponível”; a successful query with no positive durations may show an empty-state message.

`DailyTimeCalculator.calculateByPackage` is the canonical span engine for all package totals. The existing Instagram/TikTok budget totals are derived from that same result. Day boundaries use the same `UsageWindow` and local `ZoneId`; events are queried once for a snapshot, sorted chronologically, and counted only while the screen is interactive and the keyguard is hidden. InScreenLock and identifiable System UI/default launcher packages are omitted from the displayed list. Exclusions are applied after span calculation so they do not merge adjacent app spans.

Package visibility is declared only through the `<queries>` MAIN/LAUNCHER intent. No `QUERY_ALL_PACKAGES` permission or package list is persisted. Label/icon lookup failure falls back to the package name and a generic row icon. App icons are rasterized to a bounded 96x96 bitmap.

The snapshot and app list are calculated on the IO dispatcher on demand while the dashboard is visible, using its existing refresh cycle. They remain in memory only. With Usage Access, onboarding and the privacy screen disclose that InScreenLock reads all apps' usage time for today only to show this on-device panel; nothing leaves the device or is saved.

## Consequences

The dashboard adds sensitive usage detail, protected by the same explicit Android Usage Access grant. It does not collect app content, access notification text, or write per-app usage to DataStore, files or logs. The top-ten list is deterministic for equal durations (package name order), uses Material 3 dark tokens from SPEC §4, keeps rows at least 48dp high, and exposes “Nome, 1 h 12 min” semantics to TalkBack. Tests cover the canonical calculation, ranking, formatting and synthetic Compose states.
