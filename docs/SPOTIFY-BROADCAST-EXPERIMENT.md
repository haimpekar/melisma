# Spotify broadcast experiment — zero notification permission

This **separate experimental branch** replaces Melisma's device-wide media-session
monitoring with Spotify's optional documented *Device Broadcast Status* mechanism.

The app does **not** register a NotificationListenerService, request the
`BIND_NOTIFICATION_LISTENER_SERVICE` service binding, request notification
access from the user, install other APKs, or require a Spotify token.

## How to use

1. In the **Spotify Android app**, open **Settings and privacy → Playback**.
2. Turn **Device Broadcast Status** ON. It may be unavailable on some versions,
   devices, or while listening via a remote Spotify Connect device.
3. Start or change a track while the Melisma UI is visible; it registers a
   dynamic receiver only while the phone/PiP or its Android Auto screen is visible.
4. The track title, artist, duration, Spotify URI and playback-position events
   feed Melisma's existing lyric-matching and timing pipeline.

The developer diagnostic *What players publish* shows event counts.

## Limitations and security

- Spotify-only; other music players are not supported by this branch.
- Spotify broadcasts are not guaranteed. If disabled, missing or stale,
  there is no legitimate source for an up-to-date playhead without opting into
  a different mechanism. Do not show invented playback data.
- For **Play/Pause, Next, Previous**, the app sends standard Android media-key
  presses via `AudioManager.dispatchMediaKeyEvent()`, which does not require
  notification access or Spotify API authorization. Android routes those keys to
  the **current media-button consumer**, not necessarily Spotify, and does not
  confirm that Spotify acted. Controls are enabled only while a valid Spotify
  broadcast track is visible in Melisma.
- **Seek to an exact playback position** is still unsupported: system media keys
  provide no arbitrary seeking API. The scrubber stays disabled.
- Android broadcast Intents are not authenticated; another app may spoof
  track metadata. Such data is treated as untrusted text, never as code.
- A track that was already playing before Melisma becomes visible may not
  be reported until Spotify publishes an event (try changing tracks).
- Google Play Protect may still scan unknown APKs. Removing notification
  permission removes **one sensitive permission trigger**, but approval is
  never guaranteed.
- The experiment retains the original lyric source/providers and optional
  account settings. It does not require any of them for Spotify broadcasts.

## Architecture evidence

- Spotify: https://developer.spotify.com/documentation/android/tutorials/android-media-notifications
- Android: https://developer.android.com/develop/background-work/background-tasks/broadcasts
- Android media session restrictions:
  https://developer.android.com/reference/android/media/session/MediaSessionManager

## Validation

GitHub CI runs unit tests and produces a debug APK. It checks the built
Android manifest does not declare a notification listener, self-installation
permissions or Shizuku installation provider.

Real-world Spotify and Android Auto compatibility remain **unverified** until
tested on an Android phone. An emulator without the real Spotify app cannot
establish that Spotify actually emits the opt-in broadcasts.

The original `feature/visibility-scoped-listener` branch remains unchanged.
