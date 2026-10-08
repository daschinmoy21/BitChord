# BitChord Desktop

BitChord now has a Kotlin Multiplatform shared module and a Compose Multiplatform
desktop application for Linux and Windows. Desktop uses the JVM target, which is
the supported Compose Multiplatform desktop model; macOS is intentionally not a
configured target.

This personal fork focuses on NixOS with niri. It keeps the shared Android and Windows
code while improving the Linux desktop experience; selected changes are intended for upstream later.

## Spotify and browser sign-in

Enable **Settings → Account → Spotify**, then choose **Add Spotify account** and an
installed browser. Finish signing in to Spotify, then close the separate sign-in window
to return to BitChord. Chromium, Brave, Chrome, Edge and Vivaldi are offered when installed;
on Linux their launchers must be on `PATH`. YouTube Music sign-in offers the same browser choices.

Spotify appears in the sidebar and Library while enabled. Browse playlists and Liked Songs,
play matched YouTube Music recordings, or import them into persistent local playlists.
You can also import a public Spotify playlist link without connecting an account. Connect
an account for complete pagination of long playlists and access to private playlists.
Matching reports any songs it could not find and can be cancelled.

The Spotify account is shared with Canvas. **Manage account → Disconnect Spotify** removes
its saved credentials and dedicated browser profiles. Disabling the Settings toggle hides
Spotify from the library without disconnecting the account.

## Developing on NixOS with niri

The desktop target uses Java 21. The repository's [flake.nix](flake.nix) provides the JDK,
build tools, and native library paths for an `x86_64-linux` development shell:

```bash
nix develop
./gradlew :desktopApp:run
```

Or run directly from the repository root:

```bash
nix develop --command ./gradlew :desktopApp:run
```

Run inside your niri session with XWayland available. The shell sets
`_JAVA_AWT_WM_NONREPARENTING=1` before Java starts, so AWT passes compositor-driven window
resizes through to the app. It also configures `LD_LIBRARY_PATH`, including
`/run/opengl-driver/lib`, for the renderer, audio libraries, and JavaFX.
The AppImage launcher applies the same AWT setting in a Wayland session.

To validate a desktop change:

```bash
nix develop --command ./gradlew :shared:jvmTest :desktopApp:test
nix develop --command ./gradlew :desktopApp:createDistributable
```

Desktop development does not require an Android SDK. Gradle includes the Android app module
when an SDK is configured through `local.properties`, `ANDROID_HOME`, or `ANDROID_SDK_ROOT`.

On Windows, use a Java 21 shell or install and select a Java 21 JDK before
running `gradlew.bat :desktopApp:run`.

## Installing a release

The fork's [rolling main release](https://github.com/daschinmoy21/BitChord/releases/tag/build-main)
contains `BitChord-linux-x86_64.AppImage` and the universal `BitChord-android-dev.apk`.
The APK is the separate BitChord Dev app (`com.dev.bitchord`) and can coexist with upstream.

On NixOS, use an AppImage environment rather than executing the Ubuntu-built binary directly:

```bash
nix shell nixpkgs#appimage-run --command appimage-run ./BitChord-linux-x86_64.AppImage
```

The desktop packages carry their own Java runtime, FFmpeg and ONNX natives, Automix analyser,
and models. The Nix development shell is for source builds; `appimage-run` supplies the environment
for a downloaded AppImage. Desktop graphics/audio libraries and XWayland are still required.

The versioned [release workflow](.github/workflows/release.yml) produces the following desktop formats
alongside the APK. Check the fork's [releases page](https://github.com/daschinmoy21/BitChord/releases)
for available assets:

| Platform | Download | Notes |
|---|---|---|
| Linux | `BitChord-<version>-linux-x86_64.AppImage` | `chmod +x` and run on a conventional Linux desktop; use `appimage-run` on NixOS. |
| Linux | `BitChord-<version>-linux-amd64.deb` | `sudo apt install ./BitChord-*.deb` on Debian, Ubuntu and derivatives. |
| Linux | `BitChord-<version>-linux-x86_64.rpm` | `sudo dnf install ./BitChord-*.rpm` on Fedora, RHEL and openSUSE. |
| Windows | `BitChord-<version>-windows-x64-setup.exe` | The ordinary installer. Installs for the current user, so it never asks for an administrator. |
| Windows | `BitChord-<version>-windows-x64.msi` | The same thing for anyone who deploys by MSI. |
| Windows | `BitChord-<version>-windows-x64-portable.zip` | Unzip anywhere and run `BitChord.exe`; preferences and cache use normal user directories. |

The Linux packages are built on Ubuntu 22.04 against its glibc, so they install
on compatible conventional Linux distributions. On NixOS, use the AppImage through `appimage-run` or build from the Nix shell.

**Linux system libraries.** The packages carry their own Java runtime and codecs
but not the desktop's own graphics stack, and jpackage cannot derive that list —
so the deb and rpm declare only `xdg-utils`. Any machine running a desktop
session already has what is needed (GTK 3, X11 or XWayland, GL, ALSA); a minimal
or headless install does not, and will fail at startup rather than at install
time.

**Windows and the Visual C++ runtime.** The Automix analyser is linked against a
static C runtime, so on almost every machine there is nothing to install. If
BitChord starts but Automix never analyses anything, install the
[Microsoft Visual C++ 2015–2022 Redistributable (x64)](https://aka.ms/vs/17/release/vc_redist.x64.exe)
and restart it — that is the one dependency the bundled runtime cannot carry
itself, because Windows expects it to be a system component.

## Building the packages yourself

jpackage builds a package by driving the target platform's own tooling, so each
one has to be built on the platform it is for. This is what CI does; see
[`.github/workflows/release.yml`](.github/workflows/release.yml).

On Linux — `rpm`, `fakeroot` and `binutils` must be installed for the packages,
and `file` for the AppImage:

```bash
bash ./gradlew :desktopApp:packageDeb
bash ./gradlew :desktopApp:packageRpm

# The AppImage is wrapped around the app image jpackage produces.
bash ./gradlew :desktopApp:createDistributable
desktopApp/packaging/appimage.sh \
  desktopApp/build/compose/binaries/main/app/BitChord \
  dist/BitChord-linux-x86_64.AppImage
```

On Windows — [WiX Toolset 3](https://github.com/wixtoolset/wix3/releases) must
be on `PATH`, which is what jpackage builds both the `.exe` and the `.msi` with:

```bat
gradlew.bat :desktopApp:packageExe
gradlew.bat :desktopApp:packageMsi

REM The portable build is the app image, zipped.
gradlew.bat :desktopApp:createDistributable
```

Pass `-Pbitchord.version=1.2.3` to stamp a version other than the one in
`desktopApp/build.gradle.kts`; a release build takes it from the tag.
