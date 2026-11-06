package de.maibornwolff.treesitter.excavationsite.languages.swift

import de.maibornwolff.treesitter.excavationsite.shared.domain.Extract
import de.maibornwolff.treesitter.excavationsite.shared.domain.LanguageDefinition
import de.maibornwolff.treesitter.excavationsite.shared.domain.LanguageDependencyMapping
import de.maibornwolff.treesitter.excavationsite.shared.domain.Metric

/**
 * Swift metrics, identifier extraction and structural dependencies.
 */
object SwiftDefinition : LanguageDefinition {
    override val nodeMetrics: Map<String, Set<Metric>> = SwiftMetricMapping.nodeMetrics
    override val nodeExtractions: Map<String, Extract> = SwiftExtractionMapping.nodeExtractions
    override val dependencyMapping: LanguageDependencyMapping = SwiftDependencyMapping.dependencyMapping
}
