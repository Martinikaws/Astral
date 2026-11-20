package com.music.spotui.ui.screens

import app.myspoti.api.account.AccountProviders
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the car and the media session ask to sign in: only where the app itself is closed to the user. */
class CarSignInTest {
    @Test fun signedOutWithNoAccountChosenAsksToSignIn() {
        assertTrue(Onboarding.carSignInRequired(spotifySignedIn = false, accountChosen = false, provider = AccountProviders.SPOTIFY, tv = false))
        assertTrue(Onboarding.carSignInRequired(spotifySignedIn = false, accountChosen = true, provider = AccountProviders.SPOTIFY, tv = false))
    }

    @Test fun signedInNeverAsks() {
        assertFalse(Onboarding.carSignInRequired(spotifySignedIn = true, accountChosen = true, provider = AccountProviders.SPOTIFY, tv = false))
    }

    @Test fun anotherProviderWithoutSpotifyNeverAsks() {
        val other = AccountProviders.YTMUSIC
        assertFalse(Onboarding.carSignInRequired(spotifySignedIn = false, accountChosen = true, provider = other, tv = false))
    }
}
