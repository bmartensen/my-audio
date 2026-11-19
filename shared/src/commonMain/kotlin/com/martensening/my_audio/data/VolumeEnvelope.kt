package com.martensening.my_audio.data

/** Linear gain at a project frame (latency/count-in already compensated); empty means unity. */
data class VolumePoint(val frame: Long, val gain: Float)

object VolumeEnvelope {
    fun sanitize(points: List<VolumePoint>, endFrame: Long = Long.MAX_VALUE): List<VolumePoint> =
        points.filter { it.frame in 0..endFrame && it.gain.isFinite() }
            .map { it.copy(gain = it.gain.coerceIn(0f, 1f)) }
            .distinctBy { it.frame }.sortedBy { it.frame }
}
