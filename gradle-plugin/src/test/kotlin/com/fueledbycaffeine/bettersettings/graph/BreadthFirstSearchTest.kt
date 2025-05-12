package com.fueledbycaffeine.bettersettings.graph

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import org.junit.jupiter.api.Test

private class TestNode(override val successors: Set<TestNode>): GraphNode<TestNode>

class BreadthFirstSearchTest {
  @Test fun `returns all child nodes`() {
    val grandchild1 = TestNode(setOf())
    val grandchild2 = TestNode(setOf())
    val child1 = TestNode(setOf(grandchild1, grandchild2))
    val child2 = TestNode(setOf(grandchild2))
    val rootNode = TestNode(setOf(child1, child2))
    val allChildren = BreadthFirstSearch.run(listOf(rootNode))

    assertThat(allChildren).containsExactlyInAnyOrder(
      child1,
      child2,
      grandchild1,
      grandchild2,
    )
  }
}