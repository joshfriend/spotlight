package com.fueledbycaffeine.spotlight.buildscript.graph

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.squareup.moshi.JsonClass
import dev.zacsweers.moshix.sealed.annotations.TypeLabel

public sealed interface DependencyRule

@JsonClass(generateAdapter = false, generator = "sealed:type")
public sealed interface ImplicitDependencyRule : DependencyRule {
  public val includedProjects: Set<GradlePath>

  @TypeLabel("buildscript-match-rule")
  public data class BuildscriptMatchRule(
    val pattern: String,
    override val includedProjects: Set<GradlePath>,
  ) : ImplicitDependencyRule {
    internal val regex = pattern.toRegex()
  }

  @TypeLabel("project-path-match-rule")
  public data class ProjectPathMatchRule(
    val pattern: String,
    override val includedProjects: Set<GradlePath>,
  ) : ImplicitDependencyRule {
    internal val regex = pattern.toRegex()
  }
}

public sealed interface TypeSafeProjectAccessorRule : DependencyRule {
  /**
   * Gradle generates an accessor for the root project based on the root project name. Projects in
   * the build are also accessible via this accessor. For example `projects.buildscriptUtils` and
   * `projects.spotlight.buildscriptUtils` are both valid for a project named "spotlight".
   */
  public val rootProjectAccessor: String
}

/**
 * Makes no assumptions about project path naming.
 */
public data class FullModeTypeSafeProjectAccessorRule(
  override val rootProjectAccessor: String,
  /**
   * Specifies the mapping of accessor name to the gradle path.
   */
  val typeSafeAccessorMap: Map<String, GradlePath>,
) : TypeSafeProjectAccessorRule

/**
 * Assumes that project paths are lowercase and kebab-case
 */
public data class StrictModeTypeSafeProjectAccessorRule(
  override val rootProjectAccessor: String,
) : TypeSafeProjectAccessorRule
