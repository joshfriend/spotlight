package com.fueledbycaffeine.spotlight.buildscript.graph

import com.fueledbycaffeine.spotlight.buildscript.GradlePath

public sealed interface ImplicitDependencyRule {
  public val pattern: Regex
  public val includedProjects: Set<GradlePath>

  public data class BuildscriptMatchRule(
    override val pattern: Regex,
    override val includedProjects: Set<GradlePath>,
  ) : ImplicitDependencyRule

  public data class ProjectPathMatchRule(
    override val pattern: Regex,
    override val includedProjects: Set<GradlePath>,
  ) : ImplicitDependencyRule
}