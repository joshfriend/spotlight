package com.fueledbycaffeine.bettersettings.graph

interface GraphNode<T : GraphNode<T>> {
  val children: Set<T>
}