package com.fueledbycaffeine.spotlight.utils

import com.fueledbycaffeine.spotlight.graph.ImplicitDependencyRule
import com.fueledbycaffeine.spotlight.graph.ImplicitDependencyRule.BuildscriptMatchRule
import com.fueledbycaffeine.spotlight.graph.ImplicitDependencyRule.ProjectPathMatchRule
import kotlin.io.path.readText
import kotlin.text.RegexOption.MULTILINE

internal data class BuildFile(val project: GradlePath) {
  fun parseDependencies(rules: Set<ImplicitDependencyRule>): Set<GradlePath> = parseBuildFile(project, rules)
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