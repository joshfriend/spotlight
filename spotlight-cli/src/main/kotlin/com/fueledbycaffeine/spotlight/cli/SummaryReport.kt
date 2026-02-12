package com.fueledbycaffeine.spotlight.cli

import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Generates a human-readable markdown report summarizing the build impact analysis.
 */
fun generateSummaryReport(
  scores: List<ModuleScore>,
  graph: GraphData,
  perAppCriticalPaths: List<AppCriticalPath> = emptyList(),
): String {
  val edgeCount = graph.dependencyMap.values.sumOf { it.size }
  val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
  val projectName = graph.buildRoot.fileName?.toString() ?: "project"

  return buildString {
    appendLine("# Build Health Report: $projectName")
    appendLine()
    appendLine("_Generated $today by spotlight-cli_")
    appendLine()

    // ── Overview ──
    appendOverview(scores, edgeCount)

    // ── Top Offenders ──
    appendTopOffenders(scores)

    // ── Hotspots: High Churn + High Impact ──
    appendChurnHotspots(scores)

    // ── Dependency Cycles ──
    appendCycleSection(scores)

    // ── Critical Path ──
    appendCriticalPathSection(scores, perAppCriticalPaths)

    // ── Public API Chain Bottlenecks ──
    appendPublicChainSection(scores)

    // ── Direct Dependency Outliers ──
    appendDirectDepSection(scores)

    // ── ABI Surface Analysis ──
    appendAbiSection(scores)

    // ── Structural Waste: Inlining Opportunities ──
    appendInliningSection(scores)

    // ── Module Type Health ──
    appendModuleTypeHealth(scores)

    // ── Recommendations ──
    appendRecommendations(scores)
  }
}

// ── Section builders ──

private fun StringBuilder.appendOverview(scores: List<ModuleScore>, edgeCount: Int) {
  val totalModules = scores.size
  val totalSloc = scores.sumOf { it.sloc }
  val modulesWithKapt = scores.count { it.conventionPlugin?.contains("java") == true || it.pluginCost >= 12 }
  val avgDeps = scores.map { it.transitiveDependencies }.average()
  val avgDependents = scores.map { it.transitiveDependents }.average()
  val modulesChanged30d = scores.count { it.commits30d > 0 }
  val totalCommits30d = scores.sumOf { it.commits30d }
  val inliningCandidates = scores.count { it.inliningCategory != InliningCategory.NONE }
  val pctInlining = if (totalModules > 0) inliningCandidates * 100 / totalModules else 0
  val modulesInCycles = scores.count { it.cycleSize > 0 }
  val cycleCount = scores.filter { it.cycleSize > 0 }.map { it.cycleSize }.distinct().size
  val criticalPathDepth = scores.maxOfOrNull { it.criticalPathDepth } ?: 0
  val onCriticalPath = scores.count { it.onCriticalPath }

  appendLine("## Overview")
  appendLine()
  appendLine("| Metric | Value |")
  appendLine("|--------|-------|")
  appendLine("| Total modules | **${totalModules.fmt()}** |")
  appendLine("| Total dependency edges | **${edgeCount.fmt()}** |")
  appendLine("| Edges per module (avg) | **${"%.1f".format(edgeCount.toDouble() / totalModules)}** |")
  appendLine("| Total source lines | **${totalSloc.fmt()}** |")
  appendLine("| Avg transitive dependencies | **${"%.0f".format(avgDeps)}** |")
  appendLine("| Avg transitive dependents | **${"%.0f".format(avgDependents)}** |")
  appendLine("| Modules with KAPT | **$modulesWithKapt** |")
  appendLine("| Modules in dependency cycles | **$modulesInCycles** ($cycleCount cycles) |")
  appendLine("| Critical path depth | **$criticalPathDepth** ($onCriticalPath modules on path) |")
  appendLine("| Modules changed (last 30d) | **$modulesChanged30d** ($totalCommits30d total commits) |")
  appendLine("| Inlining candidates | **$inliningCandidates** ($pctInlining% of all modules) |")
  appendLine()
}

