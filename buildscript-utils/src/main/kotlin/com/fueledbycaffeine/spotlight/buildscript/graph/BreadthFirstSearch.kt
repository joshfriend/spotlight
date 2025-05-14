package com.fueledbycaffeine.spotlight.buildscript.graph

import java.util.*

public object BreadthFirstSearch {
  public fun <T: GraphNode<T>> run(nodes: List<T>, rules: Set<ImplicitDependencyRule> = emptySet()): List<T> {
    val dependenciesMap = mutableMapOf<T, Set<T>>()
    val queue = LinkedList<T>()
    queue.addAll(nodes)

    while (queue.isNotEmpty()) {
      val nextNode = queue.poll()
      if (nextNode !in dependenciesMap) {
        val successors = nextNode.findSuccessors(rules)
        dependenciesMap[nextNode] = successors
        queue.addAll(successors)
      }
    }

    return dependenciesMap.values.flatten().distinct()
  }
}