package com.fueledbycaffeine.spotlight.cli.commands

import com.fueledbycaffeine.spotlight.buildscript.BuildGraph
import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList
import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList.Companion.ALL_PROJECTS_LOCATION
import mu.KotlinLogging
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path
import kotlin.system.exitProcess
import kotlin.time.measureTimedValue

@Command(
  name = "consumers",
  mixinStandardHelpOptions = true,
  description = ["Prints the list of projects that consume the given project"],
)
class ConsumersCommand : Runnable {

  private val logger = KotlinLogging.logger {}

  @Parameters(index = "0", description = ["Gradle project path (e.g. :libs:foo:impl)"])
  lateinit var projectPath: String

  @Option(names = ["-r", "--root"], description = ["Root directory of multi-project build"], defaultValue = ".")
  lateinit var rootDir: String

  override fun run() {
    val root = Path.of(rootDir).normalize()
    val node = GradlePath(root, projectPath)

    if (!node.hasBuildFile) {
      println("No build script found for project \"$projectPath\" under $root")
      exitProcess(1)
    }

    val allProjects = SpotlightProjectList.allProjects(root).read()
    logger.info { "$ALL_PROJECTS_LOCATION contains ${allProjects.size} projects" }

    // Build the complete dependency graph
    val (graph, graphDuration) = measureTimedValue { BuildGraph(allProjects) }
    logger.info { "BFS took ${graphDuration.inWholeMilliseconds}ms" }
    
    // Find all consumers (direct and transitive)
    val (allConsumers, duration) = measureTimedValue { graph.accessorsOf(node) }
    logger.info { "Graph traversal took ${duration.inWholeMilliseconds}ms" }

    println("$projectPath has ${allConsumers.size} consumers")
    allConsumers.sortedBy { it.path }.forEach {
      println(it.path)
    }
  }
}
