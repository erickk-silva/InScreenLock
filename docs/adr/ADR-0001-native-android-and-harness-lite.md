# ADR-0001 - Native Android and Harness Lite

Status: ACCEPTED (2026-10-18). Project: InScreenLock.

Context: The MVP requires Android UsageStats, AccessibilityService and accessibility overlays. iOS cannot offer the same cross-app behavior. Timebox is 5-7 development days; target is a source + APK with honest real-device evidence.

Decision: native Kotlin, single Gradle app module, Jetpack Compose Material 3 and DataStore. Local control plane = stdlib Python Harness Lite, capsules + deterministic gates + artifact references. Persist the product SPEC and material ADRs in Git; keep run artifacts, instrumentation raw captures and local harness infrastructure out of Git. Unit and instrumentation test code remain in Git, because this is a new personal repository and repeatability matters.

Consequences: no JS/NestJS/Jest, no backend, no Redis/SQLite, no third-party multiagent runtime, no broad accessibility introspection. A new ADR is needed only for material technical changes; a frozen product contract change requires explicit human review and a new SPEC version. Monitoring only works on Android after user-granted permissions and device QA; video transitions remain optional and explicitly estimated.
