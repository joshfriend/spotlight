package com.fueledbycaffeine.spotlight.buildscript.graph

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import java.util.LinkedList

public data class Edge<T>(
  public val accessor: T,
  public val successor: T,
)

public abstract class Graph<T: GraphNode<T>>() {
  public abstract val dependencyMap: Map<T, Set<T>>

  public fun edges(): Set<Edge<T>> {
    return dependencyMap
      .flatMap { (accessor, successors) ->
        successors.map { successor -> Edge(accessor, successor) }
      }
      .toSet()
  }

  public fun successorsOf(node: T): Set<T> = dependencyMap[node]
    ?: throw IllegalArgumentException("$node is not part of this build graph")

  public fun accessorsOf(node: T): Set<T> =
    edges().filter { it.successor == node }.map { it.accessor }.toSet()

  /**
   * Finds the shortest path between source and target nodes using BFS.
   *
   * @param source The starting node
   * @param target The target node
   * @return List representing the shortest path from source to target, or null if no path exists
   */
  public fun findShortestPath(
    source: T,
    target: T
  ): List<T>? {
    val queue = LinkedList<T>()
    val visited = LinkedHashSet<T>(dependencyMap.size)
    val parentMap = LinkedHashMap<T, T?>(dependencyMap.size)

    queue.offer(source)
    visited.add(source)
    parentMap[source] = null

    while (queue.isNotEmpty()) {
      val current = queue.poll()

      if (current == target) {
        return generateSequence(target) { parentMap[it] }.toList().reversed()
      }

      // Process dependencies
      dependencyMap[current]?.forEach { dep ->
        if (dep !in visited) {
          visited.add(dep)
          parentMap[dep] = current
          queue.offer(dep)
        }
      }
    }

    return null
  }
}