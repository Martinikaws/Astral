# Astral — Release Log

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
