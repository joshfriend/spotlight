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
 * So why do this? All we want to do here is find `build.gradle(.kts)` which will be captured in CC elsewhere anyway.
 *
 * The extra file paths captured by CC don't seem to affect the reusability of the entry, but we want to keep that list
 * as small as possible.
 */
package com.gradle.scan.plugin.internal.com.fueledbycaffeine.spotlight.internal

import com.fueledbycaffeine.spotlight.buildscript.GRADLE_SCRIPT
import com.fueledbycaffeine.spotlight.buildscript.GRADLE_SCRIPT_KOTLIN
import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.SETTINGS_SCRIPT
import com.fueledbycaffeine.spotlight.buildscript.SETTINGS_SCRIPT_KOTLIN
import java.nio.file.Path
import kotlin.io.path.exists

internal object GradlePathInternal {
  fun hasBuildFile(gradlePath: GradlePath): Boolean = buildFilePath(gradlePath) != null

  fun hasSettingsFile(gradlePath: GradlePath): Boolean =
    gradlePath.projectDir.resolve(SETTINGS_SCRIPT).exists() ||
      gradlePath.projectDir.resolve(SETTINGS_SCRIPT_KOTLIN).exists()

  fun buildFilePath(gradlePath: GradlePath): Path? =when {
    gradlePath.projectDir.resolve(GRADLE_SCRIPT).exists() -> gradlePath.projectDir.resolve(GRADLE_SCRIPT)
    gradlePath.projectDir.resolve(GRADLE_SCRIPT_KOTLIN).exists() -> gradlePath.projectDir.resolve(GRADLE_SCRIPT_KOTLIN)
    else -> null
  }
}