package com.melisma.app.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.melisma.app.settings.SettingsStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * This privacy fork disables automatic update checks, including an old preference
 * saved by a previous upstream installation. A hidden update check is not acceptable.
 * The legacy Updater remains in source for compatibility but is never constructed by
 * the production application.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LaunchCheckTest {

    private lateinit var context: Context
    private lateinit var settings: SettingsStore
    private var looks = 0

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        settings = SettingsStore(context)
        looks = 0
    }

    private fun updater() = Updater(
        context,
        settings,
        fetchLatest = {
            looks++
            null
        },
        // Unit tests are the debug build, whose real answer here is "cannot update in place" — which
        // would switch off the code under test.
        updatableInPlace = true,
    )

    @Test
    fun `a new process never automatically checks for updates`() = runBlocking {
        settings.noteUpdateCheck(System.currentTimeMillis())
        updater().check(automatic = true)
        assertEquals("the privacy fork never checks on launch", 0, looks)
    }

    @Test
    fun `an old auto-update preference cannot reenable background checking`() = runBlocking {
        settings.setAutoUpdateCheck(true)
        val updater = updater()
        repeat(3) { updater.check(automatic = true) }
        assertEquals("stored preference must not override the privacy policy", 0, looks)
    }

    @Test
    fun `the setting still wins`() = runBlocking {
        settings.setAutoUpdateCheck(false)

        updater().check(automatic = true)

        assertEquals("auto-check off means no request", 0, looks)
    }

    @Test
    fun `a check the user asked for is never throttled`() = runBlocking {
        settings.setAutoUpdateCheck(false)
        settings.noteUpdateCheck(System.currentTimeMillis())

        val updater = updater()
        updater.check(automatic = false)
        updater.check(automatic = false)

        assertEquals("asking always asks", 2, looks)
    }
}
