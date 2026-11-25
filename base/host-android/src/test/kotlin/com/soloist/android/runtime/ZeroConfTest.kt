package com.soloist.android.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZeroConfTest {
    private class Fake : ServiceRegistrar {
        val events = mutableListOf<String>()
        var fail = false
        override fun register(name: String, port: Int, attributes: Map<String, String>, onResult: (String?) -> Unit): Any {
            events += "reg $name $port ${attributes["CPath"]} ${attributes["VERSION"]} ${attributes["Stack"]}"
            onResult(if (fail) null else name)
            return events.size
        }
        override fun unregister(handle: Any) { events += "unreg $handle" }
    }

    @Test fun parsesLine() {
        assertEquals(ZeroConfLine(41234, "/zc"), parseZeroConfLine("[INFO] ZeroConf service on port 41234 path /zc"))
        assertNull(parseZeroConfLine("api: websocket server listening on 127.0.0.1:5"))
        assertNull(parseZeroConfLine("ZeroConf service on port 99999 path /zc"))
    }

    @Test fun announceIsIdempotentAndReplaces() {
        val f = Fake(); val z = ZeroConf(f) {}
        z.announce("Astral", 1000, "/a"); z.announce("Astral", 1000, "/a")
        assertEquals(listOf("reg Astral 1000 /a 1.0 SP"), f.events)
        z.announce("Astral", 2000, "/a")
        assertEquals(listOf("reg Astral 1000 /a 1.0 SP", "unreg 1", "reg Astral 2000 /a 1.0 SP"), f.events)
    }

    @Test fun refreshAnnouncesTheSameServiceAgainOnlyWhenLive() {
        val f = Fake(); val z = ZeroConf(f) {}
        z.refresh(); assertEquals(emptyList<String>(), f.events)
        z.announce("Astral", 1000, "/a"); z.refresh()
        assertEquals(listOf("reg Astral 1000 /a 1.0 SP", "unreg 1", "reg Astral 1000 /a 1.0 SP"), f.events)
    }

    @Test fun withdrawOnlyWhenLiveAndFailureIsLogged() {
        val f = Fake(); val logs = mutableListOf<String>(); val z = ZeroConf(f) { logs += it }
        z.withdraw(); assertEquals(emptyList<String>(), f.events)
        f.fail = true; z.announce("Astral", 1, "/a"); z.withdraw(); z.withdraw()
        assertEquals(2, f.events.size)
        assertTrue(logs.any { it.contains("failed") })
    }
}
