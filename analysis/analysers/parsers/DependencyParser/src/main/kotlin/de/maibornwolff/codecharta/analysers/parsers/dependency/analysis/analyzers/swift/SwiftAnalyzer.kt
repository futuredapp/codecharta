package de.maibornwolff.codecharta.analysers.parsers.dependency.analysis.analyzers.swift

import de.maibornwolff.codecharta.analysers.parsers.dependency.analysis.analyzers.BaseLanguageAnalyzer
import de.maibornwolff.codecharta.analysers.parsers.dependency.analysis.model.Dependency
import de.maibornwolff.codecharta.analysers.parsers.dependency.analysis.model.FileInfo
import de.maibornwolff.codecharta.analysers.parsers.dependency.analysis.model.Path
import de.maibornwolff.codecharta.analysers.parsers.dependency.input.SupportedLanguage
import de.maibornwolff.treesitter.excavationsite.api.Declaration
import de.maibornwolff.treesitter.excavationsite.api.DeclarationType
import java.io.File

class SwiftAnalyzer(fileInfo: FileInfo) : BaseLanguageAnalyzer(fileInfo) {
    override val language = SupportedLanguage.SWIFT
    private val module by lazy { SwiftModule.of(fileInfo) }

    override fun buildPathWithName(packagePath: List<String>, declaration: Declaration): Path {
        val name = if (declaration.type == DeclarationType.UNKNOWN) {
            File(fileInfo.physicalPath.replace('\\', '/')).nameWithoutExtension + EXTENSION_SUFFIX
        } else {
            declaration.name
        }
        return Path(listOf(module) + declaration.parentPath + name)
    }

    override fun extraDependencies(declaration: Declaration): Set<Dependency> = setOf(Dependency(Path(module), isWildcard = true))

    companion object {
        private const val EXTENSION_SUFFIX = "_extension"
    }
}
