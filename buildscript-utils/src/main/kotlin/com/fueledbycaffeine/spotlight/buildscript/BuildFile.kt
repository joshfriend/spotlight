package com.fueledbycaffeine.spotlight.buildscript

import com.fueledbycaffeine.spotlight.buildscript.graph.DependencyRule
import com.fueledbycaffeine.spotlight.buildscript.graph.FullModeTypeSafeProjectAccessorRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule.BuildscriptMatchRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule.ProjectPathMatchRule
import com.fueledbycaffeine.spotlight.buildscript.graph.StrictModeTypeSafeProjectAccessorRule
import com.fueledbycaffeine.spotlight.buildscript.graph.TypeSafeProjectAccessorRule
import com.gradle.scan.plugin.internal.com.fueledbycaffeine.spotlight.internal.ccHiddenReadLines
import java.io.FileNotFoundException

public data class BuildFile(public val project: GradlePath) {
  public fun parseDependencies(
    rules: Set<DependencyRule> = emptySet(),
  ): Set<GradlePath> = parseBuildFile(project, rules)
}

private val PROJECT_DEP_PATTERN = Regex("^(?:\\s+)?(\\w+)\\W+(?:\\w+\\()*project\\([\"'](.*)[\"']\\)+")
private val TYPESAFE_PROJECT_DEP_PATTERN = Regex("^(?!\\s*//)(?:(?![\"']).)*?(?:^|\\b|\\s)(\\w+)?\\(?\\s*(\\bprojects\\.[\\w.]+)")
private val CAMELCASE_REPLACE_PATTERN = Regex("(?<=.)[A-Z]")

internal fun String.typeSafeAccessorAsDefaultGradlePath(): String {
  return GRADLE_PATH_SEP + this.replace(".", GRADLE_PATH_SEP)
    .replace(CAMELCASE_REPLACE_PATTERN, "-$0")
    .lowercase()
}

internal fun parseBuildFile(
  project: GradlePath,
  rules: Set<DependencyRule>,
): Set<GradlePath> {
  val buildscriptContents = project.buildFilePath.ccHiddenReadLines()

  return computeDirectDependencies(project, buildscriptContents) +
    computeTypeSafeProjectDependencies(project, buildscriptContents, rules) +
    computeImplicitDependencies(project, buildscriptContents, rules) +
    computeImplicitParentProjects(project)
}

/**
 * Read dependencies declared as `project(':path:to:project')`
 */
private fun computeDirectDependencies(project: GradlePath, buildscriptContents: List<String>): Set<GradlePath> {
  return buildscriptContents.mapNotNull { PROJECT_DEP_PATTERN.find(it) }
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

  // TypeSafeAccessorInference.DISABLED behavior
  if (rule == null) return emptySet()

  return buildscriptContents.mapNotNull { TYPESAFE_PROJECT_DEP_PATTERN.find(it) }
    .map { matchResult ->
      val (_, typeSafeAccessor) = matchResult.destructured
      val cleanTypeSafeAccessor = typeSafeAccessor.removeTypeSafeAccessorJunk(rule.rootProjectAccessor)
      when (rule) {
        is FullModeTypeSafeProjectAccessorRule -> {
          // TypeSafeAccessorInference.FULL behavior
          rule.typeSafeAccessorMap[cleanTypeSafeAccessor] ?: throw NoSuchElementException(
            "Could not find project mapping for type-safe project accessor \"$typeSafeAccessor\" " +
              "referenced by ${project.path}"
          )
        }
        is StrictModeTypeSafeProjectAccessorRule -> {
          // TypeSafeAccessorInference.STRICT behavior
          GradlePath(project.root, cleanTypeSafeAccessor.typeSafeAccessorAsDefaultGradlePath())
        }
      }
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
  return sequence.filterTo(mutableSetOf()) { it != project && !it.isRootProject }
}

private fun String.removeTypeSafeAccessorJunk(rootProjectAccessor: String): String =
  this.removePrefix("projects.")
    .removePrefix("$rootProjectAccessor.")
    .removeSuffix(".dependencyProject") // deprecated in gradle, to be removed in 9.0
    .removeSuffix(".path") // GeneratedClassCompilationException if you try to name a project `:path` lol