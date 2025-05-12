package com.fueledbycaffeine.spotlight.graph

import java.util.LinkedList

internal object BreadthFirstSearch {
  fun <T: GraphNode<T>> run(nodes: List<T>): List<T> {
    val dependenciesMap = mutableMapOf<T, Set<T>>()
    val queue = LinkedList<T>()
    queue.addAll(nodes)

    while (queue.isNotEmpty()) {
      val nextNode = queue.poll()
      if (nextNode !in dependenciesMap) {
        nextNode.successors.apply {
          dependenciesMap[nextNode] = this
          queue.addAll(this)
        }
      }
    }

    return dependenciesMap.values.flatten().distinct()
  }
}