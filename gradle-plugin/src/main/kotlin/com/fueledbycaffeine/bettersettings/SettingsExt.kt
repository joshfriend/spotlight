package com.fueledbycaffeine.bettersettings

import com.fueledbycaffeine.bettersettings.graph.GradlePath
import org.gradle.api.initialization.Settings

fun Settings.include(paths: List<GradlePath>) = include(paths.map { it.path })