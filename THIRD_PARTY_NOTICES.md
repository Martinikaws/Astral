# Third-party notices

Astral is free software under the GNU General Public License v3.0 (see [`LICENSE`](LICENSE)).

Astral is a modified version of **Myspoti / Orpheon** by kota-zo (https://github.com/kota-zo/myspoti,
GPL-3.0). It also includes code ported from **Spotifusion** by p1di (https://github.com/p1di/spotifusion,
GPL-3.0). Both upstreams' copyright and licence notices are kept.

Licence texts bundled in this repository:

| Licence | File |
| --- | --- |
| GNU GPL-3.0 | [`LICENSE`](LICENSE), [`base/catalog/src/main/resources/app/myspoti/catalog/metadata/COPYING-GPL-3.0.txt`](base/catalog/src/main/resources/app/myspoti/catalog/metadata/COPYING-GPL-3.0.txt) |
| GNU AGPL-3.0 | [`base/licenses/AGPL-3.0.txt`](base/licenses/AGPL-3.0.txt) (Part 5) |
| GNU LGPL-3.0 | [`base/catalog/src/main/resources/app/myspoti/catalog/metadata/LICENSE-LGPL-3.0.txt`](base/catalog/src/main/resources/app/myspoti/catalog/metadata/LICENSE-LGPL-3.0.txt) |
| Apache-2.0 | [`base/licenses/Apache-2.0.txt`](base/licenses/Apache-2.0.txt) |
| SIL OFL 1.1 (Inter, Manrope) | [`base/licenses/Inter-OFL.txt`](base/licenses/Inter-OFL.txt), [`base/licenses/Manrope-OFL.txt`](base/licenses/Manrope-OFL.txt) |
| MIT | quoted in full below where required |

---

# Part 1: inherited from Myspoti / Orpheon

The section below is Orpheon's own `THIRD_PARTY_NOTICES.md`, kept as published. Here "Myspoti" means the
upstream project that Astral is built on. Every component it lists still ships in Astral. Paths in it are
relative to `base/` (or to `base/app/.../ui/immersive/` for `shell/...` and `components/...`).


### Beat This! — MIT
https://github.com/CPJKU/beat_this

By Francesco Foscarin, Jan Schlüter and Gerhard Widmer (CPJKU, Johannes Kepler University Linz),
"Beat This! Accurate beat tracking without DBN postprocessing" (ISMIR 2024). AutoMix ships the
`small0` checkpoint, exported to ONNX and quantized to int8 (`base/app/src/main/assets/automix/
beat-this-small0-int8.onnx`); upstream publishes the code and the weights under the MIT licence.
The log-mel front end in `base/automix/.../BeatThis.kt` follows its input contract. The training
recordings are not bundled.

### ONNX Runtime — MIT
https://github.com/microsoft/onnxruntime

Copyright Microsoft Corporation. `com.microsoft.onnxruntime:onnxruntime-android` 1.23.2 runs the
Beat This model on the phone (the desktop bench uses the JVM build of the same version).

### Spotui — GPL-3.0
https://github.com/Spotui/Spotui

The DASH-to-FLAC remux (`base/app/src/main/java/com/music/spotui/data/offline/DashFlacRemux.kt`)
is ported from Spotui and stays under the GPL-3.0. The file says so in its header.

### BitChord — GPL-3.0-or-later
https://github.com/kushagrasinghx/BitChord (commit `edc1e6f`)

By the BitChord contributors. The Immersive look is ported from it. These files in
`base/app/src/main/java/com/music/spotui/ui/look/` are adapted from BitChord, stay under the
GPL-3.0-or-later, and say so in their header:

