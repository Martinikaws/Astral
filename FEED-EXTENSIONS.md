# Astral Extensions & Themes — AI Prompt & Technical Reference

> **How to use this file:** Copy and paste the entire contents of this file (or the **System Prompt** + relevant sections below) into your AI assistant (Claude, ChatGPT, Gemini, etc.) when asking it to **port a Spicetify extension** to Astral, **write a new Astral extension**, or **create a custom Astral theme** for Astral's **Themes** tab.

---

## 1. Copy-Paste System Prompt for Your AI

```text
You are an expert developer building extensions and themes for **Astral** (Android music client powered by the `app.myspoti.extensions` v1 sandboxed JavaScript runtime).

Follow these strict rules when writing Astral extensions or themes:
1. Astral extensions are packaged as a ZIP archive containing `manifest.json` at the root and a single JavaScript entry file (default `main.js`, also at the root; no subdirectories in `main`).
2. `manifest.json` MUST use `"apiVersion": 1` and declare every capability in `"actions"` (each with `"name"` and `"why"` of 1–80 characters). Do NOT use the deprecated `"provides"` / `"permissions"` fields.
3. The JavaScript runtime is a **headless, synchronous, sandboxed JS engine** (no DOM, no `window`, no `document`, no `fetch`, no `setTimeout`/`setInterval`, no `eval`, no `new Function`).
4. The runtime global object is `myspoti` (NOT `astral`). Always use `myspoti.provide(...)`, `myspoti.on(...)`, `myspoti.http.*`, `myspoti.storage.*`, `myspoti.settings.*`, `myspoti.player.*`, `myspoti.spotify.*`, and `myspoti.notify(...)`.
5. All `myspoti.http.*` calls and `myspoti.provide(...)` callbacks are **synchronous** (return the result object directly; do not use `async`/`await` or `Promise` unless purely synchronous).
6. For **Custom Themes**, declare action `"ui.style"` in `actions` and configure `"ui"` (`colors`, `hide`, `order`, `styles`, `blocks`) in `manifest.json`. Background and surface colors MUST be opaque (`#RRGGBB` or `#FF...`) and dark enough for white text (relative luminance <= 0.2).
7. Output complete, valid `manifest.json` and `main.js` files with zero placeholder `TODO`s.
```

---

## 2. Package Structure & `manifest.json` Schema (`apiVersion: 1`)

An Astral extension package (`.zip` / `.mext`) has at its root:
- `manifest.json` (required)
- `main.js` (or whatever `.js` filename `main` specifies, at the root—no `/` or `\`)
- `icon.png` (optional, square PNG <= 256 px at root)

### `manifest.json` Fields

| Field | Type | Required | Rules / Notes |
|---|---|---|---|
| `id` | `string` | Yes | Lowercase reverse-domain, >= 2 parts: `^[a-z][a-z0-9_]*(\.[a-z0-9_]+)+$` (e.g. `io.github.you.myext`) |
| `name` | `string` | Yes | 1–40 characters |
| `version` | `string` | Yes | Display version, 1–20 chars (e.g. `"1.0.0"`) |
| `versionCode` | `int` | No | Default `1`, must be `>= 1` (higher = newer) |
| `apiVersion` | `int` | No | Default `1` (must be `1`) |
| `author` | `string` | Yes | 1–60 characters |
| `description` | `string` | No | Max 500 characters |
| `homepage` | `string` | No | Optional URL |
| `main` | `string` | No | Default `"main.js"`. Must end with `.js`, no `/` or `\` |
| `actions` | `array` | Yes | At least 1 action object: `{ "name": "...", "why": "..." }` (`why`: 1–80 chars). See §3. |
| `media` | `object` | No | `{ "hosts": ["cdn.example.com", "*.audio.example.com"] }` (max 20 hosts, `*` alone forbidden). Allowlist for audio/download/block-image URLs. |
| `settings` | `array` | No | User-configurable settings (requires `"settings.page"` in `actions`). See below. |
| `icon` | `string` | No | PNG filename at root (e.g. `"icon.png"`) |
| `category` | `string` | No | Store category key (`lyrics`, `audio`, `tools`, `other`, etc.) |
| `tags` | `array` | No | Up to 8 lowercase tags (`^[a-z0-9][a-z0-9 -]{0,23}$`) |
| `minAppVersion` | `string` | No | Semver string e.g. `"2.0.2"` |
| `changelog` | `string` | No | Max 2000 characters |
| `welcome` | `object` | No | `{ "message": "...", "steps": ["..."] }` (message <= 600 chars, max 5 steps <= 160 chars each) |
| `replaces` | `array` | No | Up to 10 extension IDs this replaces |
| `conflictsWith` | `array` | No | Up to 10 extension IDs that cannot run alongside this |
| `ui` | `object` | No | Theme configuration (requires `"ui.style"` in `actions`). See §6. |
| `translations` | `object` | No | Map of BCP 47 tag (`"fr"`, `"pt-BR"`) -> `{ name?, description?, changelog?, welcome?, actions?, settings? }` |

### `settings` Item Schema
Requires `{ "name": "settings.page", "why": "..." }` in `actions`.
```json
{
  "key": "api_key",
  "type": "secret",
  "label": "API Key",
  "description": "Optional helper text",
  "default": "",
  "options": [],
  "section": "playback",
  "role": "quality",
  "perNetwork": false
}
```
- `key`: `^[A-Za-z0-9_]{1,40}$` (unique)
- `type`: `"text"` | `"secret"` | `"toggle"` | `"number"` | `"choice"` | `"button"` (`choice` requires non-empty `options`; `button` cannot have `default`)
- `section` (optional): `"playback"` or `"downloads"` (surfaces the setting in Astral's Settings when active)
- `role` (optional): `"quality"` (only on `choice` with `section`)
- `perNetwork` (optional): `true` for separate Wi-Fi / mobile data values (`choice`, `toggle`, `number`, `text`)

---

## 3. Closed Action Catalogue (`actions`)

Every capability must be declared in `actions`. `network` takes `"hosts": ["api.example.com", "*.example.com"]` (or `["*"]`, which is `CRITICAL`). `spotify.raw` can optionally list `"writes": ["POST https://..."]` (max 8).

| Action `name` | Level | Unlocks |
|---|---|---|
| `playback.control` | `NORMAL` | `myspoti.player.play/pause/next/previous/seek`; `spotify.player` controls; `spotify.devices.transfer/pullHere` |
| `playback.queue` | `NORMAL` | `spotify.player` `addToQueue`, `playNext`, `moveInQueue`, `removeFromQueue`, `clearQueue` |
| `playback.mirror` | `CRITICAL` | `myspoti.spotify.player.setLocalMute(on)` |
| `playback.events` | `NORMAL` | `myspoti.player.current()`; `myspoti.on('track' \| 'playback')`; `spotify.player` state/queue/volume |
| `lyrics.provide` | `NORMAL` | `myspoti.provide('lyrics', fn)` |
| `lyrics.romanize` | `NORMAL` | `myspoti.provide('lyrics-romanize', fn)` |
| `trackinfo.provide` | `NORMAL` | `myspoti.provide('trackinfo', fn)` |
| `audio.source` | `CRITICAL` | `myspoti.provide('audio-source', fn)` |
| `download.source` | `CRITICAL` | `myspoti.provide('download-source', fn)` |
| `home.sections` | `NORMAL` | `myspoti.provide('home', fn)` |
| `search.provide` | `NORMAL` | `myspoti.provide('search', fn)` |
| `menu.items` | `NORMAL` | `myspoti.provide('menu', fn)` |
| `player.buttons` | `NORMAL` | `myspoti.provide('player', fn)` |
| `settings.page` | `NORMAL` | `manifest.settings`; `myspoti.settings.get(key)`; `myspoti.on('setting', fn)` |
| `notify` | `NORMAL` | `myspoti.notify(text)` |
| `library.read` | `NORMAL` | `spotify.library`, `playlists` (mine), `users`, `recents`, `devices.list`, `podcasts.savedEpisodes`, `blends` |
| `library.write` | `CRITICAL` | `spotify.library` add/remove/pin/folders; `users` follow/unfollow |
| `playlists.write` | `CRITICAL` | `spotify.playlists` create/edit/add/remove/delete; `spotify.blends` |
| `spotify.catalog` | `NORMAL` | `spotify.search`, `catalog`, `playlists` (public), `home`, `podcasts`, `extras` |
| `spotify.raw` | `CRITICAL` | `myspoti.spotify.raw.gql / service / get / request` |
| `network` | `NORMAL` (`*` = `CRITICAL`) | `myspoti.http.*` to declared `hosts`; `myspoti.on('online', fn)`; `myspoti.work.whenOnline()` |
| `webview.page` | `CRITICAL` | `myspoti.provide('page', fn)` & `'message'`; `myspoti.page.post(data)` |
| `webview.auth` | `CRITICAL` | `myspoti.auth.open({ url, callback, hidden })`; `myspoti.on('auth', fn)` |
| `storage` | `NORMAL` | `myspoti.storage.get/set/remove` (512 KB limit) |
| `ui.style` | `NORMAL` | `manifest.ui` theme & layout customization |
| `attest` | `NORMAL` | `myspoti.attest.enroll/publicKey/sign/reset` |

---

## 4. Sandboxed JS Runtime Reference (`myspoti.*`)

### Globals
- `myspoti` (frozen object)
- `console` (`log`, `info`, `warn`, `error` -> `myspoti.log`)
- Standard ES built-ins (`JSON`, `Object`, `Array`, `Math`, `RegExp`, `Date`, `Map`, `Set`, etc.)
- **Forbidden:** `eval`, `Function`, `window`, `document`, `fetch`, `XMLHttpRequest`, `setTimeout`, `setInterval`.

### Core Methods
- `myspoti.apiVersion` -> `1`
- `myspoti.locale` -> BCP 47 tag (e.g. `"en-US"`)
- `myspoti.provide(hook, fn)` -> registers a synchronous hook handler
- `myspoti.on(event, fn)` -> registers an event listener (`"track"`, `"playback"`, `"auth"`, `"online"`, `"setting"`)
- `myspoti.http.get(url, options?)` / `.post(url, body, options?)` / `.request({ url, method, headers?, body?, timeout? })` -> returns `{ status, ok, headers, body, json() }`
- `myspoti.http.all([req1, req2, ...])` -> up to 4 requests in parallel
- `myspoti.query(params)` -> builds URL query string `"a=1&b=two"`
- `myspoti.storage.get(key)` / `.set(key, value)` / `.remove(key)`
- `myspoti.settings.get(key)` -> returns current setting value
- `myspoti.player.current()` -> returns `TrackInfo` or `null`
- `myspoti.player.play()` / `.pause()` / `.next()` / `.previous()` / `.seek(ms)`
- `myspoti.notify(text)` -> shows a toast/snackbar message
- `myspoti.ui.refresh(place)` -> refreshes `'home' | 'search' | 'menu' | 'player' | 'trackinfo'`
- `myspoti.util`: `sleep(ms)` (max 2000ms), `base64Encode`, `base64Decode`, `md5`, `sha1`, `sha256`, `hmacSha256(key, text)`

### Hook Input & Output Schemas

#### `TrackInfo` (passed to `track` event, `lyrics`, `audio-source`, `trackinfo`, `player`)
```json
{
  "id": "4uLU6hMCjMI75M1A2tKUQC",
  "uri": "spotify:track:4uLU6hMCjMI75M1A2tKUQC",
  "title": "Song Title",
  "artist": "Primary Artist",
  "artists": ["Primary Artist", "Featured Artist"],
  "album": "Album Name",
  "durationMs": 215000,
  "isrc": "USRC17607839",
  "cover": "https://i.scdn.co/image/..."
}
```

#### `lyrics` (`lyrics.provide`)
Return `null` if none found, or:
```json
{
  "provider": "LRCLIB",
  "synced": true,
  "translationLanguage": "en",
  "lines": [
    {
      "time": 12500,
      "end": 16200,
      "text": "Line text",
      "words": [{ "time": 12500, "end": 13100, "text": "Line" }],
      "translation": "Optional translation",
      "romanization": "Optional romanization",
      "voice": 1,
      "background": "Optional backing vocal"
    }
  ]
}
```
Or plain unsynced text: `{ "provider": "Name", "plain": "Line 1\nLine 2" }` (max 3,000 lines).

#### `audio-source` (`audio.source`)
Input: `TrackInfo` + `{ "ahead": boolean }`. Return `null` or:
```json
{
  "url": "https://cdn.example.com/audio.flac",
  "headers": { "User-Agent": "..." },
  "mimeType": "audio/flac",
  "durationMs": 215000,
  "expiresInMs": 3600000,
  "bitDepth": 24,
  "sampleRate": 96000,
  "codec": "flac",
  "source": "FLAC · 24-bit · 96 kHz"
}
```
(Alternatively `"manifest"` base64 MPEG-DASH MPD instead of `"url"`.)

#### Native Blocks (`home`, `search`, `trackinfo`, `menu`, `player`)
Blocks are rendered natively by Astral. Allowed `type`s:
- `home`, `search`, `trackinfo`: `"section"`, `"card"`, `"tracks"`, `"albums"`, `"playlists"`, `"artists"`, `"button"`, `"text"`, `"image"`
- `menu` (max 4): `{ "type": "menu", "label": "Do Thing", "icon": "extension", "action": "my_action_id" }`
- `player` (max 3): `{ "type": "button", "label": "Speed", "icon": "timer", "action": "cycle_speed" }`
- `tap` hook receives `{ "action": "...", "surface": "...", "context": { ... } }` when a block with `"action"` is tapped.

Allowed `icon` names: `add`, `album`, `artist`, `bolt`, `bookmark`, `chart`, `check`, `download`, `edit`, `extension`, `favorite`, `headphones`, `info`, `language`, `link`, `lyrics`, `mic`, `music`, `open`, `play`, `playlist`, `queue`, `radio`, `refresh`, `repeat`, `search`, `settings`, `share`, `shuffle`, `star`, `timer`, `translate`, `tune`, `volume`.

---

## 5. Porting Spicetify Extensions to Astral

Use this mapping when converting a Spicetify `.js` extension:

| Spicetify API | Astral Equivalent (`myspoti.*`) | Required `action` |
|---|---|---|
| `Spicetify.Player.data` | `myspoti.player.current()` | `playback.events` |
| `Spicetify.Player.addEventListener("songchange", fn)` | `myspoti.on("track", fn)` | `playback.events` |
| `Spicetify.Player.addEventListener("onplaypause", fn)` | `myspoti.on("playback", fn)` | `playback.events` |
| `Spicetify.Player.play() / pause() / next() / back() / seek(ms)` | `myspoti.player.play() / pause() / next() / previous() / seek(ms)` | `playback.control` |
| `Spicetify.CosmosAsync.get/post(...)` | `myspoti.spotify.raw.get/request(...)` or typed `myspoti.spotify.*` | `spotify.raw` / `spotify.catalog` / `library.read` |
| `fetch(url, opts)` | `myspoti.http.get/post/request(...)` (synchronous!) | `network` (with `hosts`) |
| `Spicetify.showNotification(msg)` | `myspoti.notify(msg)` | `notify` |
| `Spicetify.LocalStorage.get/set` | `myspoti.storage.get/set/remove` | `storage` |
| `Spicetify.ContextMenu.Item` | `myspoti.provide("menu", fn)` + `myspoti.provide("tap", fn)` | `menu.items` |
| `Spicetify.Playbar.Button` | `myspoti.provide("player", fn)` + `myspoti.provide("tap", fn)` | `player.buttons` |
| Custom HTML/CSS React modal or page | `myspoti.provide("page", fn)` + `myspoti.provide("message", fn)` | `webview.page` |
| CSS theme / hiding Spotify UI elements | `manifest.json` -> `"ui": { ... }` | `ui.style` |

### Example: Ported Extension (`manifest.json` + `main.js`)
**`manifest.json`**
```json
{
  "id": "io.github.example.lrclib",
  "name": "LRCLIB Lyrics",
  "version": "1.0.0",
  "versionCode": 1,
  "apiVersion": 1,
  "author": "YourName",
  "description": "Fetches synced lyrics from LRCLIB.",
  "main": "main.js",
  "category": "lyrics",
  "actions": [
    { "name": "lyrics.provide", "why": "Provide synced lyrics for the current track" },
    { "name": "network", "hosts": ["lrclib.net"], "why": "Fetch lyrics from lrclib.net" }
  ]
}
```

**`main.js`**
```javascript
myspoti.provide("lyrics", function (track) {
  if (!track || !track.title) return null;
  var q = myspoti.query({
    track_name: track.title,
    artist_name: track.artist,
    album_name: track.album,
    duration: Math.round((track.durationMs || 0) / 1000)
  });
  var res = myspoti.http.get("https://lrclib.net/api/get?" + q);
  if (!res.ok) return null;
  var data = res.json();
  if (data.syncedLyrics) {
    var lines = data.syncedLyrics.split("\n").map(function (line) {
      var m = line.match(/^\[(\d+):(\d+(?:\.\d+)?)\]\s*(.*)$/);
      if (!m) return null;
      var ms = Math.round((parseInt(m[1], 10) * 60 + parseFloat(m[2])) * 1000);
      return { time: ms, text: m[3] };
    }).filter(Boolean);
    return { provider: "LRCLIB", synced: true, lines: lines };
  }
  if (data.plainLyrics) {
    return { provider: "LRCLIB", synced: false, plain: data.plainLyrics };
  }
  return null;
});
```

---

## 6. Creating Custom Astral Themes (`ui.style` + `ui`)

Astral's **Themes** tab applies look-only extensions that declare `"ui.style"` and a `"ui"` object in `manifest.json`. Themes are applied natively across Astral's screens (except protected auth/payment surfaces).

### `ui` Schema (`UiStyle`)

```json
"ui": {
  "colors": {
    "accent": "#8B5CF6",
    "background": "#0B0B14",
    "surface": "#141426"
  },
  "hide": [
    "home.dj",
    "player.source"
  ],
  "order": {
    "nav": ["home", "search", "library"]
  },
  "styles": {
    "text": {
      "scale": 1.0
    }
  },
  "blocks": {
    "home": []
  }
}
```

#### Rules & Constraints
- **`colors`**: Keys are `"accent"`, `"background"`, `"surface"`. Format: `#RRGGBB` or `#AARRGGBB`.
  - `"background"` and `"surface"` **must be opaque** (`#RRGGBB` or `#FF...`) and **dark** (relative luminance `<= 0.2`) so Astral's white text stays readable; otherwise they are ignored.
