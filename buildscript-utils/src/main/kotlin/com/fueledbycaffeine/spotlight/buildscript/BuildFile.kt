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
private val TYPESAFE_PROJECT_DEP_PATTERN = Regex("^(?:\\s+)?(\\w+)\\W+projects\\.([\\w.]+)\\b", MULTILINE)

internal fun parseBuildFile(
  project: GradlePath,
  rules: Set<ImplicitDependencyRule>,
): Set<GradlePath> {
  val buildscriptContents = project.buildFilePath.readText()
  val directDependencies = PROJECT_DEP_PATTERN.findAll(buildscriptContents)
    .map { matchResult ->
      val (_, projectPath) = matchResult.destructured
      GradlePath(project.root, projectPath)
    }
    .toSet()

  val typeSafeProjectAccessorsRule = rules.filterIsInstance<TypeSafeProjectAccessorRule>().firstOrNull()
  val typeSafeProjectDependencies = if (typeSafeProjectAccessorsRule != null) {
    TYPESAFE_PROJECT_DEP_PATTERN.findAll(buildscriptContents)
      .map { matchResult ->
        val (_, typeSafeAccessor) = matchResult.destructured
        val cleanTypeSafeAccessor = typeSafeAccessor.removePrefix("projects.")
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

  val implicitDependencies = rules
    .filter { rule ->
      when (rule) {
        is BuildscriptMatchRule -> rule.pattern.find(buildscriptContents) != null
        is ProjectPathMatchRule -> rule.pattern.matches(project.path)
        is TypeSafeProjectAccessorRule -> true
      }
    }
    .flatMap { rule -> rule.includedProjects }

  return directDependencies + typeSafeProjectDependencies + implicitDependencies
}