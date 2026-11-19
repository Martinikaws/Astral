# Contributing to Astral

Thanks for helping. Astral is a Kotlin / Jetpack Compose Android app (plus a Wear OS app). The Gradle root is `base/`.

## Build

Follow "Building from Source" in the [README](README.md): JDK 21, the Android SDK, `sdk.dir` in `base/local.properties`, then for example `.\gradlew.bat :app:assembleDebug` from `base/`. Debug builds are signed with the debug key and have the updater switched off. Release signing keys are not part of the repository and you do not need them to contribute.

## Issues and pull requests

- Search existing issues first. Use the bug report or feature request form, and for a bug include the version from Settings → About, the Look, your device and Android version, and whether you run the stable or the Beta app.
- Security problems go through [SECURITY.md](SECURITY.md), not a public issue.
- Fork the repository, branch from `main`, and open the pull request against `main`. One change per pull request, with a short description of what changes and why.
- Make sure `:app:compileDebugKotlin` (and the tests of the modules you touched) pass before you open it. Match the style of the surrounding code, including how the six Looks are handled.
- User-visible text goes into `res/values/` string resources, not hard-coded in Kotlin. English is enough; translations are welcome.

## Licence of contributions

Astral is licensed under the **GNU GPL-3.0** (see [LICENSE](LICENSE)). By contributing, you agree that your contribution is released under GPL-3.0. The files under `spicy/` are ported from Spicy Lyrics Mobile and stay under **AGPL-3.0**; changes to those files are AGPL-3.0 too (see `THIRD_PARTY_NOTICES.md`, Part 5, and `base/licenses/AGPL-3.0.txt`).

## Do not copy from projects without an open-source licence

Some projects Astral takes design inspiration from publish no open-source licence. Per `THIRD_PARTY_NOTICES.md`, **nothing may be copied from LiquidMusic, backmusic_extension, SpicyThemes or mild-lyrics**: no code, assets, colour tables or text. Reimplement the idea in your own words and code.

If you port or adapt code from any other project, check that its licence is compatible with GPL-3.0, say so in the pull request, and add its copyright and licence notice to `THIRD_PARTY_NOTICES.md` in the same change.
