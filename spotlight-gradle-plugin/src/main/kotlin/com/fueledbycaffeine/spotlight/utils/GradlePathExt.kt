package com.fueledbycaffeine.spotlight.utils

import com.fueledbycaffeine.spotlight.SpotlightBuildService
import com.fueledbycaffeine.spotlight.SpotlightBuildService.Companion.NAME
import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import org.gradle.api.initialization.Settings

internal fun Settings.guessProjectsFromTaskRequests(): Set<GradlePath> {
  return startParameter.taskRequests.flatMap { it.args }
    .map { GradlePath(rootDir, it.projectPathGuess) }
    .filter { it.hasBuildFile }
    .toSet()
}

// Try removing characters that could be the task name, then assume whatever is left is the task's
// project path. If this doesn't neatly map to a location with a buildfile, it is ignored because
// it is probably some other argument like '--tests' or a test classname, etc.
private val String.projectPathGuess get() = this.replace(Regex("\\w+$"), "")

internal fun Settings.include(paths: Iterable<GradlePath>) {
  gradle.sharedServices.registerIfAbsent(NAME, SpotlightBuildService::class.java) {
    it.parameters.enabled.set(isSpotlightEnabled)
    it.parameters.spotlightProjects.set(paths)
  }

  include(paths.map { it.path })
}