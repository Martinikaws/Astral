# Astral — Release Log

## v1.0.3 — First Signed Stable Release, Spicy Lyrics Renderer & Backgrounds, More Lyrics Sources (2026-11-19)

> **Reinstall once.** Every earlier stable release (v1.0.0–v1.0.2) shipped a debug build ("Astral dev", `app.astral.dev`) that never checks for updates. v1.0.3 is the first signed release (`app.astral`). Install it once from this page, and from then on Astral updates itself in the app. The old "Astral dev" app can be uninstalled afterwards. Its settings and downloads don't carry over.

### Everything from v1.2.1-beta.1
- Starry Night as its own Look, a distinct visual identity for each of the 6 Looks across the whole app, the full-bleed faded player cover, the colour glow around playlist and album covers, and the full-screen Spotify Canvas background with blur.

### Spicy Lyrics renderer & background presets
- Lyrics are drawn with a port of the **Spicy Lyrics Mobile** renderer (word and syllable animation, bloom, spring scrolling) in both the Interactive lyrics panel and the classic lyrics screen.
- **7 background presets** in the lyrics view: Default (moving warp), Legacy, Auto, Artist Header, Cover Art, Color and Custom Color, with blur, a still option and an optional beat-reactive option (it fetches the song's beat data once). Pick them with the new palette button. A switch brings back the old lyrics view.

### More lyrics sources
- **On by default:** Unison and LRCMux.
- **Opt-in** (they ask first and say what is sent): QQ Music, Kuwo and RMM Revival.
- Switching a source on or off no longer clears saved lyrics.

### Licences
- Spicy Lyrics Mobile (AGPL-3.0) and the projects it builds on are credited in `THIRD_PARTY_NOTICES.md` Part 5, and the AGPL-3.0 text ships in `base/licenses/`.

---

## v1.2.1-beta.1 — Signed Beta Release, Starry Night Look, Full-Bleed Player & Canvas Backdrop (2026-11-18)

### Signed Beta Release & Auto-Updater (`app.astral.beta`)
- **Signed Release Pipeline**: Switched release builds to sign with the Astral release key (`base/keystore/keystore.properties`, phone + Wear OS matching signature) so `app.astral.beta` checks GitHub pre-releases (`UPDATE_REPO = Martinikaws/Astral`) and auto-updates in-app (`UpdateChecker` / `AppUpdater`).

### Looks, Contrast & Starry Night Identity
- **Starry Night as a Dedicated Look (`Look.STARRY_NIGHT`)**: Promoted Starry Night to a first-class 6th Look while keeping `StarryNightPref` as an optional "stars behind any Look" overlay (plus migration from v1.0.1/v1.0.2 settings).
- **Per-Look Visual Identity (`LookTokens`)**: Distinct surface fills, borders (specular Glassify rim, gold Starry Night hairline, flat Spotifusion cards, iOS inset-grouped LiquidMusic lists), radii, typography, default accents (`Look default` swatch with `FlowRow` wrapping), bright-cover accent clamping (`adjustForDarkGlass`), and luminance-aware switch thumbs.

### Player Cover Fade, Cover Glow & Full-Screen Canvas Background
- **Full-Bleed Faded Player Cover & Header Cover Glow**: Enlarged player artwork blending seamlessly into the backdrop (`PlayerCoverArt`), plus color-matched ambient glow around playlist and album header covers (`CoverGlow`).
- **Full-Screen Spotify Canvas Backdrop (`CanvasPref`)**: Edge-to-edge looping Canvas background across both Interactive and Classic/Spotifusion players with Android 12+ blur slider (`0–100%`) and on/off toggle in player options.

---

## v1.0.2 — Unified 5-Look Overhaul, Starry Night Transparency, Beat-Synced Modules & Spicy Lyrics Display (2026-11-18)

### Unified 5-Look System & Starry Night Visibility
- **5 App-Wide Looks**: Unified **Spotifusion 1:1**, **Glassify** (`sanoojes/spicetify-glassify`), and **LiquidMusic** (`fraustiz/LiquidMusic`, with **Liquid Glass Refraction vs Frosted Glass** toggle) directly into **Look** (`LookPref`) alongside **Astral Basic** and **Astral Interactive**, overhauling menus, navigation bars, previews (`LookStep` / `LookPreview`), and screens across the entire app.
- **Starry Night Transparency Fix**: Made root and screen containers transparent/translucent (`Color.Transparent` + single `0xB80A1422` transition veil in `MyNavHost`) when **Starry Night** is toggled ON so the twinkling stars and shooting stars visibly shine behind Home, Search, Library, Artist, Playlist, Album, Settings, Radio, and Modules across all 5 Looks.
- **Legible Contrast & Backdrop Masks**: Added luminance-checked button/chip/badge inks across Classic and Immersive screens (`ImmersiveButtons`, `SettingsScreen`, `ExtensionsScreen`, `LocalFilesScreen`, `LibraryExtras`), opaque swipe-reveal fills, and seamless `BlendMode.DstIn` backdrop fades (`ImmersiveArtistScreenParts`, `ArtistScreen`).

### Real Beat-Synced Modules & Spicy Lyrics Display Overhaul
- **Real Audio Beat & Spectrum Sync (`TrackAudioSync`)**: Added Spotify `audio-analysis` (`beats[]`, `bars[]`, `segments[]`, `pitches[12]`) + `:automix` `TrackAnalysis` playback-clock interpolation driving both **BackMusic** (`rolandyangg/backmusic_extension`) and **Visualizer** (`Konsl/spicetify-visualizer`, including NCS particle ring & 64-band Spectrum) on real song beats.
- **Spicy Lyrics Display Overhaul**: Refined kinetic word-by-word & held-word letter bloom, staggered spring scroll, opposite-aligned duet lines, italic backing vocals, pill-shaped instrumental interlude dots, and `SpicyThemes` (`7xeh/SpicyThemes`) presets while keeping Astral's existing lyrics sources.

### Branding Cleanup
- Replaced remaining user-facing `Orpheon` / `Myspoti` strings across 82 XML resource files (12 locales) and Kotlin diagnostic/extension/User-Agent strings with **Astral** (`Astral dev` for debug).

---

## v1.0.1 — Themes, Liquid Glass Refraction & Full-Screen Modules (2026-11-17)

### Themes & Lyrics Styling
- **SpicyThemes (`7xeh/SpicyThemes`)**: Added selectable Spicy Lyrics theme presets (`Astral Default`, `Starry Gold`, `Neon Rose`, `Midnight Cyan`, `Sunset Amber`, `Monochrome Frost`) in Settings, styling kinetic word highlights, glow, and mesh backdrops.
- **Glassify (`spicetify-glassify.sanooj.uk`)**: Added **Glassify** translucent glassmorphism theme option across Basic and Interactive UI.
- **LiquidMusic (`fraustiz/LiquidMusic`) + Refraction / Frosted Toggle**: Added **LiquidMusic** dynamic album-art-driven theme with a dedicated **Liquid Glass (Refraction) vs Frosted Glass** toggle in Settings (`LayerBackdrop` + `Lens` refraction vs frosted blur).

### Full-Screen Modules Tab
- **Settings-Activated Modules Tab**: Added a **Modules Tab** toggle in Settings (`ModulesPref`) that adds a full-screen **Modules** tab to navigation.
- **BackMusic (`rolandyangg/backmusic_extension`)**: Living audio-reactive backdrop with concentric sound-wave rings, 64 radial spectrum bars, floating album art centerpiece, selectable particles (**Stars**, **Snow**, **Fireflies**), track-matched palette, and quick playback controls.
- **Visualizer (`Konsl/spicetify-visualizer`)**: Dedicated full-screen real-time visualizer with **Spectrum Bars**, **NCS Pulse**, and **Waveform** modes.

---

## v1.0.0 — Initial Astral Release (2026-11-17)

### Branding & Visual Identity
- **App ID & Branding**: Rebranded full app & Wear OS companion to **Astral** (`app.astral` / `app.astral.beta`, `.dev` suffix for debug pairing).
- **Astral Logo #3**: Added downward-pointing curved comet adaptive launcher icons across `:app` and `:wear`.
- **Falling Star Boot Splash**: Added cold-start animated falling star & starlight reveal (`AstralBootSplash`).
- **Starry Night Main Theme**: Adopted Spicetify's `StarryNight` `[Base]` palette (`#000000` → `#142B44` sky, `#152238` cards, `#FFF3C4` accent, `#4687D6` secondary) with a live twinkling stars & shooting stars backdrop (`StarryNightBackdrop`), toggleable across all 3 UI styles (**Astral Basic**, **Astral Interactive Glass**, **Spotifusion 1:1**).

### Login, Playback & Audio Engine
- **WebView Login Fix + Multi-Account**: Ported Spotifusion's `WebPlayerHost` auth/SSO popup fix (`useProfile(this)`, native mobile UA on auth pages, `?allow_password=1` fallback, AdBlock bypass during login) alongside Orpheon's multi-account profile switcher.
- **Fast-Start Playback & Lossless HiFi**: Ported Spotifusion's `LosslessSource` / `SpotiFLAC`, `PlaybackCacheManager`, and `QueuePreloader`, and updated `SongPlayer` & `LocalPlayer` for instant optimistic UI updates, zero-wait local/cached starts, and corrupt cache fallback.
- **AutoMix, AdBlock & Connect**: Retained Orpheon's `:automix` DSP and Spotifusion's AdBlock/Connect handling.

### UI, Lyrics, Queue, Search, Video & Stats
- **First-Run Page Style Picker**: Choose between **Astral Basic**, **Astral Interactive (Glass)**, and **Spotifusion 1:1** for Playlist/Album/Artist pages on first launch or in Settings.
- **Better Lyrics**: Added word-synced sweep/glow, blurred mesh backdrop, spring auto-scroll, and interlude dots.
- **Search & Queue**: Added cloud-synced + decorated recent searches, drag-to-reorder queue, and swipe/drag-to-add-to-queue.
- **Music Videos**: Spotify native Canvas/video primary with automatic YouTube + `AudioAlign` + `SponsorBlock` fallback.
- **Listening Stats & Monthly Recap**: Added daily listening chart, total time listened on Astral, and Monthly Recap cards.
- **Widget, Notification & Auto-Updater**: Updated `NowPlayingWidget`, `PlaybackService` media notification, and wired `UpdateChecker` to `Martinikaws/Astral`.

### Extensions & Wear OS
- **Extensions Themes Tab & AI Guide**: Added a **Themes** tab in Extensions (`ExtensionsScreen` / `ImmersiveExtensionsScreen`) for custom JSON/ZIP themes, plus `FEED-EXTENSIONS.md` for AI-assisted Spicetify extension porting.
- **Wear OS Phone Remote**: Configured `:wear` (`WatchActivity`) and `WearLinkService` strictly as a Phone Remote (`Watch plays music without the phone: NO`).
