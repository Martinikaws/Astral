# Astral (`app.astral`) — Complete Project Brief & Context

> **Project Root**: `D:\Projects\Astral\` (Gradle root: `D:\Projects\Astral\base\`)
> **GitHub Repository**: `https://github.com/Martinikaws/Astral.git` (`v1.0.0` & `v1.0.1` already published; target next release: **`v1.0.2`**)
> **License**: **GNU GPL-3.0** (derivative of `kota-zo/myspoti`, incorporating GPL-3.0 / MIT / Apache-2.0 components; retain all copyright/license notices and `kota-zo/myspoti` credits)

---

## 1. Goal of the Project

Build **Astral** (`app.astral` / `app.astral.beta` / `.dev` for debug pairing), a polished, fast, standalone Android + Wear OS music streaming client merging the best components from Orpheon (`kota-zo/myspoti`), Spotifusion (`p1di/spotifusion`), SpotiDuck/Quackify UX, and community Spicetify extensions/themes:

| Area | Target Specification |
| :--- | :--- |
| **Branding & Identity** | **Astral** (`app.astral`). Logo #3 (downward-pointing curved comet: `#FFFFFF` 4-point star head, `#B5D4F4` primary trail, `#85B7EB` secondary trail, `#CECBF6` sparkles, `#0B1B45` / `#060F2B` navy background). Cold-start falling star boot animation (`AstralBootSplash`). Zero user-facing Orpheon/Myspoti branding remaining. |
| **Login & Account** | SpotiDuck-inspired onboarding (`WelcomeScreen`, `SetupScreen`), Spotifusion's working WebView login/SSO popup fix (`WebPlayerHost.kt`: mobile UA on auth/SSO, desktop UA on `open.spotify.com`, `onCreateWindow` with `useProfile(this)`, `?allow_password=1`, AdBlock login bypass), Orpheon's token/session management & multi-account profiles (`Accounts.kt`). |
| **Playback Engine** | Orpheon native DRM/Spotify Connect + `AutoMix` DSP + `LocalPlayer`, upgraded with Spotifusion's **Lossless / SpotiFLAC** Hi-Res FLAC, `PlaybackCacheManager`, `QueuePreloader`, Spotifusion `AdBlock` & `Connect`, and **instant track start** (eliminating Orpheon's 2–3s start lag via queue preloading, optimistic UI state updates, and non-blocking local/cached start). |
| **Unified 5-Look System** | All **5 Looks** stored in **Look** (first-run setup + Settings -> Look), each overhauling the entire app UI, menus, bars, and screens:<br>1. **Astral Basic** (`CLASSIC`): Classic Material UI + bottom bar.<br>2. **Astral Interactive** (`IMMERSIVE`): Interactive glass UI + floating tab bar.<br>3. **Spotifusion 1:1** (`SPOTIFUSION`): Full Spotify 1:1 UI overhaul (`#121212` dark cards/surfaces unless Starry Night is ON, `#1DB954` green accents, Spotify-style Home, Bottom Bar, Playlist/Album/Artist & Player).<br>4. **Glassify** (`GLASSIFY`, from `sanoojes/spicetify-glassify`): Translucent glassmorphism UI overhaul with specular `1.dp` top-left highlight borders, soft glass shadows, blurred cover backdrop & dynamic accent.<br>5. **LiquidMusic** (`LIQUID_MUSIC`, from `fraustiz/LiquidMusic`): iOS-inspired `20.dp` floating liquid glass panels, contrast-adjusted album-art accent, floating pill bar, **with a Liquid Glass (Refraction) vs Frosted Glass toggle** (`LayerBackdrop` + `Lens` refraction vs `Frost` blur). |
| **Starry Night Backdrop** | Spicetify's **Starry Night (`[Base]`)** theme (`#000000 -> #142B44` night sky gradient, `#152238` elevated cards, `#FFF3C4` starlight accent, `#4687D6` sky blue) with live twinkling `#FFFFFF` stars & diagonal shooting stars (`StarryNightBackdrop`). Must be a **toggle applied across ALL 5 Looks** (screen containers must be `Color.Transparent` when Starry Night is ON, matching `StarryNight/user.css`, so the stars actually show behind Home, Search, Library, Settings, Menus, and Modules). |
| **Lyrics Overhaul (Spicy Lyrics)** | Keep Astral's existing lyrics **sources** (LRCLIB, Spotify, AMLL, LyricsPlus, NetEase, Kugou, Genius + optional `SpicyLyricsProvider`), and overhaul **how lyrics are displayed** (inspired by `spicylyrics/mobile`): staggered spring scroll & depth focus, word-by-word & held-word letter bloom, progressive sweep for line-synced lyrics, opposite-aligned duet lines (`voice == 2`), smaller italic backing vocals, pill-shaped 3-dot instrumental interlude, animated artwork mesh backdrop, plus **`7xeh/SpicyThemes`** color presets. |
| **Full-Screen Modules Tab** | Toggleable in Settings (`ModulesPref`), taking the full screen when selected with a switcher between 2 beat-accurate modules:<br>1. **BackMusic** (`rolandyangg/backmusic_extension`): blown-up blurred cover backdrop + vignette, real beat-spawned expanding rings & 12-band mirrored bars (`SoundWaves`), `Stars`/`Snow`/`Fireflies` particles, floating bobbing `24.dp` album art centerpiece, corner `NowPlayingLabel` with 3-bar mini EQ.<br>2. **Visualizer** (`Konsl/spicetify-visualizer`): true NCS glowing particle-ring sphere + 64-channel spectrum curve/bars + beat-synced waveform driven by real Spotify `audio-analysis` (`beats[]`, `bars[]`, `segments[].pitches`, loudness) & `:automix` `TrackAnalysis` (`TrackAudioSync.kt`). |
| **Search, Queue, Video, Stats, Extras** | Cloud-synced + decorated recent Search; drag-to-reorder & swipe/drag-to-add Queue; Spotify Canvas/Video primary + YouTube fallback Music Videos; Spotifusion Podcasts & Offline Downloads; Daily Stats / Monthly Recap / Time Listened; SpotiDuck-inspired Widget, Media Notification & In-App Updater (`Martinikaws/Astral`); Android Auto; Orpheon Extensions + **Themes** tab + `FEED-EXTENSIONS.md`. |
| **Wear OS** | Orpheon `:wear` companion (`WatchActivity` -> `RemoteApp()`) strictly in **Phone Remote only** mode (`Watch plays music without the phone: NO`). |

