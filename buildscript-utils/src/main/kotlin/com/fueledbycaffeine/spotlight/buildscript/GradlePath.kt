package com.fueledbycaffeine.spotlight.buildscript

import com.fueledbycaffeine.spotlight.buildscript.graph.GraphNode
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule
import com.gradle.scan.plugin.internal.com.fueledbycaffeine.spotlight.internal.GradlePathInternal
import java.io.File
import java.io.FileNotFoundException
import java.io.Serializable
import java.nio.file.Path
import java.util.Locale.getDefault
import kotlin.io.path.relativeTo

public const val GRADLE_PATH_SEP: String = ":"
public const val GRADLE_SCRIPT: String = "build.gradle"
public const val GRADLE_SCRIPT_KOTLIN: String = "build.gradle.kts"
public val BUILDSCRIPTS: List<String> = listOf(GRADLE_SCRIPT, GRADLE_SCRIPT_KOTLIN)
private val SRC_AND_BUILD_DIRS = listOf("build", "src", "src-gen")

public data class GradlePath(
  public val root: Path,
  public val path: String,
): GraphNode<GradlePath>, Serializable {
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
  public val hasBuildFile: Boolean get() = GradlePathInternal.hasBuildFile(this)

  /**
   * The buildscript [Path] for this Gradle path.
   *
   * @throws FileNotFoundException if there is no buildscript.
   */
  public val buildFilePath: Path get() = try {
    GradlePathInternal.buildFilePath(this)
  } catch (_: GradlePathInternal.NoBuildFileException) {
    // The package for GradlePathInternal is really weird and possibly confusing so make it look like the exception
    // came from here in the public API.
    throw FileNotFoundException("No build.gradle(.kts) for $path found")
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
   * Recursively walk the directory for this project and find all the child projects.
   */
  public fun expandChildProjects(excludeDirs: List<String> = SRC_AND_BUILD_DIRS): Set<GradlePath> {
    return GradlePathInternal.expandChildProjects(this, excludeDirs)
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
