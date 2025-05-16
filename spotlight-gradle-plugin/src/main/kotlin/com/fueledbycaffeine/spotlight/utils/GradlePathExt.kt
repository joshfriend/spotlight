package com.fueledbycaffeine.spotlight.utils

import com.fueledbycaffeine.spotlight.buildscript.GRADLE_PATH_SEP
import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import org.gradle.api.initialization.Settings
import java.io.FileNotFoundException
import kotlin.io.path.exists

internal fun Settings.guessProjectsFromTaskRequests(): Set<GradlePath> {
  return startParameter.taskRequests.flatMap { it.args }
    .filter { it.looksLikeAGradlePath }
    .map {
      val path = GradlePath(rootDir, it.projectPathGuess)
      if (!path.projectDir.exists()) {
        throw FileNotFoundException("${it.projectPathGuess} is not a project dir")
      }
      path
    }
    .toSet()
}

private val String.looksLikeAGradlePath: Boolean
  get() = contains(GRADLE_PATH_SEP)

private val String.projectPathGuess: String
  get() = GRADLE_PATH_SEP + substringBeforeLast(GRADLE_PATH_SEP).removePrefix(GRADLE_PATH_SEP)

public fun Settings.include(paths: Iterable<GradlePath>) = include(paths.map { it.path })