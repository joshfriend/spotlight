package com.fueledbycaffeine.spotlight.cli

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList
import com.fueledbycaffeine.spotlight.buildscript.graph.BetweennessCentrality
import com.fueledbycaffeine.spotlight.buildscript.graph.BreadthFirstSearch
import java.nio.file.Path
import kotlin.system.measureTimeMillis

/**
 * Shared graph-building and analysis utilities used by both the BC-only report
 * and the full build impact report.
 */
data class GraphData(
  val buildRoot: Path,
  val projects: Set<GradlePath>,
  val dependencyMap: Map<GradlePath, Set<GradlePath>>,
  val centrality: Map<GradlePath, Double>,
  val reverseDependencyMap: Map<GradlePath, Set<GradlePath>>,
)

fun buildGraph(buildRoot: Path): GraphData {
  val allProjects = SpotlightProjectList.allProjects(buildRoot)
  val projects = allProjects.read().filter { it.hasBuildFile }
  println("Found ${projects.size} projects")

  println("Building dependency graph...")
  val dependencyMap: Map<GradlePath, Set<GradlePath>>
  val graphTimeMs = measureTimeMillis {
    dependencyMap = BreadthFirstSearch.run(projects)
  }
  val edgeCount = dependencyMap.values.sumOf { it.size }
  println("Graph built in ${graphTimeMs}ms: ${dependencyMap.size} nodes, $edgeCount edges")

  println("Computing betweenness centrality...")
  val centrality: Map<GradlePath, Double>
  val centralityTimeMs = measureTimeMillis {
    centrality = BetweennessCentrality.compute(dependencyMap)
  }
  println("Centrality computed in ${centralityTimeMs}ms")

  println("Building reverse dependency map...")
  val reverseDependencyMap = buildReverseDependencyMap(dependencyMap)

  return GraphData(buildRoot, projects.toSet(), dependencyMap, centrality, reverseDependencyMap)
}

/**
 * Build a reverse map: for each node, which nodes depend on it.
 */
private fun buildReverseDependencyMap(
  dependencyMap: Map<GradlePath, Set<GradlePath>>
): Map<GradlePath, Set<GradlePath>> {
  val reverse = HashMap<GradlePath, MutableSet<GradlePath>>()
  for ((node, deps) in dependencyMap) {
    reverse.getOrPut(node) { mutableSetOf() }
    for (dep in deps) {
      reverse.getOrPut(dep) { mutableSetOf() }.add(node)
    }
  }
  return reverse
}

/**
 * Count transitive dependents using BFS on the reverse graph.
 */
fun countTransitiveDependents(
  node: GradlePath,
  reverseDeps: Map<GradlePath, Set<GradlePath>>
): Int {
  val visited = mutableSetOf(node)
  val queue = ArrayDeque<GradlePath>()
  queue.addAll(reverseDeps[node] ?: emptySet())
  visited.addAll(reverseDeps[node] ?: emptySet())

  while (queue.isNotEmpty()) {
    val current = queue.removeFirst()
    for (dependent in reverseDeps[current] ?: emptySet()) {
      if (visited.add(dependent)) {
        queue.addLast(dependent)
      }
    }
  }
  // Subtract 1 to exclude the node itself
  return visited.size - 1
}

/**
 * Count transitive dependencies using BFS on the forward graph.
 */
fun countTransitiveDependencies(
  node: GradlePath,
  dependencyMap: Map<GradlePath, Set<GradlePath>>
): Int {
  val visited = mutableSetOf(node)
  val queue = ArrayDeque<GradlePath>()
  queue.addAll(dependencyMap[node] ?: emptySet())
  visited.addAll(dependencyMap[node] ?: emptySet())

  while (queue.isNotEmpty()) {
    val current = queue.removeFirst()
    for (dep in dependencyMap[current] ?: emptySet()) {
      if (visited.add(dep)) {
        queue.addLast(dep)
      }
    }
  }
  return visited.size - 1
}
