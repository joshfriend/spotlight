package com.fueledbycaffeine.spotlight.buildscript.graph

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.squareup.moshi.JsonClass
import dev.zacsweers.moshix.sealed.annotations.TypeLabel

public interface DependencyRule

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

public data class TypeSafeProjectAccessorRule(
  /**
   * Gradle generates an accessor for the root project based on the root project name. Projects in the build are also
   * accessible via this accessor. For example `projects.buildscriptUtils` and `projects.spotlight.buildscriptUtils`
   * are both valid for a project named "spotlight".
   */
  val rootProjectAccessor: String,
  val typeSafeAccessorMap: Map<String, GradlePath>? = null,
) : DependencyRule
