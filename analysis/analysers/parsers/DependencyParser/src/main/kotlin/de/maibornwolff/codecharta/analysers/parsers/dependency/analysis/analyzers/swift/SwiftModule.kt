package de.maibornwolff.codecharta.analysers.parsers.dependency.analysis.analyzers.swift

import de.maibornwolff.codecharta.analysers.parsers.dependency.analysis.model.FileInfo
import java.io.File

/** Conventional SwiftPM targets are separate modules; other source trees are one module per analysis root. */
internal object SwiftModule {
    fun of(fileInfo: FileInfo): String {
        val root = fileInfo.analysisRoot?.absoluteFile?.normalize() ?: return DEFAULT_MODULE
        val source = root.resolve(fileInfo.physicalPath).absoluteFile.normalize()
        var directory = source.parentFile
        while (directory != null && directory.toPath().startsWith(root.toPath())) {
            if (File(directory, "Package.swift").isFile) {
                val parts = source.relativeTo(directory).invariantSeparatorsPath.split('/')
                if (parts.size >= 3 && parts.first() in SOURCE_DIRECTORIES) return parts[1]
                break
            }
            directory = directory.parentFile
        }
        return root.name.ifEmpty { DEFAULT_MODULE }
    }

    private const val DEFAULT_MODULE = "<module>"
    private val SOURCE_DIRECTORIES = setOf("Sources", "Tests")
}