private fun StringBuilder.appendTopOffenders(scores: List<ModuleScore>) {
  appendLine("## Top 15 Build Impact Modules")
  appendLine()
  appendLine("These modules contribute the most to build times through a combination of graph")
  appendLine("centrality, plugin overhead, code size, rebuild fan-out, and change frequency.")
  appendLine()
  appendLine("| # | Module | Score | Why it ranks high |")
  appendLine("|---|--------|------:|-------------------|")

  for ((i, s) in scores.take(15).withIndex()) {
    val reasons = buildList {
      if (s.centralityScore >= 5) add("high centrality (${"%.0f".format(s.betweennessCentrality.fmt())})")
      if (s.fanoutScore >= 10) add("${s.transitiveDependents.fmt()} transitive dependents")
      if (s.fragilityScore >= 3) add("${s.transitiveDependencies.fmt()} transitive deps")
      if (s.pluginCost >= 10) add("expensive plugin (cost ${s.pluginCost})")
      if (s.slocScore >= 7) add("${s.sloc.fmt()} LOC")
      if (s.changeScore >= 2) add("actively changing (${s.commits30d} commits/30d)")
      if (s.publicChainPenalty >= 5) add("deep public chain (depth ${s.publicChainDepth})")
      if (s.cyclePenalty > 0) add("in dependency cycle (size ${s.cycleSize})")
      if (s.onCriticalPath) add("on critical path (depth ${s.criticalPathDepth})")
      if (s.directDepPenalty >= 2) add("${s.directDependencies} direct deps")
      if (s.inliningPenalty > 0) add("inlining candidate")
    }
    val reasonStr = reasons.joinToString("; ")
    appendLine("| ${i + 1} | `${s.path}` | ${"%.1f".format(s.total)} | $reasonStr |")
  }
  appendLine()
}

private fun StringBuilder.appendChurnHotspots(scores: List<ModuleScore>) {
  // Modules that are both actively changing AND have high structural impact
  val hotspots = scores
    .filter { it.commits30d >= 10 && (it.fanoutScore >= 5 || it.centralityScore >= 3) }
    .sortedByDescending { it.commits30d.toDouble() * (it.fanoutScore + it.centralityScore) }
    .take(10)

  if (hotspots.isEmpty()) return

  appendLine("## Churn Hotspots")
  appendLine()
  appendLine("Modules with high recent change frequency **and** high structural impact.")
  appendLine("Changes to these modules cause the widest rebuild cascades.")
  appendLine()
  appendLine("| Module | 30d commits | 90d commits | Transitive dependents | BC score |")
  appendLine("|--------|------------:|------------:|----------------------:|---------:|")

  for (s in hotspots) {
    appendLine("| `${s.path}` | ${s.commits30d} | ${s.commits90d} | ${s.transitiveDependents.fmt()} | ${"%.0f".format(s.betweennessCentrality)} |")
  }
  appendLine()
}

private fun StringBuilder.appendPublicChainSection(scores: List<ModuleScore>) {
  val publicModules = scores.filter { it.moduleType == ModuleType.PUBLIC }
  val deepChains = publicModules.filter { it.publicChainDepth > 3 }
    .sortedByDescending { it.publicChainDepth }
    .take(10)
  val publicHubs = publicModules.filter { it.publicDependentCount > 10 }
    .sortedByDescending { it.publicDependentCount }
    .take(10)

  if (deepChains.isEmpty() && publicHubs.isEmpty()) return

  appendLine("## Public API Chain Bottlenecks")
  appendLine()
  appendLine("Long `:public` → `:public` dependency chains serialize compilation at the API")
  appendLine("layer, preventing `:impl` modules from building in parallel until the full chain")
  appendLine("resolves. Extracting shared types into flat leaf modules can break these chains.")
  appendLine()

  if (deepChains.isNotEmpty()) {
    appendLine("### Deepest Public Chains")
    appendLine()
    appendLine("| Module | Chain depth | Public dependents | SLOC |")
    appendLine("|--------|------------:|------------------:|-----:|")
    for (s in deepChains) {
      appendLine("| `${s.path}` | ${s.publicChainDepth} | ${s.publicDependentCount} | ${s.sloc.fmt()} |")
    }
    appendLine()
  }

  if (publicHubs.isNotEmpty()) {
    appendLine("### Public Module Hubs")
    appendLine()
    appendLine("These `:public` modules are depended on by the most other `:public` modules,")
    appendLine("making them serialization bottlenecks in the API layer.")
    appendLine()
    appendLine("| Module | Public dependents | Chain depth | SLOC |")
    appendLine("|--------|-----------------:|------------:|-----:|")
    for (s in publicHubs) {
      appendLine("| `${s.path}` | ${s.publicDependentCount} | ${s.publicChainDepth} | ${s.sloc.fmt()} |")
    }
    appendLine()
  }
}

