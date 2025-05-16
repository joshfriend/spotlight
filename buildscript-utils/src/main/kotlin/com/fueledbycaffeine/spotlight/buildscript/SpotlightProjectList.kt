package com.fueledbycaffeine.spotlight.buildscript

import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.text.RegexOption.MULTILINE

private const val COMMENT_CHAR = "#"
private val SETTINGS_GRADLE_NAME = Regex("settings\\w*\\.gradle(?:\\.kts)?$", MULTILINE)
private val SETTINGS_GRADLE_INCLUDE = Regex("^(?:\\s+)?include\\W+[\"']([:\\w]+)[\"']", MULTILINE)

public class SpotlightProjectList(private val buildRoot: Path, private val projectList: Path) {
  public fun read(): Set<GradlePath> {
    val projects = when {
      projectList.isSettingsGradle -> readSettingsGradleFormat()
      else -> readSpotlightFormat()
    }

    val missingProjects = projects.filter { !it.hasBuildFile }
    if (missingProjects.isNotEmpty()) {
      throw FileNotFoundException(
        "These project paths listed in ${buildRoot.relativize(projectList)} do not have a buildscript:\n" +
          missingProjects.joinToString("\n") { it.path }
      )
    }

    return projects
  }

  private fun readSpotlightFormat(): Set<GradlePath> {
    return projectList.readLines()
      .filterNot { line -> line.startsWith(COMMENT_CHAR) || line.isBlank() }
      .map { GradlePath(buildRoot, it) }
      .toSet()
  }

  private fun readSettingsGradleFormat(): Set<GradlePath> {
    return SETTINGS_GRADLE_INCLUDE.findAll(projectList.readText())
      .map { match -> match.destructured.component1() }
      .map { GradlePath(buildRoot, it) }
      .toSet()
  }

  public infix fun contains(path: GradlePath): Boolean = read().contains(path)

  public fun add(vararg paths: GradlePath) {
    projectList.writeText(
      """
      ${projectList.readText().trim()}
      ${paths.joinToString("\n") { it.path }}
      
      """.trimIndent()
    )
  }

  public companion object {
    @JvmStatic
    public fun File.readProjectList(projectList: File): Set<GradlePath> {
      return this.toPath().readProjectList(projectList.toPath())
    }

    @JvmStatic
    public fun Path.readProjectList(projectList: Path): Set<GradlePath> {
      return SpotlightProjectList(this, projectList).read()
    }
  }
}

private val Path.isSettingsGradle: Boolean get() = SETTINGS_GRADLE_NAME.matches(this.name)