- **`hide`** (max 20): Valid `UiPart` keys:
  - `"home.dj"` — Spotify's DJ card on Home
  - `"player.lyrics-card"` — Lyrics card under the Classic player
  - `"player.source"` — Source subtitle line under the player's title
  - `"player.capsule"` — Bottom capsule on the Immersive player
- **`order.nav`**: Must contain `"home"`, `"search"`, `"library"` exactly once each.
- **`styles.text.scale`**: Number between `0.85` and `1.3`.
- **`blocks.home`**: Optional array of native `Block` objects shown on Home under the theme's name.

### Complete Theme Example (`manifest.json` + `main.js`)
**`manifest.json`**
```json
{
  "id": "io.github.example.midnight_violet",
  "name": "Midnight Violet",
  "version": "1.0.0",
  "versionCode": 1,
  "apiVersion": 1,
  "author": "YourName",
  "description": "Deep cosmic dark theme with violet accents and a clean player.",
  "main": "main.js",
  "actions": [
    { "name": "ui.style", "why": "Apply Midnight Violet colours and clean up player chrome" }
  ],
  "ui": {
    "colors": {
      "accent": "#A78BFA",
      "background": "#090910",
      "surface": "#131322"
    },
    "hide": ["home.dj", "player.source"],
    "order": {
      "nav": ["home", "search", "library"]
    },
    "styles": {
      "text": { "scale": 1.0 }
    }
  }
}
```

**`main.js`**
```javascript
// Theme-only package: look is declared in manifest.json ("ui").
```

---

## 7. Repository `index.json` Entry Format (Optional)

If hosting extensions or themes in a custom repository `index.json` (`format: 1`):
```json
{
  "format": 1,
  "name": "My Astral Extensions",
  "extensions": [
    {
      "id": "io.github.example.midnight_violet",
      "name": "Midnight Violet",
      "version": "1.0.0",
      "versionCode": 1,
      "apiVersion": 1,
      "author": "YourName",
      "description": "Deep cosmic dark theme with violet accents.",
      "actions": [
        { "name": "ui.style", "why": "Apply Midnight Violet colours and clean up player chrome" }
      ],
      "url": "releases/midnight-violet-1.0.0.zip",
      "sha256": "<sha256-hex-of-zip>"
    }
  ]
}
```