private fun StringBuilder.appendCycleSection(scores: List<ModuleScore>) {
  val inCycles = scores.filter { it.cycleSize > 0 }
  if (inCycles.isEmpty()) return

  // Group by cycle (using cycleSize as a rough grouping — modules in the same cycle have the same size)
  // For a more precise grouping, we'd need the actual cycle members, but this is good enough for reporting
  val distinctCycles = inCycles
    .sortedByDescending { it.cycleSize }
    .distinctBy { it.cycleSize } // Show one representative per cycle size

  appendLine("## Dependency Cycles")
  appendLine()
  appendLine("Dependency cycles force all participating modules to recompile together —")
  appendLine("a change to any member invalidates every other member. These defeat incremental")
  appendLine("compilation and should be broken by extracting shared interfaces.")
  appendLine()
  appendLine("**${inCycles.size} modules** are involved in dependency cycles.")
  appendLine()

  // Show the highest-impact modules in cycles
  val worstCycleModules = inCycles
    .sortedByDescending { it.total }
    .take(10)

  appendLine("### Highest-Impact Modules in Cycles")
  appendLine()
  appendLine("| Module | Score | Cycle size | Transitive dependents | 30d commits |")
  appendLine("|--------|------:|-----------:|----------------------:|------------:|")
  for (s in worstCycleModules) {
    appendLine("| `${s.path}` | ${"%.1f".format(s.total)} | ${s.cycleSize} | ${s.transitiveDependents.fmt()} | ${s.commits30d} |")
  }
  appendLine()
}

private fun StringBuilder.appendCriticalPathSection(
  scores: List<ModuleScore>,
  perAppCriticalPaths: List<AppCriticalPath>,
) {
  appendLine("## Critical Path Analysis")
  appendLine()
  appendLine("The critical path is the longest **library module** dependency chain — the theoretical")
  appendLine("minimum build time with infinite parallelism. App, demo, wiring, and test-app modules")
  appendLine("are excluded since they sit trivially at the top of every chain. **Only optimizations")
  appendLine("to modules on this path reduce wall-clock build time.**")
  appendLine()

  // Global critical path
  val onPath = scores.filter { it.onCriticalPath }
    .sortedByDescending { it.criticalPathDepth }
  if (onPath.isNotEmpty()) {
    val maxDepth = onPath.first().criticalPathDepth

    appendLine("### Global Critical Path (depth $maxDepth, ${onPath.size} modules)")
    appendLine()
    appendLine("| Module | Depth | Score | SLOC | 30d commits |")
    appendLine("|--------|------:|------:|-----:|------------:|")
    for (s in onPath.take(15)) {
      appendLine("| `${s.path}` | ${s.criticalPathDepth} | ${"%.1f".format(s.total)} | ${s.sloc.fmt()} | ${s.commits30d} |")
    }
    if (onPath.size > 15) {
      appendLine("| _...and ${onPath.size - 15} more_ | | | | |")
    }
    appendLine()
  }

  // Per-app critical paths
  val nonEmpty = perAppCriticalPaths.filter { it.chain.isNotEmpty() }
  if (nonEmpty.isNotEmpty()) {
    appendLine("### Per-App Critical Paths")
    appendLine()
    appendLine("The longest library-only dependency chain for each application target.")
    appendLine("Apps with deeper chains take longer to build even with full parallelism.")
    appendLine()
    appendLine("| App | Chain depth | Deepest library module |")
    appendLine("|-----|------------:|------------------------|")
    for (app in nonEmpty.take(20)) {
      val deepest = app.chain.firstOrNull() ?: "-"
      appendLine("| `${app.appPath}` | ${app.depth} | `$deepest` |")
    }
    if (nonEmpty.size > 20) {
      appendLine("| _...and ${nonEmpty.size - 20} more_ | | |")
    }
    appendLine()

    // Show the full chain for the top 3 deepest apps
    // The chain is ordered from the app's deepest dependency down to the leaf.
    // Each module must finish compiling before the one above it can start.
    for (app in nonEmpty.take(3)) {
      if (app.chain.size <= 1) continue
      val shortName = app.appPath.removePrefix(":apps:").ifEmpty { app.appPath }
      appendLine("**`$shortName`** critical chain (depth ${app.depth}):")
      appendLine()
      appendLine("Each module must compile before the one above it can start (leaf builds first):")
      appendLine()
      for ((i, module) in app.chain.reversed().withIndex()) {
        val prefix = if (i == 0) "1." else "${i + 1}."
        val suffix = if (i == app.chain.size - 1) "  ← **blocks app**" else ""
        appendLine("$prefix `$module`$suffix")
      }
      appendLine()
    }
  }
}

