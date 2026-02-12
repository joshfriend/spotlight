package com.fueledbycaffeine.spotlight.cli

import kotlin.io.path.Path

/**
 * BC-only entry point, callable from the main dispatcher or directly.
 */
fun bcMain(args: Array<String>) {
  val buildRoot = when {
    args.isNotEmpty() -> Path(args[0]).toAbsolutePath()
    else -> {
      System.err.println("Usage: betweenness-centrality <path-to-gradle-project>")
      return
    }
  }

  println("Analyzing build graph at: $buildRoot")

  val graph = buildGraph(buildRoot)
  val centrality = graph.centrality

  val sorted = centrality.entries
    .sortedByDescending { it.value }

  val maxCentrality = sorted.firstOrNull()?.value ?: 0.0

  println()
  printHeader()
  printSeparator()

  for ((i, entry) in sorted.withIndex()) {
    val (project, score) = entry
    if (score == 0.0 && i >= 50) break // stop printing zero-centrality modules after top 50
    val normalized = if (maxCentrality > 0.0) score / maxCentrality else 0.0
    printRow(i + 1, project.path, score, normalized)
  }

  val nonZero = sorted.count { it.value > 0.0 }
  println()
  println("Summary:")
  println("  Total modules: ${centrality.size}")
  println("  Modules with non-zero centrality: $nonZero")
  println("  Top connector: ${sorted.firstOrNull()?.key?.path ?: "N/A"} (score: ${"%.2f".format(maxCentrality)})")
}

private fun printHeader() {
  println("%-6s %-70s %15s %12s".format("Rank", "Module", "Centrality", "Normalized"))
}

private fun printSeparator() {
  println("-".repeat(105))
}

private fun printRow(rank: Int, module: String, score: Double, normalized: Double) {
  println("%-6d %-70s %15.2f %11.4f".format(rank, module, score, normalized))
}
