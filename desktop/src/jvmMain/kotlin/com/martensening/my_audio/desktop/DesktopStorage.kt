package com.martensening.my_audio.desktop

import com.martensening.my_audio.data.ProjectRepository
import java.io.File
import javax.swing.filechooser.FileSystemView

/**
 * Projects live visibly in the documents folder ("Dokumente\My Audio" on Windows), in the same
 * layout as on Android: `projects/<id>/project.json` with the takes under `tracks/`.
 */
object DesktopStorage {
    val root: File by lazy {
        // FileSystemView knows the real, possibly redirected Windows documents folder.
        val documents = FileSystemView.getFileSystemView().defaultDirectory
            ?.takeIf { it.isDirectory } ?: File(System.getProperty("user.home"))
        File(documents, "My Audio").apply { mkdirs() }
    }

    fun repository(): ProjectRepository = ProjectRepository.forRoot(root)
}
