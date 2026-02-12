package com.fueledbycaffeine.spotlight.cli

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path
import kotlin.system.measureTimeMillis

fun main(args: Array<String>) {
  val reportMode = args.firstOrNull { it.startsWith("--report=") }
    ?.removePrefix("--report=") ?: "impact"
  val buildRoot = args.firstOrNull { !it.startsWith("--") }
    ?.let { Path(it).toAbsolutePath() }
    ?: run {
      System.err.println("Usage: build-impact [--report=impact|inlining|summary|centrality] <path-to-gradle-project>")
      return
    }

  println("Build Impact Analysis: $buildRoot")
  println("Report mode: $reportMode")
  println()

  if (reportMode == "centrality") {
    bcMain(arrayOf(buildRoot.toString()))
    return
  }

  // Build the graph (sequential — everything depends on this)
  val graph = buildGraph(buildRoot)
  val allProjects = graph.dependencyMap.keys.toList()

  // Module types — fast, needed by several downstream steps
  println("Detecting module types...")
  val moduleTypes = allProjects.associateWith { detectModuleType(it) }

  // Run all independent analysis phases in parallel
  val totalMs = measureTimeMillis {
    runBlocking {
      // Phase 1: all independent computations run concurrently
      val pluginsFuture = async(Dispatchers.Default) {
        println("  [parallel] Parsing convention plugins...")
        val result = ConcurrentHashMap<GradlePath, PluginInfo>()
        val ms = measureTimeMillis {
          allProjects.parallelStream().forEach { project ->
            result[project] = parsePluginInfo(project)
          }
        }
        println("  [parallel] Plugins parsed in ${ms}ms")
        result
      }

      val slocFuture = async(Dispatchers.Default) {
        println("  [parallel] Counting source lines...")
        val result = ConcurrentHashMap<GradlePath, Int>()
        val ms = measureTimeMillis {
          allProjects.parallelStream().forEach { project ->
            result[project] = countSourceLines(project, moduleTypes[project] ?: ModuleType.OTHER)
          }
        }
        println("  [parallel] SLOC counted in ${ms}ms")
        result
      }

      val changeFreqFuture = async(Dispatchers.IO) {
        println("  [parallel] Computing change frequency...")
        val result: Map<String, ChangeFrequencyResult>
        val ms = measureTimeMillis {
          result = computeChangeFrequency(buildRoot, graph.projects)
        }
        println("  [parallel] Change frequency computed in ${ms}ms")
        result
      }

      val cyclesFuture = async(Dispatchers.Default) {
        println("  [parallel] Detecting dependency cycles...")
        val result: Map<String, CycleInfo>
        val ms = measureTimeMillis {
          result = detectCycles(graph.dependencyMap)
        }
        val cycleCount = result.values.map { it.cycleMembers }.distinct().size
        println("  [parallel] Cycles detected in ${ms}ms: $cycleCount cycles, ${result.size} modules")
        result
      }

      val critPathFuture = async(Dispatchers.Default) {
        println("  [parallel] Building production-only dep map for critical path...")
        val prodDeps: Map<GradlePath, Set<GradlePath>>
        val filterMs = measureTimeMillis {
          prodDeps = buildProductionDependencyMap(graph.dependencyMap)
        }
        val prodEdges = prodDeps.values.sumOf { it.size }
        val totalEdges = graph.dependencyMap.values.sumOf { it.size }
        println("  [parallel] Production dep map built in ${filterMs}ms: $prodEdges edges (filtered ${totalEdges - prodEdges} test deps)")

        println("  [parallel] Computing critical path...")
        val global: Map<String, CriticalPathInfo>
        val perApp: List<AppCriticalPath>
        val ms = measureTimeMillis {
          global = analyzeCriticalPath(prodDeps, moduleTypes)
          perApp = analyzePerAppCriticalPaths(prodDeps, moduleTypes)
        }
        val maxDepth = global.values.maxOfOrNull { it.depth } ?: 0
        println("  [parallel] Critical path computed in ${ms}ms: depth $maxDepth")
        Pair(global, perApp)
      }

      val publicChainsFuture = async(Dispatchers.Default) {
        println("  [parallel] Analyzing public chains...")
        val result: Map<GradlePath, PublicChainResult>
        val ms = measureTimeMillis {
          result = analyzePublicChains(graph.dependencyMap, graph.reverseDependencyMap, moduleTypes)
        }
        println("  [parallel] Public chains analyzed in ${ms}ms")
        result
      }

      val transitiveFuture = async(Dispatchers.Default) {
        println("  [parallel] Computing transitive counts...")
        val depCounts = ConcurrentHashMap<GradlePath, Int>()
        val depentCounts = ConcurrentHashMap<GradlePath, Int>()
        val ms = measureTimeMillis {
          allProjects.parallelStream().forEach { project ->
            depCounts[project] = countTransitiveDependencies(project, graph.dependencyMap)
            depentCounts[project] = countTransitiveDependents(project, graph.reverseDependencyMap)
          }
        }
        println("  [parallel] Transitive counts computed in ${ms}ms")
        Pair(depCounts, depentCounts)
      }

      // Await phase 1
      val pluginInfos = pluginsFuture.await()
      val slocMap = slocFuture.await()
      val changeFreqs = changeFreqFuture.await()
      val cycles = cyclesFuture.await()
      val (criticalPaths, perAppCriticalPaths) = critPathFuture.await()
      val publicChains = publicChainsFuture.await()
      val (transitiveDepCounts, transitiveDepentCounts) = transitiveFuture.await()

      // Phase 2: ABI estimation needs SLOC results
      println("  [parallel] Estimating ABI surfaces...")
      val abiMap = ConcurrentHashMap<GradlePath, AbiSurfaceInfo>()
      val abiMs = measureTimeMillis {
        allProjects.parallelStream().forEach { project ->
          abiMap[project] = estimateAbiSurface(project, slocMap[project] ?: 0)
        }
      }
      println("  [parallel] ABI surfaces estimated in ${abiMs}ms")

      // Detect inlining candidates
      println("Detecting inlining candidates...")
      val inliningResults = HashMap<GradlePath, InliningResult>()
      for (project in allProjects) {
        val dependents = graph.reverseDependencyMap[project] ?: emptySet()
        inliningResults[project] = detectInliningCandidate(
          project,
          moduleTypes[project] ?: ModuleType.OTHER,
          slocMap[project] ?: 0,
          dependents,
          graph.reverseDependencyMap,
        )
      }

      // Normalization factors
      val maxBC = graph.centrality.values.maxOrNull() ?: 1.0
      val maxDependents = transitiveDepentCounts.values.maxOrNull() ?: 1
      val maxDeps = transitiveDepCounts.values.maxOrNull() ?: 1
      val maxChanges30d = changeFreqs.values.maxOfOrNull { it.commits30d } ?: 1
      val maxChanges90d = changeFreqs.values.maxOfOrNull { it.commits90d } ?: 1
      val maxDirectDeps = graph.dependencyMap.values.maxOfOrNull { it.size } ?: 1

      // Build scores
      println("Computing composite scores...")
      val scores = allProjects.map { project ->
        val bc = graph.centrality[project] ?: 0.0
        val sloc = slocMap[project] ?: 0
        val deps = transitiveDepCounts[project] ?: 0
        val dependents = transitiveDepentCounts[project] ?: 0
        val freq = changeFreqs[project.path]
        val c30d = freq?.commits30d ?: 0
        val c90d = freq?.commits90d ?: 0
        val plugin = pluginInfos[project] ?: PluginInfo(null, false, false, false, 0)
        val inlining = inliningResults[project] ?: InliningResult(InliningCategory.NONE, 0, null)
        val chain = publicChains[project]
        val cycle = cycles[project.path]
        val critPath = criticalPaths[project.path]
        val abi = abiMap[project] ?: AbiSurfaceInfo(0, 0.0)
        val directDeps = (graph.dependencyMap[project] ?: emptySet()).size
        val directDependents = (graph.reverseDependencyMap[project] ?: emptySet()).size

        val change30Score = changeFrequencyScore(c30d, maxChanges30d) * 0.6
        val change90Score = changeFrequencyScore(c90d, maxChanges90d) * 0.4
        val critBonus = if (critPath?.onCriticalPath == true) 5.0 else 0.0
        // Demo, wiring, and app modules intentionally have high direct dep counts (DI aggregation + DAGP)
        val moduleType = moduleTypes[project] ?: ModuleType.OTHER
        val diAggregator = moduleType in setOf(ModuleType.DEMO, ModuleType.WIRING, ModuleType.APP)
        val directPenalty = if (diAggregator) 0.0
          else (directDeps.toDouble() / maxDirectDeps * 5.0).coerceAtMost(5.0)

        ModuleScore(
          path = project.path,
          moduleType = moduleTypes[project] ?: ModuleType.OTHER,
          conventionPlugin = plugin.conventionPluginId,
          betweennessCentrality = bc,
          sloc = sloc,
          transitiveDependents = dependents,
          transitiveDependencies = deps,
          directDependencies = directDeps,
          directDependents = directDependents,
          commits30d = c30d,
          commits90d = c90d,
          publicChainDepth = chain?.chainDepth ?: 0,
          publicDependentCount = chain?.publicDependentCount ?: 0,
          cycleSize = cycle?.cycleSize ?: 0,
          criticalPathDepth = critPath?.depth ?: 0,
          onCriticalPath = critPath?.onCriticalPath ?: false,
          publicDeclarations = abi.publicDeclarations,
          abiDensity = abi.abiDensity,
          inliningCategory = inlining.category,
          inliningTarget = inlining.mergeTarget,
          centralityScore = (bc / maxBC) * 25.0,
          pluginCost = plugin.pluginCost,
          slocScore = slocScore(sloc, moduleTypes[project] ?: ModuleType.OTHER),
          fanoutScore = (dependents.toDouble() / maxDependents * 20.0).coerceAtMost(20.0),
          fragilityScore = (deps.toDouble() / maxDeps * 10.0).coerceAtMost(10.0),
          changeScore = change30Score + change90Score,
          inliningPenalty = inlining.penalty,
          publicChainPenalty = chain?.penalty ?: 0,
          cyclePenalty = cycle?.penalty ?: 0,
          criticalPathBonus = critBonus,
          directDepPenalty = directPenalty,
        )
      }.sortedByDescending { it.total }

      println()

      when (reportMode) {
        "impact" -> printImpactReport(scores)
        "inlining" -> printInliningReport(scores)
        "summary" -> writeSummaryReport(scores, graph, perAppCriticalPaths)
        else -> {
          System.err.println("Unknown report mode: $reportMode")
          return@runBlocking
        }
      }

      // Always write CSV
      val csvFile = buildRoot.resolve("build_impact_report.csv").toFile()
      csvFile.writeText(ModuleScore.CSV_HEADER + "\n")
      scores.forEachIndexed { i, score ->
        csvFile.appendText(score.toCsvRow(i + 1) + "\n")
      }
      println("\nCSV written to: ${csvFile.absolutePath}")
    }
  }
  println("Total analysis time: ${totalMs}ms")
}

