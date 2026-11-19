# Security policy

## Reporting a vulnerability

Please report security problems privately, not in a public issue.

Use GitHub's private reporting: open the [Security tab](https://github.com/Martinikaws/Astral/security) of the repository and choose **Report a vulnerability**, or go straight to
<https://github.com/Martinikaws/Astral/security/advisories/new>. Only the maintainer can see the report.

Include what you found, the Astral version (Settings → About) and whether it is the stable or the Beta app, the Android version and device, and steps to reproduce it. If you can, say what an attacker would gain.

You can expect a first reply within about a week. Astral is maintained by one person in their spare time, so fixes may take longer. Please give a reasonable time to ship a fix before you publish details.

## In scope

- The Android app (`app.astral` and `app.astral.beta`).
- The Wear OS app.
- The extension sandbox: a third-party JS extension or theme reading data or calling APIs it was not granted, or escaping the runtime.
- The in-app updater: anything that lets a wrong, unsigned or tampered APK be installed, or that skips the signature check.
- Leaks of account tokens, session data or other personal data from the app.

## Out of scope

- Problems in Spotify's, YouTube's or any other third-party service itself. Report those to the service.
- The behaviour of community servers and lyrics or metadata services Astral talks to (see the Privacy section of the README).
- Anything that needs a rooted device, a debug build, or physical access to an unlocked phone.
- Spotify terms-of-service questions. Astral is unofficial; see the Disclaimer in the README.

## Supported versions

Only the latest stable release and the latest Beta release get security fixes. Older versions should update. v1.0.0 to v1.0.2 were debug builds that cannot update themselves: install the latest stable release by hand.
