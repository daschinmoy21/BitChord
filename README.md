<div align="center">

<br/>
<br/>

<img src="Logo.png" alt="BitChord app icon" width="200" />

# BitChord

### Personal fork for NixOS + niri

<br/>

[![Fork releases](https://img.shields.io/github/v/release/daschinmoy21/BitChord?include_prereleases&style=for-the-badge&label=Fork%20releases&labelColor=0d1117)](https://github.com/daschinmoy21/BitChord/releases)
[![License](https://img.shields.io/github/license/daschinmoy21/BitChord?style=for-the-badge&labelColor=0d1117)](LICENSE)
[![Checks](https://img.shields.io/github/actions/workflow/status/daschinmoy21/BitChord/check.yml?branch=main&style=for-the-badge&labelColor=0d1117)](https://github.com/daschinmoy21/BitChord/actions/workflows/check.yml)

<br/>

[**About this fork**](#about-this-fork) · [**Download**](#download) · [**Features**](#features) · [**Contributing**](#contributing) · [**Support**](#support) · [**Disclaimer**](#disclaimer)

<br/>

</div>

## About this fork

This is [@daschinmoy21](https://github.com/daschinmoy21)'s personal fork of
[BitChord](https://github.com/kushagrasinghx/BitChord), optimized for my Linux desktop:
**NixOS with the niri Wayland compositor**. The focus is making everyday listening smoother
on that setup, with desktop UX improvements and fixes built on the upstream project.

The work here includes:

- A reproducible Nix development shell and XWayland window-resize handling for niri.
- Keyboard shortcuts for seeking, skipping, volume, mute, and search, plus a shortcut help dialog.
- Interface zoom controls with a percentage indicator and an Appearance slider.
- Restoring the last song paused at its saved position, more readable player stats, and clickable album artist credits.
- Desktop playback fixes and update checks that follow this fork's releases.

I plan to upstream selected fixes and generally useful UX improvements once they have settled.
The original app, its branding, and much of its functionality are the work of the
[upstream maintainers and contributors](https://github.com/kushagrasinghx/BitChord/graphs/contributors).

See [DESKTOP.md](DESKTOP.md) for the NixOS/niri setup and [CONTRIBUTING.md](CONTRIBUTING.md)
for contributing here or preparing a change for upstream. Android and Windows targets remain
available, while development in this fork is centered on my Linux setup.

> [!IMPORTANT]
> BitChord is not affiliated with, endorsed by, or connected to YouTube or Google in any way. Use it at your own discretion.

---

<div align="center">

<img src="Banner.png" alt="BitChord banner" width="100%" />

<h1><a id="features"></a>Features</h1>

The broader BitChord feature set below is inherited from upstream; availability varies between Android and desktop.

<table>
  <tr>
    <td width="50%" valign="top">

#### Playback
- **Search, browse and play** anything available on YouTube Music.
- **Hi-Res lossless audio** — FLAC/ALAC from a configured module source, with YouTube Music as fallback.
- **Gapless playback with true crossfade**, adjustable 0–12s.
- **Automix [Beta]** — DJ-style transitions with beat-matching and tempo-stretching.
- **Offline downloads** — save tracks with embedded metadata.
- **Local music library** integration.
- **Background playback** via a proper foreground media session.
- **Apple-like lyrics animation** — credit to [binimum](https://github.com/binimum/am-lyrics).

#### Connectivity & Accounts
- **Sign in with your Google account** for personalized content.
- **Spotify integration** — connect your account to play your playlists and Liked Songs.
- **Discord Rich Presence** — in-app login, live track/artist/album and progress.
- **Scrobbling** to Last.fm and ListenBrainz.
- **Pluggable sources** — add, edit, test and health-check module sources.

    </td>
    <td width="50%" valign="top">

#### Experience
- **Animated album canvas** — motion artwork on the now-playing screen.
- **Word-synced lyrics** — word/syllable-level highlighting from multiple sources.
- **Lyrics providers** — credit to [lrc.red](https://lrc.red), [BiniLyrics](https://github.com/binimum), [BetterLyrics](https://github.com/better-lyrics/better-lyrics), [PaxSenix](https://lyrics.paxsenix.org), [LyricsPlus](https://github.com/ibratabian17/YouLyPlus), [SimpMusic](https://github.com/maxrave-dev/SimpMusic), [Unison](https://unison.boidu.dev), [Megalobiz](https://www.megalobiz.com), [KuGou](https://www.kugou.com), [LRCLIB](https://lrclib.net), [Musixmatch](https://www.musixmatch.com) and [Genius](https://genius.com).
- **Dynamic, artwork-driven theming** — Material palette extracted from album art.
- **Frosted-glass UI** — Telegram-style translucent bars via Haze, Material 3 theming.

#### Controls & Tweaks
- **Per-network audio quality** — separate quality ceilings for Wi-Fi and mobile data.
- **Playback speed control** (0.5×–2.0×) and **skip silence**.
- **Sleep timer** — fixed presets or "stop after this track".
- **System equalizer** integration.
- **Stats for nerds** — codec, bit depth, sample rate, and more on the now-playing screen.

    </td>
  </tr>
</table>

</div>

---

<div align="center">

<h1><a id="download"></a>Download</h1>

Use this fork's [Releases](https://github.com/daschinmoy21/BitChord/releases), including prereleases.
The [rolling main build](https://github.com/daschinmoy21/BitChord/releases/tag/build-main)
contains the latest successfully packaged main-branch build.

- **Linux:** download `BitChord-linux-x86_64.AppImage`. On NixOS, run it through `appimage-run`; see [DESKTOP.md](DESKTOP.md).
- **Android:** download `BitChord-android-dev.apk`, an installable universal **BitChord Dev** build. It uses `com.dev.bitchord`, so it can coexist with the upstream production app. Enable “Install unknown apps” for the app you download it with. Tagged releases are signed with this fork's own key, and the app offers them as in-app updates; a build installed before that key existed has to be uninstalled once first.
- **Windows and versioned Linux packages:** see [DESKTOP.md](DESKTOP.md) for the artifacts produced by the versioned release workflow.

These are builds of this fork. The [upstream releases](https://github.com/kushagrasinghx/BitChord/releases)
remain available for the original project.

</div>

---

<div align="center">

<h1><a id="contributing"></a>Contributing</h1>

Bug fixes and desktop UX improvements are welcome. Open issues and pull requests against this fork’s `main` branch, and mention whether a change should eventually go upstream. Please review the [Contributing Guide](CONTRIBUTING.md) and [Code of Conduct](CODE_OF_CONDUCT.md).

[**Contributing Guide**](CONTRIBUTING.md) · [**Code of Conduct**](CODE_OF_CONDUCT.md) · [**Maintainers**](MAINTAINERS.md) · [**Additional Docs**](ADDITIONAL.md)

### Thanks to the upstream contributors ❤

<a href="https://github.com/kushagrasinghx/BitChord/graphs/contributors">
  <img src="https://raw.githubusercontent.com/kushagrasinghx/BitChord/contributors/contributors.svg" />
</a>

</div>

---

<div align="center">

<h1><a id="support"></a>Support</h1>

The links below support the **upstream BitChord maintainers**. This fork keeps their work and attribution visible:

<a href="https://ko-fi.com/kushagrasinghx" target="_blank" rel="noopener noreferrer"><img src="https://cdn.jsdelivr.net/gh/JMcrafter26/badges@main/src/assets/donate/kofi-singular-alt/cozy.svg" alt="Support me on Ko-fi" height="55"/></a>
<a href="https://paypal.me/kuxhagrasingh" target="_blank" rel="noopener noreferrer"><img src="https://cdn.jsdelivr.net/gh/JMcrafter26/badges@main/src/assets/donate/paypal-plural/cozy.svg" alt="Support us on PayPal" height="55"/></a>

<br/>
<img src="upi_support.jpg" alt="UPI Support" width="250" />

</div>

---

<div align="center">

<h1><a id="disclaimer"></a>Disclaimer & Legal Notice</h1>

BitChord is an independent, community-driven third-party audio player and client. It is **not** associated with Google LLC, YouTube Music, Deezer, Telegram, or any of their parent companies.

* **No Media Hosting:** BitChord does not host, upload, or store copyrighted music files. It operates strictly as an interface to scan local device storage or stream media directly from public, public-facing, or user-authenticated APIs.
* **Fair Use & API Usage:** This software is created solely for personal research, educational, and fair-use purposes. The user is entirely responsible for ensuring their usage aligns with their local copyright laws and YouTube Terms of Service.
* **No Ad-Blocking Guarantee:** While BitChord focuses on providing a clean listening environment, it does not guarantee permanent bypasses or modifications to commercial third-party platform conditions.
* **Copyleft:** BitChord is free software under the GPLv3. The license does not let anyone forbid others from selling or redistributing copies, but any distribution must come with the Corresponding Source under the same license.

</div>

---

<div align="center">

<h1><a id="license"></a>License</h1>

This project is licensed under the **GNU General Public License v3.0 (GPLv3)**. See the [LICENSE](LICENSE) file for details.

</div>