- `MeshGradient.kt`, from `sharedUi/.../ui/player/MeshGradient.kt` (the mesh backdrop and `cyclingMeshPalette`)
- `ThinSlider.kt`, from `sharedUi/.../ui/player/ThinSlider.kt` (the thin seek bar, without Automix)
- `LyricClock.kt`, from `sharedUi/.../ui/player/LyricClock.kt` (the smoothed lyric playhead)
- `SweptLyricLine.kt`, from `sharedUi/.../ui/player/PlayerLyrics.kt` (`SweptLyricLine`, its drawing
  helpers and constants) and `sharedUi/src/androidMain/.../ui/player/PlayerPlatformUi.android.kt`
- `SweptLyricModel.kt`, from `shared/.../data/lyrics/LyricLine.kt` (word timing, lift and glow)

The Immersive UI in `base/app/src/main/java/com/music/spotui/ui/immersive/` (player, lyrics, queue,
tab bar, top bars, sheets, motion, components, Home, Search, Library, Category, album/playlist, artist, profile, podcast and download pages) is
adapted from these BitChord files, each named in the header of the Myspoti file that carries it:
`sharedUi/.../ui/player/` NowPlayingScreen, PlayerControls, PlayerState, PlayerHost, PlayerLyrics,
PlayerQueue; `sharedUi/.../ui/components/` Common, SearchField, Skeletons, SongText, OptimizedHaze;
`sharedUi/.../ui/screens/` HomeScreen, SearchScreen, LibraryScreen, ExploreScreen;
`app/.../ui/components/` MiniPlayer, FrostedTopBar, GlassNavBar, BottomFadeScrim,
floatingtabbar/FloatingTabBar, floatingtabbar/GlassSelectionPill; `app/.../ui/player/PlayerSheetMotion`;
`app/.../ui/screens/` DetailScreen, LibraryPhoneParts; `app/.../ui/utils/IosOverscroll`.

The local player's equalizer, `base/app/src/main/java/com/music/spotui/di/EqualizerProcessor.kt`, is
adapted from BitChord's `app/src/main/java/com/music/bitchord/playback/EqualizerProcessor.kt` (commit
`b690dc1`): its state-variable filter sections, their coefficients and the gliding of a change. It
stays under the GPL-3.0-or-later and says so in its header.

No BitChord fonts, icons, images or brand are included.

