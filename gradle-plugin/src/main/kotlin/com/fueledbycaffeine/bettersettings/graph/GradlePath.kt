package com.fueledbycaffeine.bettersettings.graph

import com.fueledbycaffeine.bettersettings.utils.BuildFile
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

data class GradlePath(val root: Path, val path: String): GraphNode<GradlePath> {
  constructor(root: File, path: String): this(root.toPath(), path)

  val filesystemPath: Path get() = root.resolve(
    path.removePrefix(GRADLE_PATH_SEP)
      .replace(GRADLE_PATH_SEP, File.separator)
  )

  val buildFilePath: Path get() = when {
    filesystemPath.resolve(GRADLE_SCRIPT).exists() -> filesystemPath.resolve(GRADLE_SCRIPT)
    filesystemPath.resolve(GRADLE_SCRIPT_KOTLIN).exists() -> filesystemPath.resolve(GRADLE_SCRIPT_KOTLIN)
    else -> throw FileNotFoundException("No build.gradle(.kts) for $path found")
  }

  override val children get() = BuildFile(root, buildFilePath).dependencies
}

fun File.gradlePathRelativeTo(buildRoot: File): GradlePath {
  val projectGradlePath = GRADLE_PATH_SEP + this.relativeTo(buildRoot).toString()
    .replace(File.separator, GRADLE_PATH_SEP)
  return GradlePath(buildRoot.toPath(), projectGradlePath)
}

fun Path.gradlePathRelativeTo(buildRoot: Path): GradlePath {
  val projectGradlePath = GRADLE_PATH_SEP + this.relativeTo(buildRoot).toString()
    .replace(File.separator, GRADLE_PATH_SEP)
  return GradlePath(buildRoot, projectGradlePath)
}

fun GradlePath.expandChildProjects(): List<GradlePath> {
  return Files.walk(filesystemPath).parallel()
    .filter { it.name != "build" && it.name != "src" }
    .filter { it.isDirectory() }
    .filter { path -> BUILDFILES.any { path.resolve(it).exists() } }
    .map { it.gradlePathRelativeTo(root) }
    .toList()
}