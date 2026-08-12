# AShare AI Android Engineering Guide

## Scope and Architecture

This repository contains the standalone Android client for the AShare AI research system. It is a single Android application module written in Kotlin and Jetpack Compose.

```text
app/src/main/java/com/ashareai/app/
├── data/       # Retrofit, OkHttp, SSE, DTOs, DataStore, and encrypted local secrets
├── island/     # Push, foreground monitor service, notification routing
├── ui/
│   ├── components/
│   ├── navigation/
│   ├── screens/
│   └── theme/
├── AShareApp.kt
└── MainActivity.kt
```

- Keep API clients, DTOs, URL helpers, and local preference storage in `data/`.
- Keep notification and foreground-service behavior in `island/`.
- Keep composables free of networking and persistence. Place screen state in focused screen state or `AppViewModel`, and reusable UI in `ui/components/`.
- Keep backend DTO fields in `data/model/Dtos.kt` aligned with the `/api/v1` snake_case JSON contract. Do not rename fields to camelCase unless a `@SerialName` migration is deliberately added and tested.

## Product Boundaries

The mobile client supports investment research, market observation, paper portfolios, research, backtests, AI chat, notifications, personal data import/export, and the administrator controls exposed by the backend.

The Android administrator area includes model configuration, system resource/settings control, runtime identity, and Edge Gateway configuration. Account lifecycle management (create, disable, delete, reset password) is intentionally Web-only. Do not add Android account management screens unless the product requirement changes explicitly.

The product is for research and paper trading. Do not add broker execution, real-money order placement, or language implying a recommendation or guaranteed return.

## AI Configuration and Secrets

- The model settings UI must use the backend `/api/v1/admin/model-settings` endpoints. The backend owns encryption, versioning, validation, model probing, and activation.
- Never persist an AI API Key in `SettingsStore`, Compose saved state, logs, screenshots, tests, or analytics. Hold it only as ephemeral form input and send it over the authenticated API request.
- Leaving a secret field empty must retain the server-side encrypted value. API responses expose only configuration flags such as `api_key_configured`.
- System settings and Edge Gateway changes require the short-lived `X-System-Settings-Unlock` token obtained from the administrator password flow. Do not cache this token in DataStore.
- Do not log Authorization headers, AI keys, FRP TOML tokens, unlock tokens, portfolios, or archive passphrases.

## Low-Memory and Lifecycle Rules

- The normal foreground client must start only login/session restoration and the active UI. Market polling runs only while the app is foregrounded and logged in.
- Do not initialize Mi Push or start `MonitorService` at application startup. Push registration, foreground monitoring, and notification permission requests occur only after the user actively enables notifications in Settings.
- When notifications are disabled or the user logs out, stop the foreground service, cancel push event collection, and unbind the remote device.
- Do not introduce global background loops, large caches, eager chart loading, or eager network prefetches. Load screen-specific data when the screen becomes active and release/cancel jobs when it leaves scope.
- Preserve the `LIGHTWEIGHT` versus `SUPREME` backend runtime setting semantics. Mobile controls configure the backend; they must not attempt to run quant models locally.

## UI Rules

- Follow the existing Material 3 design system and `AShareTheme` tokens. Do not add a parallel component library.
- The interface uses an Apple-inspired Liquid Glass treatment only for functional chrome: navigation, toolbars, bottom controls, popovers, and sheets. Keep charts, forms, reports, tables, and repeated data cards clear and opaque enough for financial scanning.
- Maintain visible focus, adequate contrast, 48dp touch targets, stable layouts, and dark/light theme support.
- Use familiar Material icons in icon-only controls with content descriptions. Do not use decorative emoji, gradients as the primary background, or large marketing-style layouts for operational screens.
- Test rendering on a real device or emulator for navigation, permissions, keyboard resizing, long Chinese labels, dark mode, and accessibility scaling when UI behavior changes.

## Build and Test Commands

Use JDK 17 and the checked-in Windows Gradle wrapper:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat connectedDebugAndroidTest
```

For every code, resource, manifest, Gradle, or dependency change, run:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease
```

Run `connectedDebugAndroidTest` whenever the change affects Compose UI, navigation, permissions, foreground service behavior, activity lifecycle, or notification routing and a device is available. Report the missing device rather than claiming connected tests passed.

`app/build/` and root `build/` are generated and must remain untracked.

## Tests and API Contracts

- Put local JVM tests under `app/src/test/java/` and instrumentation/Compose tests under `app/src/androidTest/`.
- Add or update `*ContractTest` serialization coverage whenever a backend model or endpoint shape changes, especially admin model settings, system settings, Edge Gateway, research, AI chat, and monitoring DTOs.
- Prefer deterministic coroutine tests and mocked network boundaries. Keep tests focused on observable behavior.
- Treat unknown fields as backward-compatible only because `ApiClient.json` deliberately uses `ignoreUnknownKeys`; required fields must still mirror the backend contract.

## Security and Release Requirements

- `local.properties`, `keystore.properties`, `.keystore`, `.jks`, signing passwords, API keys, internal server URLs, push credentials, and generated APK/AAB files must never be committed.
- Release artifacts must be signed. Do not treat `app-release-unsigned.apk` as deliverable.
- Verify a release APK with `apksigner verify --verbose` before distribution.
- Preserve authenticated Retrofit behavior and token refresh safeguards. New mutating backend requests must use idempotency keys when the server endpoint requires them.

## Editing and Git Hygiene

- Use standard Kotlin formatting: four spaces, focused functions, `PascalCase` classes/composables/files, `camelCase` functions/properties, and `UPPER_SNAKE_CASE` constants.
- Prefer existing patterns and narrowly scoped changes. Avoid unrelated refactors.
- Preserve user changes in a dirty worktree. Never use destructive Git commands to discard changes unless explicitly asked.
- Use Conventional Commit subjects, such as `feat: Add administrator model controls`.
- Before committing, inspect `git diff --check`, review the staged diff, and ensure tests appropriate to the change have passed.
