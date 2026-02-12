package com.fueledbycaffeine.spotlight.buildscript.graph

import java.util.ArrayDeque

/**
 * Computes betweenness centrality for nodes in a directed graph using Brandes' algorithm.
 *
 * Betweenness centrality measures how often a node lies on shortest paths between
 * other pairs of nodes. High-centrality modules are critical connectors in the
 * dependency graph — changes to them are likely to have wide-reaching rebuild impact.
 *
 * Complexity: O(V * (V + E)) for unweighted graphs.
 */
public object BetweennessCentrality {

  /**
   * Compute betweenness centrality for every node in the given adjacency map.
   *
   * @param adjacency a map from each node to its set of direct successors (dependencies).
   *   This is the same shape returned by [BreadthFirstSearch.run].
   * @return a map from each node to its betweenness centrality score.
   */
  public fun <T : Any> compute(adjacency: Map<T, Set<T>>): Map<T, Double> {
    val nodes = adjacency.keys
    val centrality = HashMap<T, Double>(nodes.size)
    for (node in nodes) {
      centrality[node] = 0.0
    }

    for (source in nodes) {
      // Brandes' BFS phase
      val stack = ArrayList<T>()
      val predecessors = HashMap<T, MutableList<T>>()
      val shortestPathCount = HashMap<T, Long>()
      val distance = HashMap<T, Int>()
      val queue = ArrayDeque<T>()

      shortestPathCount[source] = 1L
      distance[source] = 0
      queue.addLast(source)

      while (queue.isNotEmpty()) {
        val v = queue.removeFirst()
        stack.add(v)

        val successors = adjacency[v] ?: continue
        val distV = distance.getValue(v)
        val sigmaV = shortestPathCount.getValue(v)

        for (w in successors) {
          // First visit to w
          if (w !in distance) {
            distance[w] = distV + 1
            queue.addLast(w)
          }
          // Is this a shortest path to w?
          if (distance[w] == distV + 1) {
            shortestPathCount[w] = (shortestPathCount[w] ?: 0L) + sigmaV
            predecessors.getOrPut(w) { ArrayList() }.add(v)
          }
        }
      }

      // Brandes' back-propagation phase
      val delta = HashMap<T, Double>()
      while (stack.isNotEmpty()) {
        val w = stack.removeLast()
        val deltaW = delta.getOrDefault(w, 0.0)
        for (v in predecessors[w] ?: emptyList()) {
          val contribution = (shortestPathCount.getValue(v).toDouble() /
            shortestPathCount.getValue(w).toDouble()) * (1.0 + deltaW)
          delta[v] = (delta[v] ?: 0.0) + contribution
        }
        if (w != source) {
          centrality[w] = centrality.getValue(w) + deltaW
        }
      }
    }

    return centrality
  }
}