private val DI_AGGREGATOR_TYPES = setOf(ModuleType.DEMO, ModuleType.WIRING, ModuleType.APP)

private fun StringBuilder.appendDirectDepSection(scores: List<ModuleScore>) {
  // Exclude demo/wiring/app — they intentionally have high direct dep counts for DI aggregation
  val outliers = scores
    .filter { it.directDependencies >= 30 && it.moduleType !in DI_AGGREGATOR_TYPES }
    .sortedByDescending { it.directDependencies }
    .take(15)
  if (outliers.isEmpty()) return

  appendLine("## Direct Dependency Outliers")
  appendLine()
  appendLine("Modules with an unusually high number of direct `project()` dependencies")
  appendLine("(excluding demo/wiring/app modules which aggregate DI contributions by design).")
  appendLine("This often indicates a module that has accumulated responsibilities and should")
  appendLine("be split, or has unnecessary dependencies that should be pruned.")
  appendLine()
  appendLine("| Module | Direct deps | Direct dependents | Type | SLOC |")
  appendLine("|--------|------------:|------------------:|------|-----:|")
  for (s in outliers) {
    appendLine("| `${s.path}` | ${s.directDependencies} | ${s.directDependents} | ${s.moduleType.label} | ${s.sloc.fmt()} |")
  }
  appendLine()
}

private fun StringBuilder.appendAbiSection(scores: List<ModuleScore>) {
  // Find modules with high ABI density AND high dependents — these are the worst
  // because their large public API means internal changes are likely to leak ABI changes
  val abiRisks = scores
    .filter { it.sloc >= 1000 && it.transitiveDependents >= 100 && it.publicDeclarations > 0 }
    .sortedByDescending { it.abiDensity }
    .take(15)

  // Find modules with low ABI density — good candidates for explicit API mode / ABI jars
  val abiJarCandidates = scores
    .filter { it.sloc >= 5000 && it.transitiveDependents >= 500 && it.publicDeclarations > 0 && it.abiDensity < 0.02 }
    .sortedBy { it.abiDensity }
    .take(10)

  if (abiRisks.isEmpty() && abiJarCandidates.isEmpty()) return

  appendLine("## ABI Surface Analysis")
  appendLine()
  appendLine("The ABI (Application Binary Interface) surface measures how much of a module's")
  appendLine("code is publicly visible. High ABI density means internal changes are more likely")
  appendLine("to trigger downstream recompilation.")
  appendLine()

  if (abiRisks.isNotEmpty()) {
    appendLine("### High ABI Density (recompilation risk)")
    appendLine()
    appendLine("These widely-depended-on modules expose a large proportion of their code as")
    appendLine("public API. Internal changes frequently leak ABI changes and trigger cascading rebuilds.")
    appendLine()
    appendLine("| Module | ABI density | Public decls | SLOC | Dependents |")
    appendLine("|--------|------------:|-------------:|-----:|-----------:|")
    for (s in abiRisks) {
      appendLine("| `${s.path}` | ${"%.1f%%".format(s.abiDensity * 100)} | ${s.publicDeclarations} | ${s.sloc.fmt()} | ${s.transitiveDependents.fmt()} |")
    }
    appendLine()
  }

  if (abiJarCandidates.isNotEmpty()) {
    appendLine("### Good Candidates for ABI Jars / Explicit API Mode")
    appendLine()
    appendLine("These large, widely-depended-on modules have a **small** public API surface")
    appendLine("relative to their size. Enabling Kotlin explicit API mode or ABI jars would let")
    appendLine("internal changes skip downstream recompilation entirely.")
    appendLine()
    appendLine("| Module | ABI density | Public decls | SLOC | Dependents |")
    appendLine("|--------|------------:|-------------:|-----:|-----------:|")
    for (s in abiJarCandidates) {
      appendLine("| `${s.path}` | ${"%.1f%%".format(s.abiDensity * 100)} | ${s.publicDeclarations} | ${s.sloc.fmt()} | ${s.transitiveDependents.fmt()} |")
    }
    appendLine()
  }
}

