# Fork releases and the desktop update check

This fork is maintained for @daschinmoy21's NixOS/niri desktop. Builds and update checks use
[daschinmoy21/BitChord releases](https://github.com/daschinmoy21/BitChord/releases).
Selected fixes and UX improvements may be contributed to
[upstream BitChord](https://github.com/kushagrasinghx/BitChord) later.

## Rolling builds

[`.github/workflows/fork-appimage.yml`](../.github/workflows/fork-appimage.yml) builds a Linux
AppImage and a universal Android APK on branch pushes, except changes covered by its
`paths-ignore` filters. It publishes only after both builds succeed.

- `main` publishes the rolling prerelease [`build-main`](https://github.com/daschinmoy21/BitChord/releases/tag/build-main).
- Other branches use `build-<branch>`, with `/` replaced by `-`.
- The Linux asset is `BitChord-linux-x86_64.AppImage`.
- The Android asset is currently `BitChord-android-dev.apk`, the separate BitChord Dev app
  (`com.dev.bitchord`). The fork has no production signing secrets configured.
- Both apps receive `1.8-fork.<run_number>` as their version name. The release notes identify
  the source commit. The rolling tag and assets are replaced by each successful build.

Rolling tags such as `build-main` do not encode a version. The desktop updater ignores them;
download these builds from the release page. For NixOS AppImage instructions, see
[DESKTOP.md](../DESKTOP.md).

## Versioned releases

[`.github/workflows/release.yml`](../.github/workflows/release.yml) builds Linux, Windows, and
Android artifacts. A matching tag push (`v*.*.*`, for example `v1.8.0`) creates a **draft**
release containing all three platforms. Review the assets and publish the draft when ready.
A manual run accepts a version and uploads build artifacts; running it from a branch does not
create a release because the publishing step requires a tag ref.

The shared Android build is
[`.github/workflows/android-build.yml`](../.github/workflows/android-build.yml). Without signing
secrets it produces the installable dev APK. All four production signing secrets together
select a signed production APK; incomplete signing configuration fails rather than publishing
an unsigned production APK. Dev APKs use Android debug signing and are testing builds.

### Application versions

- Desktop reads `-Pbitchord.version=...` in `desktopApp/build.gradle.kts`. Packaged launchers pass
  the same string through the `bitchord.version` JVM property.
- Android reads the same Gradle property for `versionName`, stripping an optional leading `v`.
  Android's numeric `versionCode` is maintained separately in `app/build.gradle.kts`.
- Supported desktop update versions are numeric versions such as `1.8`, beta versions such as
  `1.8-beta1`, and fork versions such as `1.8-fork.10`, with an optional leading `v` in release tags.
- At the same numeric version, beta builds sort before the plain release, and fork builds after it.
  Build numbers compare numerically: `1.8-fork.10` is newer than `1.8-fork.9`.

### Installer versions

Desktop installers require a numeric version. `nativePackageVersion` combines the major/minor
version with `desktopVersionCode`, for example `1.8.26`.

Bump `desktopVersionCode` before publishing a versioned installer intended to upgrade a previous
one, including betas. Windows and package managers use that number for upgrade ordering.
Keep the Windows `upgradeUuid` and the Linux package name stable.

### Versioned asset names

The release workflow renames desktop artifacts to these patterns, where `<v>` is the stamped
application version:

| Platform | File |
|---|---|
| Linux | `BitChord-<v>-linux-x86_64.AppImage` |
| Linux | `BitChord-<v>-linux-amd64.deb` |
| Linux | `BitChord-<v>-linux-x86_64.rpm` |
| Windows | `BitChord-<v>-windows-x64-setup.exe` |
| Windows | `BitChord-<v>-windows-x64.msi` |
| Windows | `BitChord-<v>-windows-x64-portable.zip` |
| Android | `BitChord-android-dev.apk` (current dev builds) |
| Android | `BitChord-android.apk` (when production signing is configured) |

Keep the desktop platform suffixes stable. `installerUrl()` selects `.AppImage`, then
`-linux-amd64.deb` on Linux, and `-windows-x64-setup.exe` on Windows. A new format or architecture
needs a corresponding selection rule; the current release jobs target x86_64 desktops.

## How desktop updates work

[`DesktopUpdateChecker.kt`](../desktopApp/src/main/kotlin/com/music/bitchord/desktop/DesktopUpdateChecker.kt)
checks once per launch:

1. Fetch `https://api.github.com/repos/daschinmoy21/BitChord/releases?per_page=100`.
2. Ignore drafts and tags that are not supported versions, including rolling `build-*` tags.
3. Choose the highest supported version and compare it with the running `bitchord.version`.
   Versioned prereleases are eligible too; GitHub's `latest` endpoint is not used.
4. Offer the matching desktop asset when newer. Download opens it in the browser; if no asset
   matches, it opens the release page. Installation remains manual.

Network or parsing failures produce no update prompt. Keep Linux and Windows assets on the same
versioned release so both platforms can find their downloads. This describes the desktop checker;
the Android APK is an additional release artifact.

## Validating a release

- Confirm the built desktop packages include the native analyser and ONNX models.
- Confirm the displayed version agrees with the tag and all package names.
- Check the Android APK installs as BitChord Dev when no production signing secrets are set.
- For versioned installers, test an upgrade from the previous numeric package version.
- Confirm the release has the expected platform assets before publishing its draft.

To try the desktop prompt against a published versioned fork release:

```bash
nix develop --command ./gradlew :desktopApp:run -Pbitchord.version=0.1
```

If only rolling tags or drafts exist, no prompt is expected. Running with the same version as the
newest published versioned release should also produce no prompt.
