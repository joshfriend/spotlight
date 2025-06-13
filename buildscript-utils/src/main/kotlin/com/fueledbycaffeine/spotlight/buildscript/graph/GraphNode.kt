package com.fueledbycaffeine.spotlight.buildscript.graph

/**
 * Represents a node in a directed acyclic graph (DAG)
 */
public interface GraphNode<T : GraphNode<T>> {
  public fun findSuccessors(rules: Set<DependencyRule>): Set<T>
}