package com.fueledbycaffeine.spotlight.buildscript.graph

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinTask

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

      // Process all nodes at this level in parallel using ForkJoinTask
      val tasks = currentLevel.map { node ->
        val task = ForkJoinTask.adapt<Pair<T, Set<T>>> {
          node to node.findSuccessors(rules)
        }
        task.fork() // Submit for parallel execution
        task
      }

      // Collect results and update queue for next level
      for (task in tasks) {
        val (node, successors) = task.join() // Wait for completion
        dependenciesMap[node] = successors
        successors.filter(seen::add).forEach(queue::addLast)
      }
    }

    return dependenciesMap
  }
}