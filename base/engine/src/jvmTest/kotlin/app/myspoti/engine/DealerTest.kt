package app.myspoti.engine

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
class DealerTest {
    private val gunzip = FakeEnv()::gunzip
    private fun gzip(s: String) = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(s.encodeToByteArray()) } }.toByteArray()
    private val json = """{"cluster":{"active_device_id":"abc","devices":{"abc":{"name":"Phone","device_type":"SMARTPHONE"}}}}"""

    @Test
    fun payload_forms_all_decode_to_the_same_json() {
        val expected = EngineJson.parseToJsonElement(json)
        assertEquals(expected, Dealer.payload(expected, null, gunzip))
        assertEquals(expected, Dealer.payload(JsonPrimitive(json), null, gunzip))
        assertEquals(expected, Dealer.payload(JsonPrimitive(Base64.encode(json.encodeToByteArray())), null, gunzip))
        assertEquals(expected, Dealer.payload(JsonPrimitive(Base64.encode(gzip(json))), mapOf("Transfer-Encoding" to "gzip"), gunzip))
    }

    @Test
    fun an_unreadable_string_throws() {
        runCatching { Dealer.payload(JsonPrimitive("%%%not base64%%%"), null, gunzip) }.also { assertTrue(it.isFailure) }
    }

    @Test
    fun request_body_is_read_from_payload_compressed() {
        val frame = EngineJson.decodeFromString(
            DealerFrame.serializer(),
            """{"type":"request","key":"k1","message_ident":"hm://connect-state/v1/player/command","headers":{"Transfer-Encoding":"gzip"},"payload":{"compressed":"${Base64.encode(gzip(json))}"}}""",
        )
        assertEquals("k1", frame.key)
        assertEquals("hm://connect-state/v1/player/command", frame.messageIdent)
        assertEquals(EngineJson.parseToJsonElement(json), Dealer.requestBody(frame, gunzip))
    }

    @Test
    fun reply_carries_the_key_and_success() {
        val r = EngineJson.parseToJsonElement(Dealer.reply("a\"b", true)).jsonObject
        assertEquals("reply", r["type"]!!.jsonPrimitive.content)
        assertEquals("a\"b", r["key"]!!.jsonPrimitive.content)
        assertTrue(r["payload"]!!.jsonObject["success"]!!.jsonPrimitive.boolean)
        assertTrue(Dealer.requestSuccess("hm://connect-state/v1/player/command"))
        assertFalse(Dealer.requestSuccess("hm://other/thing"))
        assertFalse(Dealer.requestSuccess(null))
    }

    @Test
    fun secrets_are_masked_in_the_log() {
        val m = Dealer.mask("""{"access_token":"SECRETVALUE","x":1} Authorization: Bearer TOPSECRET""")
        assertFalse(m.contains("SECRETVALUE"))
        assertFalse(m.contains("TOPSECRET"))
        assertTrue(m.contains("\"x\":1"))
        assertEquals(200, Dealer.mask("a".repeat(500)).length)
    }

    @Test
    fun engine_answers_a_request_frame_and_reads_a_compressed_cluster() = runTest {
        val rig = Rig(backgroundScope)
        rig.start()
        val lines = ArrayList<String>()
        backgroundScope.launch(kotlinx.coroutines.Dispatchers.Unconfined) { rig.engine.events.collect { if (it is EngineEvent.Dealer) lines += it.line } }
        rig.engine.handleDealerFrame("""{"type":"request","key":"k9","message_ident":"hm://connect-state/v1/player/command","payload":{"compressed":"${Base64.encode(json.encodeToByteArray())}"}}""")
        val reply = EngineJson.parseToJsonElement(rig.sockets.socket.sent.last()).jsonObject
        assertEquals("k9", reply["key"]!!.jsonPrimitive.content)
        assertTrue(reply["payload"]!!.jsonObject["success"]!!.jsonPrimitive.boolean)
        rig.engine.handleDealerFrame("""{"type":"message","uri":"hm://connect-state/v1/cluster","headers":{"Transfer-Encoding":"gzip"},"payloads":["${Base64.encode(gzip(json))}"]}""")
        assertEquals("abc", rig.engine.cluster.value?.activeDeviceId)
        assertTrue(lines.any { it.startsWith("request hm://connect-state/v1/player/command answered true") })
    }
}
