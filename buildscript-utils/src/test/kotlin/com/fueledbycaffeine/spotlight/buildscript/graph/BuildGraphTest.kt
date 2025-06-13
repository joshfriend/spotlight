package com.fueledbycaffeine.spotlight.buildscript.graph

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.fueledbycaffeine.spotlight.buildscript.BuildGraph
import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.writeText

class BuildGraphTest {

  @TempDir
  lateinit var tempDir: Path

  private fun createNode(path: String): GradlePath {
    // Create the project directory and build file
    val gradlePath = GradlePath(tempDir, path)
    gradlePath.projectDir.createDirectories()
    gradlePath.projectDir.resolve("build.gradle").apply {
      createFile()
      writeText("// Test build file")
    }
    
    return GradlePath(tempDir.toFile(), path)
  }

  private fun setupDependencies(vararg dependencies: Pair<GradlePath, Set<GradlePath>>) {
    dependencies.forEach { (project, deps) ->
      val depsText = deps.joinToString("\n") { dep ->
        "  implementation project('${dep.path}')"
      }
      project.buildFilePath.writeText(
        """
        dependencies {
          $depsText
        }
        """.trimIndent()
      )
    }
  }

  @Test
  fun `findShortestPath returns shortest path between nodes`() {
    val nodeA = createNode(":a")
    val nodeB = createNode(":b")
    val nodeC = createNode(":c")
    
    // A -> B -> C
    setupDependencies(
      nodeA to setOf(nodeB),
      nodeB to setOf(nodeC),
      nodeC to emptySet()
    )
    
    val graph = BuildGraph(setOf(nodeA, nodeB, nodeC))
    val path = graph.findShortestPath(nodeA, nodeC)
    
    assertThat(path).isNotNull()
    assertThat(path!!).containsExactly(nodeA, nodeB, nodeC)
  }

  @Test
  fun `findShortestPath returns null when no path exists`() {
    val nodeA = createNode(":a")
    val nodeB = createNode(":b")
    val nodeC = createNode(":c")
    
    // A -> B, C (isolated)
    setupDependencies(
      nodeA to setOf(nodeB),
      nodeB to emptySet(),
      nodeC to emptySet()
    )
    
    val graph = BuildGraph(setOf(nodeA, nodeB, nodeC))
    val path = graph.findShortestPath(nodeA, nodeC)
    
    assertThat(path).isNull()
  }

  @Test
  fun `accessorsOf returns all direct consumers`() {
    val nodeA = createNode(":a")
    val nodeB = createNode(":b")
    val nodeC = createNode(":c")
    val nodeD = createNode(":d")
    
    // B -> A, C -> A, D -> C (so D transitively consumes A)
    setupDependencies(
      nodeA to emptySet(),
      nodeB to setOf(nodeA),
      nodeC to setOf(nodeA),
      nodeD to setOf(nodeC)
    )
    
    val graph = BuildGraph(setOf(nodeA, nodeB, nodeC, nodeD))
    val consumers = graph.accessorsOf(nodeA)
    
    assertThat(consumers).containsExactlyInAnyOrder(nodeB, nodeC)
  }

  @Test
  fun `accessorsOf returns consumers when they exist`() {
    val nodeA = createNode(":a")
    val nodeB = createNode(":b")
    
    // B -> A (B depends on A, so B is a consumer of A)
    setupDependencies(
      nodeA to emptySet(),
      nodeB to setOf(nodeA)
    )
    
    val graph = BuildGraph(setOf(nodeA, nodeB))
    val consumers = graph.accessorsOf(nodeA)
    
    assertThat(consumers).containsExactlyInAnyOrder(nodeB)
  }

  @Test
  fun `accessorsOf returns empty set when no consumers exist`() {
    val nodeA = createNode(":a")
    
    // A has no dependencies and no other nodes depend on A
    setupDependencies(
      nodeA to emptySet()
    )
    
    val graph = BuildGraph(setOf(nodeA))
    val consumers = graph.accessorsOf(nodeA)
    
    assertThat(consumers).isEmpty()
  }
}
