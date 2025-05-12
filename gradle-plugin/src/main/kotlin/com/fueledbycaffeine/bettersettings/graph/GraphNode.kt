package com.fueledbycaffeine.bettersettings.graph

interface GraphNode<T : GraphNode<T>> {
  val successors: Set<T>
}