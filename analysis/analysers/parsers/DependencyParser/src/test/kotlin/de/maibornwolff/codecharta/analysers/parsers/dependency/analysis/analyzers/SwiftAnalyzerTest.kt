package de.maibornwolff.codecharta.analysers.parsers.dependency.analysis.analyzers

import de.maibornwolff.codecharta.analysers.parsers.dependency.analysis.LanguageAnalyzerFactory
import de.maibornwolff.codecharta.analysers.parsers.dependency.analysis.analyzers.swift.SwiftAnalyzer
import de.maibornwolff.codecharta.analysers.parsers.dependency.analysis.model.FileInfo
import de.maibornwolff.codecharta.analysers.parsers.dependency.analysis.model.Node
import de.maibornwolff.codecharta.analysers.parsers.dependency.input.SupportedLanguage
import de.maibornwolff.codecharta.analysers.parsers.dependency.processing.dependencies.DependencyResolverService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SwiftAnalyzerTest {
    @TempDir
    lateinit var directory: File

    @Test
    fun `recognizes Swift source files and creates their analyzer`() {
        assertThat(SupportedLanguage.ofFileName("Feature.SWIFT")).isEqualTo(SupportedLanguage.SWIFT)
        assertThat(LanguageAnalyzerFactory.createAnalyzer(FileInfo(SupportedLanguage.SWIFT, "Feature.swift", "struct Feature {}")))
            .isInstanceOf(SwiftAnalyzer::class.java)
    }

    @Test
    fun `resolves types across directories in one source module`() {
        val nodes = resolve(
            "Scenes/Screen.swift" to "struct Screen { let model: Model; let endpoint: Endpoint }",
            "Models/Model.swift" to "struct Model {}",
            "Networking/Endpoint.swift" to "protocol Endpoint {}"
        )
        assertThat(dependencies(nodes.single { it.name() == "Screen" }))
            .containsExactlyInAnyOrder("<module>.Model", "<module>.Endpoint")
    }

    @Test
    fun `keeps extension dependencies regardless of filename`() {
        val nodes = resolve(
            "Model.swift" to "struct Model {}",
            "Helpers.swift" to "extension Model { var service: Service { Service() } }",
            "Service.swift" to "class Service {}"
        )
        val extension = nodes.single { it.physicalPath == "Helpers.swift" }
        assertThat(dependencies(extension)).containsExactlyInAnyOrder("<module>.Model", "<module>.Service")
        assertThat(nodes.single { it.name() == "Model" }.physicalPath).isEqualTo("Model.swift")
    }

    @Test
    fun `keeps multiple extensions beside the original declaration`() {
        val nodes = resolve(
            "Model.swift" to
                "struct Model {}\nextension Model { var one: Service { Service() } }\nextension Model { var two: Endpoint { Endpoint() } }",
            "Service.swift" to "class Service {}",
            "Endpoint.swift" to "class Endpoint {}"
        )
        assertThat(nodes.filter { it.name().endsWith("_extension") }.flatMap(::dependencies))
            .contains("<module>.Service", "<module>.Endpoint")
    }

    @Test
    fun `does not expose nested declarations as module-global names`() {
        val nodes = resolve(
            "Log.swift" to "class Log { struct Context {} }",
            "Screen.swift" to "struct Screen { func make(context: Context) {} }"
        )
        assertThat(dependencies(nodes.single { it.name() == "Screen" })).isEmpty()
    }

    @Test
    fun `resolves nested names lexically and when qualified`() {
        val nodes = resolve(
            "Outer.swift" to "struct Outer { struct Inner {}; func make() -> Inner { Inner() } }",
            "Client.swift" to "struct Client { let value: Outer.Inner }",
            "Other.swift" to "struct Other { struct Inner {} }"
        )
        assertThat(dependencies(nodes.single { it.name() == "Outer" })).contains("<module>.Outer.Inner")
        assertThat(dependencies(nodes.single { it.name() == "Client" })).containsExactly("<module>.Outer.Inner")
    }

    @Test
    fun `does not allow fileprivate declarations to leak to another file`() {
        val nodes = resolve(
            "One.swift" to "fileprivate struct Hidden {}\nstruct One { let hidden: Hidden }",
            "Two.swift" to "struct Two { let hidden: Hidden }"
        )
        assertThat(dependencies(nodes.single { it.name() == "One" })).containsExactly("<module>.Hidden")
        assertThat(dependencies(nodes.single { it.name() == "Two" })).isEmpty()
    }

    @Test
    fun `requires imports between conventional SwiftPM modules`() {
        packageManifest()
        val nodes = resolve(
            "Sources/Core/Model.swift" to "public struct Model {}",
            "Sources/App/Client.swift" to "import Core\nstruct Client { let model: Model }",
            "Sources/Other/Other.swift" to "struct Other { let model: Model }",
            root = directory
        )
        assertThat(dependencies(nodes.single { it.name() == "Client" })).containsExactly("Core.Model")
        assertThat(dependencies(nodes.single { it.name() == "Other" })).isEmpty()
    }

    @Test
    fun `leaves ambiguous imports unresolved but accepts qualified names`() {
        packageManifest()
        val nodes = resolve(
            "Sources/Core/Model.swift" to "public struct Model {}",
            "Sources/Other/Model.swift" to "public struct Model {}",
            "Sources/App/Client.swift" to "import Core\nimport Other\nstruct Client { let model: Model }",
            "Sources/App/Explicit.swift" to "import Core\nimport Other\nstruct Explicit { let model: Core.Model }",
            root = directory
        )
        assertThat(dependencies(nodes.single { it.name() == "Client" })).isEmpty()
        assertThat(dependencies(nodes.single { it.name() == "Explicit" })).containsExactly("Core.Model")
    }

    @Test
    fun `selective imports expose only the declaration they name`() {
        packageManifest()
        val nodes = resolve(
            "Sources/Core/Model.swift" to "public struct Model {}\npublic struct Hidden {}",
            "Sources/App/Client.swift" to "import struct Core.Model\nstruct Client { let model: Model; let other: Hidden }",
            root = directory
        )
        assertThat(dependencies(nodes.single { it.name() == "Client" })).containsExactly("Core.Model")
    }

    @Test
    fun `prefers the current module over an imported declaration of the same name`() {
        packageManifest()
        val nodes = resolve(
            "Sources/Core/Model.swift" to "public struct Model {}",
            "Sources/App/Model.swift" to "struct Model {}",
            "Sources/App/Client.swift" to "import Core\nstruct Client { let model: Model }",
            root = directory
        )
        assertThat(dependencies(nodes.single { it.name() == "Client" })).containsExactly("App.Model")
    }

    @Test
    fun `does not resolve Swift names to declarations from another language`() {
        val reports = listOf(
            SwiftAnalyzer(FileInfo(SupportedLanguage.SWIFT, "Client.swift", "struct Client { let model: Model }")).analyze(),
            LanguageAnalyzerFactory.createAnalyzer(FileInfo(SupportedLanguage.JAVA, "Model.java", "class Model {} ")).analyze()
        )
        val nodes = DependencyResolverService.resolveNodes(reports)
        assertThat(dependencies(nodes.single { it.name() == "Client" })).isEmpty()
    }

    private fun packageManifest() {
        File(directory, "Package.swift").writeText("// swift-tools-version: 6.0")
    }

    private fun resolve(vararg sources: Pair<String, String>, root: File? = null): Collection<Node> {
        val reports = sources.map { (path, code) -> SwiftAnalyzer(FileInfo(SupportedLanguage.SWIFT, path, code, root)).analyze() }
        return DependencyResolverService.resolveNodes(reports)
    }

    private fun dependencies(node: Node): List<String> = node.resolvedNodeDependencies.internalDependencies.map { it.path.withDots() }
}
