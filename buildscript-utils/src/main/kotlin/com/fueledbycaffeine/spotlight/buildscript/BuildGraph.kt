package com.fueledbycaffeine.spotlight.buildscript

import com.fueledbycaffeine.spotlight.buildscript.graph.BreadthFirstSearch
import com.fueledbycaffeine.spotlight.buildscript.graph.Graph
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule
import java.util.LinkedList

public class BuildGraph(
  allProjects: Set<GradlePath>,
  rules: Set<ImplicitDependencyRule> = emptySet(),
): Graph<GradlePath>() {
  public companion object {
    @JvmStatic
    public fun create(
      allProjects: Set<GradlePath>,
      rules: Set<ImplicitDependencyRule> = emptySet(),
    ): BuildGraph {
      return BuildGraph(allProjects, rules)
    }

    @JvmStatic
    public fun createFromNode(
      node: GradlePath,
      rules: Set<ImplicitDependencyRule> = emptySet(),
    ): BuildGraph {
      return create(setOf(node), rules)
    }
  }

  override val dependencyMap: Map<GradlePath, Set<GradlePath>> = BreadthFirstSearch.run(allProjects, rules)
}

public fun GradlePath.buildGraph(rules: Set<ImplicitDependencyRule> = emptySet()): BuildGraph =
  BuildGraph.createFromNode(this, rules)
