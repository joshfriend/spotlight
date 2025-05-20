package com.fueledbycaffeine.spotlight.buildscript.graph

import java.util.*

public object BreadthFirstSearch {
  // Initialize with generously sized capacities to avoid resizing as we grow
  private const val INITIAL_CAPACITY = 4096

  public fun <T : GraphNode<T>> flatten(nodes: Set<T>, rules: Set<ImplicitDependencyRule> = emptySet()): List<T> {
    val deps = run(nodes, rules)
    // In lieu of a flattenTo() option, this creates an intermediate
    // set to avoid the intermediate list + distinct()
    return buildList {
      val seen = LinkedHashSet<T>(INITIAL_CAPACITY)
      for (values in deps.values) {
        for (dep in values) {
          if (seen.add(dep)) {
            add(dep)
          }
        }
      }
    }
  }

  public fun <T : GraphNode<T>> run(nodes: Set<T>, rules: Set<ImplicitDependencyRule> = emptySet()): Map<T, Set<T>> {
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
}