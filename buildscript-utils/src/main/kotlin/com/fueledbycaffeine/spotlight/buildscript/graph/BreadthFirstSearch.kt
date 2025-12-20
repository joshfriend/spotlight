package com.fueledbycaffeine.spotlight.buildscript.graph

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.util.*
import java.util.concurrent.ConcurrentHashMap

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
      deps.values.forEach { addAll(it) }
    }
  }

  public fun <T : GraphNode<T>> run(
    nodes: Iterable<T>,
    rules: Set<DependencyRule> = emptySet(),
    config: ParsingConfiguration = ParsingConfiguration.DEFAULT,
  ): Map<T, Set<T>> = runBlocking(Dispatchers.Default) {
    // Thread-safe collections for concurrent coroutines
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
      
      // Process all nodes at this level in parallel using coroutines
      val results = currentLevel.map { node ->
        async { node to node.findSuccessors(rules, config) }
      }.awaitAll()
      
      // Collect results and update queue for next level
      results.forEach { (node, successors) ->
        dependenciesMap[node] = successors
        successors.filter(seen::add).forEach(queue::addLast)
      }
    }

    dependenciesMap
  }
}