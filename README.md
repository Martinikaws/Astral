<div align="center">

# ✦ Astral

**A native Android & Wear OS music client (`app.astral`) — fast-start Lossless/HiFi playback, dual Classic & Interactive Glass UI, live Starry Night sky, `beautiful-lyrics`-inspired synced lyrics, custom themes & sandboxed JS extensions.**

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Platform: Android](https://img.shields.io/badge/Android-API%2029%2B-85B7EB.svg)](#)
[![Wear OS](https://img.shields.io/badge/Wear%20OS-Phone%20Remote-CECBF6.svg)](#)
[![App ID](https://img.shields.io/badge/App%20ID-app.astral-0B1B45.svg)](#)

</div>

---

## ✨ Highlights

- **Starry Night Main Theme**: Built around Spicetify's **Starry Night** (`[Base]`) palette (`#000000` → `#142B44` night sky, `#152238` elevated cards, `#FFF3C4` starlight accent, `#4687D6` sky blue) with a live twinkling stars & diagonal shooting stars backdrop — toggleable across **all** UI styles (**Astral Basic**, **Astral Interactive Glass**, and **Spotifusion 1:1**).
- **Falling Star Boot Splash**: Cold-start animated curved comet & starlight reveal on launch.
- **Fast-Start Playback & Lossless HiFi**:
  - Instant optimistic UI & immediate local/cached playback start without WebView roundtrip lag.
  - Background `QueuePreloader` & `PlaybackCacheManager` + **Lossless / SpotiFLAC** Hi-Res FLAC support.
  - Native **AutoMix / Crossfade** DSP, **AdBlock**, **Spotify Connect**, and **Android Auto**.
- **3 Selectable Page Styles**: Choose between **Astral Basic**, **Astral Interactive (Glass)**, and **Spotifusion 1:1** for Playlist, Album, and Artist pages on first launch or anytime in Settings.
- **Better Lyrics**: Word-by-word / syllable-synced sweep & glow animations, dynamic blurred mesh backdrop, spring auto-scroll, vocal/interlude markers, translations, and romanization.
- **Rich Queue, Search & Music Videos**:
  - Cloud-synced + decorated recent cached searches.
  - Drag-to-reorder queue & swipe/drag-to-add-to-queue gestures.
  - Spotify native Music Videos / Canvas with automatic YouTube + `AudioAlign` + `SponsorBlock` fallback.
- **Stats & Monthly Recap**: Daily listening bar charts, total time listened on Astral, and Monthly Recap cards.
- **Sandboxed Extensions & Custom Themes Tab**:
  - Full Orpheon JS Extension runtime (`apiVersion: 1`) + dedicated **Themes** tab to import, preview, toggle, and delete custom JSON/ZIP themes.
  - Includes [`FEED-EXTENSIONS.md`](./FEED-EXTENSIONS.md) — paste it into any AI to port Spicetify extensions or generate Astral themes.
- **Wear OS Phone Remote**: Dedicated Wear OS companion (`:wear`) with rotary bezel volume control, queue, device picker, and library browsing (Phone Remote mode).
- **In-App Auto-Updater**: Checks GitHub Releases (`Martinikaws/Astral`) and installs matching ABI APKs in-app.

---

## 📁 Project Structure

```text
Astral/
├── base/
│   ├── app/              # Android phone app (Compose UI, playback, lossless, stats, extensions, widget)
│   ├── wear/             # Wear OS Phone Remote companion (:wear)
│   ├── api/              # Pure Kotlin contracts & media models
│   ├── api-spotify/      # Pathfinder GraphQL, Web API, AdBlock & JS bridge
│   ├── api-web/          # Web catalog client
│   ├── api-ytmusic/      # YouTube Music fallback & auth
│   ├── automix/          # Beat tracking, mix planning & segue DSP
│   ├── catalog/          # Metadata & lyrics providers (LRCLIB, MusicBrainz, Deezer, iTunes)
│   ├── engine/           # Native Spotify Connect / DRM core (KMP)
│   ├── engine-android/   # Media3 ExoPlayer & Widevine Android engine
│   ├── extensions/       # Sandboxed JS extension & UiStyle runtime
│   ├── extensions-cli/   # Extension validation CLI
│   └── host-android/     # Headless WebView host, MediaSession & notification service
├── FEED-EXTENSIONS.md    # AI prompt & spec for porting Spicetify extensions & custom themes
├── HANDOFF.md            # Build & workspace notes
├── CHANGELOG.md          # Release history
└── README.md
```

---

## 🛠️ Building from Source

**Requirements:** JDK 21, Android SDK (`compileSdk 37`, `targetSdk 36`, `minSdk 29` phone / `minSdk 30` wear).

1. Set `sdk.dir` in `base/local.properties` (e.g. `sdk.dir=D:/Projects/OrpheonWearOS/AndroidStudio`).
2. Build Phone & Wear OS APKs from `base/`:
   ```powershell
   .\gradlew.bat :app:assembleDebug :wear:assembleDebug :wear:assembleFast
   ```
3. Release auto-updates point to `Martinikaws/Astral` via `UPDATE_REPO` (override with `-PupdateRepo="owner/repo"`).

---

## 📜 Licenses, Certifications & Attributions

Astral is free and open-source software licensed under the **GNU General Public License v3.0 (GPL-3.0)**.

| Component / Inspiration | Attribution | License |
| :--- | :--- | :--- |
| **Base Codebase (Orpheon / Myspoti)** | [`kota-zo/myspoti`](https://github.com/kota-zo/myspoti) | **GPL-3.0** |
| **Lossless, Cache, Preloader & Login Fix** | [`p1di/spotifusion`](https://github.com/p1di/spotifusion) | **GPL-3.0** |
| **Starry Night Theme & Shooting Stars** | [`spicetify/spicetify-themes` (`StarryNight`)](https://github.com/spicetify/spicetify-themes/tree/master/StarryNight) & Delroy Prithvi | **MIT** |
| **Better Lyrics Visuals (Inspiration)** | [`surfbryce/beautiful-lyrics`](https://github.com/surfbryce/beautiful-lyrics) | **GPL-3.0** |
| **UI / UX Inspiration (Login, Widget, Updater)** | Clean-room GPL-3.0 reimplementation inspired by Quackify / SpotiDuck UX | **GPL-3.0** |
| **AndroidX / Media3 / Horologist / OkHttp** | Google & Square Open Source | **Apache-2.0** (`base/licenses/Apache-2.0.txt`) |
| **Metadata / Catalog Libraries** | Bundled catalog notice | **LGPL-3.0** (`base/catalog/src/main/resources/app/myspoti/catalog/metadata/LICENSE-LGPL-3.0.txt`) |
| **Typography (`Inter`, `Manrope`)** | Rasmus Andersson / Mikhail Sharanda | **SIL OFL 1.1** (`base/licenses/Inter-OFL.txt`, `base/licenses/Manrope-OFL.txt`) |

> Any derivative distribution of Astral must retain copyright and license notices and publish full corresponding source code under **GPL-3.0**.
