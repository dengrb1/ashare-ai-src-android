# Repository Guidelines

## Project Structure & Module Organization

This is a single-module Android application built with Kotlin and Jetpack Compose. Production code lives under `app/src/main/java/com/ashareai/app/`. Keep API clients, DTOs, and settings in `data/`; notification behavior in `island/`; and Compose code in `ui/`, grouped into `screens/`, `components/`, `navigation/`, and `theme/`. Resources and the manifest are under `app/src/main/res/` and `app/src/main/AndroidManifest.xml`. Dependency versions are centralized in `gradle/libs.versions.toml`.

Add local unit tests to `app/src/test/` and device or Compose UI tests to `app/src/androidTest/`. Generated output in `build/` and `app/build/` must remain untracked.

## Build, Test, and Development Commands

Use the checked-in Gradle wrapper (Windows examples):

- `.\gradlew.bat assembleDebug` builds a debug APK.
- `.\gradlew.bat installDebug` installs it on a connected emulator or device.
- `.\gradlew.bat testDebugUnitTest` runs JVM unit tests.
- `.\gradlew.bat connectedDebugAndroidTest` runs instrumentation/UI tests on a device.
- `.\gradlew.bat lintDebug` performs Android static analysis.
- `.\gradlew.bat clean` removes generated build artifacts.

Android Studio should use JDK 17. Configure the local Android SDK through `local.properties`; do not commit that file.

## Coding Style & Naming Conventions

Follow standard Kotlin formatting with four-space indentation and trailing commas in multiline declarations and calls. Use `PascalCase` for classes, composables, and files (`StockDetailScreen.kt`), `camelCase` for functions and properties, and `UPPER_SNAKE_CASE` for constants. Keep package names lowercase. Compose screens should expose focused `@Composable` functions, hoist reusable UI into `ui/components`, and keep networking or persistence out of composables. Run Android Studio's Kotlin formatter and optimize imports before committing.

## Testing Guidelines

No test suites are currently checked in. New business logic should include focused unit tests named `*Test.kt`; navigation, permissions, and critical user flows should use `*Test.kt` instrumentation tests. Prefer deterministic coroutine tests and mocked network boundaries. Run unit tests and lint for every change; run connected tests when UI or lifecycle behavior changes.

## Commit & Pull Request Guidelines

The current history uses Conventional Commit-style subjects, for example `feat: Add Settings and Stock Detail screens with theme support`. Continue with concise imperative prefixes such as `feat:`, `fix:`, `test:`, or `refactor:`. Pull requests should explain behavior changes, identify validation commands, link relevant issues, and include screenshots or recordings for visible UI changes. Keep PRs narrowly scoped and call out changes to permissions, API contracts, or ProGuard rules.

## Security & Configuration

Never commit credentials, tokens, private server URLs, signing files, or generated APK/AAB files. Treat logs from Retrofit/OkHttp carefully and avoid exposing authentication headers or personal portfolio data.
