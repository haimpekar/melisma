package com.melisma.app.media

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import com.melisma.app.settings.SettingsStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/** Spotify-specific broadcasts are opt-in and require no notification access. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpotifyBroadcastMediaTest {
    private lateinit var context: Context
    private lateinit var subject: SpotifyBroadcastMedia
    private var visible = true
    private val dispatchedKeys = mutableListOf<Int>()

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        visible = true
        dispatchedKeys.clear()
        subject = SpotifyBroadcastMedia(
            context,
            SettingsStore(context),
            listeningAllowed = { visible },
            sendMediaKey = { dispatchedKeys += it },
        )
        subject.start()
        idle()
    }

    @After fun cleanup() {
        visible = false
        subject.stop()
        idle()
    }

    private fun idle() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun broadcast(action: String, configure: (Intent) -> Unit) {
        val intent = Intent(action).putExtra("timeSent", System.currentTimeMillis())
        configure(intent)
        context.sendBroadcast(intent)
        idle()
    }

    @Test fun spotifyMetadataAndPlaybackBecomeSynchronizedLyricsSnapshot() {
        broadcast("com.spotify.music.metadatachanged") {
            it.putExtra("id", "spotify:track:0123456789ABCDEFGHIJKL")
            it.putExtra("track", "Test song")
            it.putExtra("artist", "Test artist")
            it.putExtra("album", "Test album")
            it.putExtra("length", 215)
        }
        broadcast("com.spotify.music.playbackstatechanged") {
            it.putExtra("playing", true)
            it.putExtra("playbackPosition", 12500)
        }
        assertEquals("Test song", subject.snapshot.value.track?.title)
        assertEquals("Test artist", subject.snapshot.value.track?.artist)
        assertEquals(215_000L, subject.snapshot.value.track?.durationMs)
        assertTrue(subject.snapshot.value.playback.isPlaying)
        assertTrue(subject.snapshot.value.playback.currentMs() >= 12_500L)
        assertTrue(subject.snapshot.value.canControl)
        assertTrue(subject.snapshot.value.transport.playPause)
        assertTrue(subject.snapshot.value.transport.skipNext)
        assertTrue(subject.snapshot.value.transport.skipPrevious)
        assertFalse(subject.snapshot.value.transport.seek)
    }

    @Test fun pausingStopsPlayheadAdvancement() {
        broadcast("com.spotify.music.metadatachanged") {
            it.putExtra("track", "Track")
            it.putExtra("artist", "Artist")
        }
        broadcast("com.spotify.music.playbackstatechanged") {
            it.putExtra("playing", false)
            it.putExtra("playbackPosition", 32000)
        }
        assertFalse(subject.snapshot.value.playback.isPlaying)
        assertEquals(32000L, subject.snapshot.value.playback.currentMs())
    }

    @Test fun hidingStopsReceiverAndClearsSensitivePlaybackState() {
        broadcast("com.spotify.music.metadatachanged") {
            it.putExtra("track", "Private song")
            it.putExtra("artist", "Artist")
        }
        assertTrue(subject.snapshot.value.hasTrack)
        visible = false
        subject.stop()
        idle()
        assertFalse(subject.snapshot.value.hasTrack)
        broadcast("com.spotify.music.metadatachanged") {
            it.putExtra("track", "Must not appear")
        }
        assertFalse(subject.snapshot.value.hasTrack)
    }

    @Test fun staleStickyBroadcastIsIgnored() {
        context.sendBroadcast(
            Intent("com.spotify.music.metadatachanged")
                .putExtra("timeSent", System.currentTimeMillis() - 2 * 60 * 60 * 1000L)
                .putExtra("track", "Old song")
                .putExtra("artist", "Old artist")
        )
        idle()
        assertFalse(subject.snapshot.value.hasTrack)
    }
    @Test fun visibleSpotifyTrackAcceptsPlayPauseNextAndPreviousButtons() {
        broadcast("com.spotify.music.metadatachanged") {
            it.putExtra("track", "Playing")
            it.putExtra("artist", "Singer")
        }
        subject.togglePlayPause()
        subject.skipNext()
        subject.skipPrevious()
        idle()
        assertEquals(
            listOf(
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_MEDIA_NEXT,
                KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            ),
            dispatchedKeys,
        )
    }

    @Test fun mediaKeysNeverDispatchWithoutSpotifyMetadataOrWhileHidden() {
        subject.skipNext()
        idle()
        assertTrue("Cannot control an unknown media player", dispatchedKeys.isEmpty())

        broadcast("com.spotify.music.metadatachanged") {
            it.putExtra("track", "Playing")
            it.putExtra("artist", "Singer")
        }
        visible = false
        subject.togglePlayPause()
        subject.skipNext()
        idle()
        assertTrue("Hidden Melisma must not send media keys", dispatchedKeys.isEmpty())
        subject.stop()
        idle()
        visible = true
        subject.skipPrevious()
        idle()
        assertTrue("Stopped receiver must not send media keys", dispatchedKeys.isEmpty())
    }

    @Test fun disabledSpotifyPlayerDoesNotOfferOrDispatchControls() {
        val store = SettingsStore(context)
        store.setPlayerIgnored("com.spotify.music", true)
        // New repository is deliberately kept outside the app UI in the test.
        val second = SpotifyBroadcastMedia(
            context, store, listeningAllowed = { visible },
            sendMediaKey = { dispatchedKeys += it },
        )
        second.start()
        idle()
        broadcast("com.spotify.music.metadatachanged") {
            it.putExtra("track", "Ignored song")
            it.putExtra("artist", "Artist")
        }
        assertFalse(second.snapshot.value.canControl)
        second.skipNext()
        idle()
        assertTrue(dispatchedKeys.isEmpty())
        second.stop()
        idle()
    }

}
