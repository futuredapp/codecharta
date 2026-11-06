package de.maibornwolff.treesitter.excavationsite.languages.swift

import de.maibornwolff.treesitter.excavationsite.api.DeclarationType
import de.maibornwolff.treesitter.excavationsite.api.Language
import de.maibornwolff.treesitter.excavationsite.api.TreeSitterDependencies
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SwiftDependencyTest {
    @Test
    fun `extracts Swift types including actors and lowercase names`() {
        val result = analyze(
            """
            class Controller {}
            struct Model {}
            enum State { case ready }
            protocol Service {}
            actor Worker {}
            struct lowercase {}
            typealias Models = [Model]
            """
        )
        assertThat(result.declarations.associate { it.name to it.type }).containsExactlyInAnyOrderEntriesOf(
            mapOf(
                "Controller" to DeclarationType.CLASS,
                "Model" to DeclarationType.RECORD,
                "State" to DeclarationType.ENUM,
                "Service" to DeclarationType.INTERFACE,
                "Worker" to DeclarationType.CLASS,
                "lowercase" to DeclarationType.RECORD,
                "Models" to DeclarationType.CLASS
            )
        )
        assertThat(result.declarations.last().usedTypes.map { it.name }).containsExactly("Model")
    }

    @Test
    fun `extracts whole-module and selective imports`() {
        val imports = analyze("import Foundation\nimport struct Core.Record\n@testable import Models").imports
        assertThat(imports.map { it.path }).containsExactly(listOf("Foundation"), listOf("Core", "Record"), listOf("Models"))
        assertThat(imports.map { it.isWildcard }).containsExactly(true, false, true)
    }

    @Test
    fun `extracts signatures generic constraints constructors and static receivers`() {
        val result = analyze(
            """
            struct Screen<T: Protocol> : Rendering {
                let model: Box<Model>?
                func make<U: Rule>(_ input: Input) -> Output {
                    let output = Output()
                    Factory.build(input)
                    return output
                }
            }
            """
        )
        assertThat(result.declarations.single().usedTypes.map { it.name })
            .containsExactlyInAnyOrder("Protocol", "Rendering", "Box", "Model", "Rule", "Input", "Output", "Factory", "Factory.build")
    }

    @Test
    fun `keeps nested declarations scoped and their uses separate from the host`() {
        val declarations = analyze(
            """
            class Outer {
                struct Inner { let value: InnerDependency }
                let value: OuterDependency
            }
            """
        ).declarations
        val outer = declarations.single { it.name == "Outer" }
        val inner = declarations.single { it.name == "Inner" }
        assertThat(inner.parentPath).containsExactly("Outer")
        assertThat(inner.usedTypes.map { it.name }).containsExactly("InnerDependency")
        assertThat(outer.usedTypes.map { it.name }).containsExactly("OuterDependency")
    }

    @Test
    fun `retains extensions separately from the declaration and keeps their dependencies`() {
        val declarations = analyze(
            """
            struct Model {}
            extension Model { var service: Service { Service() } }
            extension Model { var endpoint: Endpoint { Endpoint() } }
            """
        ).declarations
        assertThat(declarations.filter { it.type == DeclarationType.RECORD }).hasSize(1)
        val extensions = declarations.filter { it.type == DeclarationType.UNKNOWN }
        assertThat(extensions).hasSize(2)
        assertThat(extensions.flatMap { it.usedTypes }.map { it.name }).containsExactlyInAnyOrder("Model", "Service", "Model", "Endpoint")
        assertThat(extensions.map { it.parentPath }).containsExactly(listOf("Model"), listOf("Model"))
    }

    @Test
    fun `does not remove code after URL strings or count comments and literal text`() {
        val declarations = analyze(
            """
            struct First { let url = "https://example.com" }
            /* outer /* nested FakeType */ comment */
            struct Second { let model: Model; let text = "PhantomType {}" }
            """
        ).declarations
        assertThat(declarations.map { it.name }).containsExactly("First", "Second")
        assertThat(declarations.flatMap { it.usedTypes }.map { it.name }).containsExactly("Model")
    }

    @Test
    fun `does not discard application types named like framework types`() {
        val declaration = analyze("struct Client { let endpoint: Endpoint; let result: ResponseEndpoint }").declarations.single()
        assertThat(declaration.usedTypes.map { it.name }).containsExactlyInAnyOrder("Endpoint", "ResponseEndpoint")
    }

    @Test
    fun `extracts top-level functions and values without exposing function-local types`() {
        val declarations = analyze(
            """
            func make(_ input: Input) -> Output {
                struct Local { let other: LocalDependency }
                return Output()
            }
            let shared: Service = Service()
            """
        ).declarations
        assertThat(declarations.map { it.name }).containsExactly("make", "shared")
        assertThat(declarations.first().type).isEqualTo(DeclarationType.FUNCTION)
        assertThat(declarations.last().type).isEqualTo(DeclarationType.VARIABLE)
    }

    @Test
    fun `does not invent declarations for comments and imports alone`() {
        assertThat(analyze("import Foundation\n// struct Missing {}").declarations).isEmpty()
    }

    @Test
    fun `inherits generic bindings in nested declarations`() {
        val inner = analyze("struct Outer<T> { struct Inner { let value: T.Nested; let model: Model } }")
            .declarations
            .single { it.name == "Inner" }
        assertThat(inner.usedTypes.map { it.name }).containsExactly("Model")
    }

    @Test
    fun `reads references inside string interpolation without reading literal text`() {
        val declaration = analyze("struct Screen { let text = \"PhantomType \\(Model())\" }").declarations.single()
        assertThat(declaration.usedTypes.map { it.name }).containsExactly("Model")
    }

    @Test
    fun `handles long member access chains without recursive traversal`() {
        val source = "struct Client { func run() { " + "Library.".repeat(1500) + "make() } }"
        val declaration = analyze(source).declarations.single()
        assertThat(declaration.name).isEqualTo("Client")
    }

    private fun analyze(source: String) = TreeSitterDependencies.analyze(source.trimIndent(), Language.SWIFT)
}
