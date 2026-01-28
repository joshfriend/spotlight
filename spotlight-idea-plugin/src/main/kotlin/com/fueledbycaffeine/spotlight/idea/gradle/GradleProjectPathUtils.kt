package com.fueledbycaffeine.spotlight.idea.gradle

import com.fueledbycaffeine.spotlight.buildscript.GradlePath

/**
 * Shared utilities for parsing and validating Gradle project paths.
 * Uses the same patterns as RegexBuildscriptParser in buildscript-utils.
 */
object GradleProjectPathUtils {
  // Same patterns as RegexBuildscriptParser
  val PROJECT_CALL_PATTERN = Regex("""project\s*\((['"])(.*?)\1\)""")
  // Match projects.xxx.yyy - no trailing \b to allow dots within the accessor
  val TYPE_SAFE_ACCESSOR_PATTERN = Regex("""\b(projects\.[\w.]+)""")
  
  /**
   * Checks if a filename is a Gradle build file.
   */
  fun isGradleBuildFile(filename: String?): Boolean {
    return filename?.endsWith(".gradle") == true || filename?.endsWith(".gradle.kts") == true
  }
  
  /**
   * Builds a map of type-safe accessor names to GradlePath objects.
   */
  fun buildAccessorMap(allProjects: Set<GradlePath>): Map<String, GradlePath> {
    return allProjects.associateBy { it.typeSafeAccessorName }
  }
  
  /**
   * Cleans a type-safe accessor by removing common prefixes and suffixes.
   * Matches the logic in RegexBuildscriptParser.removeTypeSafeAccessorJunk()
   */
  fun cleanTypeSafeAccessor(accessor: String, rootProjectAccessor: String = ""): String {
    return accessor
      .removePrefix("projects.")
      .removePrefix("$rootProjectAccessor.")
      .removeSuffix(".dependencyProject") // deprecated in gradle, to be removed in 9.0
      .removeSuffix(".path") // GeneratedClassCompilationException if you try to name a project `:path`
  }
}
