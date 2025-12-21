package com.fueledbycaffeine.spotlight.buildscript.graph

public object BreadthFirstSearch {
  // Initialize with generously sized capacities to avoid resizing as we grow
  private const val INITIAL_CAPACITY = 4096

  public fun <T : GraphNode<T>> flatten(
    nodes: Iterable<T>,
    rules: Set<DependencyRule> = emptySet(),
  ): Set<T> {
    val deps = run(nodes, rules)
    // In lieu of a flattenTo() option, this creates an intermediate
    // set to avoid the intermediate list + distinct()
    return buildSet(INITIAL_CAPACITY) {
      // Include the initial nodes in the result
      addAll(nodes)
      deps.values.forEach { addAll(it) }
    }
  }

  public fun <T : GraphNode<T>> run(
    nodes: Iterable<T>,
    rules: Set<DependencyRule> = emptySet(),
  ): Map<T, Set<T>> {
    // Simple collections - no need for thread-safe since we're running synchronously
    val seen = mutableSetOf<T>()
    val dependenciesMap = mutableMapOf<T, Set<T>>()
    val queue = ArrayDeque<T>(INITIAL_CAPACITY)

    queue.addAll(nodes)
    seen += nodes

    while (queue.isNotEmpty()) {
      val node = queue.removeFirst()
      val successors = node.findSuccessors(rules)
      dependenciesMap[node] = successors
      successors.filter(seen::add).forEach(queue::addLast)
    }

    return dependenciesMap
  }
}