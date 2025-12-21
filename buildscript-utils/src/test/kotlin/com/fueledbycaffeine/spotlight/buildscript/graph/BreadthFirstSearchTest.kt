package com.fueledbycaffeine.spotlight.buildscript.graph

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import org.junit.jupiter.api.Test

private class TestNode(val successors: Set<TestNode>) : GraphNode<TestNode> {
  override fun findSuccessors(rules: Set<DependencyRule>): Set<TestNode> = successors
}

class BreadthFirstSearchTest {
  @Test fun `returns all child nodes`() {
    val grandchild1 = TestNode(setOf())
    val grandchild2 = TestNode(setOf())
    val child1 = TestNode(setOf(grandchild1, grandchild2))
    val child2 = TestNode(setOf(grandchild2))
    val rootNode = TestNode(setOf(child1, child2))
    val allChildren = BreadthFirstSearch.flatten(setOf(rootNode))

    assertThat(allChildren).containsExactlyInAnyOrder(
      rootNode,
      child1,
      child2,
      grandchild1,
      grandchild2,
    )
  }

  @Test fun `includes initial nodes in flattened result`() {
    val leaf1 = TestNode(setOf())
    val leaf2 = TestNode(setOf())
    val parent1 = TestNode(setOf(leaf1))
    val parent2 = TestNode(setOf(leaf2))

    val result = BreadthFirstSearch.flatten(setOf(parent1, parent2))

    assertThat(result).containsExactlyInAnyOrder(
      parent1,
      parent2,
      leaf1,
      leaf2,
    )
  }
}