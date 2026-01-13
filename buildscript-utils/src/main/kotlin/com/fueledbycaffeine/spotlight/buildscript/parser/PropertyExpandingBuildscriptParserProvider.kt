package com.fueledbycaffeine.spotlight.buildscript.parser

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.graph.DependencyRule
import com.fueledbycaffeine.spotlight.buildscript.parser.internal.computeImplicitParentProjects
import kotlin.io.path.readText

/**
 * Example provider that expands simple Gradle-property-backed variables before delegating
 * to the existing [RegexBuildscriptParser].
 *
 * This is meant as a reference implementation for cases like:
 *
 * ```groovy
 * def twig = providers.gradleProperty("twig").get()
 * dependencies {
 *   implementation(project(":$twig"))
 * }
 * ```
 *
 * The value for `twig` is read from [ParserConfiguration] (which the Gradle plugin can back
 * with `providers.gradleProperty("twig")`).
 */
public class PropertyExpandingBuildscriptParserProvider(
  private val configuration: ParserConfiguration = ParserConfiguration.EMPTY,
) : BuildscriptParserProvider {

  override val priority: Int = -10

  override fun configure(configuration: ParserConfiguration): BuildscriptParserProvider =
    PropertyExpandingBuildscriptParserProvider(configuration)

  override fun getParser(): BuildscriptParser = PropertyExpandingBuildscriptParser(configuration)
}

private class PropertyExpandingBuildscriptParser(
  private val configuration: ParserConfiguration,
) : BuildscriptParser {

  override fun parse(project: GradlePath, rules: Set<DependencyRule>): Set<GradlePath> {
    // Fast path: if no config, don’t do any extra work.
    if (configuration === ParserConfiguration.EMPTY) {
      return RegexBuildscriptParser.parse(project, rules)
    }

    val original = project.buildFilePath.readText()
    val expanded = expandVariables(original)

    // Delegate to the existing regex parser, but against the expanded source.
    // Rather than changing RegexBuildscriptParser’s API, we parse inline using its regex.
    // (This keeps the example self-contained.)

    val contentWithoutBlockComments = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
      .replace(expanded, "")
    val buildscriptContents = contentWithoutBlockComments.lines()
      .map { it.substringBefore("//") }

    val projectDepPattern = Regex("""project\\s*\\((['\"])(.*?)\\1\\)""")

    val directDeps = buildscriptContents
      .flatMap { projectDepPattern.findAll(it) }
      .map { matchResult ->
        val (_, projectPath) = matchResult.destructured
        GradlePath(project.root, projectPath)
      }
      .toSet()

    // Keep behavior consistent with RegexBuildscriptParser by also adding implicit parents.
    val implicitParents = computeImplicitParentProjects(project)

    return directDeps + implicitParents
  }

  /**
   * Very small demo expander:
   * - finds `def <name> = providers.gradleProperty("<key>").get()`
   * - replaces occurrences of `$<name>` with the configured value for `<key>`
   *
   * This intentionally doesn’t try to be a full Groovy/Kotlin evaluator.
   */
  private fun expandVariables(source: String): String {
    val assignment = Regex(
      """\\bdef\\s+([A-Za-z_]\\w*)\\s*=\\s*providers\\.gradleProperty\\(\\s*(['\"])([^'\"]+)\\2\\s*\\)\\.get\\(\\s*\\)"""
    )

    val variables = buildMap {
      assignment.findAll(source).forEach { m ->
        val varName = m.groupValues[1]
        val key = m.groupValues[3]
        val value = configuration.get(key) ?: return@forEach
        put(varName, value)
      }
    }

    if (variables.isEmpty()) return source

    var expanded = source
    for ((name, value) in variables) {
      expanded = expanded.replace("\\$$name".toRegex(), value)
    }
    return expanded
  }
}

