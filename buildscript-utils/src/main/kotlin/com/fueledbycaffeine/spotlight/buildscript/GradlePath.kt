package com.fueledbycaffeine.spotlight.buildscript

import com.fueledbycaffeine.spotlight.buildscript.graph.GraphNode
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale.getDefault
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.relativeTo

public const val GRADLE_PATH_SEP: String = ":"
public const val GRADLE_SCRIPT: String = "build.gradle"
public const val GRADLE_SCRIPT_KOTLIN: String = "build.gradle.kts"
public val BUILDSCRIPTS: List<String> = listOf(GRADLE_SCRIPT, GRADLE_SCRIPT_KOTLIN)

public data class GradlePath(
  public val root: Path,
  public val path: String,
): GraphNode<GradlePath> {
  public constructor(root: File, path: String): this(root.toPath(), path)

  /**
   * The [Path] where this project is located
   */
  public val projectDir: Path get() = root.resolve(
    path.removePrefix(GRADLE_PATH_SEP)
      .replace(GRADLE_PATH_SEP, File.separator)
  )

  /**
   * Indicates if this project path has either a build.gradle or a build.gradle.kts script
   */
  public val hasBuildFile: Boolean
    get() = projectDir.resolve(GRADLE_SCRIPT).exists() ||
      projectDir.resolve(GRADLE_SCRIPT_KOTLIN).exists()

  /**
   * The buildscript [Path] for this Gradle path.
   *
   * @throws FileNotFoundException if there is no buildscript.
   */
  public val buildFilePath: Path get() = when {
    projectDir.resolve(GRADLE_SCRIPT).exists() -> projectDir.resolve(GRADLE_SCRIPT)
    projectDir.resolve(GRADLE_SCRIPT_KOTLIN).exists() -> projectDir.resolve(GRADLE_SCRIPT_KOTLIN)
    else -> throw FileNotFoundException("No build.gradle(.kts) for $path found")
  }

  /**
   * The equivalent type-safe project accessor of [path] used to reference this Gradle project in a buildscript
   */
  public val typeSafeAccessorName: String
    get() = path.removePrefix(GRADLE_PATH_SEP)
      .splitToSequence(GRADLE_PATH_SEP)
      .joinToString(".") { child ->
        child.splitToSequence(Regex("[.\\-_]"))
          .mapIndexed { i, segment -> if (i != 0) segment.capitalize() else segment }
          .joinToString("")
      }

  public val isRootProject: Boolean get() = path == GRADLE_PATH_SEP

  /**
   * Recursively walk the directory for this project and find all of the child projects.
   */
  public fun expandChildProjects(excludeDirs: List<String> = listOf("build", "src")): Set<GradlePath> {
    return Files.walk(projectDir).parallel()
      .filter { it.name !in excludeDirs }
      .filter { it.isDirectory() }
      .filter { path -> BUILDSCRIPTS.any { path.resolve(it).exists() } }
      .map { it.gradlePathRelativeTo(root) }
      .toList().toSet()
  }

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

private fun String.capitalize(): String = replaceFirstChar {
  if (it.isLowerCase()) it.titlecase(getDefault()) else it.toString()
}
