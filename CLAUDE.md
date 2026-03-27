# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MyTV Android is a native Android TV IPTV live stream player, written in 100% Kotlin with Jetpack Compose. It targets Android 5+ (minSdk 21) and is designed for stable playback on low-end Android TV devices. The app supports multiple device form factors via separate Activities: LeanbackActivity (TV), MobileActivity (phone), PadActivity (tablet).

## Build & Development Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew installDebug           # Install debug build on connected device
./gradlew testDebugUnitTest      # Run local JUnit tests
./gradlew connectedDebugAndroidTest  # Run instrumentation tests on device/emulator
./gradlew lintDebug              # Run Android lint checks
./gradlew assembleRelease        # Release APK (requires signing config)
```

Run a single test class: `./gradlew testDebugUnitTest --tests "top.yogiczy.mytv.ClassName"`

Release signing requires `key.properties` file or environment variables: `KEYSTORE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

## Architecture

**Single module** (`app/`) under namespace `top.yogiczy.mytv`.

### Key Layers

- **Activities** (`activities/`): Entry points per device type. `LeanbackActivity` is the main TV launcher.
- **UI** (`ui/`): Jetpack Compose screens organized by feature under `ui/screens/leanback/`. No Compose Navigation library — routing is manual via state. TV remote input handled by `ui/utils/handleLeanbackKeyEvents()`.
- **Data** (`data/`): Entities (`Iptv`, `Epg`, `GitRelease`) and repositories with strategy-pattern parsers/fetchers:
  - IPTV sources: `IptvParser` interface → `M3uIptvParser`, `TvboxIptvParser`
  - EPG guides: `EpgFetcher` interface → `XmlEpgFetcher`, `XmlGzEpgFetcher`
- **Video Player** (`ui/screens/leanback/video/player/`): Abstract `LeanbackVideoPlayer` base class with Media3 concrete implementation. Includes custom policies for load control, buffering, renderer selection, audio channels — tuned for legacy devices.
- **Settings/Persistence** (`ui/utils/SP.kt`): SharedPreferences wrapper with enum key registry.
- **Embedded HTTP Server** (`ui/utils/HttpServer.kt`): Runs on port 10481 for remote settings configuration via web UI. Web assets in `res/raw/`.

### Special Build Logic

The `app/build.gradle.kts` contains custom Gradle tasks that **relocate Guava classes** inside Media3 AARs/JARs (rewrites bytecode `com/google/common/` → `top/yogiczy/mytv/shaded/guava/`) to avoid classpath conflicts. The `verifyMedia3GuavaRelocation` task validates this.

### Notable Infrastructure

- `UnsafeTrustManager.kt`: Trusts all SSL certificates (many IPTV sources use expired/self-signed certs). Initialized in `MyTVApplication.onCreate()`.
- `BootReceiver.kt`: Auto-launches app on device boot (controlled by `SP.KEY.APP_BOOT_LAUNCH`).
- `app/libs/lib-decoder-ffmpeg-release.aar`: Bundled FFmpeg decoder for old devices.

## Coding Conventions

- Kotlin with 4-space indentation, packages under `top.yogiczy.mytv`
- `PascalCase` for classes/screens/viewmodels, `camelCase` for functions/properties
- Suffixes: `Screen`, `State`, `Repository`, `Parser`, `Fetcher`, `Policy`
- Commit messages use emoji prefixes + short summaries (often in Chinese): `:bug: 修复…`, `:sparkles: 新增…`, `:art: 优化…`

## Code Review

Invoke the project's code-review skill (`$code-review`) after non-trivial changes, especially touching: `video/player/`, `data/repositories/`, `BootReceiver`, `HttpServer`, or `UnsafeTrustManager`. Skill definition at `.codex/skills/code-review/SKILL.md`.

## Tech Stack

- **UI**: Jetpack Compose + androidx.tv (tv-foundation, tv-material)
- **Player**: Media3 (ExoPlayer) 1.3.1 with custom FFmpeg decoder
- **Networking**: OkHttp 4.12.0
- **Serialization**: kotlinx-serialization 1.7.0
- **Build**: Gradle Kotlin DSL, AGP 8.5.0, Kotlin 2.0.0, Java 17
- **CI**: GitHub Actions (`.github/workflows/release.yml`), triggered on `v*.*.*` tags
