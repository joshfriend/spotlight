package com.fueledbycaffeine.bettersettings.dsl

import com.fueledbycaffeine.bettersettings.BetterSettingsPlugin
import com.fueledbycaffeine.bettersettings.utils.BuildFile
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.file.BuildLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Configuration options for [BetterSettingsPlugin].
 *
 * Example values represent the defaults:
 *
 * betterSettings {
 *   allProjects file("gradle/all-projects.txt")
 *   targetProjects file("gradle/target-projects.txt")
 *   implicitProjects file("gradle/implicit-projects.txt")
 * }
 */
@Suppress("UnstableApiUsage")
open class BetterSettingsExtension @Inject constructor(
  layout: BuildLayout,
  objects: ObjectFactory,
) {
  companion object {
    const val EXTENSION_NAME = "betterSettings"
    const val ALL_PROJECTS_FILE = "gradle/all-projects.txt"
    const val TARGET_PROJECTS_FILE = "gradle/target-projects.txt"
    const val IMPLICIT_PROJECTS_FILE = "gradle/implicit-projects.txt"

    fun ExtensionContainer.getBetterSettings(): BetterSettingsExtension {
      return try {
        getByType(BetterSettingsExtension::class.java)
      } catch (_: UnknownDomainObjectException) {
        create(EXTENSION_NAME, BetterSettingsExtension::class.java)
      }
    }
  }

  /**
   * A file containing a list of every project in the build. You may omit projects from
   * this list if they need to be conditionally included based on conditions managed outside this plugin, but they will
   * have to be added to your build some other way!
   * For build invocations that are not IDE sync, or when [targetProjects] file is missing or empty, all projects are
   * loaded in the build.
   */
  val allProjects: Property<RegularFile> = objects.fileProperty()
    .convention(layout.rootDirectory.file(ALL_PROJECTS_FILE))

  /**
   * A file containing the list of projects you would like loaded into the IDE. The projects listed here, as well as
   * any of their transitives identified by [BuildFile.dependencies] will be used instead of the [allProjects] list
   * during IDE sync.
   */
  val targetProjects: Property<RegularFile> = objects.fileProperty()
    .convention(layout.rootDirectory.file(TARGET_PROJECTS_FILE))

  /**
   * A file containing a list of projects that should always be included in the build. This is useful in cases where
   * your conventions plugins or some other build logic adds project dependencies to your build dynamically (e.g.
   * adding a testing utilities project automatically to every project).
   * This plugin parses your build graph statically without configuring projects, so it does not know about any
   * dependencies added by build logic!
   */
  val implicitProjects: Property<RegularFile> = objects.fileProperty()
    .convention(layout.rootDirectory.file(IMPLICIT_PROJECTS_FILE))
}