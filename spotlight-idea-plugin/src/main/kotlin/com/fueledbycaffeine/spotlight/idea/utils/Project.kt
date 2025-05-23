package com.fueledbycaffeine.spotlight.idea.utils

import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList
import com.intellij.openapi.project.Project
import java.nio.file.Path

internal val Project.spotlightIdeProjectList: SpotlightProjectList
  get() = SpotlightProjectList.ideProjects(Path.of(basePath!!))

internal val Project.spotlightAllProjectList: SpotlightProjectList
  get() = SpotlightProjectList.allProjects(Path.of(basePath!!))
