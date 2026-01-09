package com.fueledbycaffeine.spotlight.buildscript.graph

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.squareup.moshi.JsonClass
import dev.zacsweers.moshix.sealed.annotations.TypeLabel
import java.nio.file.Path

public sealed interface DependencyRule

@JsonClass(generateAdapter = false, generator = "sealed:type")
public sealed interface ImplicitDependencyRule : DependencyRule {

  /**
   * Returns the set of projects that should be added as implicit dependencies based on this rule.
   */
  public fun findMatchingProjects(projectPath: String, buildscriptContents: List<String>, root: Path): Set<GradlePath>

  /**
   * Rule for adding implicit dependencies when a buildscript contains a matching pattern.
   *
   * When the regex pattern matches any line in a project's buildscript, all projects in
   * [includedProjects] are added as implicit dependencies.
   *
   * For example, to add a shared dependency for all projects using a specific plugin:
   * ```json
   * {
   *   "type": "buildscript-match-rule",
   *   "pattern": "id 'com.example.feature'",
   *   "includedProjects": [":shared:feature-support"]
   * }
   * ```
   *
   * @param pattern A regex pattern to match against buildscript contents.
   * @param includedProjects Projects to add as dependencies when the pattern matches.
   */
  @TypeLabel("buildscript-match-rule")
  public data class BuildscriptMatchRule(
    val pattern: Regex,
    val includedProjects: Set<GradlePath>,
  ) : ImplicitDependencyRule {
    override fun findMatchingProjects(projectPath: String, buildscriptContents: List<String>, root: Path): Set<GradlePath> {
      return if (buildscriptContents.any { pattern.containsMatchIn(it) }) includedProjects else emptySet()
    }
  }

  /**
   * Rule for adding implicit dependencies based on a project's Gradle path.
   *
   * When a project's path matches the regex pattern, all projects in [includedProjects]
   * are added as implicit dependencies. Useful for applying common dependencies to projects
   * in specific directories or matching naming conventions.
   *
   * For example, to add common test infrastructure to all feature modules:
   * ```json
   * {
   *   "type": "project-path-match-rule",
   *   "pattern": ":features:.*",
   *   "includedProjects": [":testing:common"]
   * }
   * ```
   *
   * @param pattern A regex pattern to match against project paths.
   * @param includedProjects Projects to add as dependencies when the pattern matches.
   */
  @TypeLabel("project-path-match-rule")
  public data class ProjectPathMatchRule(
    val pattern: Regex,
    val includedProjects: Set<GradlePath>,
  ) : ImplicitDependencyRule {
    override fun findMatchingProjects(projectPath: String, buildscriptContents: List<String>, root: Path): Set<GradlePath> {
      return if (pattern.containsMatchIn(projectPath)) includedProjects else emptySet()
    }
  }

  /**
   * Rule for extracting project paths from buildscripts using regex capture groups and a template.
   *
   * Unlike [BuildscriptMatchRule] which includes static project dependencies when a pattern matches,
   * this rule extracts values using capture groups and substitutes them into a template to produce
   * the project path dynamically.
   *
   * For example, to capture the target project from `com.android.test` projects`:
   * ```json
   * {
   *   "type": "buildscript-capture-rule",
   *   "pattern": "targetProjectPath\\s*=\\s*[\"']([^\"']+)[\"']",
   *   "projectTemplate": "$1"
   * }
   * ```
   * When matching `targetProjectPath = ":example-app"`, this extracts `:example-app` and substitutes it
   * into the template, producing `:example-app` as a dependency.
   *
   * Templates can transform captured values:
   * ```json
   * {
   *   "type": "buildscript-capture-rule",
   *   "pattern": "targetProjectPath\\s*=\\s*[\"']([^\"']+)[\"']",
   *   "projectTemplate": "$1-benchmark"
   * }
   * ```
   * This would transform `:example-app` into `:example-app-benchmark`.
   *
   * @param pattern A regex pattern with capture groups.
   * @param projectTemplate A template string where `$1`, `$2`, etc. are replaced with captured groups.
   */
  @TypeLabel("buildscript-capture-rule")
  public data class BuildscriptCaptureRule(
    val pattern: Regex,
    val projectTemplate: String,
  ) : ImplicitDependencyRule {
    override fun findMatchingProjects(projectPath: String, buildscriptContents: List<String>, root: Path): Set<GradlePath> {
      return buildscriptContents
        .mapNotNull { line -> pattern.find(line) }
        .map { match -> GradlePath(root, match.groupValues[0].replace(pattern, projectTemplate)) }
        .toSet()
    }
  }
}

/**
 * Rule for resolving type-safe project accessor references in buildscripts.
 *
 * Gradle generates an accessor for the root project based on the root project name. Projects in
 * the build are also accessible via this accessor. For example `projects.buildscriptUtils` and
 * `projects.spotlight.buildscriptUtils` are both valid for a project named "spotlight".
 */
public data class TypeSafeProjectAccessorRule(
  val rootProjectAccessor: String,
  /**
   * Specifies the mapping of accessor name to the gradle path.
   */
  val typeSafeAccessorMap: Map<String, GradlePath>,
) : DependencyRule
