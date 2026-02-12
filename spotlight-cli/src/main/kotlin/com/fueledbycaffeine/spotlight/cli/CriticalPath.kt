package com.fueledbycaffeine.spotlight.cli

import com.fueledbycaffeine.spotlight.buildscript.GradlePath

data class CriticalPathInfo(
  /** Longest path from this node down to any leaf in the dependency graph. */
  val depth: Int,
  /**
   * Whether this library module sits on the global critical path — the longest dependency
   * chain among library modules (excluding app/demo/test-app/wiring build targets).
   */
  val onCriticalPath: Boolean,
)

/**
 * The critical path traced for a single app target, showing which library modules
 * serialize its compilation.
 */
data class AppCriticalPath(
  val appPath: String,
  /** Depth of the longest library-only chain reachable from this app. */
  val depth: Int,
  /** Ordered list of modules on the critical path (deepest first, leaf last). */
  val chain: List<String>,
)

/** Module types that are build targets or glue — not library modules worth optimizing. */
private val EXCLUDED_ROOT_TYPES = setOf(
  ModuleType.APP, ModuleType.DEMO, ModuleType.TEST_APP, ModuleType.TEST_SUITE, ModuleType.WIRING,
)

/**
 * Compute critical path analysis using iterative reverse topological order.
 *
 * The "critical path" is the longest dependency chain among **library modules only**
 * (excluding app/demo/test-app/wiring targets, which are trivially at the top of every chain).
 * This identifies which library modules serialize compilation the most.
 *
 * O(V + E), fully iterative via Kahn's algorithm.
 */
fun analyzeCriticalPath(
  dependencyMap: Map<GradlePath, Set<GradlePath>>,
  moduleTypes: Map<GradlePath, ModuleType>,
): Map<String, CriticalPathInfo> {
  val depth = computeDepthMap(dependencyMap)

  // Trace the global critical path from the deepest LIBRARY module
  val criticalPathNodes = mutableSetOf<String>()
  val libraryDepths = depth.filter { (node, _) ->
    val type = moduleTypes[node]
    type == null || type !in EXCLUDED_ROOT_TYPES
  }
  val maxLibraryDepth = libraryDepths.values.maxOrNull() ?: 0
  val roots = libraryDepths.filter { it.value == maxLibraryDepth }.keys

  val bestSuccessor = computeBestSuccessorMap(dependencyMap, depth)
  for (root in roots) {
    val visited = mutableSetOf<GradlePath>()
    var current: GradlePath? = root
    while (current != null && visited.add(current)) {
      criticalPathNodes.add(current.path)
      current = bestSuccessor[current]
    }
  }

  return dependencyMap.keys.associate { node ->
    node.path to CriticalPathInfo(
      depth = depth[node] ?: 0,
      onCriticalPath = node.path in criticalPathNodes,
    )
  }
}

/**
 * Compute per-app critical paths — for each real app target (excluding demos),
 * trace the longest library-only dependency chain.
 *
 * This answers: "for each app, which library modules serialize its compilation?"
 */
fun analyzePerAppCriticalPaths(
  dependencyMap: Map<GradlePath, Set<GradlePath>>,
  moduleTypes: Map<GradlePath, ModuleType>,
): List<AppCriticalPath> {
  // Only analyze real apps, not demos
  val appTargets = moduleTypes.filter { it.value == ModuleType.APP }.keys

  // Pre-compute depth and successor maps
  val depth = computeDepthMap(dependencyMap)
  val bestSuccessor = computeBestSuccessorMap(dependencyMap, depth)

  return appTargets.map { app ->
    // Find the deepest library-only dependency reachable from this app
    val directDeps = dependencyMap[app] ?: emptySet()

    // Among direct deps that are library modules, find the one with the greatest depth
    val libraryDeps = directDeps.filter { dep ->
      val type = moduleTypes[dep]
      type != null && type !in EXCLUDED_ROOT_TYPES
    }

    // Also check through wiring modules — follow one hop through wiring to find libraries
    val wiringDeps = directDeps.filter { moduleTypes[it] == ModuleType.WIRING }
    val librariesThroughWiring = wiringDeps.flatMap { wiring ->
      (dependencyMap[wiring] ?: emptySet()).filter { dep ->
        val type = moduleTypes[dep]
        type != null && type !in EXCLUDED_ROOT_TYPES
      }
    }

    val allLibraryRoots = (libraryDeps + librariesThroughWiring).toSet()
    val deepestRoot = allLibraryRoots.maxByOrNull { depth[it] ?: 0 }

    if (deepestRoot == null) {
      AppCriticalPath(app.path, 0, emptyList())
    } else {
      val chain = mutableListOf<String>()
      val visited = mutableSetOf<GradlePath>()
      var current: GradlePath? = deepestRoot
      while (current != null && visited.add(current)) {
        chain.add(current.path)
        current = bestSuccessor[current]
      }
      AppCriticalPath(app.path, depth[deepestRoot] ?: 0, chain)
    }
  }.sortedByDescending { it.depth }
}

// ── Internal helpers ──

/**
 * Compute depth (longest path to leaf) for every node using Kahn's algorithm
 * in reverse topological order.
 */
private fun computeDepthMap(
  dependencyMap: Map<GradlePath, Set<GradlePath>>,
): Map<GradlePath, Int> {
  val unprocessedDeps = HashMap<GradlePath, Int>()
  for ((node, deps) in dependencyMap) {
    unprocessedDeps[node] = deps.size
  }

  val reverseDeps = HashMap<GradlePath, MutableList<GradlePath>>()
  for ((node, deps) in dependencyMap) {
    for (dep in deps) {
      reverseDeps.getOrPut(dep) { mutableListOf() }.add(node)
    }
  }

  val depth = HashMap<GradlePath, Int>()
  val queue = ArrayDeque<GradlePath>()

  // Start with leaves
  for ((node, count) in unprocessedDeps) {
    if (count == 0) {
      queue.addLast(node)
      depth[node] = 0
    }
  }

  // Handle nodes that appear only as dependencies (not in the key set)
  for ((_, deps) in dependencyMap) {
    for (dep in deps) {
      if (dep !in unprocessedDeps && dep !in depth) {
        depth[dep] = 0
        queue.addLast(dep)
      }
    }
  }

  // Process in reverse topological order
  while (queue.isNotEmpty()) {
    val node = queue.removeFirst()
    val nodeDepth = depth[node] ?: 0

    for (dependent in reverseDeps[node] ?: emptyList()) {
      val candidateDepth = nodeDepth + 1
      val currentBest = depth[dependent] ?: 0
      if (candidateDepth > currentBest) {
        depth[dependent] = candidateDepth
      }

      val remaining = (unprocessedDeps[dependent] ?: 1) - 1
      unprocessedDeps[dependent] = remaining
      if (remaining == 0) {
        queue.addLast(dependent)
      }
    }
  }

  // Cycle fallback
  for (node in dependencyMap.keys) {
    if (node !in depth) {
      depth[node] = 0
    }
  }

  return depth
}

/**
 * Compute best successor (the dependency with the greatest depth) for each node,
 * used for tracing the critical path chain.
 */
private fun computeBestSuccessorMap(
  dependencyMap: Map<GradlePath, Set<GradlePath>>,
  depth: Map<GradlePath, Int>,
): Map<GradlePath, GradlePath?> {
  val result = HashMap<GradlePath, GradlePath?>()
  for ((node, deps) in dependencyMap) {
    result[node] = deps.maxByOrNull { depth[it] ?: 0 }
  }
  return result
}
