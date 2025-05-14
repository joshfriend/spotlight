package com.fueledbycaffeine.spotlight.buildscript.graph

public interface GraphNode<T : GraphNode<T>> {
  public fun findSuccessors(rules: Set<ImplicitDependencyRule>): Set<T>
}