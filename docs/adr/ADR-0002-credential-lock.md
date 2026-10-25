# ADR-0002 - Credential-backed daily lock

Status: ACCEPTED (2026-10-25). Project: InScreenLock.

## Context

The owner explicitly changes the reaction described in AC-07 of frozen SPEC v1.1. A threshold reaction can be a full-window, touch-blocking overlay only while Instagram or TikTok is the foreground target. The overlay must offer Home and authenticated unlock, and must disappear on target exit, screen off, lock screen or unrelated/system UI. This is voluntary friction; Android settings, accessibility controls, force stop and uninstall remain available.

## Decision

Replace stored `PAUSE` with `LOCK`, migrating legacy `PAUSE` values on read; new installs default to `LOCK`. A lock is eligible only when Android reports a secure device credential or a local PIN exists. Otherwise the app displays that locking is unavailable and uses the warning reaction. Missing UsageStats data always fails open.

`UnlockActivity` is an internal, excluded-from-recents Compose activity. It supports Android device credentials through `KeyguardManager.createConfirmDeviceCredentialIntent` and a local 4-8 digit PIN. A successful unlock is persisted for 15 minutes, expires at the exact deadline, and is invalidated at local day rollover. PINs are never persisted: DataStore stores a random 16-byte salt and PBKDF2WithHmacSHA256 output (120,000 iterations, 256 bits), verified with `MessageDigest.isEqual`. Derivation runs on a worker dispatcher. Persisted failed-attempt state delays verification after five failures, starting at 30 seconds, doubling to a five-minute ceiling.

Changes that weaken the configured protection require re-authentication in `UnlockActivity`: monitoring off, a higher limit, LOCK to WARNING, local data clearing, and PIN change/removal. More restrictive changes and setting the first PIN do not. No new Android permission or library is needed.

Backup remains disabled and both backup rule formats exclude all app data, so uninstall/reinstall cannot restore preferences, PIN verifier, warning or unlock state. Android UsageStats remains system-owned and is never copied by this app.

## Consequences and limits

The lock is not tamper-proof. A user can disable Accessibility, force-stop or uninstall through Android. Activity launch from an accessibility service and device-credential UI vary by Android/OEM version and require physical validation; a missing or canceled credential flow must never grant unlock. Wall-clock changes can shorten or extend the persisted 15-minute interval, so callers re-evaluate expiry at every foreground event and refresh. Local time-zone/day changes invalidate the grant.

## Escopo de eventos de Acessibilidade

The service receives `TYPE_WINDOW_STATE_CHANGED` events from any package and reads only `packageName`; it discards the event and does not retain its contents. Enabled IME packages and `com.android.systemui` are treated as transient windows: the overlay is hidden and the service retains the active target for a short UsageStats foreground reconfirmation, followed by its normal 10-second refresh. A recent UsageStats foreground transition to another non-transient package ends the target session immediately. Missing UsageStats foreground data preserves the existing event-driven behavior. The service does not inspect window content, bounds or visibility.

### Limits: multi-window and picture-in-picture

Without window-content access, extra permissions or an Android platform signal identifying the target's visible region, the service cannot reliably determine that Instagram or TikTok is in split-screen or picture-in-picture. The full-window accessibility overlay may therefore cover or block another app in those modes. This remains a known limitation to validate on physical devices; the overlay still offers “Ir para o início,” and system navigation remains available. No multi-window detection is claimed.
