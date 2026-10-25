# Contributing to BitChord

This repository is @daschinmoy21's personal BitChord fork, focused on NixOS with niri and
desktop UX improvements. Contributions here target this setup, with selected generally useful
changes intended for [upstream BitChord](https://github.com/kushagrasinghx/BitChord) later.

## Proposing Contributions
- Substantial features, refactors, or UI redesigns should normally be discussed before implementation. Open an issue on GitHub to outline your proposal.
- Straightforward bug fixes, documentation improvements, or minor corrections can be submitted directly as a Pull Request.

## Branch and Pull Request Workflow
For this fork, branch from and open pull requests against **`main`**. If you plan to submit a
change upstream, check upstream's current contributing guide and target branch separately;
its release branches do not determine this fork's workflow.

When contributing:
- Fork the repository and create a descriptive branch (e.g., `fix/streaming-buffer`, `docs/translation-guide`).
- Keep pull requests focused on a single change or bug fix. Avoid mixing unrelated refactors, cosmetic tweaks, or mass reformatting with functional changes.
- Keep your branch reasonably synchronized with the target base branch.
- Provide a clear PR description explaining what was changed and why.
- Include relevant testing and validation details with your PR. For desktop UI changes, mention the OS/compositor used and what you tried by hand.
- Indicate whether the change is specific to this fork or a candidate for upstream. Keep upstream candidates focused so they can be ported independently.
- Address review feedback promptly and keep discussions focused on technical merits.

## Development Setup
The project requires the following tools:
- **Desktop:** Java 21 and native graphics/audio libraries. On NixOS, `nix develop` provides the development environment; see [DESKTOP.md](DESKTOP.md).
- **Android:** Java 17 and an Android SDK.
- **Android SDK**: `compileSdk = 37`, `targetSdk = 36`, `minSdk = 26`.
- **C/C++ NDK & CMake**: CMake 3.22.1+ and Android NDK (for native audio DSP components configured under `app/src/main/cpp`).
- **Listen Together Backend (Optional)**: the Go version declared in [`backend/go.mod`](backend/go.mod) if developing or testing the party server (`backend/`).

## Build and Test Commands
Run Gradle commands from the repository root. For the desktop on NixOS:

```bash
nix develop --command ./gradlew :desktopApp:run
nix develop --command ./gradlew :shared:jvmTest :desktopApp:test
```

The Android commands below require an SDK. The Android app module is omitted on a desktop-only
machine without one.

Build:
```bash
./gradlew assembleDevDebug
```

Windows:
```powershell
.\gradlew.bat assembleDevDebug
```

Tests:
```bash
./gradlew testDevDebugUnitTest
```

Windows:
```powershell
.\gradlew.bat testDevDebugUnitTest
```

Run a specific test:
```bash
./gradlew testDevDebugUnitTest --tests "com.music.bitchord.playback.audio.DirectAudioStreamingRegressionTest"
```

## Code Quality
- Follow idiomatic Kotlin and Jetpack Compose conventions.
- Maintain the project's existing architecture and separation of concerns (UI, domain logic, playback services, and data repositories).
- Write unit tests for new logic, fixes, and edge cases.
- Contributors should run relevant tests, must not introduce new failures, and should document known pre-existing failures when applicable.
- Keep diffs focused and minimal. Avoid unnecessary third-party dependencies.

## Audio Changes and Telemetry
For audio changes: application telemetry must not be presented as proof of physical hardware behavior unless that hardware behavior was actually verified.

Distinguish clearly between source metadata, decoder format, internal DSP format, AudioTrack output, AudioFlinger/HAL state, advertised device capability, and external physical endpoint behavior.

## Review Process
Pull requests are reviewed for correctness, architecture fit, and maintainability, with relevant CI checks passing before merge. Preparing a change for upstream is a separate review process under upstream’s own contribution rules.

## Dependencies and Licensing
BitChord is licensed under the **GNU General Public License v3.0 (GPLv3)**.

All contributed code and dependencies must be strictly compatible with GPLv3. Avoid introducing external dependencies unless strictly necessary; any new dependency must be evaluated for necessity, binary size, and license compliance.
