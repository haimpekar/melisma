package com.melisma.app.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Unregistered compatibility stub for legacy Updater source.
 *
 * This fork never instantiates the updater, has no install permissions, and declares
 * no receiver in its manifest. Keeping this inert class avoids editing the larger
 * upstream updater implementation while guaranteeing no install-status action occurs.
 */
class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}
