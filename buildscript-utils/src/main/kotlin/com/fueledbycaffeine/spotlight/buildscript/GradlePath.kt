package com.fueledbycaffeine.spotlight.buildscript

import com.fueledbycaffeine.spotlight.buildscript.graph.DependencyRule
import com.fueledbycaffeine.spotlight.buildscript.graph.GraphNode
import java.io.File
import java.io.FileNotFoundException
import java.io.Serializable
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
public const val SETTINGS_SCRIPT: String = "settings.gradle"
public const val SETTINGS_SCRIPT_KOTLIN: String = "settings.gradle.kts"
public val BUILDSCRIPTS: List<String> = listOf(GRADLE_SCRIPT, GRADLE_SCRIPT_KOTLIN)
public val SETTINGS_SCRIPTS: List<String> = listOf(SETTINGS_SCRIPT, SETTINGS_SCRIPT_KOTLIN)
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
   * Indicates if this project path has either a build.gradle, build.gradle.kts, or some other
   * *.gradle(.kts) script.
   */
  public val hasBuildFile: Boolean get() =
    projectDir.resolve(GRADLE_SCRIPT).exists() || projectDir.resolve(GRADLE_SCRIPT_KOTLIN).exists()

  /**
   * Indicates if this project path has either a settings.gradle or a settings.gradle.kts script
   */
  public val hasSettingsFile: Boolean get() =
    projectDir.resolve(SETTINGS_SCRIPT).exists() ||
      projectDir.resolve(SETTINGS_SCRIPT_KOTLIN).exists()

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
   * Returns the parent gradle project path, or null if this is the root project
   */
  public val parent: GradlePath? get() {
    val parentPath = path.substringBeforeLast(GRADLE_PATH_SEP, missingDelimiterValue = "")
      .takeIf { it.isNotBlank() } ?: GRADLE_PATH_SEP
    return when {
      isRootProject -> null
      else -> GradlePath(root, parentPath)
    }
  }

  public val isFromMainBuild: Boolean get() {
    val firstParentWithSettings = generateSequence(this) { it.parent }
      .first { it.hasSettingsFile }
    return firstParentWithSettings.isRootProject
  }

  /**
   * Recursively walk the directory for this project and find all the child projects.
   */
  public fun expandChildProjects(excludeDirs: List<String> = SRC_AND_BUILD_DIRS): Set<GradlePath> {
    return projectDir.findGradleBuildFiles(excludeDirs)
      .mapTo(mutableSetOf()) { it.parent.gradlePathRelativeTo(root) }
  }

  public override fun findSuccessors(rules: Set<DependencyRule>): Set<GradlePath> {
    return BuildFile(this).parseDependencies(rules)
  }
}

/**
 * [Files.walk] will recurse directories automatically, but the order of paths returned is not ordered in any way, so
 * filtering the results can only be done after observing the filesystem entries, which ends up observing source/build
 * files unless manually hidden from configuration cache by placing this code in the build scan plugin package.
 * This manual recursion will avoid traversing directories we want to completely ignore.
 *
 * The [Files.newDirectoryStream] is intentionally captured in CC because the directory contents are structural to
 * being able to invalidate CC in cases where builds are invoked with `-p`. The [Path.isDirectory] calls are hidden
 * because the result may not be important for CC, and we won't know until afterwords.
 */
private fun Path.findGradleBuildFiles(excludeDirs: List<String>): Set<Path> {
  return Files.newDirectoryStream(this)
    .use { stream ->
      stream.flatMap { path ->
        if (Files.isDirectory(path) && path.name !in excludeDirs) {
          path.findGradleBuildFiles(excludeDirs)
        } else if (path.name in BUILDSCRIPTS) {
          setOf(path)
        } else {
          emptySet()
        }
      }
    }
    .toCollection(mutableSetOf())
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

/**
 * Minimize the set of project paths to exclude by filtering out any that are prefixed by another path.
 */
public fun Collection<GradlePath>.minimize(): Set<GradlePath> {
  if (size < 2) return toSet()
  val sorted = sortedBy { it.path }
  if (sorted[0].path == ":") {
    // Root project consumes all
    return setOf(sorted[0])
  }
  return sortedBy { it.path }
    .fold(mutableListOf<GradlePath>()) { acc, path ->
      val last = acc.lastOrNull()
      if (last != null && path.path.startsWith("${last.path}:")) {
        acc
      } else {
        acc.add(path)
        acc
      }
    }
    .toSet()
}
