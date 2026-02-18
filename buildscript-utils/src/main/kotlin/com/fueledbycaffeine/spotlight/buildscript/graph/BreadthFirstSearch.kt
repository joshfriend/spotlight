package com.fueledbycaffeine.spotlight.buildscript.graph

import java.util.*

public object BreadthFirstSearch {
  // Initialize with generously sized capacities to avoid resizing as we grow
  private const val INITIAL_CAPACITY = 4096

  public fun <T : GraphNode<T>> flatten(nodes: Iterable<T>, rules: Set<DependencyRule> = emptySet()): Set<T> {
    val deps = run(nodes, rules)
    // In lieu of a flattenTo() option, this creates an intermediate
    // set to avoid the intermediate list + distinct()
    return buildSet(INITIAL_CAPACITY) {
      // Include the initial nodes in the result
      addAll(nodes)
      for (values in deps.values) {
        addAll(values)
      }
    }
  }

  public fun <T : GraphNode<T>> run(nodes: Iterable<T>, rules: Set<DependencyRule> = emptySet()): Map<T, Set<T>> {
    // one set for all the visited bookkeeping
    val seen = HashSet<T>(INITIAL_CAPACITY)
    val dependenciesMap = LinkedHashMap<T, Set<T>>(INITIAL_CAPACITY)
    val queue = ArrayDeque<T>(INITIAL_CAPACITY)

    queue.addAll(nodes)
    seen += nodes

    while (queue.isNotEmpty()) {
      val nextNode = queue.removeFirst()
      val successors = nextNode.findSuccessors(rules)
      dependenciesMap[nextNode] = successors

      for (successor in successors) {
        if (seen.add(successor)) {
          queue.addLast(successor)
        }
      }
    }

    return dependenciesMap
  }

  /**
   * Finds all nodes that depend on the given target nodes (transitively).
   *
   * This performs a reverse traversal of the dependency graph, starting from the
   * target nodes and finding all nodes that have them as dependencies (directly or transitively).
   *
   * @param targets The target nodes to find dependents for
   * @param allNodes All nodes in the graph (needed to build reverse mappings)
   * @param rules Additional dependency rules to apply
   * @return Set of all nodes that depend on the targets (including the targets themselves)
   */
  public fun <T : GraphNode<T>> affectedProjects(
    targets: Iterable<T>,
    allNodes: Iterable<T>,
    rules: Set<DependencyRule> = emptySet()
  ): Set<T> {
    // Build reverse dependency map: node -> set of nodes that depend on it
    val dependentsMap = buildDependentsGraph(allNodes, rules)

    // BFS from targets using reverse map
    val seen = HashSet<T>(INITIAL_CAPACITY)
    val queue = ArrayDeque<T>(INITIAL_CAPACITY)

    queue.addAll(targets)
    seen.addAll(targets)

    while (queue.isNotEmpty()) {
      val nextNode = queue.removeFirst()
      val accessors = dependentsMap[nextNode] ?: emptySet()

      for (accessor in accessors) {
        if (seen.add(accessor)) {
          queue.addLast(accessor)
        }
      }
    }

    // Return all seen nodes including the original targets
    return seen
  }

  /**
   * Builds a reverse dependency map (node -> nodes that depend on it).
   */
  private fun <T : GraphNode<T>> buildDependentsGraph(
    allNodes: Iterable<T>,
    rules: Set<DependencyRule>
  ): Map<T, Set<T>> {
    val dependentsMap = mutableMapOf<T, MutableSet<T>>()

    for (node in allNodes) {
      val successors = node.findSuccessors(rules)
      for (successor in successors) {
        dependentsMap.getOrPut(successor) { mutableSetOf() }.add(node)
      }
    }

    return dependentsMap
  }
}