private fun printImpactReport(scores: List<ModuleScore>) {
  println("=" .repeat(210))
  println("%-6s %-50s %-8s %6s %5s %5s %5s %5s %5s %5s %4s %4s %5s %5s %5s %5s".format(
    "Rank", "Module", "Type", "TOTAL", "BC", "Plug", "SLOC", "Fan", "Frag", "Chg", "Cyc", "CP", "DDep", "Inl", "PChn", "30d"
  ))
  println("-".repeat(210))

  for ((i, score) in scores.withIndex()) {
    if (i >= 100) break
    val cpMarker = if (score.onCriticalPath) "*" else ""
    println("%-6d %-50s %-8s %6.1f %5.1f %5d %5d %5.1f %5.1f %5.1f %4d %3.1f%1s %5.1f %5d %5d %5d".format(
      i + 1,
      score.path.take(50),
      score.moduleType.label.take(8),
      score.total,
      score.centralityScore,
      score.pluginCost,
      score.slocScore,
      score.fanoutScore,
      score.fragilityScore,
      score.changeScore,
      score.cyclePenalty,
      score.criticalPathBonus,
      cpMarker,
      score.directDepPenalty,
      score.inliningPenalty,
      score.publicChainPenalty,
      score.commits30d,
    ))
  }

  println()
  println("Summary:")
  println("  Total modules scored: ${scores.size}")
  println("  Highest score: ${scores.firstOrNull()?.path} (${"%.1f".format(scores.firstOrNull()?.total)})")

  val typeBreakdown = scores.groupBy { it.moduleType }
    .mapValues { "%.1f".format(it.value.sumOf { s -> s.total } / it.value.size) }
    .entries.sortedByDescending { it.value }
  println("  Average score by type:")
  for ((type, avg) in typeBreakdown) {
    val count = scores.count { it.moduleType == type }
    println("    %-18s %s avg (%d modules)".format(type.label, avg, count))
  }
}

