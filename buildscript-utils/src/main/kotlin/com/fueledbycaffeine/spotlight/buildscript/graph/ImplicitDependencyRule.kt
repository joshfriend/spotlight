package com.fueledbycaffeine.spotlight.buildscript.graph

import com.fueledbycaffeine.spotlight.buildscript.GradlePath

public sealed interface ImplicitDependencyRule {
//  public val pattern: Regex
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
    val rootProjectName: String,
    val typeSafeAccessorMap: Map<String, GradlePath>,
    override val includedProjects: Set<GradlePath> = emptySet(),
  ) : ImplicitDependencyRule
}