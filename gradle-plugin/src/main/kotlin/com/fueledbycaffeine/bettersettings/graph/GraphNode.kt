package com.fueledbycaffeine.bettersettings.graph

internal interface GraphNode<T : GraphNode<T>> {
  val successors: Set<T>
}