---

## 2. Timeline & What Has Been Done So Far

### Already Released on GitHub (`https://github.com/Martinikaws/Astral`)
1. **`v1.0.0`** (`9b36fc6` -> `8c5fcbf`): Full Orpheon base ported & rebranded (`app.astral`), Logo #3 icons, `AstralBootSplash`, `WebPlayerHost` login fix, Lossless/SpotiFLAC + `PlaybackCacheManager` + `QueuePreloader` + fast-start `SongPlayer`/`LocalPlayer`, `SpicyLyricsProvider`, Custom Search/Queue/MusicVideos/Stats, Extensions Themes Tab + `FEED-EXTENSIONS.md`, Wear OS Phone Remote, `README.md`, `CHANGELOG.md`.
2. **`v1.0.1`** (`d847242`, current `main`): Added `AstralThemePref` (`StarryNight`, `Glassify`, `LiquidMusic`, `SpicyThemes` presets, `liquidGlassRefraction` toggle) and `ModulesScreen` + `ModulesPref` (`BackMusic` + `Visualizer`), published 4 APKs to GitHub Release `v1.0.1`.

### User Feedback After Testing `v1.0.1` on Phone (Why `v1.0.2` Overhaul Was Started)
1. **Themes vs Look**: `Glassify` and `LiquidMusic` should be complete UI overhauls stored in **Look** alongside **Spotifusion 1:1** (moved from PageStyle into **Look**), so switching Look overhauls the whole menu & app.
2. **Starry Night Not Visible**: Stacked opaque `AppBackground` / `Immersive.Page` / `Scaffold` / `HomeScreen` / `MainBottomNavigation` layers completely hid `StarryNightBackdrop`. In `StarryNight/user.css`, screen containers are transparent so the sky shines through.
3. **Both Music Modules**: Were using synthetic `sin()` waves instead of syncing to the real song beat & spectrum, and didn't visually match `rolandyangg/backmusic_extension` and `Konsl/spicetify-visualizer`.
4. **Spicy Lyrics Clarification**: Spicy Lyrics (`spicylyrics/mobile`) overhauls **how lyrics are displayed**, not just how they're sourced.
5. **Remaining Branding**: Some `Orpheon` / `Myspoti` strings remained in `myspoti_strings.xml`, translation locales, debug `app_name`, `Logbook.kt`, `BetaLogs.kt`, `Actions.kt`, etc.

### Uncommitted `v1.0.2` Work Already Written in Working Tree (Moved from `D:\Projects\OrpheonWearOS\Astral` to `D:\Projects\Astral`)
- **Branding Cleanup (82 XMLs + 19 Kotlin files)**: Switched 571 resource strings across 12 locales + Kotlin UI/error/User-Agent strings to `Astral` (`:extensions`, `:catalog`, `:host-android` compiled cleanly).
- **Unified 5-Look Core & Root Transparency (17 core files)**: Expanded `LookPref.Look` (`CLASSIC`, `IMMERSIVE`, `SPOTIFUSION`, `GLASSIFY`, `LIQUID_MUSIC`), wired `AppBackground` / `Immersive.Page` -> `Color.Transparent` when Starry Night is on, updated `App.kt`, `MainActivity.kt`, `MyNavHost.kt`, `MainBottomNavigation.kt`, `ImmersiveTabBar.kt`, `LookStep.kt`, `SetupScreen.kt`, `SettingsScreen.kt`, `ImmersiveSettingsScreen.kt`, and fixed foreground dark-ink usages in `AppComponents.kt`, `ErrorRetry.kt`, `PlayerSheets.kt`, `ShowScreen.kt`, `ImmersiveHomeChips.kt`, `ImmersiveHomeProfile.kt`, `ImmersiveWhatsNewScreen.kt`.
- **Real Audio Beat Sync & Modules Overhaul**: Created `TrackAudioSync.kt` (1,028 lines: Spotify `audio-analysis` `beats[]`/`bars[]`/`segments[]`/`pitches[12]` + `AutoMix.analysis`, interpolated to real playback time) and updated `ModulesScreen.kt`.
- **Spicy Lyrics Display Overhaul**: Updated `SweptLyricLine.kt`, `LyricsScreen.kt`, `ImmersiveLyricsPanel.kt`.