Code that BitChord itself carries from other projects, kept with its own header:
- `shell/FloatingTabBar.kt`: compose-floating-tab-bar by Elyes Mansour
  (https://github.com/elyesmansour/compose-floating-tab-bar), Apache-2.0, through Echo-Music
  (https://github.com/EchoMusicApp/Echo-Music), GPL-3.0.
- `shell/GlassSelectionPill.kt`: the pill motion follows liquid_glass_easy
  (https://pub.dev/packages/liquid_glass_easy), MIT License, Copyright (c) 2025 Ahmed Gamil.
- `components/ImmersiveOverscroll.kt`: from Convx (https://github.com/cosmictaserdev-creator/Convx), GPL-3.0.
- `shell/ImmersiveGlass.kt` (Liquid Glass): adapted from BitChord's `app/.../ui/components/LiquidGlass.kt`,
  itself adapted from Echo-Music's GlassEffectConfig / `Modifier.liquidGlass`
  (https://github.com/EchoMusicApp/Echo-Music), GPL-3.0. The lens of `shell/GlassSelectionPill.kt` and
  the minimizing chrome of `shell/ImmersiveChrome.kt` follow BitChord's `floatingtabbar/` files.

### Apple Music TV — GPL-3.0
https://github.com/JObersi10/apple-music-tv (commit `7e5c594`)

By JObersi10. The Android TV look is ported from it. These files in
`base/app/src/main/java/com/music/spotui/ui/tv/` are adapted from its
`android/app/src/main/java/com/applemusicktv/ui/` files, stay under the GPL-3.0, and say so in
their header:

- `TvParts.kt`, from `components/TopNavBar.kt`, `AlbumCard.kt`, `SpotlightCards.kt` (GradientCard)
  and `Skeleton.kt` (the floating tab pill, the focus scale, halo and border of the cards, the shelf skeleton)
- `TvApp.kt`, from `AppShell.kt`, `screens/HomeScreen.kt` and `screens/PlaylistDetailScreen.kt`
  (the shell, Home's shelves, a list's page)
- `TvNowPlaying.kt`, from `screens/NowPlayingScreen.kt` (the full-screen player, its idle mode and
  the D-pad seek bar)

Only its UI is used: none of its playback, Widevine or network code, and none of its icons (Apple's
SF Symbols), images or brand.

### Backdrop — Apache-2.0
https://github.com/Kyant0/backdrop (v2.0.0, `io.github.kyant0:backdrop`), Copyright 2025 Kyant0.

The refraction, blur, rim highlight and shadow of the Immersive look's Liquid Glass. Vendored as
source in `base/app/src/main/java/com/music/spotui/ui/immersive/shell/backdrop/`, taken from
BitChord's vendored copy (which adds a backdrop resolution scale and an exported backdrop, used to
keep the GPU cost down; the published artifact has neither). Every file keeps its Apache-2.0 header;
the package is renamed. Licence text in `base/licenses/Apache-2.0.txt` (it also covers Haze and
compose-floating-tab-bar above).

### Haze — Apache-2.0
https://github.com/chrisbanes/haze

The frosted bars of the Immersive look (Gradle dependency `dev.chrisbanes.haze:haze`).

### Reorderable — Apache-2.0

`sh.calvin.reorderable:reorderable` 3.1.0 (https://github.com/Calvin-LL/Reorderable), Copyright Calvin Liang,
used as a library (unmodified) for dragging the rows of the Immersive queue. Licence text in
`base/licenses/Apache-2.0.txt`.

### Inter — SIL Open Font License 1.1
https://github.com/rsms/inter

The Immersive look's typeface (`base/app/src/main/res/font/inter.ttf`), Copyright 2020 The Inter
Project Authors. Cut down to Latin with its weight axis only. Licence text in
`base/licenses/Inter-OFL.txt`.

### Chromium `cast_channel.proto` — BSD-3-Clause
https://chromium.googlesource.com/chromium/src/+/main/components/cast_channel/proto/cast_channel.proto

Copyright The Chromium Authors. Casting to Google Cast devices
(`base/app/src/main/java/com/music/spotui/cast/`) is Myspoti's own CastV2 sender, without the
Google Cast SDK. `CastMessage.kt` codes by hand the `CastMessage` message that this file defines
(its field numbers and types); no Chromium code is included.

### InnerTune — GPL-3.0
https://github.com/z-huang/InnerTune (commit `bfba5ec`)

By Zion Huang and the InnerTune contributors. YouTube Music as an account provider
(`base/api-ytmusic/`) follows it for the sign-in url and its "back on music.youtube.com" test
(`app/.../ui/screens/LoginScreen.kt`), the `SAPISIDHASH` auth header and the `WEB_REMIX` request
shape (`innertube/.../InnerTube.kt`), and the library's browse ids (`innertube/.../YouTube.kt`).
`YtmSession.kt`, `InnerTube.kt` and `YtmLibrary.kt` say so in their header and stay under the
GPL-3.0. No API key, stream or download code is taken.

### Mornye Extension — LGPL-3.0-only

`catalog/src/main/resources/app/myspoti/catalog/metadata/{deezer,tidal,ytmusic}.js` are modified versions of the
Deezer, Tidal and YouTube Music extensions of [Mornye-Extension](https://github.com/mornyeapp/Mornye-Extension),
Copyright 2026 zarzet, taken at commit `0c326fa36fc130127d90083808c6b51ea6d83aa2`.

Changes: everything but catalogue metadata was removed (audio, downloads, sign-in, settings of those), and the code
that remains was left as written otherwise. `catalog/tools/strip_metadata.py` performs these changes from the original
files, so they can be checked or redone. The texts of the LGPL-3.0 and of the GPL-3.0 it builds on are next to the
scripts. The ideas behind `catalog/.../Match.kt` also come from Mornye.

Each script runs as source and can be replaced without rebuilding the application.

### uBlock Origin filter lists (uAssets) — GPL-3.0
https://github.com/uBlockOrigin/uAssets (`filters/filters-2020.txt`, commit `dbc8ad17eea0d9f2815553c099643995f7a0ae83`)

By Raymond Hill and the uAssets contributors. The ad blocker for Spotify Connect devices
(`base/api-spotify/.../bridge/AdBlock.kt`) refuses the hosts and paths of
`base/api-spotify/src/main/resources/bridge/page-filter.txt`; the rules marked "uAssets" there are
taken from the list's open.spotify.com block (`2mdn.net`, `adxcel.com`, `amillionads.com`,
`adstudio-assets.scdn.co`, the `scdn.co/mp3-ad/` path), reduced to a host or a host and path. No
filter engine, cosmetic rule or scriptlet is taken.

### EasyList / EasyPrivacy — GPL-3.0 or CC BY-SA 3.0
https://github.com/easylist/easylist (`easyprivacy/easyprivacy_specific.txt`, commit `876bb4a29090827868d93ab8d00960f959e16028`)

By the EasyList authors. The tracker rule marked "EasyPrivacy" in the same list
(`spotifycdn.com/cdn/js/retargeting-pixels.`) comes from it, used under the GPL-3.0.

### Hush — GPL-3.0

By coldMEW. The ad host `s0.2mdn.net` came from its list; it is now covered by uAssets' `2mdn.net`.

### Horologist and Wear Compose Material3 — Apache-2.0
https://github.com/google/horologist

Copyright Google LLC. The watch app's player and library pages (`base/wear`) are built on
Horologist's media and audio UI (`horologist-media-ui-material3`, `horologist-audio-ui-material3`,
`horologist-compose-layout`, 0.8.4-alpha) and AndroidX Wear Compose Material3, used as libraries.
Two icon paths in `base/wear/.../WatchIcons.kt` are Google's Material Icons (Apache-2.0).

### Jetcaster and JetStream samples — Apache-2.0
https://github.com/android/compose-samples (Jetcaster/tv, Jetcaster/core/designsystem), Copyright 2024 The Android Open Source Project
https://github.com/android/tv-samples (JetStreamCompose), Copyright 2023 Google LLC

The Android TV screens (`base/app/src/main/java/com/music/spotui/ui/tv/`) adapt parts of these
samples: spacing and type scale, cover-tinted backgrounds, the two-column list and podcast pages,
cards, focus look (border, scale, glow), focus restoring rows, the seek bar, the home carousel,
the tab bar's sliding indicator and gradient placeholders. Modified for Myspoti, 2026-11. Files
holding adapted code say so at their top. Licensed under the Apache License, Version 2.0
(https://www.apache.org/licenses/LICENSE-2.0). Neither sample ships a NOTICE file.

---

# Part 2: inherited from Spotifusion

Spotifusion's notices, for the code Astral ports from it: lossless / SpotiFLAC, the playback cache and
queue preloader, the WebView sign-in fix, AdBlock and Connect handling, and offline downloads.

### SpotiDuck / Quackify
https://github.com/SpotiDuck. Inspiration, user experience patterns, design concepts and client
optimizations (Astral: onboarding, widget, in-app updater). No SpotiDuck assets or brand are included.

### Myspoti / Spotui — GPL-3.0
https://github.com/kota-zo/myspoti and https://github.com/Spotui/Spotui, by kota-zo and the MetrolistGroup.
See Part 1.

### BitChord
https://github.com/kushagrasinghx/BitChord, by Kushagra Singh (kushagrasinghx).
Spotifusion took the `TrackMatcher` title/artist scoring, the NewPipe progressive stream resolver, the
JioSaavn 320 kbps AAC extraction and the range-chunked downloader of `OfflineDownloader.kt` from BitChord.
Spotifusion's notice records that code under the MIT licence; BitChord is now GPL-3.0 (and
GPL-3.0-or-later for the files in Part 1). The MIT notice Spotifusion carried is kept:

> MIT License — Copyright (c) 2024 Kushagra Singh
>
> Permission is hereby granted, free of charge, to any person obtaining a copy
> of this software and associated documentation files (the "Software"), to deal
> in the Software without restriction, including without limitation the rights
> to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
> copies of the Software, and to permit persons to whom the Software is
> furnished to do so, subject to the following conditions:
>
> The above copyright notice and this permission notice shall be included in all
> copies or substantial portions of the Software.
>
> THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
> IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
> FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
> AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
> LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
> OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
> SOFTWARE.

### SpotiFLAC Mobile — MIT
https://github.com/zarzet/SpotiFLAC-Mobile, by zarzet. Astral's lossless code (`lossless/`) follows
SpotiFLAC's community server registry and its signed-session protocol (`ZarzSession.kt`: bootstrap,
hosted challenge, grant, exchange, ZARZ-HMAC-V1 signatures and download tickets), written from the
protocol and ported through Spotifusion. SpotiFLAC's download extensions (`tidal-web`, `qobuz-web`) are
Apache-2.0 (text in `base/licenses/Apache-2.0.txt`). SpotiFLAC Mobile's MIT notice:

> MIT License — Copyright (c) 2026 zarzet
>
> Permission is hereby granted, free of charge, to any person obtaining a copy
> of this software and associated documentation files (the "Software"), to deal
> in the Software without restriction, including without limitation the rights
> to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
> copies of the Software, and to permit persons to whom the Software is
> furnished to do so, subject to the following conditions:
>
> The above copyright notice and this permission notice shall be included in all
> copies or substantial portions of the Software.
>
> THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
> IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
> FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
> AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
> LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
> OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
> SOFTWARE.

### NewPipeExtractor — GPL-3.0
https://github.com/TeamNewPipe/NewPipeExtractor, by TeamNewPipe. YouTube / YouTube Music stream
metadata and audio stream extraction.

### LRCLIB — MIT
https://lrclib.net (https://github.com/tranxuanthang/lrcget), by Tran Xuan Thang. Used as an online
lyrics service; no code is bundled.

### Google Material Icons — Apache-2.0
https://github.com/google/material-design-icons, Copyright Google LLC. Apache License 2.0
(http://www.apache.org/licenses/LICENSE-2.0); text in `base/licenses/Apache-2.0.txt`.

---

# Part 3: added by Astral

## Design references: no code or assets copied

Astral's Starry Night backdrop, its Glassify and LiquidMusic looks, its Spicy Lyrics-style display, its
SpicyThemes-style colour presets and its BackMusic and Visualizer modules are Astral's own Kotlin / Jetpack
Compose implementations. They are written from scratch after the look and behaviour of the projects below.
No source code, stylesheet, image or other asset from these projects is included, with one exception: Astral's Spicy Lyrics renderer and lyrics backgrounds are ported from Spicy Lyrics Mobile, AGPL-3.0 (see Part 5). Colour values and preset
names are Astral's own. The Starry Night look follows an MIT-licensed theme, whose notice is quoted below.
The projects are named to credit the inspiration; naming them does not imply endorsement, and their licences
do not apply to Astral's code.

| Astral feature | Inspired by | That project's licence (checked 2026-11-18) |
| --- | --- | --- |
| Starry Night backdrop & palette | [spicetify/spicetify-themes — StarryNight](https://github.com/spicetify/spicetify-themes/tree/master/StarryNight) | MIT (notice below) |
| Glassify look | [sanoojes/spicetify-glassify](https://github.com/sanoojes/spicetify-glassify) | AGPL-3.0 |
| LiquidMusic look | [fraustiz/LiquidMusic](https://github.com/fraustiz/LiquidMusic) | none published (all rights reserved) |
| Spicy Lyrics-style lyrics display | [spikerko/spicy-lyrics](https://github.com/spikerko/spicy-lyrics), [spicylyrics/mobile](https://github.com/spicylyrics/mobile) | AGPL-3.0 (the renderer is now ported code, see Part 5) |
| Spicy Lyrics colour presets | [7xeh/SpicyThemes](https://github.com/7xeh/SpicyThemes) | ST Source-Available License v1.0 (not open source) |
| BackMusic module | [rolandyangg/backmusic_extension](https://github.com/rolandyangg/backmusic_extension) | none published (all rights reserved) |
| Visualizer module | [Konsl/spicetify-visualizer](https://github.com/Konsl/spicetify-visualizer) | MIT |

Because LiquidMusic, backmusic_extension and SpicyThemes grant no open-source licence, nothing
from them may be copied into Astral. Contributors: please keep it that way.

### Spicetify Themes (StarryNight) — MIT

> MIT License
>
> Copyright (c) 2019 morpheusthewhite
>
> Permission is hereby granted, free of charge, to any person obtaining a copy
> of this software and associated documentation files (the "Software"), to deal
> in the Software without restriction, including without limitation the rights
> to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
> copies of the Software, and to permit persons to whom the Software is
> furnished to do so, subject to the following conditions:
>
> The above copyright notice and this permission notice shall be included in all
> copies or substantial portions of the Software.
>
> THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
> IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
> FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
> AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
> LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
> OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
> SOFTWARE.

## Online services

Astral talks to these services at run time. Their data is not part of this repository, and their own
terms apply to it:
- **Spicy Lyrics API** (`api.spicylyrics.org`): optional word-synced lyrics source (`SpicyLyricsProvider.kt`).
- The other lyrics and metadata sources the app's settings list: LRCLIB (`lrclib.net`), Genius (`genius.com`),
  NetEase (`music.163.com`), Kugou (`lyrics.kugou.com`), AMLL (`raw.githubusercontent.com/amll-dev/amll-ttml-db`),
  LyricsPlus (`lyricsplus.binimum.org`, `lyricsplus.prjktla.my.id`), lrc.red (`lrc.red`), MusicBrainz
  (`musicbrainz.org`), ListenBrainz labs (`labs.api.listenbrainz.org`), Cover Art Archive (`coverartarchive.org`),
  Deezer (`api.deezer.com`) and iTunes (`itunes.apple.com`). They are sent the track title, artist, duration and,
  for some, the ISRC or Spotify track id.
- **Unison** (`unison.boidu.dev`), **LRCMux** (`api.lrcmux.dev`), **QQ Music** (`*.y.qq.com`), **Kuwo** (`*.kuwo.cn`) and
  **RMM Revival** (`lyrics.rmmreviv.al`, plus Apple's iTunes search, which RMM Revival uses): extra lyrics sources. Part 5
  lists what each is sent; QQ Music, Kuwo and RMM Revival stay off until the user turns them on.
- **Google Translate** (`translate.googleapis.com`): lyric translation, only when the user uses it. The lyric text is sent.
- **Spotify** (`*.spotify.com`, `*.scdn.co`, `*.spotifycdn.com`): sign-in, playback, library, account, artwork. Astral is not
  affiliated with Spotify.
- **YouTube** (`youtube.com`, `music.youtube.com`), through NewPipeExtractor: music videos, and the audio fallback of the
  playback preloader.
- **Lossless backends**, on by default (Settings can turn lossless off): the SpotiFLAC session gateway (`api.zarz.moe`), the community
  servers listed in a registry gist fetched from `gist.githubusercontent.com` (defaults `tidal.anandserver.cfd` and
  `amazon.anandserver.cfd`; the registry can add others), and the Tidal (`tidal.com`) and Qobuz (`qobuz.com`) public
  endpoints. They are sent the track title, artist, duration, ISRC and Spotify track id.
- **SponsorBlock** (`sponsor.ajay.app`): sponsor segments of podcasts and videos, if enabled.
- **Radio Browser** (`*.api.radio-browser.info`): radio station directory.
- **GitHub** (`Martinikaws/Astral`): in-app update check and release download (`api.github.com`), plus the extension
  and metadata-script indexes on `raw.githubusercontent.com`.

Astral has no analytics, crash-reporting or telemetry library, and no server of its own.

---

# Part 4: libraries Astral depends on

Every library the Gradle build pulls in directly (from `base/gradle/libs.versions.toml` and the modules'
`build.gradle.kts`, checked 2026-11-18). Their licence texts come with each library's published artifact.
The Apache-2.0 text is also in `base/licenses/Apache-2.0.txt`.

## Shipped in the app

| Library | Author | Licence |
| --- | --- | --- |
| Kotlin standard library, kotlinx.coroutines, kotlinx.serialization | JetBrains | Apache-2.0 |
| Jetpack Compose (UI, Foundation, Material 3, Material Icons), Activity, AppCompat, Core, Lifecycle, Navigation, Palette, ProfileInstaller, WebKit, Media | Google / The Android Open Source Project (AndroidX) | Apache-2.0 |
| Media3 (ExoPlayer, DASH, HLS, DataSource, Database, UI) | Google / The Android Open Source Project | Apache-2.0 |
| Wear Compose Foundation & Material 3 | Google / The Android Open Source Project | Apache-2.0 |
| Material Components for Android | Google | Apache-2.0 |
| Horologist (Compose Layout, Audio UI Material 3) | Google | Apache-2.0 |
| OkHttp, okhttp-brotli, okhttp-zstd | Square, Inc. | Apache-2.0 |
| Glide and Glide Compose | Bump Technologies / Google | BSD-2-Clause, with parts under MIT and Apache-2.0 |
| ZXing core | ZXing authors | Apache-2.0 |
| ONNX Runtime | Microsoft | MIT (see Part 1) |
| NewPipeExtractor | TeamNewPipe | GPL-3.0 |
| Mozilla Rhino | Mozilla Foundation | MPL-2.0 |
| Haze | Chris Banes | Apache-2.0 |
| Reorderable | Calvin Liang | Apache-2.0 |
| Google Play services (Cronet, Code Scanner, Wearable) | Google | Proprietary, Android Software Development Kit License; not open source |

The Google Play services client libraries are closed-source binaries bundled in the APK under the Android
Software Development Kit License. They talk to the Google Play services app on the phone or watch.

## Build and test only (not shipped in the app)

| Tool | Author | Licence |
| --- | --- | --- |
| Gradle, the Gradle wrapper and the Foojay toolchains resolver plugin | Gradle, Inc. | Apache-2.0 |
| Android Gradle Plugin, Kotlin and Compose Multiplatform Gradle plugins | Google / JetBrains | Apache-2.0 |
| JUnit 4 | JUnit team | EPL-1.0 |
| JUnit 5 (Jupiter, Platform) | JUnit team | EPL-2.0 |
| AndroidX Test (runner, ext-junit, Compose ui-test-junit4) | Google | Apache-2.0 |
| Compose UI Tooling and ui-test-manifest (debug builds only, not in release builds) | Google | Apache-2.0 |
| kotlinx-coroutines-test, kotlinx-io (core, bytestring) | JetBrains | Apache-2.0 |
| Robolectric | Robolectric authors | MIT |
| Roborazzi | takahirom | Apache-2.0 |
| kXML 2 | Stefan Haustein | MIT |
| JSON-java (`org.json:json` 20240303) | Sean Leary | Public domain |

---

# Part 5: Spicy Lyrics Mobile

Astral's Spicy Lyrics renderer and its lyrics background presets are ported from **Spicy Lyrics Mobile**
by TheX24 and contributors (https://github.com/spicylyrics/mobile, commit `f005aaa`), licensed under the
GNU Affero General Public License v3.0. The text is in [`base/licenses/AGPL-3.0.txt`](base/licenses/AGPL-3.0.txt).
Section 13 of the GNU GPL v3 allows combining GPL-3.0 code with AGPL-3.0 code. The ported files keep the
AGPL-3.0, and each starts with a header naming its upstream file. They live in
`base/app/src/main/java/com/music/spotui/spicy/`, and the ported tests in `base/app/src/test/java/com/music/spotui/spicy/`.

Spicy Lyrics Mobile itself adapts code from these projects. Their notices come with the ported code:

| Project | What it covers in Astral | Licence |
| --- | --- | --- |
| [Spicy Lyrics](https://github.com/Spikerko/spicy-lyrics) by Spikerko and contributors | The lyrics renderer: layout, word and line animation, scrolling, interludes | AGPL-3.0 |
| [Spicy Lyrics fork](https://github.com/iPixelGalaxy/spicy-lyrics) by iPixelGalaxy | The Legacy background | AGPL-3.0 |
| [@kawarp/core](https://github.com/better-lyrics/kawarp), Copyright (c) 2026 Better Lyrics | The Default (moving) background, `spicy/canvas/kawarp/` | MIT |
| [cubic-spline](https://github.com/morganherlocker/cubic-spline), Copyright (c) 2014 Morgan Herlocker | Word-animation spline, `spicy/animation/CubicSplineInterpolator.kt` | MIT |
| [spr](https://github.com/Fraktality/spr), Copyright (c) 2023 Fractality | Spring simulation, `spicy/animation/SpringSimulation.kt` | MIT |

Not taken: the parts Spicy Lyrics Mobile credits to *mild-lyrics* (source fetching order, lyric blends,
Genius romanization alignment), because that project publishes no licence. Their usage analytics were not
taken either.

The MIT licence text that applies to @kawarp/core, cubic-spline and spr:

> Permission is hereby granted, free of charge, to any person obtaining a copy
> of this software and associated documentation files (the "Software"), to deal
> in the Software without restriction, including without limitation the rights
> to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
> copies of the Software, and to permit persons to whom the Software is
> furnished to do so, subject to the following conditions:
>
> The above copyright notice and this permission notice shall be included in all
> copies or substantial portions of the Software.
>
> THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
> IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
> FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
> AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
> LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
> OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
> SOFTWARE.

## Lyrics sources ported from Spicy Lyrics Mobile

The fetch and parse code for these lyrics sources is ported from Spicy Lyrics Mobile
(`core/src/main/kotlin/com/tx24/spicyplayer/network/data/providers/`), AGPL-3.0. It lives in
`data/api/UnisonLyrics.kt`, `LrcMuxLyrics.kt`, `QqKuwoLyrics.kt`, `RmmRevivalLyrics.kt` and the marked blocks of
`LyricsApi.kt`. The LRCMux source is Spicy Lyrics Mobile's MIT-licensed port of Lyrica's lrcmux adapter.

| Source | Default | What Astral sends it |
| --- | --- | --- |
| [Unison](https://unison.boidu.dev) | on | the song's title and artist |
| [LRCMux](https://lrcmux.dev) | on | title, artist and length |
| QQ Music (`y.qq.com`) | off; asks first | title and artist |
| Kuwo (`kuwo.cn`) | off; asks first | title and artist |
| [RMM Revival](https://lyrics.rmmreviv.al) | off; asks first | title and artist to Apple's iTunes search, then the Apple Music ID |

YouTube transcripts were left out on purpose, because they need YouTube's private app API. Each service's
own terms apply to the lyrics it returns.

---

# Trademarks

Spotify is a trademark of Spotify AB. YouTube and YouTube Music are trademarks of Google LLC. Apple Music
and SF Symbols are trademarks of Apple Inc. Other product names belong to their owners. Astral is an
independent, unofficial project. It is not affiliated with, endorsed or sponsored by Spotify, Google, Apple
or any project named above.
