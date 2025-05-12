package com.fueledbycaffeine.spotlight.utils

import com.fueledbycaffeine.spotlight.graph.GraphNode
import org.gradle.api.initialization.Settings
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.relativeTo

private const val GRADLE_PATH_SEP = ":"
private const val GRADLE_SCRIPT = "build.gradle"
private const val GRADLE_SCRIPT_KOTLIN = "build.gradle.kts"
private val BUILDFILES = listOf(GRADLE_SCRIPT, GRADLE_SCRIPT_KOTLIN)

internal data class GradlePath(val root: Path, val path: String): GraphNode<GradlePath> {
  constructor(root: File, path: String): this(root.toPath(), path)

  val projectDir: Path get() = root.resolve(
    path.removePrefix(GRADLE_PATH_SEP)
      .replace(GRADLE_PATH_SEP, File.separator)
  )

  val buildFilePath: Path get() = when {
    projectDir.resolve(GRADLE_SCRIPT).exists() -> projectDir.resolve(GRADLE_SCRIPT)
    projectDir.resolve(GRADLE_SCRIPT_KOTLIN).exists() -> projectDir.resolve(GRADLE_SCRIPT_KOTLIN)
    else -> throw FileNotFoundException("No build.gradle(.kts) for $path found")
  }

  val isRootProject: Boolean get() = path == GRADLE_PATH_SEP

  override val successors get() = BuildFile(root, buildFilePath).dependencies
}

internal fun File.gradlePathRelativeTo(buildRoot: File): GradlePath {
  val projectGradlePath = GRADLE_PATH_SEP + this.relativeTo(buildRoot).toString()
    .replace(File.separator, GRADLE_PATH_SEP)
  return GradlePath(buildRoot.toPath(), projectGradlePath)
}

internal fun Path.gradlePathRelativeTo(buildRoot: Path): GradlePath {
  val projectGradlePath = GRADLE_PATH_SEP + this.relativeTo(buildRoot).toString()
    .replace(File.separator, GRADLE_PATH_SEP)
  return GradlePath(buildRoot, projectGradlePath)
}

internal fun GradlePath.expandChildProjects(): List<GradlePath> {
  return Files.walk(projectDir).parallel()
    .filter { it.name != "build" && it.name != "src" }
    .filter { it.isDirectory() }
    .filter { path -> BUILDFILES.any { path.resolve(it).exists() } }
    .map { it.gradlePathRelativeTo(root) }
    .toList()
}

internal fun Settings.guessProjectsFromTaskRequests(): List<GradlePath> {
  return startParameter.taskRequests.flatMap { it.args }
    .filter { it.looksLikeAGradlePath }
    .map {
      val path = GradlePath(rootDir, it.projectPathGuess)
      if (!path.projectDir.exists()) {
        throw FileNotFoundException("${it.projectPathGuess} is not a project dir")
      }
      path
    }
}

private val String.looksLikeAGradlePath: Boolean
  get() = contains(GRADLE_PATH_SEP)

private val String.projectPathGuess: String
  get() = GRADLE_PATH_SEP + substringBeforeLast(GRADLE_PATH_SEP).removePrefix(GRADLE_PATH_SEP)

internal fun Settings.include(paths: List<GradlePath>) = include(paths.map { it.path })