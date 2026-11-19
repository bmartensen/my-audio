package com.martensening.my_audio.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class OggOpusWriterTest {
    private class Page(val flags: Int, val granule: Long, val sequence: Int, val packets: List<ByteArray>)

    private fun parse(bytes: ByteArray): List<Page> {
        val pages = mutableListOf<Page>()
        var p = 0
        while (p < bytes.size) {
            val b = ByteBuffer.wrap(bytes, p, bytes.size - p).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals("OggS", String(bytes, p, 4))
            val count = bytes[p + 26].toInt() and 0xFF
            val lacing = (0 until count).map { bytes[p + 27 + it].toInt() and 0xFF }
            val end = p + 27 + count + lacing.sum()
            val page = bytes.copyOfRange(p, end)
            val stored = b.getInt(p + 22)
            page.fill(0, 22, 26)
            assertEquals("CRC of page ${pages.size}", stored, OggOpusWriter.crc(page))
            val packets = mutableListOf<ByteArray>()
            var offset = p + 27 + count
            var size = 0
            lacing.forEach {
                size += it
                if (it < 255) { packets += bytes.copyOfRange(offset, offset + size); offset += size; size = 0 }
            }
            pages += Page(bytes[p + 5].toInt(), b.getLong(p + 6), b.getInt(p + 18), packets)
            p = end
        }
        return pages
    }

    /** A CELT 20 ms stereo packet (TOC config 31, one frame) of [size] bytes. */
    private fun packet(size: Int) = ByteArray(size) { if (it == 0) (31 shl 3 or 0x04).toByte() else it.toByte() }

    @Test
    fun `crc matches the Ogg reference value`() {
        // Polynomial 0x04C11DB7, no reflection, zero init and XOR: check value for "123456789".
        assertEquals(0x89A1897F.toInt(), OggOpusWriter.crc("123456789".toByteArray()))
    }

    @Test
    fun `packet durations follow the TOC byte`() {
        assertEquals(960, OggOpusWriter.packetSamples(byteArrayOf((31 shl 3).toByte())))
        assertEquals(1920, OggOpusWriter.packetSamples(byteArrayOf((31 shl 3 or 1).toByte())))
        assertEquals(2880, OggOpusWriter.packetSamples(byteArrayOf((3 shl 3).toByte())))
        assertEquals(3 * 480, OggOpusWriter.packetSamples(byteArrayOf((30 shl 3 or 3).toByte(), 3)))
    }

    @Test
    fun `stream follows RFC 7845 page rules`() {
        val out = ByteArrayOutputStream()
        val preSkip = 312
        val totalSamples = 120L * 960 - 500
        val writer = OggOpusWriter(out, channels = 2, preSkip = preSkip, totalSamples48k = totalSamples, serial = 7)
        val written = (0 until 120).map { packet(300 + it) }
        written.forEach(writer::writePacket)
        writer.finish()
        val pages = parse(out.toByteArray())

        val head = pages[0].packets.single()
        assertEquals(0x02, pages[0].flags)
        assertEquals("OpusHead", String(head, 0, 8))
        assertEquals(2, head[9].toInt())
        assertEquals(preSkip, ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN).getShort(10).toInt())
        assertEquals("OpusTags", String(pages[1].packets.single(), 0, 8))
        assertEquals(0L, pages[1].granule)
        assertEquals((0 until pages.size).toList(), pages.map { it.sequence })

        val audio = pages.drop(2)
        // The muxer bug: the first audio page must count the samples of the packets it completes.
        assertEquals(preSkip + audio[0].packets.size * 960L, audio[0].granule)
        audio.zipWithNext().forEach { (a, b) -> assertTrue(b.granule >= a.granule) }
        audio.dropLast(1).forEach { assertEquals(0, it.flags) }
        assertEquals(0x04, audio.last().flags)
        assertEquals(preSkip + totalSamples, audio.last().granule)
        val packets = audio.flatMap { it.packets }
        assertEquals(written.size, packets.size)
        written.zip(packets).forEach { (a, b) -> assertArrayEquals(a, b) }
    }
}
