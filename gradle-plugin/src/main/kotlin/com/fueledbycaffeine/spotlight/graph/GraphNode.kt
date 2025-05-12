package com.fueledbycaffeine.spotlight.graph

internal interface GraphNode<T : GraphNode<T>> {
  val successors: Set<T>
}