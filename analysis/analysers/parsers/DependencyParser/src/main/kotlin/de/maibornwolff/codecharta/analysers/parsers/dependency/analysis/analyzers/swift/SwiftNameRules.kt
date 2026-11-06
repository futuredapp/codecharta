package de.maibornwolff.codecharta.analysers.parsers.dependency.analysis.analyzers.swift

import de.maibornwolff.codecharta.analysers.parsers.dependency.analysis.model.DeclarationNames
import de.maibornwolff.codecharta.analysers.parsers.dependency.analysis.model.NameRules
import de.maibornwolff.codecharta.analysers.parsers.dependency.analysis.model.Node
import de.maibornwolff.codecharta.analysers.parsers.dependency.analysis.model.Path
import de.maibornwolff.codecharta.analysers.parsers.dependency.analysis.model.ProjectNames

/** Lexical types, the current module, then imports; an ambiguous or unimported name draws no edge. */
internal object SwiftNameRules : NameRules {
    override fun namesOf(node: Node, project: ProjectNames): DeclarationNames = DeclarationNames { name ->
        val parts = name.split('.')
        val candidates = project.projectDictionary[parts.last()]
            .orEmpty()
            .filter { project.fileScopedPaths.isVisible(it, node.physicalPath) }
            .distinct()
        val scope = node.pathWithName.parts
        val lexical = (scope.size downTo 1).firstNotNullOfOrNull { size ->
            candidates.singleOrNull { it.parts == scope.take(size) + parts }
        }
        lexical ?: imported(node, parts, candidates) ?: Path.unknown(name)
    }

    private fun imported(node: Node, name: List<String>, candidates: List<Path>): Path? {
        val allowed = node.dependencies.flatMap { dependency ->
            val imported = dependency.path.parts
            if (dependency.isWildcard) {
                listOf(imported + name) + if (name.first() == imported.firstOrNull()) listOf(name) else emptyList()
            } else if (name.first() == imported.lastOrNull()) {
                listOf(imported + name.drop(1))
            } else if (name.take(imported.size) == imported) {
                listOf(name)
            } else {
                emptyList()
            }
        }
        return candidates.filter { it.parts in allowed }.singleOrNull()
    }
}
