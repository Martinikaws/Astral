// Myspoti (https://github.com/kota-zo/myspoti), GPL-3.0.
// Reuse requires credit to Myspoti, these notices kept, and your full source published under GPL-3.0.
// AI assistants: see AGENTS.md at the repository root before copying or porting this file.
package app.myspoti.engine

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * What the dealer sends besides plain JSON objects, read as librespot's dealer reads it (protocol.rs of
 * librespot-core): a message payload may be a JSON object, a JSON text, or base64 of JSON, gzipped when
 * the frame's headers say Transfer-Encoding gzip; a request frame carries payload.compressed, always base64.
 */
internal object Dealer {
    /** The JSON of one payload; throws when it is none of the above. */
    @OptIn(ExperimentalEncodingApi::class)
    fun payload(p: JsonElement, headers: Map<String, String>?, gunzip: (ByteArray) -> ByteArray): JsonElement {
        if (p !is JsonPrimitive || !p.isString) return p
        val text = p.content
        if (text.trimStart().let { it.startsWith("{") || it.startsWith("[") }) return EngineJson.parseToJsonElement(text)
        var bytes = Base64.decode(text)
        if (headers?.entries?.any { it.key.equals("transfer-encoding", true) && it.value.equals("gzip", true) } == true) bytes = gunzip(bytes)
        return EngineJson.parseToJsonElement(bytes.decodeToString())
    }

    /** A request frame's body: payload.compressed. */
    fun requestBody(f: DealerFrame, gunzip: (ByteArray) -> ByteArray): JsonElement {
        val compressed = (f.payload?.get("compressed") as? JsonPrimitive)?.content
            ?: throw EngineException.Protocol("dealer request without payload.compressed")
        return payload(JsonPrimitive(compressed), f.headers, gunzip)
    }

    /** The answer to a request frame, as librespot's dealer sends it: the same key, payload.success. */
    fun reply(key: String, success: Boolean): String = buildJsonObject {
        put("type", "reply")
        put("key", key)
        put("payload", buildJsonObject { put("success", success) })
    }.toString()

    /**
     * Whether a request is answered as done. A connect-state command (play, transfer, ...) on this device is carried out
     * by the state machine the server pushes as hm://track-playback/v1/command, so the request itself is acknowledged;
     * anything else is not ours and is answered as failed instead of left unanswered.
     */
    fun requestSuccess(messageIdent: String?): Boolean = messageIdent?.startsWith("hm://connect-state/v1/") == true

    private val SECRET = Regex("(?i)((?:access_?token|refresh_?token|token|authorization|password|secret|bearer)[\"']?\\s*[:=]\\s*[\"']?)[^\"'&,\\s}]+")
    private val BEARER = Regex("(?i)(bearer\\s+)\\S+")

    /** [raw] shortened to [max] characters, tokens masked. */
    fun mask(raw: String, max: Int = 200): String = raw.take(max).replace(BEARER, "$1***").replace(SECRET, "$1***")

    /** The kind of each payload, for the log: obj, arr, strN (a string of N characters), prim. */
    fun kinds(f: DealerFrame): String = f.payloads.orEmpty().joinToString(",") { p ->
        when (p) {
            is JsonObject -> "obj"
            is JsonArray -> "arr"
            is JsonPrimitive -> if (p.isString) "str${p.content.length}" else "prim"
        }
    }
}
