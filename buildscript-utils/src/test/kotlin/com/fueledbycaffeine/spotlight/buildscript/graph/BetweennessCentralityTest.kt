package com.fueledbycaffeine.spotlight.buildscript.graph

import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class BetweennessCentralityTest {

  /**
   * Linear chain: A → B → C → D
   *
   * Shortest paths through intermediaries:
   *   (A,C) via B: σ_AC=1, σ_AC(B)=1 → B gets 1
   *   (A,D) via B: σ_AD=1, σ_AD(B)=1 → B gets 1
   *   (A,D) via C: σ_AD=1, σ_AD(C)=1 → C gets 1
   *   (B,D) via C: σ_BD=1, σ_BD(C)=1 → C gets 1
   *
   * Expected: A=0, B=2, C=2, D=0
   */
  @Test fun `linear chain`() {
    val graph = mapOf(
      "A" to setOf("B"),
      "B" to setOf("C"),
      "C" to setOf("D"),
      "D" to emptySet(),
    )
    val result = BetweennessCentrality.compute(graph)
    assertThat(result.getValue("A")).isEqualTo(0.0)
    assertThat(result.getValue("B")).isEqualTo(2.0)
    assertThat(result.getValue("C")).isEqualTo(2.0)
    assertThat(result.getValue("D")).isEqualTo(0.0)
  }

  /**
   * Diamond: A → B, A → C, B → D, C → D
   *
   * Shortest paths:
   *   (A,B): direct, no intermediate
   *   (A,C): direct, no intermediate
   *   (A,D): two paths A→B→D and A→C→D, σ_AD=2
   *     σ_AD(B) = 1, σ_AD(C) = 1
   *     B gets 1/2, C gets 1/2
   *   (B,D): direct, no intermediate
   *   (C,D): direct, no intermediate
   *   No paths exist from B→C, C→B, D→anything
   *
   * Expected: A=0, B=0.5, C=0.5, D=0
   */
  @Test fun `diamond graph with two equal paths`() {
    val graph = mapOf(
      "A" to setOf("B", "C"),
      "B" to setOf("D"),
      "C" to setOf("D"),
      "D" to emptySet(),
    )
    val result = BetweennessCentrality.compute(graph)
    assertThat(result.getValue("A")).isEqualTo(0.0)
    assertThat(result.getValue("B")).isCloseTo(0.5, 0.001)
    assertThat(result.getValue("C")).isCloseTo(0.5, 0.001)
    assertThat(result.getValue("D")).isEqualTo(0.0)
  }

  /**
   * Star topology (directed outward): center → A, center → B, center → C
   *
   * No node is an intermediary on any shortest path (all paths are length 1
   * from center, and no paths exist between leaves).
   *
   * Expected: all zeros
   */
  @Test fun `star topology has zero centrality`() {
    val graph = mapOf(
      "center" to setOf("A", "B", "C"),
      "A" to emptySet(),
      "B" to emptySet(),
      "C" to emptySet(),
    )
    val result = BetweennessCentrality.compute(graph)
    result.values.forEach { assertThat(it).isEqualTo(0.0) }
  }

  /**
   * Bottleneck: A → X, B → X, X → C, X → D
   *
   * Shortest paths through X:
   *   (A,C): A→X→C, X is intermediate → X gets 1
   *   (A,D): A→X→D, X is intermediate → X gets 1
   *   (B,C): B→X→C, X is intermediate → X gets 1
   *   (B,D): B→X→D, X is intermediate → X gets 1
   *
   * No other node is ever an intermediary.
   * Expected: A=0, B=0, X=4, C=0, D=0
   */
  @Test fun `bottleneck node gets high centrality`() {
    val graph = mapOf(
      "A" to setOf("X"),
      "B" to setOf("X"),
      "X" to setOf("C", "D"),
      "C" to emptySet(),
      "D" to emptySet(),
    )
    val result = BetweennessCentrality.compute(graph)
    assertThat(result.getValue("A")).isEqualTo(0.0)
    assertThat(result.getValue("B")).isEqualTo(0.0)
    assertThat(result.getValue("X")).isEqualTo(4.0)
    assertThat(result.getValue("C")).isEqualTo(0.0)
    assertThat(result.getValue("D")).isEqualTo(0.0)
  }

  /**
   * Longer chain with parallel path: A → B → C → D, A → D
   *
   * Shortest paths:
   *   (A,B): direct → no intermediate
   *   (A,C): A→B→C (len 2) → B gets σ_AB/σ_AC * (1+δ_C) = 1/1 * 1 = 1
   *   (A,D): A→D (len 1) vs A→B→C→D (len 3) → shortest is A→D, no intermediate
   *   (B,C): direct → no intermediate
   *   (B,D): B→C→D (len 2) → C gets 1
   *
   * Expected: A=0, B=1, C=1, D=0
   */
  @Test fun `direct shortcut bypasses chain`() {
    val graph = mapOf(
      "A" to setOf("B", "D"),
      "B" to setOf("C"),
      "C" to setOf("D"),
      "D" to emptySet(),
    )
    val result = BetweennessCentrality.compute(graph)
    assertThat(result.getValue("A")).isEqualTo(0.0)
    assertThat(result.getValue("B")).isEqualTo(1.0)
    assertThat(result.getValue("C")).isEqualTo(1.0)
    assertThat(result.getValue("D")).isEqualTo(0.0)
  }

  /**
   * Disconnected components: A → B, C → D (no edges between them)
   *
   * No node is an intermediary on any path (all paths are length 1).
   * Expected: all zeros
   */
  @Test fun `disconnected components have zero centrality`() {
    val graph = mapOf(
      "A" to setOf("B"),
      "B" to emptySet(),
      "C" to setOf("D"),
      "D" to emptySet(),
    )
    val result = BetweennessCentrality.compute(graph)
    result.values.forEach { assertThat(it).isEqualTo(0.0) }
  }

  @Test fun `single node graph`() {
    val graph = mapOf("A" to emptySet<String>())
    val result = BetweennessCentrality.compute(graph)
    assertThat(result.getValue("A")).isEqualTo(0.0)
  }
}
