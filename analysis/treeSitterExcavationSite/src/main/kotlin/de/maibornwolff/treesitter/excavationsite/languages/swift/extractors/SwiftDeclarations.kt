package de.maibornwolff.treesitter.excavationsite.languages.swift.extractors

import de.maibornwolff.treesitter.excavationsite.shared.domain.Declaration
import de.maibornwolff.treesitter.excavationsite.shared.domain.DeclarationType
import de.maibornwolff.treesitter.excavationsite.shared.domain.SyntaxNode

internal object SwiftDeclarations {
    const val EXTENSION_NAME = "<extension>"
    val TYPE_DECLARATIONS = setOf("class_declaration", "struct_declaration", "protocol_declaration", "typealias_declaration")

    fun extract(root: SyntaxNode): List<Declaration> {
        val declarations = mutableListOf<Declaration>()
        // Syntax nodes stay local to this extractor call; only declaration values escape it.
        val scopes = ArrayDeque<Triple<SyntaxNode, List<String>, Boolean>>()
        scopes.addLast(Triple(root, emptyList(), false))
        while (scopes.isNotEmpty()) {
            val (scope, parents, fileScoped) = scopes.removeLast()
            val children = scope.namedChildren().toList()
            val nestedScopes = mutableListOf<Triple<SyntaxNode, List<String>, Boolean>>()
            for (node in children) {
                val isPrivate = fileScoped || isFileScoped(node)
                if (node.type in TYPE_DECLARATIONS) {
                    val extension = node.children().any { it.type == "extension" }
                    val name = if (extension) {
                        EXTENSION_NAME
                    } else {
                        node
                            .namedChildren()
                            .firstOrNull { it.type == "type_identifier" }
                            ?.text
                            ?.trim('`') ?: continue
                    }
                    val parentPath = if (extension) {
                        parents + node.namedChildren().firstOrNull { it.type == "user_type" }?.let(SwiftUsedTypes::nameParts).orEmpty()
                    } else {
                        parents
                    }
                    declarations.add(
                        Declaration(name, kindOf(node, extension), SwiftUsedTypes.extract(node, name), parentPath, isFileScoped = isPrivate)
                    )
                    node.namedChildren().filter { it.type in BODIES }.forEach { body ->
                        nestedScopes.add(Triple(body, if (extension) parentPath else parents + name, isPrivate))
                    }
                } else if (parents.isEmpty() && node.type == "function_declaration") {
                    val name = node.namedChildren().firstOrNull { it.type == "simple_identifier" }?.text?.trim('`') ?: continue
                    declarations.add(
                        Declaration(name, DeclarationType.FUNCTION, SwiftUsedTypes.extract(node, name), isFileScoped = isPrivate)
                    )
                } else if (parents.isEmpty() && node.type == "property_declaration") {
                    node.namedChildren().filter { it.type == "pattern" }.forEach { pattern ->
                        val name = pattern.namedChildren().singleOrNull { it.type == "simple_identifier" }?.text?.trim('`')
                        if (name != null) {
                            declarations.add(
                                Declaration(name, DeclarationType.VARIABLE, SwiftUsedTypes.extract(node, name), isFileScoped = isPrivate)
                            )
                        }
                    }
                }
            }
            nestedScopes.asReversed().forEach(scopes::addLast)
        }
        return declarations
    }

    private fun kindOf(node: SyntaxNode, extension: Boolean): DeclarationType {
        if (extension) return DeclarationType.UNKNOWN
        if (node.type == "protocol_declaration") return DeclarationType.INTERFACE
        if (node.children().any { it.type == "struct" }) return DeclarationType.RECORD
        if (node.children().any { it.type == "enum" }) return DeclarationType.ENUM
        return DeclarationType.CLASS
    }

    private fun isFileScoped(node: SyntaxNode): Boolean = node
        .namedChildren()
        .filter { it.type == "modifiers" }
        .flatMap { it.namedChildren() }
        .any { it.text == "private" || it.text == "fileprivate" }

    private val BODIES = setOf("class_body", "enum_class_body", "protocol_body")
}
