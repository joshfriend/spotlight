package com.fueledbycaffeine.spotlight.graph

internal interface GraphNode<T : GraphNode<T>> {
  fun findSuccessors(rules: Set<ImplicitDependencyRule>): Set<T>
}