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

  @Test fun `affectedProjects returns direct and transitive dependents`() {
    // Graph: grandchild <- child <- parent (parent depends on child, child depends on grandchild)
    val grandchild = TestNode(setOf())
    val child = TestNode(setOf(grandchild))
    val parent = TestNode(setOf(child))
    val allNodes = setOf(parent, child, grandchild)

    // Find all nodes that depend on grandchild
    val result = BreadthFirstSearch.affectedProjects(setOf(grandchild), allNodes)

    assertThat(result).containsExactlyInAnyOrder(grandchild, child, parent)
  }

  @Test fun `affectedProjects returns only target when no dependents`() {
    val isolated = TestNode(setOf())
    val other = TestNode(setOf())
    val allNodes = setOf(isolated, other)

    val result = BreadthFirstSearch.affectedProjects(setOf(isolated), allNodes)

    assertThat(result).containsExactlyInAnyOrder(isolated)
  }

  @Test fun `affectedProjects with multiple targets`() {
    // Graph: leaf3 isolated, leaf1 <- parent1, leaf2 <- parent2, with a root depending on both parents
    val leaf1 = TestNode(setOf())
    val leaf2 = TestNode(setOf())
    val leaf3 = TestNode(setOf())
    val parent1 = TestNode(setOf(leaf1))
    val parent2 = TestNode(setOf(leaf2))
    val root = TestNode(setOf(parent1, parent2))
    val allNodes = setOf(root, parent1, parent2, leaf1, leaf2, leaf3)

    // Find all nodes that depend on leaf1, leaf2, or leaf3
    val result = BreadthFirstSearch.affectedProjects(setOf(leaf1, leaf2, leaf3), allNodes)

    assertThat(result).containsExactlyInAnyOrder(leaf1, leaf2, leaf3, parent1, parent2, root)
  }

  @Test fun `affectedProjects handles diamond dependency`() {
    // Diamond graph: root depends on left and right, both depend on leaf
    //    root
    //   /    \
    // left  right
    //   \    /
    //    leaf
    val leaf = TestNode(setOf())
    val left = TestNode(setOf(leaf))
    val right = TestNode(setOf(leaf))
    val root = TestNode(setOf(left, right))
    val allNodes = setOf(root, left, right, leaf)

    val result = BreadthFirstSearch.affectedProjects(setOf(leaf), allNodes)

    assertThat(result).containsExactlyInAnyOrder(leaf, left, right, root)
  }
}