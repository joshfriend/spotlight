package com.fueledbycaffeine.spotlight.cli.commands

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.buildGraph

import mu.KotlinLogging
import picocli.CommandLine.*
import java.nio.file.Path
import kotlin.system.exitProcess
import kotlin.time.measureTimedValue

@Command(
  name = "reason",
  mixinStandardHelpOptions = true,
  description = ["Show the dependency path(s) from a project to a target project"]
)
class ReasonCommand : Runnable {

  private val logger = KotlinLogging.logger {}

  @Parameters(index = "0", description = ["Source project path (project producing the dependency)"])
  lateinit var sourcePath: String

  @Parameters(index = "1", description = ["Target project path (desired dependency)"])
  lateinit var targetPath: String

  @Option(names = ["-r", "--root"], description = ["Root directory of multi-project build"], defaultValue = ".")
  lateinit var rootDir: String

  override fun run() {
    val root = Path.of(rootDir).normalize()
    val sourceNode = GradlePath(root, sourcePath)
    val targetNode = GradlePath(root, targetPath)
    
    // Validate source project exists
    if (!sourceNode.hasBuildFile) {
      println("No build script found for source project \"$sourcePath\" under $root")
      exitProcess(1)
    }
    
    // Validate target project exists
    if (!targetNode.hasBuildFile) {
      println("No build script found for target project \"$targetPath\" under $root")
      exitProcess(1)
    }
    
    // Build dependency graph from source
    val (graph, graphDuration) = measureTimedValue { sourceNode.buildGraph() }
    logger.info { "BFS took ${graphDuration.inWholeMilliseconds}ms" }
    
    // Find paths using BFS
    val (path, pathDuration) = measureTimedValue { graph.findShortestPath(sourceNode, targetNode) }
    logger.info { "Path calculation took ${pathDuration.inWholeMilliseconds}ms" }
    
    if (path == null) {
      println("No path found from $sourcePath to $targetPath")
      exitProcess(1)
    }

    printPath(path)
  }

  private fun printPath(path: List<GradlePath>) {
    path.forEachIndexed { index, node ->
      val indent = "  ".repeat(index)
      val prefix = if (index == 0) "" else "└─ "
      println("$indent$prefix${node.path}")
    }
  }
}
