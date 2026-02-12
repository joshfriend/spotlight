package com.fueledbycaffeine.spotlight.cli

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile

private val SOURCE_EXTENSIONS = setOf("kt", "java")
private val SOURCE_SETS = listOf("main", "debug", "release", "beta")

/**
 * Count non-blank source lines across all standard source sets (main, debug, release, beta).
 * Walks both kotlin and java source files in each set.
 */
fun countSourceLines(project: GradlePath, moduleType: ModuleType): Int {
  val projectDir = project.projectDir
  return SOURCE_SETS
    .map { projectDir.resolve("src/$it") }
    .filter { it.exists() }
    .sumOf { countLinesInDir(it) }
}

private fun countLinesInDir(dir: Path): Int {
  var total = 0
  Files.walk(dir).use { stream ->
    stream.filter { it.isRegularFile() && it.extension in SOURCE_EXTENSIONS }
      .forEach { file ->
        Files.lines(file).use { lines ->
          total += lines.filter { it.isNotBlank() }.count().toInt()
        }
      }
  }
  return total
}

/**
 * Convert SLOC count to a tiered score (0-15).
 * App modules are intentionally 0-SLOC (DI aggregation), so they don't get the empty penalty.
 */
fun slocScore(sloc: Int, moduleType: ModuleType): Int {
  return when {
    sloc == 0 && moduleType == ModuleType.APP -> 0
    sloc == 0 -> 2  // Empty module: configuration-time waste
    sloc < 1_000 -> 1
    sloc < 5_000 -> 3
    sloc < 15_000 -> 7
    sloc < 30_000 -> 11
    else -> 15
  }
}
