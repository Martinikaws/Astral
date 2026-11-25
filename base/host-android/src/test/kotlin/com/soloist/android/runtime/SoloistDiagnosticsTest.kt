package com.soloist.android.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class SoloistDiagnosticsTest {
    private val hdr = "  sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode\n"
    private val tcp = hdr +
        "   0: 0100007F:A1B2 00000000:0000 0A 00000000:00000000 00:00000000 00000000 10123 0 555 1 0\n" +
        "   1: 0100007F:1F90 0100007F:C000 01 00000000:00000000 00:00000000 00000000 10123 0 556 1 0\n"
    private val udp = hdr +
        "   2: 00000000:14E9 00000000:0000 07 00000000:00000000 00:00000000 00000000 10123 0 777 2 0\n"

    @Test fun tcpKeepsOnlyListening() = assertEquals(listOf(0xA1B2), boundPorts(tcp, tcp = true))
    @Test fun udpKeepsAll() = assertEquals(listOf(5353), boundPorts(udp, tcp = false))
    @Test fun inodeFilter() {
        assertEquals(emptyList<Int>(), boundPorts(udp, tcp = false, inodes = setOf("1")))
        assertEquals(listOf(5353), boundPorts(udp, tcp = false, inodes = setOf("777")))
    }
}
