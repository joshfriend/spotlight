package com.fueledbycaffeine.spotlight.graph

import com.fueledbycaffeine.spotlight.utils.GradlePath

internal sealed interface ImplicitDependencyRule {
  val pattern: Regex
  val includedProjects: Set<GradlePath>

  data class BuildscriptMatchRule(
    override val pattern: Regex,
    override val includedProjects: Set<GradlePath>,
  ) : ImplicitDependencyRule

  data class ProjectPathMatchRule(
    override val pattern: Regex,
    override val includedProjects: Set<GradlePath>,
  ) : ImplicitDependencyRule
}