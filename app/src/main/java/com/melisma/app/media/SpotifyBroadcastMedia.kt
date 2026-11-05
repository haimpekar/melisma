package com.melisma.app.media

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.melisma.app.settings.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Experimental, Spotify-only media source without NotificationListenerService.
 *
 * Uses Spotify's documented optional "Device Broadcast Status" feature. The
 * receiver is registered only while a Melisma lyrics surface is visible. This
 * does not require Spotify OAuth, an API key, or notification permission.
 *
 * Spotify can disable or change these broadcasts at any time. This class never
 * claims to know track/playback state if Spotify does not publish it.
 */
class SpotifyBroadcastMedia(
    private val context: Context,
    private val settings: SettingsStore,
    private val listeningAllowed: () -> Boolean = { true },
    /**
     * Media keys are public Android input events, independent of the notification
     * listener. Injectable so tests never issue actual system-wide media commands.
     */
    private val sendMediaKey: (Int) -> Unit = { keyCode ->
        val manager = context.getSystemService(AudioManager::class.java)
        if (manager != null) {
            val downTime = SystemClock.uptimeMillis()
            manager.dispatchMediaKeyEvent(
                KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0),
            )
            manager.dispatchMediaKeyEvent(
                KeyEvent(downTime, downTime + 1, KeyEvent.ACTION_UP, keyCode, 0),
            )
        }
    },
) {
    private val handler = Handler(Looper.getMainLooper())
    private val _snapshot = MutableStateFlow(PlayerSnapshot())
    val snapshot: StateFlow<PlayerSnapshot> = _snapshot.asStateFlow()

    // Kept for compatibility with Melisma's existing UI: no Android permission
    // is necessary to listen for Spotify's public, opt-in broadcasts.
    private val _permissionGranted = MutableStateFlow(true)
    val permissionGranted: StateFlow<Boolean> = _permissionGranted.asStateFlow()

    private var registered = false
    private var lastTrack: TrackInfo? = null
    private var lastPosition = PlaybackPosition.IDLE
    private var lastMetadataAt = 0L
    private var lastPlaybackAt = 0L
    private var totalEvents = 0
    private var lastAction: String? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!registered || !listeningAllowed() || intent == null) return
            val action = intent.action ?: return
            if (action != ACTION_METADATA && action != ACTION_PLAYBACK && action != ACTION_QUEUE) return

            // Broadcasts may be sticky. Never show stale hours-old metadata as current music.
            val now = System.currentTimeMillis()
            val sent = intent.getLongExtra("timeSent", now)
            if (sent > now + MAX_FUTURE_SKEW_MS || sent < now - MAX_EVENT_AGE_MS) return

            totalEvents++
            lastAction = action.substringAfterLast('.')
            when (action) {
                ACTION_METADATA -> applyMetadata(intent, now, sent)
                ACTION_PLAYBACK -> applyPlayback(intent, now, sent)
                ACTION_QUEUE -> Unit
            }
            publish()
        }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == handler.looper) block() else handler.post(block)
    }

    fun isPermissionGranted(): Boolean = true

    // The legacy UI action is redirected to Spotify, not to notification settings.
    fun notificationAccessIntent(): Intent =
        context.packageManager.getLaunchIntentForPackage(SPOTIFY_PACKAGE)
            ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage(SPOTIFY_PACKAGE)

    fun start() = onMain {
        if (!listeningAllowed() || registered) return@onMain
        registered = true
        val filter = IntentFilter().apply {
            addAction(ACTION_METADATA)
            addAction(ACTION_PLAYBACK)
            addAction(ACTION_QUEUE)
        }
        try {
            // Android 14 requires an explicit exported flag for other apps' broadcasts.
            ContextCompat.registerReceiver(
                context.applicationContext,
                receiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED,
            )
            settings.notePlayerSeen(SPOTIFY_PACKAGE, "Spotify")
        } catch (e: Exception) {
            registered = false
            lastAction = "registration failed: ${e.javaClass.simpleName}"
        }
    }

    fun stop() = onMain {
        if (registered) {
            registered = false
            runCatching { context.applicationContext.unregisterReceiver(receiver) }
        }
        lastTrack = null
        lastPosition = PlaybackPosition.IDLE
        lastMetadataAt = 0L
        lastPlaybackAt = 0L
        _snapshot.value = PlayerSnapshot()
    }

    fun refresh() = onMain {
        if (listeningAllowed()) start() else stop()
    }

    fun republish() = onMain { publish() }

    private fun applyMetadata(intent: Intent, now: Long, sent: Long) {
        if (sent < lastMetadataAt) return
        lastMetadataAt = sent
        val title = intent.getStringExtra("track")?.trim()?.take(300).orEmpty()
        val artist = intent.getStringExtra("artist")?.trim()?.take(300).orEmpty()
        if (title.isBlank() && artist.isBlank()) return

        val track = TrackInfo(
            title = title,
            artist = artist,
            album = intent.getStringExtra("album")?.trim()?.take(300).orEmpty(),
            durationMs = intent.getIntExtra("length", 0).coerceAtLeast(0).toLong() * 1000L,
            mediaId = intent.getStringExtra("id")?.takeIf { it.startsWith("spotify:track:") },
            packageName = SPOTIFY_PACKAGE,
        )
        val changed = track.cacheKey != lastTrack?.cacheKey
        lastTrack = track
        if (changed) {
            // Metadata changed often means the song just began. If a newer
            // playback-state broadcast already arrived, preserve that position.
            if (lastPlaybackAt < sent) {
                lastPosition = PlaybackPosition(
                    isPlaying = true,
                    positionMs = (now - sent).coerceAtLeast(0L),
                    atElapsedRealtime = SystemClock.elapsedRealtime(),
                    durationMs = track.durationMs,
                )
            }
        }
    }

    private fun applyPlayback(intent: Intent, now: Long, sent: Long) {
        if (sent < lastPlaybackAt) return
        lastPlaybackAt = sent
        if (!intent.hasExtra("playing") && !intent.hasExtra("playbackPosition")) return
        val playing = intent.getBooleanExtra("playing", lastPosition.isPlaying)
        val position = when (val value = intent.extras?.get("playbackPosition")) {
            is Int -> value.toLong()
            is Long -> value
            else -> lastPosition.currentMs()
        }.coerceAtLeast(0L)
        val elapsed = if (playing) (now - sent).coerceAtLeast(0L) else 0L
        lastPosition = PlaybackPosition(
            isPlaying = playing,
            positionMs = position + elapsed,
            atElapsedRealtime = SystemClock.elapsedRealtime(),
            durationMs = lastTrack?.durationMs ?: 0L,
        )
    }

    private fun publish() {
        if (!registered || !listeningAllowed()) return
        val track = lastTrack?.takeIf { it.packageName !in settings.current.ignoredPlayers }
        _snapshot.value = if (track == null) PlayerSnapshot() else PlayerSnapshot(
            track = track,
            playback = lastPosition.copy(durationMs = track.durationMs),
            sourcePackage = SPOTIFY_PACKAGE,
            sourceLabel = "Spotify (device broadcast)",
            // Android media keys can control the system's current media-button recipient.
            // They are not a targeted Spotify API and cannot confirm delivery.
            transport = Transport(playPause = true, skipNext = true, skipPrevious = true),
        )
    }

    /**
     * Send ordinary headset-style media buttons only after Spotify broadcast
     * metadata is present and Melisma is visible. No privileged media-session
     * token or notification permission is involved.
     *
     * Android chooses the current media-button recipient. If the user switches
     * to another player, its session may receive the key instead of Spotify.
     */
    private fun dispatchControl(keyCode: Int) = onMain {
        if (!registered || !listeningAllowed()) return@onMain
        if (_snapshot.value.sourcePackage != SPOTIFY_PACKAGE || !_snapshot.value.hasTrack) return@onMain
        runCatching { sendMediaKey(keyCode) }
            .onFailure { Log.w(TAG, "Unable to dispatch media button $keyCode", it) }
    }

    fun togglePlayPause() = dispatchControl(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    fun skipNext() = dispatchControl(KeyEvent.KEYCODE_MEDIA_NEXT)
    fun skipPrevious() = dispatchControl(KeyEvent.KEYCODE_MEDIA_PREVIOUS)

    // Media buttons cannot seek to an exact millisecond: keep the scrubber disabled.
    fun seekTo(positionMs: Long) = Unit

    fun openSourceApp() {
        runCatching {
            context.startActivity(
                notificationAccessIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    suspend fun describeSessions(): String = buildString {
        appendLine("Melisma Spotify broadcast experiment")
        appendLine("No notification-listener service or permission")
        appendLine("Receiver registered: $registered")
        appendLine("Spotify events received: $totalEvents")
        appendLine("Last event: ${lastAction ?: "none"}")
        appendLine("Current song: ${lastTrack?.title ?: "none"}")
        appendLine("Transport: system media buttons, not Spotify-targeted remote control")
        appendLine("Controls: play/pause, previous, next; seek unavailable")
        appendLine("Spotify -> Settings and privacy -> Playback -> Device Broadcast Status: ON")
        appendLine("If there are no events, try pausing and resuming or switching tracks.")
    }

    private companion object {
        const val TAG = "SpotifyBroadcastMedia"
        const val SPOTIFY_PACKAGE = "com.spotify.music"
        const val ACTION_METADATA = "com.spotify.music.metadatachanged"
        const val ACTION_PLAYBACK = "com.spotify.music.playbackstatechanged"
        const val ACTION_QUEUE = "com.spotify.music.queuechanged"
        const val MAX_EVENT_AGE_MS = 30 * 60 * 1000L
        const val MAX_FUTURE_SKEW_MS = 30_000L
    }
}
