package com.fueledbycaffeine.spotlight.fixtures

import org.gradle.testkit.runner.BuildResult

fun BuildResult.includedProjects(): List<String> {
  val includeProjectsLine = output.lines()
    .first { it.startsWith("Included projects:") }

  return Regex("project '([^']+)'").findAll(includeProjectsLine)
    .map {
      val (path) = it.destructured
      path
    }
    .toList()
}