package com.fueledbycaffeine.spotlight.cli

data class ModuleScore(
  val path: String,
  val moduleType: ModuleType,
  val conventionPlugin: String?,

  // Raw metrics
  val betweennessCentrality: Double,
  val sloc: Int,
  val transitiveDependents: Int,
  val transitiveDependencies: Int,
  val directDependencies: Int,
  val directDependents: Int,
  val commits30d: Int,
  val commits90d: Int,
  val publicChainDepth: Int,
  val publicDependentCount: Int,
  val cycleSize: Int,
  val criticalPathDepth: Int,
  val onCriticalPath: Boolean,
  val publicDeclarations: Int,
  val abiDensity: Double,

  // Inlining
  val inliningCategory: InliningCategory,
  val inliningTarget: String?,

  // Scored components (0 to max for each)
  val centralityScore: Double,    // 0-25
  val pluginCost: Int,            // 0-20
  val slocScore: Int,             // 0-15
  val fanoutScore: Double,        // 0-20
  val fragilityScore: Double,     // 0-10
  val changeScore: Double,        // 0-10
  val inliningPenalty: Int,       // 0-10
  val publicChainPenalty: Int,    // 0-8
  val cyclePenalty: Int,          // 0-10
  val criticalPathBonus: Double,  // 0-5
  val directDepPenalty: Double,   // 0-5
) {
  val total: Double
    get() = centralityScore + pluginCost + slocScore +
      fanoutScore + fragilityScore + changeScore +
      inliningPenalty + publicChainPenalty +
      cyclePenalty + criticalPathBonus + directDepPenalty

  companion object {
    val CSV_HEADER = listOf(
      "rank", "module", "type", "convention_plugin", "total_score",
      "centrality_score", "plugin_cost", "sloc_score", "fanout_score",
      "fragility_score", "change_score", "inlining_penalty", "public_chain_penalty",
      "cycle_penalty", "critical_path_bonus", "direct_dep_penalty",
      "bc_raw", "sloc", "transitive_dependents", "transitive_deps",
      "direct_deps", "direct_dependents",
      "commits_30d", "commits_90d", "public_chain_depth", "public_dependent_count",
      "cycle_size", "critical_path_depth", "on_critical_path",
      "public_declarations", "abi_density",
      "inlining_category", "inlining_target",
    ).joinToString(",")
  }

  fun toCsvRow(rank: Int): String {
    return listOf(
      rank, path, moduleType.label, conventionPlugin ?: "", "%.2f".format(total),
      "%.2f".format(centralityScore), pluginCost, slocScore, "%.2f".format(fanoutScore),
      "%.2f".format(fragilityScore), "%.2f".format(changeScore), inliningPenalty, publicChainPenalty,
      cyclePenalty, "%.2f".format(criticalPathBonus), "%.2f".format(directDepPenalty),
      "%.2f".format(betweennessCentrality), sloc, transitiveDependents, transitiveDependencies,
      directDependencies, directDependents,
      commits30d, commits90d, publicChainDepth, publicDependentCount,
      cycleSize, criticalPathDepth, onCriticalPath,
      publicDeclarations, "%.4f".format(abiDensity),
      inliningCategory.label, inliningTarget ?: "",
    ).joinToString(",")
  }
}
