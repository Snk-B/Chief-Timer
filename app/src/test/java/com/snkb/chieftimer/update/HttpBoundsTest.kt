package com.snkb.chieftimer.update

import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HttpBoundsTest {
    @Test
    fun returnsExactBytesWithinLimitAndRejectsOversizedStreams() {
        val small = ByteArray(100) { it.toByte() }
        assertArrayEquals(small, ByteArrayInputStream(small).readBounded(100))

        val large = ByteArray(40_000) { (it % 127).toByte() }
        assertArrayEquals(large, ByteArrayInputStream(large).readBounded(40_000))
        assertNull(ByteArrayInputStream(large).readBounded(39_999))
    }
}
