package com.fueledbycaffeine.spotlight.utils

import com.fueledbycaffeine.spotlight.buildscript.GRADLE_PATH_SEP
import com.fueledbycaffeine.spotlight.buildscript.GRADLE_SCRIPT
import com.fueledbycaffeine.spotlight.buildscript.GRADLE_SCRIPT_KOTLIN
import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.gradlePathRelativeTo
import org.gradle.api.initialization.Settings
import java.io.FileNotFoundException
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name

private val BUILDFILES = listOf(GRADLE_SCRIPT, GRADLE_SCRIPT_KOTLIN)

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