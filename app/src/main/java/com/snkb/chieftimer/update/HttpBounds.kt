package com.snkb.chieftimer.update

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Reads at most [maxBytes] from this stream; returns null early if the stream exceeds the cap. */
internal fun InputStream.readBounded(maxBytes: Int): ByteArray? {
    val out = ByteArrayOutputStream()
    val buf = ByteArray(16 * 1024)
    var total = 0
    while (true) {
        val n = read(buf)
        if (n < 0) break
        total += n
        if (total > maxBytes) return null
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}