---

## 3. Current State & Exact Missing / Remaining Work

### Build & Environment Notes
- **Android SDK**: `D:\Projects\OrpheonWearOS\AndroidStudio` (`base/local.properties`).
- **JDK 21 (Required)**: `C:\Users\Martini\.gradle\jdks\azul_systems__inc_-21-amd64-windows.2` (system default JDK 15 fails AGP 9.4.1).
- **Connected Phone**: `R58R5253K5H` (Samsung Galaxy S20 FE, `SM_G780G`, `arm64-v8a`).
- **Exact `:app:compileDebugKotlin` Status (Verified 2026-11-18)**:
  All subprojects (`:api`, `:api-web`, `:api-ytmusic`, `:automix`, `:extensions`, `:catalog`, `:pass`, `:api-spotify`, `:engine`, `:engine-android`, `:host-android`) compile cleanly. `:app:compileDebugKotlin` fails with **ONLY 2 errors**:
  1. `base/app/src/main/java/com/music/spotui/ui/look/LookPreview.kt:192-194` — `LookDemo` calls `ImmersiveDemo(clock, still, Look.*)`, but `ImmersiveDemo`'s declaration still only takes `(clock: DemoClock, still: Boolean)`.
  2. `base/app/src/main/java/com/music/spotui/ui/screens/LyricsScreen.kt:1597` — `val inlineDuet = remember(l) { ... }` is inside `LazyColumn { ... }` (`LazyListScope`) instead of hoisted above `LazyColumn`.

### Missing / Remaining Work for `v1.0.2`
1. **Fix the 2 Compile Errors** (`LookPreview.kt` + `LyricsScreen.kt:1597`) and verify `:app:compileDebugKotlin` + `:app:testDebugUnitTest`.
2. **Screen Transparency Sweep & 5-Look UI Adaptation (was interrupted mid-task)**:
   - Remove redundant/stacked opaque `AppBackground` / `Immersive.Page` / `#121212` / `stickyHeader` backgrounds so `StarryNightBackdrop` actually renders cleanly behind all screens:
     - `HomeScreen.kt`, `ImmersiveHomeScreen.kt`
     - `LibraryScreeen.kt`, `ImmersiveLibraryScreen.kt`
     - `SearchScreen.kt`, `ImmersiveSearchScreen.kt`
     - `ImmersivePagesParts.kt` (used by Immersive Settings & all Immersive subpages)
     - `WelcomeScreen.kt` (translucent dark-navy scrim when Starry Night is on)
     - Secondary screens (`ArtistScreen.kt`, `ContextScreen.kt`, `AlbumByIdScreen.kt`, `QueueScreen.kt`, `DownloadsScreen.kt`, `ProfileScreen.kt`, `StatsScreens.kt`, `CategoryScreen.kt`, `EqualizerScreen.kt`, `LocalFilesScreen.kt`, `HiddenSongsScreen.kt`, `ImmersiveActivityScreens.kt`, `ImmersiveProfileScreen.kt`, `ImmersiveAlbumByIdScreen.kt`, `ImmersiveArtistScreen.kt`, `ImmersiveContextScreen.kt`, `ImmersiveShowScreen.kt`, `ImmersiveCategoryScreen.kt`).
   - Polish visual overhaul differences across **Spotifusion 1:1**, **Glassify**, and **LiquidMusic** on Home, Library, Search, Menus, and Player.
3. **Code Review & Verification of `v1.0.2` WIP**:
   - Review `TrackAudioSync.kt` + `ModulesScreen.kt` for beat-sync accuracy (anchor updates on seek/pause/resume, `audio-analysis` fetch/caching, `BackMusic` + `Visualizer` visual fidelity to their repos, and `StarryNightBackdrop` compatibility).
   - Review `SpicyLyrics` (`SweptLyricLine.kt`, `LyricsScreen.kt`, `ImmersiveLyricsPanel.kt`), unified `LookPref` / `App.kt` / `Theme.kt` / `Settings` changes, and remaining `Orpheon`/`Myspoti` branding (`CastProtocol.kt:25`, `extensions-cli`).
4. **Release `v1.0.2`**:
   - Run full tests & build 4 APKs (`:app:assembleDebug`, `:wear:assembleFast`, plus `:wear:assembleDebug`).
   - Install & smoke-test on `R58R5253K5H`.
   - Update `CHANGELOG.md` + `README.md`, merge to `main`, push to GitHub, and publish Release `v1.0.2`.
