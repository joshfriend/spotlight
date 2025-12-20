package com.fueledbycaffeine.spotlight.buildscript.graph

import java.util.*

public object BreadthFirstSearch {
  // Initialize with generously sized capacities to avoid resizing as we grow
  private const val INITIAL_CAPACITY = 4096

  public fun <T : GraphNode<T>> flatten(
    nodes: Iterable<T>,
    rules: Set<DependencyRule> = emptySet(),
    config: ParsingConfiguration = ParsingConfiguration.DEFAULT,
  ): Set<T> {
    val deps = run(nodes, rules, config)
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

  public fun <T : GraphNode<T>> run(
    nodes: Iterable<T>,
    rules: Set<DependencyRule> = emptySet(),
    config: ParsingConfiguration = ParsingConfiguration.DEFAULT,
  ): Map<T, Set<T>> {
    // one set for all the visited bookkeeping
    val seen = HashSet<T>(INITIAL_CAPACITY)
    val dependenciesMap = LinkedHashMap<T, Set<T>>(INITIAL_CAPACITY)
    val queue = ArrayDeque<T>(INITIAL_CAPACITY)

    queue.addAll(nodes)
    seen += nodes

    while (queue.isNotEmpty()) {
      val nextNode = queue.removeFirst()
      val successors = nextNode.findSuccessors(rules, config)
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