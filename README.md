<div align="center">

<img src="Logo.png" alt="BitChord" width="140" />

# BitChord · Desktop fork

A personal music client for **NixOS + niri**, with a maintained native desktop app, Navidrome integration, and a shared Android build.

[![Releases](https://img.shields.io/github/v/release/daschinmoy21/BitChord?include_prereleases&label=Fork%20releases)](https://github.com/daschinmoy21/BitChord/releases)
[![Checks](https://img.shields.io/github/actions/workflow/status/daschinmoy21/BitChord/check.yml?branch=main&label=Checks)](https://github.com/daschinmoy21/BitChord/actions/workflows/check.yml)
[![License](https://img.shields.io/github/license/daschinmoy21/BitChord)](LICENSE)

[Download](#download) · [Features](#features) · [Navidrome](#navidrome) · [Offline playlists](#offline-playlists) · [Build](#build-from-source) · [Credits](#credits)

</div>

## About this fork

Maintained by [@daschinmoy21](https://github.com/daschinmoy21) for everyday listening on Linux. Development focuses on a native Compose desktop app that fits a keyboard-driven NixOS/niri setup: working window resizing, system media controls, a tray, persistent playback state, and quick access to your music libraries.

This repository maintains its own desktop app and UI alongside shared Kotlin code and Android targets. Changes are selected and adapted for this fork rather than automatically mirroring another client. Feature availability differs between desktop, Android, TV, and the web player.

## Download

Get builds from **[this fork’s releases](https://github.com/daschinmoy21/BitChord/releases)**. The [rolling `build-main` release](https://github.com/daschinmoy21/BitChord/releases/tag/build-main) tracks the latest successfully packaged `main` build; its notes identify the source commit.

- **Linux:** `BitChord-linux-x86_64.AppImage` in rolling builds. Versioned releases can also provide AppImage, DEB, and RPM packages.
- **Windows:** versioned releases provide an installer and portable ZIP when available.
- **Android:** `BitChord-android-dev.apk` installs as **BitChord Dev** (`com.dev.bitchord`), separately from the production app.
- **macOS:** the desktop target is checked in CI and can be built from source. Check release assets for available packages.

### Running an AppImage on NixOS

```bash
nix shell nixpkgs#appimage-run --command appimage-run ./BitChord-linux-x86_64.AppImage
```

Desktop update checks follow this fork’s versioned releases. Rolling `build-*` tags are downloaded manually. See [desktop setup](DESKTOP.md) and [release details](docs/DESKTOP_RELEASES.md) for packaging, versioning, and update behavior.

## Features

### A native Linux desktop

- **Keyboard hints:** tap **Ctrl** to label clickable controls, then type a label to activate one. Press **?** for the shortcut sheet.
- **Playback shortcuts:** seek, skip, change volume, mute, and focus search without opening menus.
- **UI zoom:** adjust interface size with keyboard shortcuts or the Appearance setting.
- **niri/XWayland support:** compositor-driven window resizing and the AWT setting needed for it.
- **Desktop integration:** MPRIS media controls, a system tray, and audio-device selection.
- **Session restore:** reopen the last track paused at its saved position.
- **Readable playback details:** audio-pipeline information, improved stats contrast, and clickable artist credits.

### Your libraries in one app

- Search, browse, and play YouTube Music, with account sign-in for your library and personalized content.
- Browse Navidrome albums, artists, songs, and live playlists using your own server credentials.
- Connect Spotify to browse playlists and Liked Songs, with tracks matched to YouTube Music recordings. Public playlist links can also be imported into local playlists.
- Search Library by collection name and artist credit, with filters and results grouped by source.
- Browse local music and downloads, and keep local playlists alongside account playlists.
- Add and health-check supported music sources, with matching improvements for decorated titles, translated titles, and artist credits.

### Listening and playback

- Gapless playback, configurable crossfade, and Automix transitions.
- Lossless playback from compatible sources, with source selection and fallback controls.
- Equalizer, loudness normalization, playback speed, and a sleep timer.
- A queue with drag reordering, play-next actions, and playback history.
- Track downloads and optional automatic updates for downloaded playlists.

### Lyrics, artwork, and sharing

- Synced lyrics, including word-level highlighting when the provider supplies timings.
- Multiple lyrics providers, configurable source order, and on-demand translation.
- Animated Canvas artwork and artwork-driven player visuals.
- Listening statistics and Replay, plus shareable story cards for songs and listening summaries.
- Discord Rich Presence, Last.fm and ListenBrainz scrobbling, and Listen Together.

## Navidrome

Add a Navidrome source in **Settings → Sources**, enter the server address and credentials, then test and save it. Enable the source to include its library.

On desktop:

- **Sidebar:** expand **Navidrome playlists** to browse playlist rows inline. Collapse the group to free sidebar space.
- **Library:** the **Navidrome playlists** shelf shows artwork cards alongside the other collection sections. It also appears under the **Playlists** filter; use **Show all** when the shelf contains more cards than fit in its preview.
- **Refresh:** use the refresh icon in the group or shelf header. The playlist list and open server playlist also refresh automatically every minute while the app is open.
- **Playback:** opening a playlist reads its current tracks directly from the server, without importing a local copy.
- **Favourites:** hearts on Navidrome tracks use the server’s star/unstar actions. Existing local Navidrome favourites are migrated before the server becomes authoritative.

Navidrome browsing works without a YouTube sign-in. Configure playback priority, metered-network behavior, and play reporting in the Navidrome settings.

Android also supports adding a Navidrome source and browsing its library, but the desktop-specific playlist sections described above belong to the desktop app.

## Offline playlists

Open a desktop playlist’s **⋯** menu and choose **Download and keep updated**. BitChord creates an ordered offline copy in your local playlists, including repeated tracks, and downloads the audio into `~/Music/BitChord`.

- Checks run at startup and hourly while BitChord is open. Private YouTube playlists require the corresponding account session.
- **Sync now** checks immediately and retries failed downloads. Automatic updates with more than **50 new songs** wait for explicit approval through this action.
- Completed downloads appear in the offline copy as they arrive. Managed copies follow the original playlist; add or rearrange tracks in the original rather than editing the managed copy.
- **Stop automatic downloads** pauses the subscription and retains its offline copy. Re-enabling uses the same copy and remembers excluded tracks.
- Explicitly deleted or cancelled tracks stay excluded from automatic downloads. Downloading them manually restores them.
- Removing a song from the online playlist updates the offline playlist’s membership but retains its audio file in Downloads.
- Failed or unreadable server responses keep the previous saved playlist intact.

## Keyboard controls

| Key | Action |
| --- | --- |
| Tap Ctrl | Show clickable-control hints |
| ? | Open the full shortcut sheet |
| Space | Play or pause |
| ← / → | Seek 5 seconds; hold Shift for 15 seconds |
| Ctrl + ← / → | Previous or next track |
| ↑ / ↓ | Change volume |
| M | Mute or unmute |
| / or Ctrl + K | Focus search |
| Ctrl + + / − / 0 | Zoom in, zoom out, or reset |
| J / K | Scroll down or up |
| Alt + ← | Go back |

Text fields retain normal typing behavior. The in-app shortcut sheet lists the additional navigation and scrolling controls.

## Build from source

For the supported Nix development environment on `x86_64-linux`:

```bash
git clone https://github.com/daschinmoy21/BitChord.git
cd BitChord
nix develop --command ./gradlew :desktopApp:run
```

The development shell supplies Java 21, build tools, and the native graphics/audio libraries. Run inside your desktop session with XWayland available. It sets `_JAVA_AWT_WM_NONREPARENTING=1` for Wayland compositors and configures the native library paths.

Run the checks and create a desktop app image:

```bash
nix develop --command ./gradlew :shared:jvmTest :desktopUi:jvmTest :desktopApp:test
nix develop --command ./gradlew :desktopApp:createDistributable
```

Desktop builds do not require an Android SDK. To build Android, configure an SDK through `local.properties`, `ANDROID_HOME`, or `ANDROID_SDK_ROOT`, then use the Android commands in [CONTRIBUTING.md](CONTRIBUTING.md).

## Project documentation

- [Desktop setup and platform packaging](DESKTOP.md)
- [Fork releases and updates](docs/DESKTOP_RELEASES.md)
- [Contribution and validation guide](CONTRIBUTING.md)
- [Listen Together server](backend/README.md)
- [Lyrics translation](docs/LYRICS_TRANSLATION.md)
- [Documentation index](ADDITIONAL.md)

Issues and pull requests for this fork belong in [daschinmoy21/BitChord](https://github.com/daschinmoy21/BitChord), targeting **`main`**. Include your platform and reproduction steps when reporting a bug, and say whether a proposed fix is a candidate for upstream.

## License and third-party services

Distributed under the **GNU General Public License v3.0**; see [LICENSE](LICENSE). This client is independent of YouTube, Google, Spotify, and the other services it connects to. Access to music depends on your sources and accounts; the app does not host a music catalogue. Use connected services in accordance with their terms and applicable law.

## Credits

This fork is based on **[BitChord by @kushagrasinghx](https://github.com/kushagrasinghx/BitChord)**. The original project’s architecture, design, branding, and much of its functionality are the work of its [maintainers and contributors](https://github.com/kushagrasinghx/BitChord/graphs/contributors). Their work made this desktop fork possible.

The lyrics experience also builds on [am-lyrics by binimum](https://github.com/binimum/am-lyrics). Lyrics integrations include [lrc.red](https://lrc.red), [BiniLyrics](https://github.com/binimum), [BetterLyrics](https://github.com/better-lyrics/better-lyrics), [PaxSenix](https://lyrics.paxsenix.org), [LyricsPlus](https://github.com/ibratabian17/YouLyPlus), [SimpMusic](https://github.com/maxrave-dev/SimpMusic), [Unison](https://unison.boidu.dev), [Megalobiz](https://www.megalobiz.com), [KuGou](https://www.kugou.com), [LRCLIB](https://lrclib.net), [Musixmatch](https://www.musixmatch.com), and [Genius](https://genius.com). See the repository’s source files and licence notices for other component attributions.
