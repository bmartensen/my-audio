package com.martensening.my_audio.audio

import java.io.OutputStream
import kotlin.random.Random

/**
 * Writes raw Opus packets as an Ogg/Opus stream per RFC 7845. Android's `MediaMuxer` Ogg output
 * sets each page's granule position to the start of its packet instead of the end and omits the
 * end-of-stream flag; strict players such as WhatsApp reject such files as broken.
 *
 * Header pages carry OpusHead and OpusTags alone; audio pages collect packets for about one second.
 * Granule positions count 48 kHz samples including [preSkip]; the last page ends at
 * [preSkip] + [totalSamples48k] so decoders trim the encoder's padding.
 */
class OggOpusWriter(
    private val out: OutputStream,
    private val channels: Int,
    private val preSkip: Int,
    private val totalSamples48k: Long,
    private val serial: Int = Random.nextInt(),
    vendor: String = "My Audio",
) {
    private var sequence = 0
    private var granule = preSkip.toLong()
    private var lastPageGranule = 0L
    private val packets = mutableListOf<ByteArray>()
    private var pageSamples = 0
    private var pageSegments = 0

    init {
        require(channels in 1..2) { "Opus mapping family 0 supports one or two channels" }
        val head = ByteArray(19).also {
            "OpusHead".toByteArray().copyInto(it)
            it[8] = 1
            it[9] = channels.toByte()
            putLe(it, 10, preSkip.toLong(), 2)
            putLe(it, 12, 48_000, 4)
            // Output gain 0 and mapping family 0 stay zero.
        }
        writePage(listOf(head), granule = 0, flags = FLAG_BOS)
        val vendorBytes = vendor.toByteArray()
        val tags = ByteArray(8 + 4 + vendorBytes.size + 4).also {
            "OpusTags".toByteArray().copyInto(it)
            putLe(it, 8, vendorBytes.size.toLong(), 4)
            vendorBytes.copyInto(it, 12)
        }
        writePage(listOf(tags), granule = 0, flags = 0)
    }

    fun writePacket(packet: ByteArray) {
        val segments = packet.size / 255 + 1
        require(segments <= 255) { "Opus packet too large for one Ogg page" }
        if (pageSegments + segments > 255) flushPage(last = false)
        packets += packet
        pageSegments += segments
        val samples = packetSamples(packet)
        granule += samples
        pageSamples += samples
        if (pageSamples >= 48_000) flushPage(last = false)
    }

    /** Writes the remaining packets as the end-of-stream page. */
    fun finish() {
        flushPage(last = true)
        out.flush()
    }

    private fun flushPage(last: Boolean) {
        if (packets.isEmpty() && !last) return
        val end = if (last) (preSkip + totalSamples48k).coerceIn(lastPageGranule, granule) else granule
        writePage(packets.toList(), end, if (last) FLAG_EOS else 0)
        lastPageGranule = end
        packets.clear()
        pageSamples = 0
        pageSegments = 0
    }

    private fun writePage(content: List<ByteArray>, granule: Long, flags: Int) {
        val lacing = content.flatMap { packet -> List(packet.size / 255) { 255 } + packet.size % 255 }
        val bodySize = content.sumOf { it.size }
        val page = ByteArray(27 + lacing.size + bodySize)
        "OggS".toByteArray().copyInto(page)
        page[5] = flags.toByte()
        putLe(page, 6, granule, 8)
        putLe(page, 14, serial.toLong(), 4)
        putLe(page, 18, sequence++.toLong(), 4)
        page[26] = lacing.size.toByte()
        lacing.forEachIndexed { i, value -> page[27 + i] = value.toByte() }
        var offset = 27 + lacing.size
        content.forEach { it.copyInto(page, offset); offset += it.size }
        putLe(page, 22, crc(page).toLong(), 4)
        out.write(page)
    }

    companion object {
        private const val FLAG_BOS = 0x02
        private const val FLAG_EOS = 0x04

        private val CRC_TABLE = IntArray(256) { index ->
            var r = index shl 24
            repeat(8) { r = if (r and 0x80000000.toInt() != 0) (r shl 1) xor 0x04C11DB7 else r shl 1 }
            r
        }

        /** Ogg's CRC-32: polynomial 0x04C11DB7, not reflected, initial value and final XOR zero. */
        fun crc(data: ByteArray): Int {
            var c = 0
            for (b in data) c = (c shl 8) xor CRC_TABLE[((c ushr 24) xor (b.toInt() and 0xFF)) and 0xFF]
            return c
        }

        /** Duration of an Opus packet in 48 kHz samples from its TOC byte (RFC 6716, section 3.1). */
        fun packetSamples(packet: ByteArray): Int {
            if (packet.isEmpty()) return 0
            val toc = packet[0].toInt() and 0xFF
            val config = toc shr 3
            val frameSize = when {
                config < 12 -> intArrayOf(480, 960, 1920, 2880)[config and 3] // SILK 10/20/40/60 ms
                config < 16 -> intArrayOf(480, 960)[config and 1] // Hybrid 10/20 ms
                else -> intArrayOf(120, 240, 480, 960)[config and 3] // CELT 2.5/5/10/20 ms
            }
            val frames = when (toc and 3) {
                0 -> 1
                1, 2 -> 2
                else -> if (packet.size > 1) packet[1].toInt() and 0x3F else 0
            }
            return frames * frameSize
        }

        private fun putLe(target: ByteArray, offset: Int, value: Long, bytes: Int) {
            for (i in 0 until bytes) target[offset + i] = (value ushr (8 * i)).toByte()
        }
    }
}
