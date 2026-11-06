package de.maibornwolff.treesitter.excavationsite.languages.swift

import de.maibornwolff.treesitter.excavationsite.languages.swift.extractors.SwiftDeclarations
import de.maibornwolff.treesitter.excavationsite.shared.domain.ImportDeclaration
import de.maibornwolff.treesitter.excavationsite.shared.domain.LanguageDependencyMapping
import de.maibornwolff.treesitter.excavationsite.shared.domain.SyntaxNode

internal object SwiftDependencyMapping {
    val dependencyMapping = LanguageDependencyMapping(
        extractPackagePath = { emptyList() },
        extractImports = ::imports,
        extractDeclarations = SwiftDeclarations::extract
    )

    private fun imports(root: SyntaxNode): List<ImportDeclaration> = root
        .namedChildren()
        .filter { it.type == "import_declaration" }
        .mapNotNull { declaration ->
            val identifier = declaration.namedChildren().firstOrNull { it.type == "identifier" } ?: return@mapNotNull null
            val parts = identifier.namedChildren().map { it.text.trim('`') }.toList()
            ImportDeclaration(parts, isWildcard = declaration.children().none { it.type in SELECTIVE_IMPORT_KINDS })
        }.toList()

    private val SELECTIVE_IMPORT_KINDS = setOf("typealias", "struct", "class", "enum", "protocol", "let", "var", "func")
}
