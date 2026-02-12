package com.fueledbycaffeine.spotlight.cli

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import kotlin.io.path.readText

/**
 * Configurations that are test-only and do NOT affect production compilation.
 * Dependencies declared under these configurations don't block app compilation
 * due to Gradle's configuration/task avoidance.
 */
private val TEST_CONFIGURATIONS = setOf(
  "testImplementation",
  "testApi",
  "testCompileOnly",
  "testRuntimeOnly",
  "androidTestImplementation",
  "androidTestApi",
  "androidTestCompileOnly",
  "androidTestRuntimeOnly",
  "testFixturesImplementation",
  "testFixturesApi",
  "screenshotTestImplementation",
)

private val PROJECT_DEP_WITH_CONFIG = Regex(
  """(\w+)\s*(?:\(|\s).*?project\s*\(\s*(['"])(.*?)\2\s*\)"""
)

private val BLOCK_COMMENT = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)

/**
 * Build a production-only dependency map by re-parsing build files and filtering
 * out test configuration dependencies. This is used for critical path analysis
 * where only production compilation ordering matters.
 *
 * Falls back to the full dependency map for modules whose build files can't be parsed.
 */
fun buildProductionDependencyMap(
  fullDependencyMap: Map<GradlePath, Set<GradlePath>>,
): Map<GradlePath, Set<GradlePath>> {
  return fullDependencyMap.mapValues { (project, fullDeps) ->
    if (!project.hasBuildFile) return@mapValues fullDeps

    try {
      val content = project.buildFilePath.readText()
      val withoutBlockComments = BLOCK_COMMENT.replace(content, "")
      val lines = withoutBlockComments.lines().map { it.substringBefore("//") }

      // Find all test-configuration project deps to exclude
      val testDeps = mutableSetOf<String>()
      for (line in lines) {
        for (match in PROJECT_DEP_WITH_CONFIG.findAll(line)) {
          val config = match.groupValues[1]
          val projectPath = match.groupValues[3]
          if (config in TEST_CONFIGURATIONS) {
            testDeps.add(projectPath)
          }
        }
      }

      if (testDeps.isEmpty()) {
        fullDeps
      } else {
        fullDeps.filterTo(mutableSetOf()) { it.path !in testDeps }
      }
    } catch (_: Exception) {
      fullDeps
    }
  }
}
