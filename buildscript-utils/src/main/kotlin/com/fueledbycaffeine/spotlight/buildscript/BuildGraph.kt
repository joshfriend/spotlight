package com.fueledbycaffeine.spotlight.buildscript

import com.fueledbycaffeine.spotlight.buildscript.graph.BreadthFirstSearch
import com.fueledbycaffeine.spotlight.buildscript.graph.DependencyRule
import com.fueledbycaffeine.spotlight.buildscript.graph.Graph

public class BuildGraph private constructor(
  private val projects: Set<GradlePath>,
  private val rules: Set<DependencyRule> = emptySet(),
): Graph<GradlePath>() {
  public companion object {
    @JvmStatic
    public fun create(
      projects: Set<GradlePath>,
      rules: Set<DependencyRule> = emptySet(),
    ): BuildGraph {
      return BuildGraph(projects, rules)
    }

    @JvmStatic
    public fun create(
      node: GradlePath,
      rules: Set<DependencyRule> = emptySet(),
    ): BuildGraph {
      return create(setOf(node), rules)
    }
  }

  override val dependencyMap: Map<GradlePath, Set<GradlePath>> by lazy { BreadthFirstSearch.run(projects, rules) }
}
