package app.myspoti.engine

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Startup latency invariants for the native engine: concurrent resolution, non-blocking
 * player load before reporting round trips finish, and DRM pre-warming.
 */
class StartupLatencyTest {
    @Test
    fun resolver_fetches_storage_resolve_and_seektables_concurrently() = runTest {
        val srStarted = CompletableDeferred<Unit>()
        val stStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val http = object : Http {
            override suspend fun request(request: HttpRequest): HttpResponse = when {
                "/storage-resolve/" in request.url -> {
                    srStarted.complete(Unit)
                    release.await()
                    HttpResponse(200, emptyMap(), Fixtures.bytes("storage-resolve.json"))
                }
                request.url.startsWith(EngineConfig.SEEKTABLES) -> {
                    stStarted.complete(Unit)
                    release.await()
                    HttpResponse(200, emptyMap(), Fixtures.bytes("seektable.json"))
                }
                else -> HttpResponse(404, emptyMap(), ByteArray(0))
            }
        }
        val env = FakeEnv()
        val config = EngineConfig()
        val resolver = Resolver(SpClient(http, FakeTokens(), config) {}, config, env).apply {
            spclient = "https://gew4-spclient.spotify.com"
        }
        val track = EngineJson.decodeFromString(MediaResponse.serializer(), Fixtures.text("media-response.json"))
            .media.values.first().item

        val job = async { resolver.resolve(track, 5160) }
        runCurrent()

        assertTrue(srStarted.isCompleted, "storage-resolve must start immediately")
        assertTrue(stStarted.isCompleted, "seektables must start before storage-resolve completes")

        release.complete(Unit)
        val media = job.await()
        assertTrue(media.segments.isNotEmpty())
        assertTrue(media.cdnUrls.isNotEmpty())
    }

    @Test
    fun replaceState_calls_player_load_while_before_track_load_is_in_flight() = runTest {
        val rig = Rig(backgroundScope)
        rig.start()

        val btlGate = CompletableDeferred<Unit>()
        rig.http.gates["before_track_load"] = btlGate

        val loadJob = launch { rig.engine.handleDealerFrame(rig.replaceState(2, paused = false, seekTo = 0)) }
        runCurrent()

        // player.load() has already run even though before_track_load PUT has not returned yet.
        assertNotNull(rig.player.loaded)
        assertEquals(listOf("load ${rig.trackUriOf(2)} @0 play=true"), rig.player.calls)
        assertFalse(loadJob.isCompleted, "lock must remain held until before_track_load completes")

        btlGate.complete(Unit)
        loadJob.join()
        assertEquals(listOf("before_track_load"), rig.http.debugSources())

        // On a track switch, player.load() also runs while track_data_finalized PUT is still in flight.
        val finGate = CompletableDeferred<Unit>()
        rig.http.gates["track_data_finalized"] = finGate
        val switchJob = launch { rig.engine.handleDealerFrame(rig.replaceState(3, paused = false, seekTo = 0)) }
        runCurrent()

        assertEquals(rig.trackUriOf(3), rig.player.loaded!!.trackUri)
        assertFalse(switchJob.isCompleted)

        finGate.complete(Unit)
        switchJob.join()
        assertEquals(
            listOf("before_track_load", "track_data_finalized", "before_track_load"),
            rig.http.debugSources(),
        )
    }

    @Test
    fun prewarmDrm_fetches_certificate_and_licence_url_once_and_caches_them() = runTest {
        val rig = Rig(backgroundScope)
        rig.start()
        assertEquals(0, rig.http.count("application-certificate"))
        assertEquals(0, rig.http.count("/melody/v1/license_url"))

        rig.engine.prewarmDrm()
        assertEquals(1, rig.http.count("application-certificate"))
        assertEquals(1, rig.http.count("/melody/v1/license_url"))

        rig.engine.handleDealerFrame(rig.replaceState(2, paused = false, seekTo = 0))
        val ch = rig.player.licence!!
        ch.serviceCertificate()
        ch.serviceCertificate()
        ch.licence(Fixtures.bytes("licence-challenge.bin"))
        ch.licence(Fixtures.bytes("licence-challenge.bin"))

        assertEquals(1, rig.http.count("application-certificate"))
        assertEquals(1, rig.http.count("/melody/v1/license_url"))
        assertEquals(2, rig.http.count("/audio/license"))
    }
}
