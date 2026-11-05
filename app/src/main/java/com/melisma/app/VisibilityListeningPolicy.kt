package com.melisma.app

/**
 * An app-wide notification listener must only be bound when Melisma can actually show lyrics.
 * Neither a paired car nor background Spotify playback is sufficient.
 */
internal object VisibilityListeningPolicy {
    fun mayListen(phoneVisible: Boolean, carScreenVisible: Boolean): Boolean =
        phoneVisible || carScreenVisible
}
