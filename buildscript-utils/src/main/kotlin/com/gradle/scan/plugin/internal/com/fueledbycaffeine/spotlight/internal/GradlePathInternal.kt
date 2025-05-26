/**
 * Gradle provides a system property to exclude files matching patterns from being captured in configuration cache:
 * https://github.com/gradle/gradle/blob/v8.14.1/subprojects/core/src/main/java/org/gradle/initialization/StartParameterBuildOptions.java#L551
 * This doesn't work for the use case in Spotlight since it is a start parameter and therefore read before the plugin
 * loads, so we can't set it with the plugin and would require the user to configure it.
 *
 * The other alternative is to pretend you are the build scan plugin:
 * https://github.com/gradle/gradle/blob/v8.14.1/platforms/core-configuration/configuration-cache/src/main/kotlin/org/gradle/internal/cc/impl/Workarounds.kt#L77-L81
 *
 * That's what we're doing here because it just works (for now)
 *
 * So why do this? [GradlePathInternal.expandChildProjects] ends up observing filesystem entries besides
 * `build.gradle(.kts)` because it uses [Files.newDirectoryStream], which can only filter files *after* observing them.
 * All we want to do here is find `build.gradle(.kts)` which will be captured in CC elsewhere anyway.
 *
 * The extra file paths captured by CC don't seem to affect the reusability of the entry, but we want to keep that list
 * as small as possible.
 */
package com.gradle.scan.plugin.internal.com.fueledbycaffeine.spotlight.internal

import com.fueledbycaffeine.spotlight.buildscript.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name

/**
 * [Files.walk] will recurse directories automatically, but the order of paths returned is not ordered in any way, so
 * filtering the results can only be done after observing the filesystem entries, which ends up observing source/build
 * files. This manual recursion will avoid traversing directories we want to completely ignore.
 */
private fun Path.findGradleBuildFiles(excludeDirs: List<String>): Set<Path> {
  return Files.newDirectoryStream(this)
    .use { stream ->
      stream.flatMap { path ->
        if (path.isDirectory() && path.name !in excludeDirs) {
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

internal object GradlePathInternal {
  fun expandChildProjects(gradlePath: GradlePath, excludeDirs: List<String>): Set<GradlePath> {
    return gradlePath.projectDir.findGradleBuildFiles(excludeDirs)
      .mapTo(mutableSetOf()) { it.parent.gradlePathRelativeTo(gradlePath.root) }
  }

  fun hasBuildFile(gradlePath: GradlePath): Boolean =
    gradlePath.projectDir.resolve(GRADLE_SCRIPT).exists() ||
    gradlePath.projectDir.resolve(GRADLE_SCRIPT_KOTLIN).exists()

  fun buildFilePath(gradlePath: GradlePath): Path = when {
    gradlePath.projectDir.resolve(GRADLE_SCRIPT).exists() -> gradlePath.projectDir.resolve(GRADLE_SCRIPT)
    gradlePath.projectDir.resolve(GRADLE_SCRIPT_KOTLIN).exists() -> gradlePath.projectDir.resolve(GRADLE_SCRIPT_KOTLIN)
    else -> throw NoBuildFileException()
  }

  class NoBuildFileException(): Exception()
}