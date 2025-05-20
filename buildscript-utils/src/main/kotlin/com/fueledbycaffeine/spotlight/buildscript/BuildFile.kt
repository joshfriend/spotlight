package com.fueledbycaffeine.spotlight.buildscript

import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule.*
import java.io.FileNotFoundException
import kotlin.io.path.readText
import kotlin.text.RegexOption.MULTILINE

public data class BuildFile(public val project: GradlePath) {
  public fun parseDependencies(
    rules: Set<ImplicitDependencyRule> = emptySet(),
  ): Set<GradlePath> = parseBuildFile(project, rules)
}

private val PROJECT_DEP_PATTERN = Regex("^(?:\\s+)?(\\w+)\\W+project\\([\"'](.*)[\"']\\)", MULTILINE)
private val TYPESAFE_PROJECT_DEP_PATTERN = Regex("^(?!\\s*//).*?(?:^|\\W)(\\w+)?\\(?\\s*(projects\\.[\\w.]+)", MULTILINE)

internal fun parseBuildFile(
  project: GradlePath,
  rules: Set<ImplicitDependencyRule>,
): Set<GradlePath> {
  val buildscriptContents = project.buildFilePath.readText()
  val directDependencies = computeDirectDependencies(project, buildscriptContents)
  val typeSafeProjectDependencies = computeTypeSafeProjectDependencies(project, buildscriptContents, rules)

  // A call to `Settings#include()` implicitly calls `include` on the parent directories, up to the root project.
  // If one of those directories has a buildscript, it will be included in the build as well, and we need to parse it.
  val parentProjects = computeImplicitParentProjects(project)

  val implicitDependencies = computeImplicitDependencies(project, buildscriptContents, rules)

  return directDependencies + typeSafeProjectDependencies + implicitDependencies + parentProjects
}

private fun computeDirectDependencies(project: GradlePath, buildscriptContents: String): Set<GradlePath> {
  return PROJECT_DEP_PATTERN.findAll(buildscriptContents)
    .map { matchResult ->
      val (_, projectPath) = matchResult.destructured
      GradlePath(project.root, projectPath)
    }
    .toSet()
}

private fun computeTypeSafeProjectDependencies(
  project: GradlePath,
  buildscriptContents: String,
  rules: Set<ImplicitDependencyRule>,
): Set<GradlePath> {
  val typeSafeProjectAccessorsRule = rules.filterIsInstance<TypeSafeProjectAccessorRule>().firstOrNull()
  return if (typeSafeProjectAccessorsRule != null) {
    TYPESAFE_PROJECT_DEP_PATTERN.findAll(buildscriptContents)
      .map { matchResult ->
        val (_, typeSafeAccessor) = matchResult.destructured
        val cleanTypeSafeAccessor = typeSafeAccessor.removeTypeSafeAccessorJunk()
          .removePrefix("${typeSafeProjectAccessorsRule.rootProjectName}.")
        typeSafeProjectAccessorsRule.typeSafeAccessorMap[cleanTypeSafeAccessor]
          ?: throw FileNotFoundException(
            "Could not find project buildscript for type-safe project accessor \"$typeSafeAccessor\" " +
              "referenced by ${project.path}"
          )
      }
      .toSet()
  } else {
    emptySet()
  }
}

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

private fun computeImplicitParentProjects(project: GradlePath): Set<GradlePath> {
  // Start with the grandparent directory of the build file
  // libs/foo/impl/build.gradle.kts -> libs/foo
  // Then iterate up to the root directory
  val sequence = generateSequence(project.path) { current ->
    current.substringBeforeLast(GRADLE_PATH_SEP, missingDelimiterValue = "")
      .takeIf { it.isNotBlank() }
  }
  return sequence
    .map { parent -> GradlePath(project.root, parent) }
    .filterTo(mutableSetOf()) { it != project && it.hasBuildFile }
}

private fun String.removeTypeSafeAccessorJunk(): String =
  this.removePrefix("projects.")
    .removeSuffix(".dependencyProject") // deprecated in gradle, to be removed in 9.0
    .removeSuffix(".path") // GeneratedClassCompilationException if you try to name a project `:path` lol