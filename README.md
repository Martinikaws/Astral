<div align="center">

<img src="./assets/astral-icon.svg" width="128" height="128" alt="Astral Logo" />

# Astral ✦

[![Download Latest APK](https://img.shields.io/badge/DOWNLOAD-LATEST%20APK-85B7EB?style=for-the-badge&logo=android&logoColor=white&labelColor=152238)](https://github.com/Martinikaws/Astral/releases/latest)

[![Platform](https://img.shields.io/badge/Platform-Android-1ED760?style=flat-square&logo=android&logoColor=white&labelColor=152238)](#)
[![Wear OS](https://img.shields.io/badge/Wear%20OS-Phone%20Remote-CECBF6?style=flat-square&logo=wearos&logoColor=white&labelColor=152238)](#)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4%2B-7F52FF?style=flat-square&logo=kotlin&logoColor=white&labelColor=152238)](#)
[![License](https://img.shields.io/badge/License-GNU%20GPLv3-4687D6?style=flat-square&labelColor=152238)](https://www.gnu.org/licenses/gpl-3.0)
[![App ID](https://img.shields.io/badge/App%20ID-app.astral-0B1B45?style=flat-square&labelColor=152238)](#)

**Fast-start Lossless/HiFi Android & Wear OS music client — Starry Night theme, Spicy Lyrics word-synced renderer, dual Basic & Interactive Glass UI, and sandboxed JS extensions.**

</div>

---

## ✨ Highlights

- **Starry Night Main Theme**: Spicetify's **Starry Night** (`[Base]`) palette (`#000000` → `#142B44` night sky, `#152238` elevated cards, `#FFF3C4` starlight accent, `#4687D6` sky blue) with a live twinkling stars & diagonal shooting stars backdrop — toggleable across **all 5 Looks** with transparent screen surfaces so the night sky shines cleanly behind every menu and page.
- **5 Unified App-Wide Looks**: Pick between **Astral Basic**, **Astral Interactive**, **Spotifusion 1:1** (full Spotify layout & greens), **Glassify** (specular translucent glass cards & blurred cover backdrop), and **LiquidMusic** (20dp floating iOS-style liquid glass panels, dynamic album-art contrast accent, plus a **Liquid Glass Refraction vs Frosted Glass** toggle) in First-Run Setup or Settings → Look.
- **Falling Star Boot Splash**: Cold-start animated curved comet & starlight reveal on launch.
- **Fast-Start Playback & Lossless HiFi**:
  - Instant optimistic UI & immediate local/cached playback start without WebView roundtrip lag.
  - Background `QueuePreloader` & `PlaybackCacheManager` + **Lossless / SpotiFLAC** Hi-Res FLAC support.
  - Native **AutoMix / Crossfade** DSP, **AdBlock**, **Spotify Connect**, and **Android Auto**.
- **Spicy Lyrics Display Overhaul**: Kinetic word/syllable-synced sweep & held-word letter bloom (`spikerko/spicy-lyrics` / `spicylyrics/mobile` display style), staggered spring scroll, opposite-aligned duet/vocalist lines, pill instrumental interlude dots, animated mesh backdrop, and **`7xeh/SpicyThemes`** color presets over LRCLIB, Spotify, AMLL, LyricsPlus, NetEase, Kugou, Genius & optional Spicy Lyrics sources.
- **Full-Screen Beat-Synced Modules Tab**: Toggleable in Settings (`ModulesPref`) with real-time Spotify `audio-analysis` + `:automix` beat/spectrum sync (`TrackAudioSync`):
  - **BackMusic** (`rolandyangg/backmusic_extension`): blurred album backdrop, real beat-spawned wave rings & 12-band mirrored bars, selectable particles (`Stars`, `Snow`, `Fireflies`, `Dust`), floating bobbing cover centerpiece, and mini EQ label.
  - **Visualizer** (`Konsl/spicetify-visualizer`): NCS glowing particle-ring sphere, 64-band Spectrum curve/bars, and beat waveform.
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
├── assets/               # Astral vector icon & README media
├── base/
│   ├── app/              # Android phone app (Compose UI, playback, lossless, stats, extensions, widget)
│   ├── wear/             # Wear OS Phone Remote companion (:wear)
│   ├── api/              # Pure Kotlin contracts & media models
│   ├── api-spotify/      # Pathfinder GraphQL, Web API, AdBlock & JS bridge
│   ├── api-web/          # Web catalog client
│   ├── api-ytmusic/      # YouTube Music fallback & auth
│   ├── automix/          # Beat tracking, mix planning & segue DSP
│   ├── catalog/          # Metadata & lyrics providers (Spicy Lyrics, LRCLIB, MusicBrainz, Deezer, iTunes)
│   ├── engine/           # Native Spotify Connect / DRM core (KMP)
│   ├── engine-android/   # Media3 ExoPlayer & Widevine Android engine
│   ├── extensions/       # Sandboxed JS extension & UiStyle runtime
│   ├── extensions-cli/   # Extension validation CLI
│   └── host-android/     # Headless WebView host, MediaSession & notification service
├── FEED-EXTENSIONS.md    # AI prompt & spec for porting Spicetify extensions & custom themes
├── PROJECT_BRIEF.md      # Full project architecture, state & backlog brief
├── TASKS.md              # Shared multi-agent task board
├── HANDOFF.md            # Build & workspace coordination log
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

Astral is free and open-source software licensed under the **GNU General Public License v3.0 (GPL-3.0)**. The full licence text is in [`LICENSE`](LICENSE). Every bundled third-party component, with its copyright and licence notice, is listed in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

**Code Astral is built on:**

| Component | Attribution | License |
| :--- | :--- | :--- |
| **Base Codebase (Orpheon / Myspoti)** | [`kota-zo/myspoti`](https://github.com/kota-zo/myspoti) | **GPL-3.0** |
| **Lossless, Cache, Preloader & Login Fix** | [`p1di/spotifusion`](https://github.com/p1di/spotifusion) | **GPL-3.0** |

**Design references (Astral's own code; no code or assets copied):**

| Astral feature | Inspired by | That project's license |
| :--- | :--- | :--- |
| **Starry Night Theme & Shooting Stars** | [`spicetify/spicetify-themes` (`StarryNight`)](https://github.com/spicetify/spicetify-themes/tree/master/StarryNight) & Delroy Prithvi | MIT |
| **Glassify Look** | [`sanoojes/spicetify-glassify`](https://github.com/sanoojes/spicetify-glassify) | AGPL-3.0 |
| **LiquidMusic Look** | [`fraustiz/LiquidMusic`](https://github.com/fraustiz/LiquidMusic) | none published |
| **Spicy Lyrics Display** | [`spikerko/spicy-lyrics`](https://github.com/spikerko/spicy-lyrics/), [`spicylyrics/mobile`](https://github.com/spicylyrics/mobile) | AGPL-3.0 |
| **Spicy Lyrics Colour Presets** | [`7xeh/SpicyThemes`](https://github.com/7xeh/SpicyThemes) | source-available, not open source |
| **BackMusic Module** | [`rolandyangg/backmusic_extension`](https://github.com/rolandyangg/backmusic_extension) | none published |
| **Visualizer Module** | [`Konsl/spicetify-visualizer`](https://github.com/Konsl/spicetify-visualizer) | MIT |
| **UI / UX (Login, Widget, Updater)** | Quackify / SpotiDuck UX | not applicable (UX only) |

**Bundled libraries and assets:**

| Component | Attribution | License |
| :--- | :--- | :--- |
| **AndroidX / Media3 / Horologist / OkHttp** | Google & Square Open Source | **Apache-2.0** (`base/licenses/Apache-2.0.txt`) |
| **Metadata / Catalog Libraries** | Bundled catalog notice | **LGPL-3.0** (`base/catalog/src/main/resources/app/myspoti/catalog/metadata/LICENSE-LGPL-3.0.txt`) |
| **Typography (`Inter`, `Manrope`)** | Rasmus Andersson / Mikhail Sharanda | **SIL OFL 1.1** (`base/licenses/Inter-OFL.txt`, `base/licenses/Manrope-OFL.txt`) |

> Any derivative distribution of Astral must retain copyright and license notices and publish full corresponding source code under **GPL-3.0**.
>
> Astral is an independent, unofficial project, not affiliated with or endorsed by Spotify, Google or Apple. Spotify is a trademark of Spotify AB.
