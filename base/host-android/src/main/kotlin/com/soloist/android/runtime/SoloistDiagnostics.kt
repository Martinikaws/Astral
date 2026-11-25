package com.soloist.android.runtime

/**
 * Local ports from a /proc/net/{tcp,tcp6,udp,udp6} dump. TCP keeps only LISTEN rows (state 0A).
 * With [inodes] only sockets owned by those inodes count, since /proc/net lists the whole namespace.
 */
fun boundPorts(procNet: String, tcp: Boolean, inodes: Set<String>? = null): List<Int> =
    procNet.lineSequence().drop(1).mapNotNull { row ->
        val c = row.trim().split(Regex("""\s+"""))
        if (c.size < 10) return@mapNotNull null
        if (tcp && c[3] != "0A") return@mapNotNull null
        if (inodes != null && c[9] !in inodes) return@mapNotNull null
        c[1].substringAfterLast(':').toIntOrNull(16)
    }.distinct().sorted().toList()
