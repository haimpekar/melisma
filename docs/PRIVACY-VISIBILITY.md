# Visibility-scoped notification listener (privacy fork)

Melisma uses Android's broad Notification Listener permission as the authority to
read media sessions. In this fork, an active binding is justified **only while a
Melisma lyrics interface is visible**.

- `MainActivity.onStart/onStop` tracks phone activity visibility (including
  actually visible picture-in-picture).
- `MelismaSession` lifecycle `ON_START/ON_STOP` tracks when Melisma itself,
  rather than Android Auto in general, is displayed on the car screen.
- If neither interface is visible, we immediately detach media callbacks,
  clear the active track, cancel lyrics and prefetch jobs, and request the
  notification listener to unbind. Background Spotify playback cannot prolong
  the binding.
- The listener rejects unexpected system-initiated binds while hidden.
  Android 14+ additionally has default automatic binding disabled.
- A delayed media retry cannot reconnect while the app is hidden.
- A rapid close/reopen while an unbind is pending reconnects once the
  service reports disconnection.

**Limitations:** Android's notification-access permission remains granted.
A deliberately malicious or compromised build could ignore this app-side policy.
Unbinding is not equivalent to Android revoking the permission. The listener
callbacks ignore notification payloads, but while connected the service is
still capable of receiving unrelated notifications.

## Before trusting a build on a real device

1. Open Melisma on phone with Spotify playing -> listener connects, lyrics load.
2. Hide the app (no PiP, no car) -> listener disconnects immediately.
3. Show/dismiss phone PiP -> access follows actual visibility.
4. Show Melisma on Android Auto -> listener reconnects; switch to
   Spotify/Maps -> it disconnects if no phone lyrics are showing.
5. Disconnect Android Auto -> it disconnects, regardless of Spotify playback.
6. Open phone lyrics with car screen hidden -> it reconnects.
7. Toggle notification permission, reboot the phone and simulate unexpected
   listener reconnections -> no off-screen listening.
8. Test very rapid hide/reopen and overlapping phone and car views.
9. Confirm no repeated background lyric requests remain after the UI hides.

CI includes pure eligibility tests, but physical Android/Android Auto lifecycle
tests still need to be run on the target device before any security guarantee.

This change does **not** remove self-updating. Disable automatic update checks and
Shizuku updates in the app settings when using a pinned release.
