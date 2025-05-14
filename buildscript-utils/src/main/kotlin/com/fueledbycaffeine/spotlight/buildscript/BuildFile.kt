package com.fueledbycaffeine.spotlight.buildscript

import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule.BuildscriptMatchRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule.ProjectPathMatchRule
import kotlin.io.path.readText
import kotlin.text.RegexOption.MULTILINE

public data class BuildFile(public val project: GradlePath) {
  public fun parseDependencies(rules: Set<ImplicitDependencyRule> = emptySet()): Set<GradlePath> = parseBuildFile(project, rules)
}

private val PROJECT_DEP_PATTERN = Regex("^(?:\\s+)?(\\w+)\\W+project\\([\"'](.*)[\"']\\)", MULTILINE)

internal fun parseBuildFile(project: GradlePath, rules: Set<ImplicitDependencyRule>): Set<GradlePath> {
  val buildscriptContents = project.buildFilePath.readText()
  val directDependencies = PROJECT_DEP_PATTERN.findAll(buildscriptContents)
    .map { matchResult ->
      val (_, projectPath) = matchResult.destructured
      GradlePath(project.root, projectPath)
    }
    .toSet()

  val implicitDependencies = rules
    .filter { rule ->
      when (rule) {
        is BuildscriptMatchRule -> rule.pattern.find(buildscriptContents) != null
        is ProjectPathMatchRule -> rule.pattern.matches(project.path)
      }
    }
    .flatMap { rule -> rule.includedProjects }

  return directDependencies + implicitDependencies
}