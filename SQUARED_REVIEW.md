# Squared theme review

Reviewed Astral's Squared integration against the v1.0.7.1 baseline (`6587b1d`), including the artist and album screenshots. Three GPT‑6 Luna reviewers independently inspected collections and profiles, navigation and settings, and the shell and player. The findings below distinguish confirmed code behavior from areas that need a device check.

## Fixed in this branch

| Area | Finding | Change |
| --- | --- | --- |
| Artist, album and playlist rows | The row's swipe wrapper used the global Material background. Collection pages substituted the artwork-derived foreground but left that background at the global value. This produced the solid black slab and black-on-black text in the artist capture. | The shared collection page provides both its foreground and its background to the row. Swipe-to-queue retains the opaque surface needed during a drag. |
| Collection contrast | The catalogue's text color could override the calculated foreground even where it contrasted poorly with the rendered page. Mid-tone artwork could also get white text under the old fixed luminance cutoff. | Use catalogue or tinted ink when its contrast ratio against the page is at least 4.5:1. Compute a readable fallback from the actual page color. |
| User Profile | The profile route had no Squared branch. The shared profile also made its self-profile decision before the account ID was available. | Route to a Squared layout using avatar-derived colors, a matching playlist list and padding for the Squared player and tab bar. Wait for account identity before enabling another user's social actions. Reuse Astral's existing data, actions and navigation. |

## Open findings

| Priority | Area | Evidence and next check |
| --- | --- | --- |
| High | Settings accessibility | `settings/SquaredSettingsParts.kt` custom sliders use pointer gestures without an accessible range or set-progress action. The custom toggle exposes no switch state. Confirm with TalkBack and keyboard controls. |
| Medium | Lyrics on Canvas | `player/SquaredLyricsView.kt` draws inactive lines with white at roughly 35% opacity. Busy or pale artwork can reduce readability. A device check should establish the contrast with the actual Canvas scrim in portrait, landscape and full lyrics. |
| Medium | Layout coverage | Squared episode and folder routes use the app's shared destinations. Some Squared translations remain missing. Validate the routes and text in a non-English locale before the next release. |
| Low | Collection header | `screens/SquaredCollectionScreen.kt` remembers collapse in raw pixels while recomputing its range. If the available range shrinks after rotation or an inset change, a previously collapsed header can briefly be outside the new range. |
| Low | Mini-player controls | `player/SquaredMiniPlayer.kt` renders a 36 dp play/pause control. Measure its actual touch bounds inside the clipped 48 dp bar on device before changing the layout. |
| Low | Shell background work | `SquaredShell.kt` collects glass configuration with `collectAsState` while the shell remains composed. Lifecycle-aware collection would avoid updates while the app is backgrounded. |
| Low | Unknown navigation targets | `screens/SquaredNavigation.kt` silently ignores unsupported addresses. Home currently filters unsupported cards, so the review found no common broken Home path. |

## Review coverage

| Surface | Checked |
| --- | --- |
| Shell, glass and player | Backdrop ownership, empty backdrop, player transition capture, swipe/dismiss thresholds, mini-player controls, queue laziness and lyrics drawing |
| Collections and profile | Shared collection palette, swipe forwarding, artist/album song rows, collapse bounds, profile identity/actions and playlist routes |
| Home, Search and Library | Feed mapping, playback and navigation callbacks, local-library routes and search result limits |
| Settings and accessibility | Playback, lyrics, Canvas and energy settings wiring, custom controls and screen-reader semantics |

The player and shell record artwork separately from the controls that sample it. The audit found no recursive capture in that path. Queue rows are emitted lazily, and the Search API returns a finite result set, so neither warrants a rewrite from the issues reported here.

A device pass still needs to cover the supplied artist and album examples, self and other-user profiles, light and dark system themes, tablet and landscape layouts, TalkBack, bright Canvas and fast player open/close. The Android bridge could not start in the current environment, so those observations were not made here.
