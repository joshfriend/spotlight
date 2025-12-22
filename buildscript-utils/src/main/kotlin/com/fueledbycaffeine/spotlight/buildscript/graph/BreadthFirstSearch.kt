package com.fueledbycaffeine.spotlight.buildscript.graph

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

public object BreadthFirstSearch {
  // Initialize with generously sized capacities to avoid resizing as we grow
  private const val INITIAL_CAPACITY = 4096

  public fun <T : GraphNode<T>> flatten(
    nodes: Iterable<T>,
    rules: Set<DependencyRule> = emptySet(),
    parallel: Boolean = false,
  ): Set<T> {
    val deps = run(nodes, rules, parallel)
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
    parallel: Boolean = false,
  ): Map<T, Set<T>> {
    // Thread-safe collections for concurrent execution
    val seen = Collections.newSetFromMap(ConcurrentHashMap<T, Boolean>(INITIAL_CAPACITY))
    val dependenciesMap = ConcurrentHashMap<T, Set<T>>(INITIAL_CAPACITY)
    val queue = ArrayDeque<T>(INITIAL_CAPACITY)

    queue.addAll(nodes)
    seen += nodes

    while (queue.isNotEmpty()) {
      // Collect current level
      val currentLevel = mutableListOf<T>()
      while (queue.isNotEmpty()) {
        currentLevel.add(queue.removeFirst())
      }

      if (currentLevel.isEmpty()) break

      val results = if (parallel) {
        runBlocking(Dispatchers.IO) {
          currentLevel.map { node ->
            async { node to node.findSuccessors(rules) }
          }.awaitAll()
        }
      } else {
        currentLevel.map { node -> node to node.findSuccessors(rules) }
      }

      // Collect results and update queue for next level
      for ((node, successors) in results) {
        dependenciesMap[node] = successors
        successors.filter(seen::add).forEach(queue::addLast)
      }
    }

    return dependenciesMap
  }
}