private fun printInliningReport(scores: List<ModuleScore>) {
  val candidates = scores.filter { it.inliningCategory != InliningCategory.NONE }
    .groupBy { it.inliningCategory }

  println("=" .repeat(130))
  println("INLINING OPPORTUNITIES")
  println("=" .repeat(130))

  for (category in InliningCategory.entries) {
    if (category == InliningCategory.NONE) continue
    val modules = candidates[category] ?: continue

    println()
    println("--- ${category.label.uppercase()} (${modules.size} modules) ---")
    println("%-55s %6s %6s %6s  %s".format("Module", "Score", "SLOC", "Deps", "Merge Target"))
    println("-".repeat(130))

    for (score in modules.take(50)) {
      println("%-55s %6.1f %6d %6d  %s".format(
        score.path.take(55),
        score.total,
        score.sloc,
        score.transitiveDependents,
        score.inliningTarget ?: "",
      ))
    }
    if (modules.size > 50) {
      println("  ... and ${modules.size - 50} more")
    }
  }

  println()
  println("Summary:")
  println("  Total inlining candidates: ${candidates.values.sumOf { it.size }}")
  for (category in InliningCategory.entries) {
    if (category == InliningCategory.NONE) continue
    val count = candidates[category]?.size ?: 0
    if (count > 0) {
      println("  ${category.label}: $count modules")
    }
  }
}
