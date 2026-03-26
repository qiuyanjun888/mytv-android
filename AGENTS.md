# Repository Guidelines

## Project Structure & Module Organization

This repository is a single-module Android app. Application code lives in `app/src/main/java/top/yogiczy/mytv`, with `activities/` for entry points, `data/` for entities and repositories, `ui/` for Compose screens and themes, and `utils/` for shared helpers. Android resources are in `app/src/main/res`, bundled decoder binaries are in `app/libs`, and documentation assets are in `screenshots/`. Unit tests belong in `app/src/test`, and device or emulator tests belong in `app/src/androidTest`.

## Build, Test, and Development Commands

Use the Gradle wrapper from the repository root:

- `./gradlew assembleDebug` builds a debug APK.
- `./gradlew installDebug` installs the debug build on a connected device.
- `./gradlew testDebugUnitTest` runs local JUnit tests in `app/src/test`.
- `./gradlew connectedDebugAndroidTest` runs instrumentation tests on a device or emulator.
- `./gradlew lintDebug` runs Android lint checks.
- `./gradlew assembleRelease` builds the release APK; it requires `key.properties` or `KEYSTORE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`.

## Coding Style & Naming Conventions

Follow Kotlin and Android Studio defaults: 4-space indentation, no tabs, and keep packages under `top.yogiczy.mytv`. Use `PascalCase` for classes, Compose screens, and view models (`MainScreen`, `SettingsViewModel`), `camelCase` for functions and properties, and `lower_snake_case` for Android resources (`network_security_config.xml`). Match existing suffixes such as `Screen`, `State`, `Repository`, and `Parser`. Use the IDE formatter before submitting; no repo-specific formatter is configured.

## Testing Guidelines

The project uses JUnit4 for local tests and AndroidX instrumentation for device tests. Add or update tests for behavior changes in repositories, parsers, and screen-routing logic. Prefer descriptive test names such as `IptvRepositoryTest` or `MainActivityInstrumentedTest`. There is no enforced coverage threshold, but changes without targeted tests should be justified in the PR.

## Commit & Pull Request Guidelines

Recent history uses emoji-style prefixes plus short summaries, often in Chinese, for example `:bug: 修复…`, `:sparkles: 新增…`, `:art: 优化…`, and `:bookmark: v1.4.4`. Keep commits focused and readable. Pull requests should include a clear summary, test notes, linked issues when applicable, and screenshots for UI changes across Leanback, mobile, or pad flows.

## Security & Configuration Tips

Do not commit `local.properties`, `key.properties`, keystores, or signing secrets. Release automation is defined in `.github/workflows/release.yml` and publishes tagged builds in the `vX.Y.Z` format.

## Agent-Specific Instructions

This repository includes a project-local skill at `.codex/skills/code-review/SKILL.md`. Invoke `$code-review` after non-trivial code changes, especially when touching `ui/screens/leanback`, `video/player`, `data/repositories`, `BootReceiver`, `HttpServer`, or `UnsafeTrustManager`. Review output should list findings first, include file references, and call out testing gaps when behavior changes without coverage.
