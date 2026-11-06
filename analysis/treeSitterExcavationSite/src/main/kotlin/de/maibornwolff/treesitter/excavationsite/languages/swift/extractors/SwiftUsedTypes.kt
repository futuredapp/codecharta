package de.maibornwolff.treesitter.excavationsite.languages.swift.extractors

import de.maibornwolff.treesitter.excavationsite.shared.domain.SyntaxNode
import de.maibornwolff.treesitter.excavationsite.shared.domain.TypeUsage
import de.maibornwolff.treesitter.excavationsite.shared.domain.UsedType
import de.maibornwolff.treesitter.excavationsite.shared.domain.ancestors

internal object SwiftUsedTypes {
    fun extract(declaration: SyntaxNode, ownName: String): Set<UsedType> {
        val uses = linkedSetOf<UsedType>()
        // The traversal stack never outlives the call that owns these syntax nodes.
        val visits = ArrayDeque<Triple<SyntaxNode, Set<String>, TypeUsage>>()
        visits.addLast(Triple(declaration, declaration.ancestors().flatMap { genericNames(it).asSequence() }.toSet(), TypeUsage.USAGE))
        while (visits.isNotEmpty()) {
            val (node, boundNames, enclosingUsage) = visits.removeLast()
            if (node != declaration && node.type in SwiftDeclarations.TYPE_DECLARATIONS) continue
            val children = node.namedChildren().toList()
            val scopeNames = boundNames + genericNames(node)
            val usage = if (node.type == "inheritance_specifier") TypeUsage.INHERITANCE else enclosingUsage

            fun add(name: String?) {
                if (!name.isNullOrEmpty() && name != ownName && name.substringBefore('.') !in scopeNames) {
                    uses.add(UsedType(name, usage = usage))
                }
            }
            when (node.type) {
                "user_type" -> add(nameParts(node).joinToString("."))
                "call_expression" -> add(children.firstOrNull()?.let(::referenceName))
                "navigation_expression" -> {
                    val name = referenceName(node)
                    add(name?.substringBeforeLast('.', ""))
                }
            }
            children.asReversed().forEach { visits.addLast(Triple(it, scopeNames, usage)) }
        }
        return uses
    }

    private fun genericNames(node: SyntaxNode): List<String> = node
        .namedChildren()
        .filter { it.type == "type_parameters" }
        .flatMap { it.namedChildren() }
        .filter { it.type == "type_parameter" }
        .mapNotNull { it.namedChildren().firstOrNull { child -> child.type == "type_identifier" }?.text?.trim('`') }
        .toList()

    fun nameParts(node: SyntaxNode): List<String> = node
        .namedChildren()
        .filter { it.type == "type_identifier" }
        .map { it.text.trim('`') }
        .toList()
        .let { if (it.size > 1 && (it.last() == "Type" || it.last() == "Protocol")) it.dropLast(1) else it }

    private fun referenceName(node: SyntaxNode): String? {
        val parts = mutableListOf<String>()
        val pending = ArrayDeque<SyntaxNode>()
        pending.addLast(node)
        while (pending.isNotEmpty()) {
            val current = pending.removeLast()
            when (current.type) {
                "simple_identifier", "type_identifier" -> parts.add(current.text.trim('`'))
                "user_type" -> parts.addAll(nameParts(current))
                "navigation_expression", "navigation_suffix", "identifier" ->
                    current.namedChildren().toList().asReversed().forEach(pending::addLast)
                else -> return null
            }
        }
        return parts.joinToString(".")
    }
}
