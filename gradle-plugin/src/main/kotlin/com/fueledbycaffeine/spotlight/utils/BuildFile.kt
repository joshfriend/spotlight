package com.fueledbycaffeine.spotlight.utils

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.text.RegexOption.MULTILINE

internal data class BuildFile(val root: Path, val path: Path) {
  val dependencies: Set<GradlePath> = parseBuildFile(root, path)
}

private val PROJECT_DEP_PATTERN = Regex("^(?:\\s+)?(\\w+)\\W+project\\([\"'](.*)[\"']\\)", MULTILINE)

internal fun parseBuildFile(root: Path, path: Path): Set<GradlePath> {
  return PROJECT_DEP_PATTERN.findAll(path.readText())
    .map { matchResult ->
      val (_, projectPath) = matchResult.destructured
      GradlePath(root, projectPath)
    }
    .toSet()
}