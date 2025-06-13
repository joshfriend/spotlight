package com.fueledbycaffeine.spotlight.buildscript

import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule.*
import com.gradle.scan.plugin.internal.com.fueledbycaffeine.spotlight.internal.ccHiddenReadText
import java.io.FileNotFoundException
import kotlin.text.RegexOption.MULTILINE

public data class BuildFile(public val project: GradlePath) {
  public fun parseDependencies(
    rules: Set<ImplicitDependencyRule> = emptySet(),
  ): Set<GradlePath> = parseBuildFile(project, rules)
}

private val PROJECT_DEP_PATTERN = Regex("^(?:\\s+)?(\\w+)\\W+project\\([\"'](.*)[\"']\\)", MULTILINE)
private val TYPESAFE_PROJECT_DEP_PATTERN = Regex("^(?!\\s*//)(?:(?![\"']).)*?(?:^|\\W)(\\w+)?\\(?\\s*(projects\\.[\\w.]+)", MULTILINE)
private val CAMELCASE_REPLACE_PATTERN = Regex("(?<=.)[A-Z]")

internal fun String.typeSafeAccessorAsDefaultGradlePath(): String {
  return GRADLE_PATH_SEP + this.replace(".", GRADLE_PATH_SEP)
    .replace(CAMELCASE_REPLACE_PATTERN, "-$0")
    .lowercase()
}

internal fun parseBuildFile(
  project: GradlePath,
  rules: Set<ImplicitDependencyRule>,
): Set<GradlePath> {
  val buildscriptContents = project.buildFilePath.ccHiddenReadText()

  return computeDirectDependencies(project, buildscriptContents) +
    computeTypeSafeProjectDependencies(project, buildscriptContents, rules) +
    computeImplicitDependencies(project, buildscriptContents, rules) +
    computeImplicitParentProjects(project)
}

/**
 * Read dependencies declared as `project(':path:to:project')`
 */
private fun computeDirectDependencies(project: GradlePath, buildscriptContents: String): Set<GradlePath> {
  return PROJECT_DEP_PATTERN.findAll(buildscriptContents)
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
  buildscriptContents: String,
  rules: Set<ImplicitDependencyRule>,
): Set<GradlePath> {
  val rule = rules.filterIsInstance<TypeSafeProjectAccessorRule>().firstOrNull()

  // TypeSafeAccessorInference.DISABLED behavior
  if (rule == null) return emptySet()

  return TYPESAFE_PROJECT_DEP_PATTERN.findAll(buildscriptContents)
    .map { matchResult ->
      val (_, typeSafeAccessor) = matchResult.destructured
      val cleanTypeSafeAccessor = typeSafeAccessor.removeTypeSafeAccessorJunk(rule.rootProjectAccessor)
      if (rule.typeSafeAccessorMap != null) {
        // TypeSafeAccessorInference.FULL behavior
        rule.typeSafeAccessorMap[cleanTypeSafeAccessor] ?: throw FileNotFoundException(
          "Could not find project buildscript for type-safe project accessor \"$typeSafeAccessor\" " +
            "referenced by ${project.path}"
        )
      } else {
        // TypeSafeAccessorInference.STRICT behavior
        GradlePath(project.root, cleanTypeSafeAccessor.typeSafeAccessorAsDefaultGradlePath())
      }
    }
    .toSet()
}

/**
 * Infer additional dependencies based on the project path or buildscript contents (e.g. plugins applied, custom DSL)
 */
private fun computeImplicitDependencies(
  project: GradlePath,
  buildscriptContents: String,
  rules: Set<ImplicitDependencyRule>,
): Set<GradlePath> {
  return rules
    .filter { rule ->
      when (rule) {
        is BuildscriptMatchRule -> rule.pattern.find(buildscriptContents) != null
        is ProjectPathMatchRule -> rule.pattern.matches(project.path)
        is TypeSafeProjectAccessorRule -> true
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
  return sequence.filterTo(mutableSetOf()) { it != project && it.hasBuildFile }
}

private fun String.removeTypeSafeAccessorJunk(rootProjectAccessor: String): String =
  this.removePrefix("projects.")
    .removePrefix("$rootProjectAccessor.")
    .removeSuffix(".dependencyProject") // deprecated in gradle, to be removed in 9.0
    .removeSuffix(".path") // GeneratedClassCompilationException if you try to name a project `:path` lol