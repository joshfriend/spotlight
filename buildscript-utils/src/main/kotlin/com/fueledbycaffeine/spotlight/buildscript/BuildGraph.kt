package com.fueledbycaffeine.spotlight.buildscript

import com.fueledbycaffeine.spotlight.buildscript.graph.BreadthFirstSearch
import com.fueledbycaffeine.spotlight.buildscript.graph.Graph
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule

public class BuildGraph private constructor(
  private val allProjects: Set<GradlePath>,
  private val rules: Set<ImplicitDependencyRule> = emptySet(),
): Graph<GradlePath>() {
  internal companion object {
    @JvmStatic
    fun create(
      allProjects: Set<GradlePath>,
      rules: Set<ImplicitDependencyRule> = emptySet(),
    ): BuildGraph {
      return BuildGraph(allProjects, rules)
    }

    @JvmStatic
    fun createFromNode(
      node: GradlePath,
      rules: Set<ImplicitDependencyRule> = emptySet(),
    ): BuildGraph {
      return create(setOf(node), rules)
    }
  }

  override val dependencyMap: Map<GradlePath, Set<GradlePath>> by lazy { BreadthFirstSearch.run(allProjects, rules) }
}

public fun GradlePath.buildGraph(rules: Set<ImplicitDependencyRule> = emptySet()): BuildGraph =
  BuildGraph.createFromNode(this, rules)