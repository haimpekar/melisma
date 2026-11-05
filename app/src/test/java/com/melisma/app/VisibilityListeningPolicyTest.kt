package com.melisma.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisibilityListeningPolicyTest {
    @Test fun hiddenSurfacesMustNotListen() {
        assertFalse(VisibilityListeningPolicy.mayListen(false, false))
    }

    @Test fun visiblePhoneCanListen() {
        assertTrue(VisibilityListeningPolicy.mayListen(true, false))
    }

    @Test fun visibleCarLyricsCanListen() {
        assertTrue(VisibilityListeningPolicy.mayListen(false, true))
    }

    @Test fun overlapKeepsListenerOnlyUntilBothSurfacesHide() {
        assertTrue(VisibilityListeningPolicy.mayListen(true, true))
        assertTrue(VisibilityListeningPolicy.mayListen(false, true))
        assertFalse(VisibilityListeningPolicy.mayListen(false, false))
    }
}
