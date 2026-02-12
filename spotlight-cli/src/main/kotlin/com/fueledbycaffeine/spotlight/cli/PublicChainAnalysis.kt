package com.fueledbycaffeine.spotlight.cli

import com.fueledbycaffeine.spotlight.buildscript.GradlePath

data class PublicChainResult(
  val chainDepth: Int,
  val publicDependentCount: Int,
  val penalty: Int,
)

/**
 * Analyze public-to-public dependency chains.
 *
 * Builds a filtered subgraph containing only PUBLIC -> PUBLIC edges,
 * then computes chain depth (longest path) and public in-degree for each node.
 */
fun analyzePublicChains(
  dependencyMap: Map<GradlePath, Set<GradlePath>>,
  reverseDeps: Map<GradlePath, Set<GradlePath>>,
  moduleTypes: Map<GradlePath, ModuleType>,
): Map<GradlePath, PublicChainResult> {
  val publicNodes = moduleTypes.filterValues { it == ModuleType.PUBLIC }.keys

  // Build public-only subgraph
  val publicDeps = HashMap<GradlePath, Set<GradlePath>>()
  for (node in publicNodes) {
    val deps = dependencyMap[node] ?: emptySet()
    publicDeps[node] = deps.filter { it in publicNodes }.toSet()
  }

  // Build public-only reverse map
  val publicReverse = HashMap<GradlePath, MutableSet<GradlePath>>()
  for (node in publicNodes) {
    publicReverse.getOrPut(node) { mutableSetOf() }
    for (dep in publicDeps[node] ?: emptySet()) {
      publicReverse.getOrPut(dep) { mutableSetOf() }.add(node)
    }
  }

  // Compute longest chain depth from each node using memoized DFS
  val depthCache = HashMap<GradlePath, Int>()
  fun chainDepth(node: GradlePath, visiting: MutableSet<GradlePath> = mutableSetOf()): Int {
    depthCache[node]?.let { return it }
    if (!visiting.add(node)) return 0 // cycle guard

    val deps = publicDeps[node] ?: emptySet()
    val depth = if (deps.isEmpty()) 0
    else 1 + (deps.maxOfOrNull { chainDepth(it, visiting) } ?: 0)

    visiting.remove(node)
    depthCache[node] = depth
    return depth
  }

  val results = HashMap<GradlePath, PublicChainResult>()
  for (node in publicNodes) {
    val depth = chainDepth(node)
    val publicDependentCount = publicReverse[node]?.size ?: 0

    var penalty = 0
    // Chain depth penalty: 3-8 points for depth > 3
    if (depth > 3) {
      penalty += (depth - 3).coerceAtMost(5) + 3
    }
    // Public hub penalty: 3-5 points for >15 public dependents
    if (publicDependentCount > 15) {
      penalty += ((publicDependentCount - 15) / 10).coerceIn(3, 5)
    }

    results[node] = PublicChainResult(depth, publicDependentCount, penalty)
  }

  return results
}
