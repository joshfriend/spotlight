package com.fueledbycaffeine.spotlight.buildscript.graph

import com.fueledbycaffeine.spotlight.buildscript.GradlePath

public sealed interface ImplicitDependencyRule {
  public val includedProjects: Set<GradlePath>

  public data class BuildscriptMatchRule(
    val pattern: Regex,
    override val includedProjects: Set<GradlePath>,
  ) : ImplicitDependencyRule

  public data class ProjectPathMatchRule(
    val pattern: Regex,
    override val includedProjects: Set<GradlePath>,
  ) : ImplicitDependencyRule

  public data class TypeSafeProjectAccessorRule(
    /**
     * Gradle generates an accessor for the root project based on the root project name. Projects in the build are also
     * accessible via this accessor. For example `projects.buildscriptUtils` and `projects.spotlight.buildscriptUtils`
     * are both valid for a project named "spotlight".
     */
    val rootProjectAccessor: String,
    val typeSafeAccessorMap: Map<String, GradlePath>? = null,
    override val includedProjects: Set<GradlePath> = emptySet(),
  ) : ImplicitDependencyRule
}