package com.music.spotui.pass

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PassAndroidSentTest {
    @Test fun thisInstallsOwnSessionsAreNeverSent() {
        assertFalse(PassAndroid.sent("SpotiflacSession"))
        assertFalse(PassAndroid.sent("SpotiflacSession_1.2.3"))
        assertFalse(PassAndroid.sent("WebViewChromiumPrefs"))
        assertFalse(PassAndroid.sent("auth_token_store"))
    }

    @Test fun settingsAreSent() {
        assertTrue(PassAndroid.sent("myspoti_prefs"))
    }
}
