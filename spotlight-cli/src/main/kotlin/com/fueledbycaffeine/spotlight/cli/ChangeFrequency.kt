package com.fueledbycaffeine.spotlight.cli

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import java.io.File
import java.nio.file.Path

data class ChangeFrequencyResult(
  val commits30d: Int,
  val commits90d: Int,
)

/**
 * Compute change frequency for all modules by running a single git log command
 * and binning commit-level changes by module path.
 *
 * Uses `--diff-filter=ACMR` to only count meaningful changes (not deletes/renames)
 * and groups by commit hash so we count actual commits, not files.
 */
fun computeChangeFrequency(
  buildRoot: Path,
  projects: Set<GradlePath>,
): Map<String, ChangeFrequencyResult> {
  // Build module prefix lookup: "common/transaction/public/" -> ":common:transaction:public"
  val modulePathPrefixes = projects.associate { project ->
    val dirPrefix = project.path.removePrefix(":").replace(":", File.separator) + File.separator
    dirPrefix to project.path
  }

  val commits30d = countCommitsPerModule(buildRoot, "30.days.ago", modulePathPrefixes)
  val commits90d = countCommitsPerModule(buildRoot, "90.days.ago", modulePathPrefixes)

  // Merge into a single result per module
  val allPaths = commits30d.keys + commits90d.keys
  return allPaths.associateWith { path ->
    ChangeFrequencyResult(
      commits30d = commits30d[path] ?: 0,
      commits90d = commits90d[path] ?: 0,
    )
  }
}

private fun countCommitsPerModule(
  buildRoot: Path,
  since: String,
  modulePathPrefixes: Map<String, String>,
): Map<String, Int> {
  // Output: one commit hash per line, then changed file paths, separated by blank lines
  val process = ProcessBuilder(
    "git", "log", "--since=$since", "--name-only", "--diff-filter=ACMR", "--pretty=format:%H"
  )
    .directory(buildRoot.toFile())
    .redirectErrorStream(true)
    .start()

  val lines = process.inputStream.bufferedReader().readLines()
  process.waitFor()

  // Parse commit blocks: each block starts with a 40-char hex hash
  val commitsByModule = HashMap<String, MutableSet<String>>()
  var currentCommit: String? = null

  for (line in lines) {
    if (line.length == 40 && line.all { it in '0'..'9' || it in 'a'..'f' }) {
      currentCommit = line
      continue
    }
    if (line.isBlank() || currentCommit == null) continue

    // Match this file to a module
    for ((prefix, gradlePath) in modulePathPrefixes) {
      if (line.startsWith(prefix) && line.substringAfter(prefix).startsWith("src/")) {
        commitsByModule.getOrPut(gradlePath) { mutableSetOf() }.add(currentCommit)
        break
      }
    }
  }

  return commitsByModule.mapValues { it.value.size }
}

/**
 * Score change frequency on a 0-10 scale relative to the maximum.
 */
fun changeFrequencyScore(changes: Int, maxChanges: Int): Double {
  if (maxChanges == 0) return 0.0
  return (changes.toDouble() / maxChanges * 10.0).coerceAtMost(10.0)
}