private fun StringBuilder.appendInliningSection(scores: List<ModuleScore>) {
  val candidates = scores.filter { it.inliningCategory != InliningCategory.NONE }
  if (candidates.isEmpty()) return

  val byCategory = candidates.groupBy { it.inliningCategory }

  appendLine("## Structural Waste: Inlining Opportunities")
  appendLine()
  appendLine("${candidates.size} modules could be eliminated by inlining into their parent")
  appendLine("modules, reducing Gradle configuration overhead and graph complexity.")
  appendLine()

  for (category in InliningCategory.entries) {
    if (category == InliningCategory.NONE) continue
    val modules = byCategory[category] ?: continue

    val description = when (category) {
      InliningCategory.FAKE_INLINE -> "`:fake` modules that can be moved into their sibling `:impl`'s debug source set"
      InliningCategory.TESTING_FIXTURE -> "`:testing`/`:testingAndroid` modules that can become Gradle test fixtures"
      InliningCategory.WIRING_MERGE -> "`:*-wiring`/`:*-robots` modules that can be merged into their sibling `:impl`"
      InliningCategory.EMPTY_MODULE -> "Modules with zero source lines that add configuration overhead for no build output"
      InliningCategory.NONE -> ""
    }

    appendLine("### ${category.label} (${modules.size} modules)")
    appendLine()
    appendLine("$description.")
    appendLine()

    // Show top 5 by score
    appendLine("| Module | Score | SLOC | Dependents |")
    appendLine("|--------|------:|-----:|-----------:|")
    for (s in modules.take(5)) {
      appendLine("| `${s.path}` | ${"%.1f".format(s.total)} | ${s.sloc.fmt()} | ${s.transitiveDependents} |")
    }
    if (modules.size > 5) {
      appendLine("| _...and ${modules.size - 5} more_ | | | |")
    }
    appendLine()
  }
}

private fun StringBuilder.appendModuleTypeHealth(scores: List<ModuleScore>) {
  appendLine("## Module Type Breakdown")
  appendLine()

  val groups = scores.groupBy { it.moduleType }
    .entries
    .sortedByDescending { it.value.size }

  appendLine("| Type | Count | Avg score | Total SLOC | Avg 30d commits |")
  appendLine("|------|------:|----------:|-----------:|----------------:|")
  for ((type, modules) in groups) {
    val avgScore = modules.sumOf { it.total } / modules.size
    val totalSloc = modules.sumOf { it.sloc }
    val avgCommits = modules.sumOf { it.commits30d }.toDouble() / modules.size
    appendLine("| ${type.label} | ${modules.size} | ${"%.1f".format(avgScore)} | ${totalSloc.fmt()} | ${"%.1f".format(avgCommits)} |")
  }
  appendLine()
}

