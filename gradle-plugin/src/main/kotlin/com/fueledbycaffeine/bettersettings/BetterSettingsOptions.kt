package com.fueledbycaffeine.bettersettings

import org.gradle.api.initialization.Settings
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import java.nio.file.Path

const val PROP_ALL_PROJECTS_LIST = "com.fueledbycaffeine.better-settings.all-projects"
const val PROP_TARGET_PROJECTS_LIST = "com.fueledbycaffeine.better-settings.target-projects"
const val PROP_IMPLICIT_PROJECTS_LIST = "com.fueledbycaffeine.better-settings.implicit-projects"
const val ALL_PROJECTS_FILE = "gradle/all-projects.txt"
const val TARGET_PROJECTS_FILE = "gradle/target-projects.txt"
const val IMPLICIT_PROJECTS_FILE = "gradle/implicit-projects.txt"

private val ProviderFactory.allProjectsListFileName: Provider<String>
  get() = gradleProperty(PROP_ALL_PROJECTS_LIST)
    .orElse(ALL_PROJECTS_FILE)

private val ProviderFactory.targetProjectsListFileName: Provider<String>
  get() = gradleProperty(PROP_TARGET_PROJECTS_LIST)
    .orElse(TARGET_PROJECTS_FILE)

private val ProviderFactory.implicitProjectsListFileName: Provider<String>
  get() = gradleProperty(PROP_IMPLICIT_PROJECTS_LIST)
    .orElse(IMPLICIT_PROJECTS_FILE)

val Settings.allProjectListFile: Path
  get() = rootDir.toPath().resolve(providers.allProjectsListFileName.get())
val Settings.targetProjectListFile: Path
  get() = rootDir.toPath().resolve(providers.targetProjectsListFileName.get())
val Settings.implicitProjectListFile: Path
  get() = rootDir.toPath().resolve(providers.implicitProjectsListFileName.get())
