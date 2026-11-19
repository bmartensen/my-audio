package com.martensening.my_audio.audio

import com.martensening.my_audio.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

class StereoImportTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun oppositePhasesSurviveResamplingAndWaveform() {
        val input = FloatArray(44100 * 2) { i ->
            (0.5 * sin(2 * PI * 440 * (i / 2) / 44100) * if (i % 2 == 0) 1 else -1).toFloat()
        }
        fun write(name: String, chunk: Int): java.io.File = folder.newFile(name).also { file ->
            ImportedAudioWriter(file, 44100, 2, 48000).use { writer ->
                for (start in 0 until 44100 step chunk) {
                    val end = minOf(start + chunk, 44100)
                    writer.push(input.copyOfRange(start * 2, end * 2))
                }
                writer.finish()
                assertEquals(48000L, writer.frames)
            }
        }
        val file = write("stereo.wav", 37)
        assertArrayEquals(file.readBytes(), write("whole.wav", 44100).readBytes())
        val info = PcmWavInfo.read(file)
        assertEquals(2, info.channels)
        assertEquals(48000L, info.frames)
        assertEquals(48000, info.sampleRate)
        val bytes = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        for (frame in 0 until 48000) {
            val left = bytes.getShort(44 + frame * 4).toInt()
            val right = bytes.getShort(46 + frame * 4).toInt()
            assertTrue(kotlin.math.abs(left + right) <= 1)
        }
        val peaks = Waveform.peaks(file, 100)
        assertTrue(peaks.all { it > 0.49f && it < 0.51f })
    }

    @Test fun isolatedRightChannelAndMonoKeepFrameCount() {
        for (channels in 1..2) {
            val file = folder.newFile("$channels.wav")
            ImportedAudioWriter(file, 48000, channels, 48000).use { writer ->
                val input = FloatArray(100 * channels) { if (channels == 2 && it % 2 == 0) 0f else 0.25f }
                writer.push(input)
                writer.finish()
                assertEquals(100L, writer.frames)
            }
            assertEquals(100L, PcmWavInfo.read(file).frames)
            assertTrue(Waveform.peaks(file, 10).all { it > 0.249f })
            val bytes = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            if (channels == 2) for (frame in 0 until 100) assertEquals(0, bytes.getShort(44 + frame * 4).toInt())
        }
    }

    @Test fun stereoBackupKeepsChannelsAndOldProjectsStayVersionOne() {
        val repo = ProjectRepository(folder.newFolder("repository"))
        val mono = repo.create("Mono", 48000, 10)
        assertEquals(1, ProjectJson.formatVersion(ProjectJson.encode(mono)))
        val file = folder.newFile("stereo.wav")
        PcmWavWriter(file, 48000, 2).use { it.write(0.5f); it.write(-0.5f) }
        val bytes = file.readBytes()
        val project = repo.createFromTakes("Stereo", 48000,
            listOf(ProjectRepository.ImportedTake("One card", file, 1, 2)), 20)
        assertEquals(2, ProjectJson.formatVersion(ProjectJson.encode(project)))
        assertEquals(2, repo.load(project.id)!!.tracks.single().channels)
        val zip = folder.newFile("backup.zip")
        assertTrue(repo.exportBackup(project.id, zip))
        val restored = zip.inputStream().use { repo.importBackup(it, 30) }!!
        val track = restored.tracks.single()
        assertEquals(2, track.channels)
        assertEquals(1L, track.lengthFrames)
        assertArrayEquals(bytes, repo.trackFile(restored.id, track).readBytes())
    }

    @Test fun newTakesDefaultOffButLegacyCleanupAndReferencesSurvive() {
        val effects = TrackEffects()
        assertFalse(effects.removeBleed || effects.removeMetronome)
        val track = Track(2, "Take", "2.wav", 0, 48000, createdAtMillis = 2, referenceFileName = "2.ref.wav")
        val project = Project(1, "Demo", 1, 48000, tracks = listOf(track))
        val decoded = ProjectJson.decode(ProjectJson.encode(project)).tracks.single()
        assertEquals(effects, decoded.effects)
        assertEquals("2.ref.wav", decoded.referenceFileName)
        assertEquals(1, decoded.channels)
        val legacy = """{"version":1,"id":1,"name":"Old","createdAtMillis":1,"sampleRate":48000,
          "tracks":[{"id":2,"name":"Take","fileName":"2.wav","offsetFrames":0,"lengthFrames":1,
          "createdAtMillis":2,"effects":{}}]}"""
        val old = ProjectJson.decode(legacy).tracks.single()
        assertTrue(old.effects.removeBleed && old.effects.removeMetronome)
    }
}
