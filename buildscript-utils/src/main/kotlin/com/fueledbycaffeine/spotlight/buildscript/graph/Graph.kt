package com.fueledbycaffeine.spotlight.buildscript.graph

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
}