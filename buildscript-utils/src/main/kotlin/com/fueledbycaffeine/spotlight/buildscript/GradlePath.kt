package com.fueledbycaffeine.spotlight.buildscript

import com.fueledbycaffeine.spotlight.buildscript.graph.GraphNode
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.relativeTo

public const val GRADLE_PATH_SEP: String = ":"
public const val GRADLE_SCRIPT: String = "build.gradle"
public const val GRADLE_SCRIPT_KOTLIN: String = "build.gradle.kts"

public data class GradlePath(
  public val root: Path,
  public val path: String
): GraphNode<GradlePath> {
  public constructor(root: File, path: String): this(root.toPath(), path)

  public val projectDir: Path get() = root.resolve(
    path.removePrefix(GRADLE_PATH_SEP)
      .replace(GRADLE_PATH_SEP, File.separator)
  )

  public val hasBuildFile: Boolean
    get() = projectDir.resolve(GRADLE_SCRIPT).exists() ||
      projectDir.resolve(GRADLE_SCRIPT_KOTLIN).exists()

  public val buildFilePath: Path get() = when {
    projectDir.resolve(GRADLE_SCRIPT).exists() -> projectDir.resolve(GRADLE_SCRIPT)
    projectDir.resolve(GRADLE_SCRIPT_KOTLIN).exists() -> projectDir.resolve(GRADLE_SCRIPT_KOTLIN)
    else -> throw FileNotFoundException("No build.gradle(.kts) for $path found")
  }

  public val isRootProject: Boolean get() = path == GRADLE_PATH_SEP

  public override fun findSuccessors(rules: Set<ImplicitDependencyRule>): Set<GradlePath> {
    return BuildFile(this).parseDependencies(rules)
  }
}

public fun File.gradlePathRelativeTo(buildRoot: File): GradlePath {
  val projectGradlePath = GRADLE_PATH_SEP + this.relativeTo(buildRoot).toString()
    .replace(File.separator, GRADLE_PATH_SEP)
  return GradlePath(buildRoot.toPath(), projectGradlePath)
}

public fun Path.gradlePathRelativeTo(buildRoot: Path): GradlePath {
  val projectGradlePath = GRADLE_PATH_SEP + this.relativeTo(buildRoot).toString()
    .replace(File.separator, GRADLE_PATH_SEP)
  return GradlePath(buildRoot, projectGradlePath)
}
