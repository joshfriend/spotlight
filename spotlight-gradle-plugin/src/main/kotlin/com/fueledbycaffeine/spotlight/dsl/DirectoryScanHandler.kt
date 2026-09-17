package com.fueledbycaffeine.spotlight.dsl

import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

/** Configures scanning directories for build.gradle or build.gradle.kts files. */
public abstract class DirectoryScanHandler @Inject constructor(objects: ObjectFactory) {
  internal val discoverUnlistedProjects = objects.property(Boolean::class.java).convention(true)
  internal val excludedProjectPaths = objects.setProperty(String::class.java).convention(emptySet())
  internal val excludedDirectoryPatterns = objects.setProperty(Regex::class.java).convention(emptySet())

  /**
   * Whether to scan directories for projects absent from all-projects.txt. Defaults to true.
   *
   * Setting this to false disables only the directory scan in checkAllProjectsList and fixAllProjectsList.
   * Dependency-graph traversal always runs, so required projects are still checked and added by the fix task.
   */
  public fun discoverUnlistedProjects(discover: Boolean) {
    discoverUnlistedProjects.set(discover)
  }

  /**
   * Excludes exact Gradle project paths, such as ":optional-tool:public", from the directory scan.
   * Child projects are not excluded.
   *
   * This only skips projects during the directory scan. It does not remove them from all-projects.txt
   * or prevent Spotlight from loading them to run a task or satisfy a dependency.
   * Both `:checkAllProjectsList` and `:fixAllProjectsList` fail if an excluded project is a required dependency.
   */
  public fun excludeProjectPaths(vararg paths: String) {
    excludedProjectPaths.addAll(*paths)
  }

  /**
   * Excludes matching directories and their descendants from the directory scan.
   *
   * Regexes match the entire root-relative directory path, using '/' separators and no trailing slash.
   * For example, "generated/.*" excludes directories beneath the root's generated directory.
   *
   * This only skips directories during the directory scan. It does not remove their projects from all-projects.txt
   * or prevent Spotlight from loading them to run a task or satisfy a dependency.
   * Both `:checkAllProjectsList` and `:fixAllProjectsList` fail if an excluded project is a required dependency.
   */
  public fun excludeDirectoriesMatchingRegex(vararg patterns: String) {
    excludedDirectoryPatterns.addAll(patterns.map(::Regex))
  }
}
