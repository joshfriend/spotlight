package com.fueledbycaffeine.spotlight.cli.commands

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.graph.BreadthFirstSearch
import mu.KotlinLogging
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path
import kotlin.system.exitProcess
import kotlin.time.measureTimedValue

@Command(
  name = "list-dependencies",
  mixinStandardHelpOptions = true,
  description = ["Prints the full list of dependencies for a Gradle project path"],
)
class ListDependenciesCommand : Runnable {

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

    // Build transitive dependency set (BreadthFirstSearch flattens BFS result)
    val (deps, duration) = measureTimedValue { BreadthFirstSearch.flatten(listOf(node)) }
    logger.info { "BFS took ${duration.inWholeMilliseconds}ms" }

    println("$projectPath has ${deps.size} dependencies")
    deps.sortedBy { it.path }.forEach {
      println(it.path)
    }
  }
}
