package app.myspoti.spotify

import app.myspoti.spotify.gql.GqlException
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class CanvasResponseTest {
    @Test fun cachesOnlyAnExplicitMissOrAValidClip() {
        assertNull(parseCanvasResponse(Json.parseToJsonElement("""{"data":{"trackUnion":{"canvas":null}}}""")))
        val clip = parseCanvasResponse(Json.parseToJsonElement(
            """{"data":{"trackUnion":{"canvas":{"url":"https://example.com/clip.mp4","type":"VIDEO"}}}}""",
        ))
        assertEquals("https://example.com/clip.mp4", clip?.videoUrl)
    }

    @Test fun rejectsPartialErrorsEvenWhenTheCanvasFieldIsNull() {
        assertThrows(GqlException::class.java) {
            parseCanvasResponse(Json.parseToJsonElement(
                """{"data":{"trackUnion":{"canvas":null}},"errors":[{"message":"timeout"}]}""",
            ))
        }
    }

    @Test fun rejectsIncompleteResponses() {
        for (response in listOf("""{"data":{}}""", """{"data":{"trackUnion":{}}}""",
            """{"data":{"trackUnion":{"canvas":{"url":""}}}}""")) {
            assertThrows(GqlException::class.java) { parseCanvasResponse(Json.parseToJsonElement(response)) }
        }
    }
}
