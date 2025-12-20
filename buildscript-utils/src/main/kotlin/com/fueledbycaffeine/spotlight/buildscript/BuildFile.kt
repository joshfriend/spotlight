package com.fueledbycaffeine.spotlight.buildscript

import com.fueledbycaffeine.spotlight.buildscript.graph.DependencyRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule.BuildscriptMatchRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule.ProjectPathMatchRule
import com.fueledbycaffeine.spotlight.buildscript.graph.TypeSafeProjectAccessorRule
import kotlin.io.path.readText

public data class BuildFile(public val project: GradlePath) {
  public fun parseDependencies(
    rules: Set<DependencyRule> = emptySet(),
  ): Set<GradlePath> = parseBuildFile(project, rules)
}

private val PROJECT_DEP_PATTERN = Regex("""project\s*\((['"])(.*?)\1\)""")
// This regex is intentionally simple for performance. Comments are filtered out before matching.
private val TYPESAFE_PROJECT_DEP_PATTERN = Regex("""\b(projects\.[\w.]+)\b""")
private val STRING_LITERAL_PATTERN = Regex("\"[^\"]*\"|'[^']*'")
private val BLOCK_COMMENT_PATTERN = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)

internal fun parseBuildFile(
  project: GradlePath,
  rules: Set<DependencyRule>,
): Set<GradlePath> {
  val fileContent = project.buildFilePath.readText()
  val contentWithoutBlockComments = BLOCK_COMMENT_PATTERN.replace(fileContent, "")
  val buildscriptContents = contentWithoutBlockComments.lines()
    .map { it.substringBefore("//") }

  return computeDirectDependencies(project, buildscriptContents) +
    computeTypeSafeProjectDependencies(project, buildscriptContents, rules) +
    computeImplicitDependencies(project, buildscriptContents, rules) +
    computeImplicitParentProjects(project)
}

/**
 * Read dependencies declared as `project(':path:to:project')`
 */
private fun computeDirectDependencies(project: GradlePath, buildscriptContents: List<String>): Set<GradlePath> {
  return buildscriptContents
    .flatMap { PROJECT_DEP_PATTERN.findAll(it) }
    .map { matchResult ->
      val (_, projectPath) = matchResult.destructured
      GradlePath(project.root, projectPath)
    }
    .toSet()
}

/**
 * Read dependencies declared using type-safe project accessors (`projects.features.featureA`)
 */
private fun computeTypeSafeProjectDependencies(
  project: GradlePath,
  buildscriptContents: List<String>,
  rules: Set<DependencyRule>,
): Set<GradlePath> {
  val rule = rules.filterIsInstance<TypeSafeProjectAccessorRule>().firstOrNull()
    ?: return emptySet()

  return buildscriptContents
    .asSequence()
    .map { it.replace(STRING_LITERAL_PATTERN, "") } // Pre-filter strings
    .flatMap { line -> TYPESAFE_PROJECT_DEP_PATTERN.findAll(line) }
    .map { matchResult ->
      val (typeSafeAccessor) = matchResult.destructured
      val cleanTypeSafeAccessor = typeSafeAccessor.removeTypeSafeAccessorJunk(rule.rootProjectAccessor)
      // Look up project in the accessor mapping
      rule.typeSafeAccessorMap[cleanTypeSafeAccessor] ?: throw NoSuchElementException(
        "Could not find project mapping for type-safe project accessor \"$typeSafeAccessor\" " +
          "referenced by ${project.path}"
      )
    }
    .toSet()
}

/**
 * Infer additional dependencies based on the project path or buildscript contents (e.g. plugins applied, custom DSL)
 */
private fun computeImplicitDependencies(
  project: GradlePath,
  buildscriptContents: List<String>,
  rules: Set<DependencyRule>,
): Set<GradlePath> {
  return rules
    .filterIsInstance<ImplicitDependencyRule>()
    .filter { rule ->
      when (rule) {
        is BuildscriptMatchRule -> buildscriptContents.any { rule.regex.containsMatchIn(it) }
        is ProjectPathMatchRule -> rule.regex.containsMatchIn(project.path)
      }
    }
    .flatMapTo(mutableSetOf()) { rule -> rule.includedProjects }
}

/**
 * A call to `Settings#include()` implicitly calls `include` on the parent directories, up to the root project.
 * If one of those directories has a buildscript, it will be included in the build as well, and we need to parse it.
 */
private fun computeImplicitParentProjects(project: GradlePath): Set<GradlePath> {
  // Start with the grandparent directory of the build file
  // libs/foo/impl/build.gradle.kts -> libs/foo
  // Then iterate up to the root directory
  val sequence = generateSequence(project) { it.parent }
  return sequence.filterTo(mutableSetOf()) { it != project && !it.isRootProject && it.hasBuildFile }
}

private fun String.removeTypeSafeAccessorJunk(rootProjectAccessor: String): String =
  this.removePrefix("projects.")
    .removePrefix("$rootProjectAccessor.")
    .removeSuffix(".dependencyProject") // deprecated in gradle, to be removed in 9.0
    .removeSuffix(".path") // GeneratedClassCompilationException if you try to name a project `:path` lol
