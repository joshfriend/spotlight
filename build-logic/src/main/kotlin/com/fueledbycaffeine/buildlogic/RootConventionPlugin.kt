package com.fueledbycaffeine.buildlogic

import com.autonomousapps.DependencyAnalysisExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension

class RootConventionPlugin : Plugin<Project> {
  override fun apply(project: Project): Unit = with(project) {
    group = "com.fueledbycaffeine"
    val libs = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
    extensions.getByType(DependencyAnalysisExtension::class.java).apply {
      reporting { it.printBuildHealth(true) }
      issues { issues ->
        issues.all { projectIssues ->
          projectIssues.onAny { it.severity("fail") }
        }
      }
      structure { structure ->
        structure.bundle("jupiter") {
          it.primary(libs.findLibrary("junit-jupiter").get())
          it.includeGroup("org.junit.jupiter")
        }
        structure.bundle("testkit") {
          it.primary(libs.findLibrary("autonomousapps-testkit").get())
          it.includeDependency(libs.findLibrary("autonomousapps-testkit-support").get())
          it.includeDependency(libs.findLibrary("truth").get())
        }
        structure.bundle("moshi") {
          it.primary(libs.findLibrary("moshi").get())
        }
      }
    }
  }
}
