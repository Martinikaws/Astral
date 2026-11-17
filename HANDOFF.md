# Astral — Project Handoff & Workspace Map

## Status Snapshot (Saved 2026-11-17 19:33)
Base codebase copied from `D:\Projects\OrpheonWearOS\orpheon\base` to `D:\Projects\OrpheonWearOS\Astral\base` and rebranded to **Astral** (`app.astral` / `app.astral.beta` / `.dev` for debug).

### Completed & Saved on Disk
1. **Gradle & Branding (`app.astral`)**:
   - `base/settings.gradle.kts` (`rootProject.name = "Astral"`)
   - `base/app/build.gradle.kts` (`APK_NAME = "Astral"`, `applicationId = "app.astral"` / `app.astral.beta`)
   - `base/wear/build.gradle.kts` (`applicationId = "app.astral"` / `app.astral.beta`, `:wear:assembleDebug` verified)
   - `base/app/src/main/res/values/strings.xml`, `base/wear/src/main/res/values/strings.xml`
   - **Logo #3 (Curved Comet reversed pointing downwards) & Astral Color Palette** (`#0B1B45`, `#FFFFFF`, `#B5D4F4`, `#85B7EB`, `#CECBF6`, `#060F2B`) in `app/src/main/res/drawable*`, `wear/src/main/res/`, `WelcomeScreen.kt`, `Color.kt`.
2. **Auth & Onboarding**:
   - `WebPlayerHost.kt`: Spotifusion WebView login fix (`onCreateWindow` SSO popups with `useProfile(this)`, native mobile UA on auth/SSO pages, desktop UA on `open.spotify.com`, `allow_password=1` fallback, AdBlock bypass during login) + Orpheon multi-account profiles.
   - `WelcomeScreen.kt` & `SetupScreen.kt` + `PageStylePref.kt`: SpotiDuck-inspired onboarding + first-run Playlist/Album/Artist Page Style picker (`ORPHEON_BASIC`, `ORPHEON_GLASS`, `SPOTIFUSION`).
3. **Playback, Lossless HiFi, Cache & Fast-Start**:
   - `lossless/`: `LosslessRegistry.kt`, `LosslessSource.kt`, `DashFlacRemux.kt`, `ZarzSession.kt`
   - `data/cache/`: `PlaybackCacheManager.kt`, `PlaybackCacheStore.kt`
   - `di/QueuePreloader.kt`, `SongPlayer.kt`, `LocalPlayer.kt`: Optimistic UI update, queue preloading, corrupt cache fallback, fast local/cached start.
   - `LosslessPref.kt`, `SpotiflacSessionPref.kt`, `SpotiflacGate.kt`, `SpotiflacScreen.kt`.
4. **Search, Queue, Music Videos, Lyrics & Stats**:
   - `SearchScreen.kt` & `ImmersiveSearchScreen.kt`: Cloud-synced + decorated recent cached searches.
   - `QueueScreen.kt` & `ImmersiveQueuePanel.kt`: Drag-to-reorder & swipe/drag-to-add-to-queue.
   - `MusicVideoController.kt` & `MusicVideo.kt`: Spotify Canvas/video primary with YouTube + AudioAlign + SponsorBlock fallback.
   - `LyricsScreen.kt`, `ImmersiveLyricsPanel.kt`, `SweptLyricLine.kt`: `beautiful-lyrics` style word-synced animations, dynamic backdrop, interlude dots.
   - `ListenHistory.kt`, `StatsScreens.kt`, `ImmersiveActivityScreens.kt`: Daily Stats, Monthly Recap, Total Time Listened.
5. **Extensions, Themes Tab & AI Porting Guide**:
   - `ExtensionStore.kt`, `ExtensionsScreen.kt`, `ImmersiveExtensionsScreen.kt`: Added **Themes** tab with custom theme import/preview/toggle/delete.
   - `D:\Projects\OrpheonWearOS\Astral\FEED-EXTENSIONS.md`: AI-ready prompt & manifest/runtime/theme porting guide.
6. **Widget, Media Notification, In-App Updater, Navigation & Settings**:
   - `NowPlayingWidget.kt`, `PlaybackService.kt`, `UpdateChecker.kt`, `AppUpdater.kt`.
   - `Routes.kt`, `MyNavHost.kt`, `SettingsScreen.kt` & `ImmersiveSettingsScreen.kt` (including `ON_RESUME` `spotiflacActive` refresh), `MainActivity.kt`, `AndroidManifest.xml` (`spotiflac://session-grant` deep link).
7. **Wear OS (Phone Remote Only)**:
   - `wear/.../WatchActivity.kt` (`RemoteApp()` only), `app/.../wear/WearLinkService.kt` (`/orpheon/remote/...` routed to `WatchRemote`; standalone watch sync disabled).

### Build State
- `:app:compileDebugKotlin` **PASSED** in `task-150`; APK packaging (`:app:assembleDebug`, `:wear:assembleDebug`, `:wear:assembleFast`) was completing at save time.
- Next command if APKs need to be rebuilt/installed:
  `.\gradlew.bat :app:assembleDebug :wear:assembleDebug :wear:assembleFast --console=plain` (with `JAVA_HOME=C:\Users\Martini\.gradle\jdks\azul_systems__inc_-21-amd64-windows.2`).
