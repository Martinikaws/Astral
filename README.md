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

## 📲 Install

1. Open the [latest release](https://github.com/Martinikaws/Astral/releases/latest) and download one APK:
   - **ARMv8** (`Astral-<version>-ARMv8.apk`): almost every phone made in the last several years. Pick this one unless you know otherwise.
   - **ARMv7**: older 32-bit phones.
   - **universal**: works on both. Use it if you are not sure.
   - **TV**: for Android TV.
   - **wear-os**: the Wear OS remote for your watch (it controls the phone app).
2. Open the downloaded file. Android will ask you to allow installing apps from this source ("install unknown apps"). Allow it for your browser or file manager, then install.
3. Start Astral and sign in with your Spotify account.
4. From then on, Astral checks GitHub Releases for updates and installs them inside the app.

**Coming from v1.0.0, v1.0.1 or v1.0.2?** Those releases were debug builds ("Astral dev") that never check for updates. Install v1.0.3 by hand once; it is the first signed release (`app.astral`). After that the app updates itself. Settings and downloads from the old app do not carry over, and you can uninstall "Astral dev" afterwards.

**Beta channel.** Pre-releases ship as a separate app, **Astral Beta** (`app.astral.beta`). It installs side by side with the stable app, has its own settings and sign-in, and updates from GitHub pre-releases. Its version numbers run ahead of the stable ones.

---

## ⚠️ Disclaimer

Astral is an unofficial, community project. It is **not affiliated with, endorsed by or sponsored by Spotify**. It uses Spotify's web services in ways Spotify does not officially support, which may break without notice and may conflict with Spotify's terms of service. Use it at your own risk, with your own account.

Astral is provided as is, without warranty of any kind (GPL-3.0, sections 15 and 16). Spotify is a trademark of Spotify AB.

---

## 🔒 Privacy

Astral has no analytics, crash reporting or telemetry of its own. The build pulls in no Firebase, Crashlytics, Sentry or similar library, and the manifest switches Firebase and Google Analytics collection off. Astral has no server of its own.

It still talks to other services, and they see what the request needs:

- **Spotify**: sign-in, playback, your library and account, as in any Spotify client.
- **Lyrics and metadata services** receive the track title, artist, duration and sometimes the ISRC or Spotify track id: LRCLIB, AMLL (`amll-ttml-db` on GitHub), LyricsPlus, NetEase, Kugou, Genius, Spicy Lyrics, Unison, LRCMux, lrc.red, MusicBrainz and ListenBrainz (with Cover Art Archive), Deezer and iTunes. **QQ Music, Kuwo and RMM Revival** are off until you turn them on, and Astral asks first.
- **Lyric translation** sends the lyric text to Google Translate (`translate.googleapis.com`), only when you use translation.
- **Lossless audio**: lossless is on by default. To find a FLAC for a track, Astral sends its title, artist, duration, ISRC and Spotify id to the lossless backends: the SpotiFLAC session gateway (`api.zarz.moe`), community servers whose list is fetched from a GitHub gist (default `*.anandserver.cfd`), and Tidal and Qobuz public endpoints. The background queue preloader uses this, and falls back to YouTube through NewPipeExtractor, for the current and upcoming tracks. You can turn lossless off in Settings.
- **YouTube** (via NewPipeExtractor) for music videos and as the audio fallback above; **SponsorBlock** (`sponsor.ajay.app`) for podcast and video sponsor segments, if you enable it.
- **GitHub**: update checks (`api.github.com`), release downloads, and the extension and metadata-script indexes.
- **Radio Browser** (`*.api.radio-browser.info`) for radio stations.

---

## ✨ Highlights

- **6 Unified App-Wide Looks**: Pick between **Astral Basic**, **Astral Interactive**, **Spotifusion 1:1** (full Spotify layout & greens), **Glassify** (specular translucent glass cards & blurred cover backdrop), **LiquidMusic** (iOS-style inset lists, bold titles, red accent, plus a **Liquid Glass Refraction vs Frosted Glass** toggle) and **Starry Night** (deep navy sky, twinkling stars, shooting stars and warm starlight gold) in First-Run Setup or Settings → Look.
- **Starry Night**: Spicetify's **Starry Night** palette (`#000000` → `#142B44` night sky, `#152238` elevated cards, `#FFF3C4` starlight accent, `#4687D6` sky blue) is its own Look. A separate "stars behind any Look" toggle still draws the twinkling stars and shooting stars on top of the other five Looks.
- **Falling Star Boot Splash**: Cold-start animated curved comet & starlight reveal on launch.
- **Fast-Start Playback & Lossless HiFi**:
  - Instant optimistic UI & immediate local/cached playback start without WebView roundtrip lag.
  - Background `QueuePreloader` & `PlaybackCacheManager` + **Lossless / SpotiFLAC** Hi-Res FLAC support.
  - Native **AutoMix / Crossfade** DSP, **AdBlock**, **Spotify Connect**, and **Android Auto**.
- **Spicy Lyrics Display Overhaul**: Kinetic word/syllable-synced sweep & held-word letter bloom (`spikerko/spicy-lyrics` / `spicylyrics/mobile` display style), staggered spring scroll, opposite-aligned duet/vocalist lines, pill instrumental interlude dots, animated mesh backdrop, and **`7xeh/SpicyThemes`** color presets over LRCLIB, Spotify, AMLL, LyricsPlus, NetEase, Kugou, Genius, lrc.red, Spicy Lyrics, and Unison and LRCMux (on by default), plus QQ Music, Kuwo and RMM Revival (opt-in, they ask first).
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
├── CHANGELOG.md          # Release history
├── SECURITY.md           # How to report a vulnerability
├── CONTRIBUTING.md       # How to contribute
└── README.md
```

---

## 🛠️ Building from Source

**Requirements:** JDK 21, Android SDK (`compileSdk 37`, `targetSdk 36`, `minSdk 29` phone / `minSdk 30` wear).

1. Set `sdk.dir` in `base/local.properties` (e.g. `sdk.dir=C:/Android/Sdk`).
2. Build Phone & Wear OS APKs from `base/`:
   ```powershell
   .\gradlew.bat :app:assembleDebug :wear:assembleDebug :wear:assembleFast
   ```
3. Release auto-updates point to `Martinikaws/Astral` via `UPDATE_REPO` (override with `-PupdateRepo="owner/repo"`).
4. **Signed releases** (the in-app updater needs release builds signed with the same key):
   - Create the key once. `base/keystore/` is gitignored, so create the folder first:
     ```powershell
     mkdir base\keystore
     keytool -genkeypair -v -keystore base/keystore/astral-release.jks -alias astral -keyalg RSA -keysize 4096 -validity 10000
     ```
     Then copy `base/keystore.properties.example` to `base/keystore/keystore.properties` and fill in the passwords.
   - Back up the key (`.jks` and passwords). Losing it means no installed copy of Astral can ever update again.
   - Build from `base/`:
     ```powershell
     .\gradlew.bat :app:assembleRelease :wear:assembleRelease :app:collectApks -PappVersionName=1.0.3
     ```
   - Upload `base/app/build/release-apks/*.apk` to the GitHub release. The updater looks for `Astral-<version>-ARMv8.apk`, `-ARMv7.apk` and `-universal.apk`.
   - Never upload `app-*-debug.apk`. Debug builds are signed with the debug key and the updater is off in them.

---

## 📜 Licenses, Certifications & Attributions

Astral is free and open-source software licensed under the **GNU General Public License v3.0 (GPL-3.0)**. The full licence text is in [`LICENSE`](LICENSE). Every bundled third-party component, with its copyright and licence notice, is listed in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

**Code Astral is built on:**

| Component | Attribution | License |
| :--- | :--- | :--- |
| **Base Codebase (Orpheon / Myspoti)** | [`kota-zo/myspoti`](https://github.com/kota-zo/myspoti) | **GPL-3.0** |
| **Lossless, Cache, Preloader & Login Fix** | [`p1di/spotifusion`](https://github.com/p1di/spotifusion) | **GPL-3.0** |

**Design references (Astral's own code; no code or assets copied, except the Spicy Lyrics renderer, which is ported AGPL-3.0 code):**

| Astral feature | Inspired by | That project's license |
| :--- | :--- | :--- |
| **Starry Night Theme & Shooting Stars** | [`spicetify/spicetify-themes` (`StarryNight`)](https://github.com/spicetify/spicetify-themes/tree/master/StarryNight) & Delroy Prithvi | MIT |
| **Glassify Look** | [`sanoojes/spicetify-glassify`](https://github.com/sanoojes/spicetify-glassify) | AGPL-3.0 |
| **LiquidMusic Look** | [`fraustiz/LiquidMusic`](https://github.com/fraustiz/LiquidMusic) | none published |
| **Spicy Lyrics Display** | [`spikerko/spicy-lyrics`](https://github.com/spikerko/spicy-lyrics/), [`spicylyrics/mobile`](https://github.com/spicylyrics/mobile) | AGPL-3.0 (renderer and backgrounds are ported code, see `THIRD_PARTY_NOTICES.md` Part 5) |
| **Spicy Lyrics Colour Presets** | [`7xeh/SpicyThemes`](https://github.com/7xeh/SpicyThemes) | source-available, not open source |
| **BackMusic Module** | [`rolandyangg/backmusic_extension`](https://github.com/rolandyangg/backmusic_extension) | none published |
| **Visualizer Module** | [`Konsl/spicetify-visualizer`](https://github.com/Konsl/spicetify-visualizer) | MIT |
| **UI / UX (Login, Widget, Updater)** | Quackify / SpotiDuck UX | not applicable (UX only) |

**Bundled libraries and assets:**

| Component | Attribution | License |
| :--- | :--- | :--- |
| **Kotlin, kotlinx, AndroidX, Compose, Media3, Wear Compose, Horologist, Material, OkHttp, ZXing, Haze, Reorderable** | JetBrains, Google, Square, ZXing, Chris Banes, Calvin Liang | **Apache-2.0** (`base/licenses/Apache-2.0.txt`) |
| **NewPipeExtractor** | TeamNewPipe | **GPL-3.0** |
| **Glide** | Bump Technologies / Google | **BSD-2-Clause** (parts MIT / Apache-2.0) |
| **ONNX Runtime & Beat This! model** | Microsoft / CPJKU | **MIT** |
| **Mozilla Rhino** | Mozilla Foundation | **MPL-2.0** |
| **Google Play services (Cronet, Code Scanner, Wearable)** | Google | **Proprietary** (Android SDK License, closed-source client libraries) |
| **Metadata / Catalog Libraries** | Bundled catalog notice | **LGPL-3.0** (`base/catalog/src/main/resources/app/myspoti/catalog/metadata/LICENSE-LGPL-3.0.txt`) |
| **Typography (`Inter`, `Manrope`)** | Rasmus Andersson / Mikhail Sharanda | **SIL OFL 1.1** (`base/licenses/Inter-OFL.txt`, `base/licenses/Manrope-OFL.txt`) |

> Any derivative distribution of Astral must retain copyright and license notices and publish full corresponding source code under **GPL-3.0**.
>
> Astral is an independent, unofficial project, not affiliated with or endorsed by Spotify, Google or Apple. Spotify is a trademark of Spotify AB.