private fun StringBuilder.appendRecommendations(scores: List<ModuleScore>) {
  appendLine("## Recommended Actions")
  appendLine()

  // 1. Highest impact module
  val top = scores.firstOrNull()
  if (top != null) {
    appendLine("### 1. Investigate `${top.path}`")
    appendLine()
    appendLine("This module has the highest composite score (${"%.1f".format(top.total)}). ")
    if (top.centralityScore >= 15) {
      appendLine("It sits on more shortest paths than any other module in the graph, meaning it's")
      appendLine("the primary chokepoint for build parallelism. ")
    }
    if (top.sloc >= 15000) {
      appendLine("At ${top.sloc.fmt()} LOC, it's large enough that recompilation is expensive. ")
    }
    if (top.commits30d >= 20) {
      appendLine("With ${top.commits30d} commits in 30 days, it's actively churning, amplifying its build impact. ")
    }
    appendLine("Consider breaking it into smaller, more focused modules with narrower API surfaces.")
    appendLine()
  }

  // 2. Churn hotspots
  val churnHotspots = scores
    .filter { it.commits30d >= 20 && it.transitiveDependents >= 100 }
    .sortedByDescending { it.commits30d }
    .take(3)
  if (churnHotspots.isNotEmpty()) {
    appendLine("### 2. Stabilize high-churn, high-impact modules")
    appendLine()
    appendLine("These modules are changing frequently and each change invalidates hundreds of dependents:")
    appendLine()
    for (s in churnHotspots) {
      appendLine("- `${s.path}`: ${s.commits30d} commits/30d, ${s.transitiveDependents.fmt()} dependents")
    }
    appendLine()
    appendLine("Consider extracting stable interfaces, using ABI jars, or splitting volatile implementation")
    appendLine("details from stable API surfaces.")
    appendLine()
  }

  // 3. Public chain flattening
  val deepPublicChains = scores
    .filter { it.moduleType == ModuleType.PUBLIC && it.publicChainDepth > 4 }
    .sortedByDescending { it.publicChainDepth }
    .take(3)
  if (deepPublicChains.isNotEmpty()) {
    appendLine("### 3. Flatten public API dependency chains")
    appendLine()
    appendLine("The following `:public` modules sit at the root of deep `:public` → `:public` chains,")
    appendLine("serializing compilation across the API layer:")
    appendLine()
    for (s in deepPublicChains) {
      appendLine("- `${s.path}`: chain depth ${s.publicChainDepth}, ${s.publicDependentCount} public dependents")
    }
    appendLine()
    appendLine("Extract shared value types and identifiers into flat leaf modules to allow parallel compilation.")
    appendLine()
  }

  // 4. Cycle breaking
  val cycleModules = scores.filter { it.cycleSize > 0 }
  if (cycleModules.isNotEmpty()) {
    appendLine("### 4. Break dependency cycles")
    appendLine()
    appendLine("${cycleModules.size} modules are trapped in dependency cycles, defeating incremental compilation.")
    appendLine("Focus on cycles containing high-impact modules:")
    appendLine()
    for (s in cycleModules.sortedByDescending { it.total }.take(3)) {
      appendLine("- `${s.path}`: cycle of ${s.cycleSize}, score ${"%.1f".format(s.total)}")
    }
    appendLine()
    appendLine("Extract shared interfaces into a separate module that both sides depend on,")
    appendLine("breaking the circular reference.")
    appendLine()
  }

  // 5. Inlining
  val inliningCount = scores.count { it.inliningCategory != InliningCategory.NONE }
  if (inliningCount > 0) {
    val fakeCount = scores.count { it.inliningCategory == InliningCategory.FAKE_INLINE }
    val testingCount = scores.count { it.inliningCategory == InliningCategory.TESTING_FIXTURE }
    val wiringCount = scores.count { it.inliningCategory == InliningCategory.WIRING_MERGE }
    val emptyCount = scores.count { it.inliningCategory == InliningCategory.EMPTY_MODULE }

    appendLine("### 5. Pursue module inlining ($inliningCount candidates)")
    appendLine()
    appendLine("Eliminating these modules would reduce Gradle configuration time and simplify the graph:")
    appendLine()
    if (wiringCount > 0) appendLine("- **$wiringCount wiring modules** → merge DI glue into `:impl`")
    if (testingCount > 0) appendLine("- **$testingCount testing modules** → convert to Gradle test fixtures")
    if (fakeCount > 0) appendLine("- **$fakeCount fake modules** → move to `:impl` debug source set")
    if (emptyCount > 0) appendLine("- **$emptyCount empty modules** → remove or consolidate")
    appendLine()
    appendLine("Start with the highest-scored candidates in each category for maximum impact.")
    appendLine()
  }

  // 6. Edge density
  appendLine("### 6. Monitor dependency graph density")
  appendLine()
  appendLine("Track the total edge count over time. Rapid growth in edges (without proportional module")
  appendLine("growth) indicates modules are accumulating unnecessary dependencies. Use the")
  appendLine("`--report=centrality` mode with the historical analysis scripts to track this trend.")
  appendLine()
}

// ── Formatting helpers ──

private fun Int.fmt(): String = "%,d".format(this)

private fun Double.fmt(): Double = this

fun writeSummaryReport(
  scores: List<ModuleScore>,
  graph: GraphData,
  perAppCriticalPaths: List<AppCriticalPath> = emptyList(),
) {
  val report = generateSummaryReport(scores, graph, perAppCriticalPaths)

  val reportFile = graph.buildRoot.resolve("build_health_report.md").toFile()
  reportFile.writeText(report)
  println("Report written to: ${reportFile.absolutePath}")

  // Also print to stdout
  println()
  println(report)
}
