@file:OptIn(ExperimentalPathApi::class)

package com.fueledbycaffeine.spotlight.functionaltest

import com.autonomousapps.kit.GradleBuilder
import com.autonomousapps.kit.GradleProject
import com.autonomousapps.kit.truth.TestKitTruth.Companion.assertThat
import com.fueledbycaffeine.spotlight.functionaltest.fixtures.*
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.io.path.*

class SpotlightBuildFunctionalTest {
  @ParameterizedTest
  @EnumSource(GradleProject.DslKind::class)
  fun `computes explicit dependencies correctly`(dslKind: GradleProject.DslKind) {
    // Given
    val project = SpiritboxProject().build(dslKind = dslKind)

    // When
    val result = project.build(":rotoscope:assemble")

    // Then
    assertThat(result).task(":rotoscope:assemble").succeeded()
    assertThat(result).task(":rotoscope:rotoscope:compileJava").succeeded()
    assertThat(result).task(":rotoscope:hysteria:compileJava").noSource()
    assertThat(result).task(":rotoscope:sew-me-up:compileJava").noSource()
    val includedProjects = result.includedProjects()
    val expectedProjects = listOf(
      project.rootProject.settingsScript.rootProjectName,
      ":rotoscope",
      ":rotoscope:rotoscope",
      ":rotoscope:hysteria",
      ":rotoscope:sew-me-up",
    )
    assertThat(includedProjects).containsExactlyElementsIn(expectedProjects)
    val ccReport = result.ccReport()
    assertThat(ccReport.inputs).containsExactlyElementsIn(listOf(
      CCDiagnostic.Input(type="system property", name="spotlight.enabled"),
      CCDiagnostic.Input(type="system property", name="idea.sync.active"),
      CCDiagnostic.Input(type="file system entry", name="gradle/spotlight-rules.json"),
    ))
  }

  @Test
  fun `configuration cache can be reused`() {
    // Given
    val project = SpiritboxProject().build()

    // When
    val result = project.build(":rotoscope:assemble", "--dry-run")
    val rotoscopeJava = project.projectDir(":rotoscope:rotoscope")
      .resolve("src/main/java/com/rotoscope/Rotoscope.java")
    rotoscopeJava.appendText("\n// some source change")
    val result2 = project.build(":rotoscope:assemble", "--dry-run")

    // Then
    assertThat(result.configurationCacheStored).isTrue()
    assertThat(result2.configurationCacheReused).isTrue()
  }

  @Test
  fun `supports isolated projects`() {
    // Given
    val project = SpiritboxProject().build()
    project.setGradleProperties("org.gradle.unsafe.isolated-projects" to "true")

    // When
    val result = project.build(":rotoscope:assemble", "--dry-run")
    val rotoscopeJava = project.projectDir(":rotoscope:rotoscope")
      .resolve("src/main/java/com/rotoscope/Rotoscope.java")
    rotoscopeJava.appendText("\n// some source change")
    val result2 = project.build(":rotoscope:assemble", "--dry-run")

    // Then
    assertThat(result.configurationCacheStored).isTrue()
    assertThat(result2.configurationCacheReused).isTrue()
  }

  @Test
  fun `does not break configuration cache invalidation on build file changes`() {
    // Given
    val project = SpiritboxProject().build()

    // When
    val result = project.build(":rotoscope:assemble", "--dry-run")
    val buildscript = project.projectDir(":rotoscope").resolve("build.gradle")
    buildscript.appendText("\n// some buildscript modification")
    val result2 = project.build(":rotoscope:assemble", "--dry-run")

    // Then
    assertThat(result.configurationCacheStored).isTrue()
    assertThat(result2.configurationCacheStored).isTrue()
    assertThat(result2.configurationCacheReused).isFalse()
    assertThat(result2.configurationCacheInvalidationReason)
      .isEqualTo("file 'rotoscope/build.gradle' has changed.")
  }

  @ParameterizedTest
  @EnumSource(GradleProject.DslKind::class)
  fun `can include implicit dependencies by project path`(dslKind: GradleProject.DslKind) {
    // Given
    val project = SpiritboxProject().build(dslKind = dslKind)

    val rules = project.rootDir.resolve("gradle/spotlight-rules.json")
    rules.writeText("""
    [
      {
        "type": "project-path-match-rule",
        "pattern": ":rotoscope",
        "includedProjects": [":tsunami-sea"]
      }
    ]
    """.trimIndent())

    // When
    val result = project.build(":rotoscope:assemble")

    // Then
    assertThat(result).task(":rotoscope:assemble").succeeded()
    val includedProjects = result.includedProjects()
    val expectedProjects = listOf(
      project.rootProject.settingsScript.rootProjectName,
      ":rotoscope",
      ":rotoscope:rotoscope",
      ":rotoscope:hysteria",
      ":rotoscope:sew-me-up",
      ":tsunami-sea"
    )
    assertThat(includedProjects).containsExactlyElementsIn(expectedProjects)
    val ccReport = result.ccReport()
    assertThat(ccReport.inputs).containsExactlyElementsIn(listOf(
      CCDiagnostic.Input(type="system property", name="spotlight.enabled"),
      CCDiagnostic.Input(type="system property", name="idea.sync.active"),
      CCDiagnostic.Input(type="file system entry", name="gradle/spotlight-rules.json"),
    ))
  }

  @ParameterizedTest
  @EnumSource(GradleProject.DslKind::class)
  fun `can include implicit dependencies by buildscript contents`(dslKind: GradleProject.DslKind) {
    // Given
    val project = SpiritboxProject().build(dslKind = dslKind)
    val rotoscopeBuildscript = project.rootDir.resolve("rotoscope/${dslKind.buildFile}")
    val contents = rotoscopeBuildscript.readText()
    rotoscopeBuildscript.writeText("// some marker\n$contents")
    val rules = project.rootDir.resolve("gradle/spotlight-rules.json")
    rules.writeText("""
    [
      {
        "type": "buildscript-match-rule",
        "pattern": "some marker",
        "includedProjects": [":eternal-blue"]
      }
    ]
    """.trimIndent())

    // When
    val result = project.build(":rotoscope:assemble")

    // Then
    assertThat(result).task(":rotoscope:assemble").succeeded()
    val includedProjects = result.includedProjects()
    val expectedProjects = listOf(
      project.rootProject.settingsScript.rootProjectName,
      ":rotoscope",
      ":rotoscope:rotoscope",
      ":rotoscope:hysteria",
      ":rotoscope:sew-me-up",
      ":eternal-blue",
      ":eternal-blue:circle-with-me",
      ":eternal-blue:constance",
      ":eternal-blue:eternal-blue",
      ":eternal-blue:halcyon",
      ":eternal-blue:holy-roller",
      ":eternal-blue:hurt-you",
      ":eternal-blue:secret-garden",
      ":eternal-blue:silk-in-the-strings",
      ":eternal-blue:sun-killer",
      ":eternal-blue:the-summit",
      ":eternal-blue:we-live-in-a-strange-world",
      ":eternal-blue:yellowjacket",
    )
    assertThat(includedProjects).containsExactlyElementsIn(expectedProjects)
    val ccReport = result.ccReport()
    assertThat(ccReport.inputs).containsExactlyElementsIn(listOf(
      CCDiagnostic.Input(type="system property", name="spotlight.enabled"),
      CCDiagnostic.Input(type="system property", name="idea.sync.active"),
      CCDiagnostic.Input(type="file system entry", name="gradle/spotlight-rules.json"),
    ))
  }

  @Test
  fun `can include only the root project`() {
    // Given
    val project = SpiritboxProject().build()

    // When
    val result = project.build(":help")

    // Then
    assertThat(result).task(":help").succeeded()
    assertThat(result).output().contains("Requested targets include 0 projects transitively")
    val includedProjects = result.includedProjects()
    assertThat(includedProjects).containsExactly(project.rootProject.settingsScript.rootProjectName)
    val ccReport = result.ccReport()
    assertThat(ccReport.inputs).containsExactlyElementsIn(listOf(
      CCDiagnostic.Input(type="system property", name="spotlight.enabled"),
      CCDiagnostic.Input(type="system property", name="idea.sync.active"),
      CCDiagnostic.Input(type="file system entry", name="gradle/spotlight-rules.json"),
    ))
  }

  @Test
  fun `can run a global task`() {
    // Given
    val project = SpiritboxProject().build()

    // When
    val result = project.build("assemble")

    // Then
    assertThat(result).task(":rotoscope:assemble").succeeded()
    assertThat(result).task(":the-fear-of-fear:assemble").succeeded()
    assertThat(result).task(":eternal-blue:assemble").succeeded()
    val includedProjects = result.includedProjects()
    val allProjects = project.allProjects.readLines() +
      project.rootProject.settingsScript.rootProjectName
    assertThat(includedProjects).containsExactlyElementsIn(allProjects)
    val ccReport = result.ccReport()
    assertThat(ccReport.inputs).containsExactlyElementsIn(listOf(
      CCDiagnostic.Input(type="system property", name="spotlight.enabled"),
      CCDiagnostic.Input(type="system property", name="idea.sync.active"),
      CCDiagnostic.Input(type="file system entry", name="gradle/all-projects.txt"),
      CCDiagnostic.Input(type="file", name="gradle/all-projects.txt"),
    ))
  }

  @Test
  fun `can run a task with specific working directory`() {
    // Given
    val project = SpiritboxProject().build()

    // When
    val result = GradleBuilder.build(
      project.rootDir.resolve("rotoscope"),
      "assemble",
      "--info",
      "--configuration-cache",
    )

    // Then
    assertThat(result).task(":rotoscope:assemble").succeeded()
    val includedProjects = result.includedProjects()
    val expectedProjects = listOf(
      project.rootProject.settingsScript.rootProjectName,
      ":rotoscope",
      ":rotoscope:rotoscope",
      ":rotoscope:hysteria",
      ":rotoscope:sew-me-up",
    )
    assertThat(includedProjects).containsExactlyElementsIn(expectedProjects)
    val ccReport = result.ccReport()
    assertThat(ccReport.inputs).containsExactlyElementsIn(listOf(
      CCDiagnostic.Input(type="system property", name="spotlight.enabled"),
      CCDiagnostic.Input(type="system property", name="idea.sync.active"),
      CCDiagnostic.Input(type="directory content", name="rotoscope"),
      CCDiagnostic.Input(type="directory content", name="rotoscope/sew-me-up"),
      CCDiagnostic.Input(type="directory content", name="rotoscope/hysteria"),
      CCDiagnostic.Input(type="directory content", name="rotoscope/rotoscope"),
      CCDiagnostic.Input(type="file system entry", name="gradle/spotlight-rules.json"),
    ))
  }

  @Test
  fun `can run task from an included build`() {
    // Given
    val project = SpiritboxProject().build()

    // When
    val result = project.build(":included-build:assemble")

    // Then
    assertThat(result).task(":included-build:assemble").succeeded()
    val includedProjects = result.includedProjects()
    val expectedProjects = listOf(
      project.rootProject.settingsScript.rootProjectName,
    )
    assertThat(includedProjects).containsExactlyElementsIn(expectedProjects)
    val ccReport = result.ccReport()
    assertThat(ccReport.inputs).containsExactlyElementsIn(listOf(
      CCDiagnostic.Input(type="system property", name="spotlight.enabled"),
      CCDiagnostic.Input(type="system property", name="idea.sync.active"),
      CCDiagnostic.Input(type="file system entry", name="gradle/spotlight-rules.json"),
    ))
  }

  @Test
  fun `can run a task with set of target overrides`() {
    // Given
    val project = SpiritboxProject().build()
    val settings = project.rootDir.resolve("settings.gradle")
    settings.appendText("""
      def targetProjects = providers.gradleProperty("target-projects")
      spotlight {
        targetsOverride = targetProjects
      }
    """.trimIndent())

    // When
    val result = project.build("assemble", "-Ptarget-projects=:rotoscope")

    // Then
    assertThat(result).task(":rotoscope:assemble").succeeded()
    val includedProjects = result.includedProjects()
    val expectedProjects = listOf(
      project.rootProject.settingsScript.rootProjectName,
      ":rotoscope",
      ":rotoscope:rotoscope",
      ":rotoscope:hysteria",
      ":rotoscope:sew-me-up",
    )
    assertThat(includedProjects).containsExactlyElementsIn(expectedProjects)
    val ccReport = result.ccReport()
    assertThat(ccReport.inputs).containsExactlyElementsIn(listOf(
      CCDiagnostic.Input(type="system property", name="spotlight.enabled"),
      CCDiagnostic.Input(type="file system entry", name="gradle/spotlight-rules.json"),
    ))
  }

  @Test
  fun `invalidates configuration cache when adding a project to directory`() {
    // Given
    val project = SpiritboxProject().build()

    // When
    val result1 = GradleBuilder.build(
      project.rootDir.resolve("rotoscope"),
      "assemble",
      "--info",
      "--configuration-cache",
    )
    project.rootDir.toPath().resolve("rotoscope/a-new-project/").apply {
      createDirectories()
      resolve("build.gradle").apply {
        createFile()
        writeText(
          """
          plugins {
            id 'java'
          }
          """.trimIndent()
        )
      }
    }
    val result2 = GradleBuilder.build(
      project.rootDir.resolve("rotoscope"),
      "assemble",
      "--info",
      "--configuration-cache",
    )

    // Then
    assertThat(result1.configurationCacheStored).isTrue()
    assertThat(result2.configurationCacheReused).isFalse()
    val includedProjects = result2.includedProjects()
    val expectedProjects = listOf(
      project.rootProject.settingsScript.rootProjectName,
      ":rotoscope",
      ":rotoscope:rotoscope",
      ":rotoscope:hysteria",
      ":rotoscope:sew-me-up",
      ":rotoscope:a-new-project",
    )
    assertThat(includedProjects).containsExactlyElementsIn(expectedProjects)
    val ccReport = result2.ccReport()
    assertThat(ccReport.inputs).containsExactlyElementsIn(listOf(
      CCDiagnostic.Input(type="system property", name="spotlight.enabled"),
      CCDiagnostic.Input(type="system property", name="idea.sync.active"),
      CCDiagnostic.Input(type="directory content", name="rotoscope"),
      CCDiagnostic.Input(type="directory content", name="rotoscope/sew-me-up"),
      CCDiagnostic.Input(type="directory content", name="rotoscope/a-new-project"),
      CCDiagnostic.Input(type="directory content", name="rotoscope/hysteria"),
      CCDiagnostic.Input(type="directory content", name="rotoscope/rotoscope"),
      CCDiagnostic.Input(type="file system entry", name="gradle/spotlight-rules.json"),
    ))
  }

  @Test
  fun `invalidates configuration cache when removing a project from directory`() {
    // Given
    val project = SpiritboxProject().build()
    val projectToRemove = project.rootDir.toPath().resolve("rotoscope/project-to-remove/")
    projectToRemove.apply {
      createDirectories()
      resolve("build.gradle").apply {
        createFile()
        writeText(
          """
          plugins {
            id 'java'
          }
          """.trimIndent()
        )
      }
    }

    // When
    val result1 = GradleBuilder.build(
      project.rootDir.resolve("rotoscope"),
      "assemble",
      "--info",
      "--configuration-cache",
    )
    projectToRemove.deleteRecursively()
    val result2 = GradleBuilder.build(
      project.rootDir.resolve("rotoscope"),
      "assemble",
      "--info",
      "--configuration-cache",
    )

    // Then
    assertThat(result1.configurationCacheStored).isTrue()
    assertThat(result2.configurationCacheReused).isFalse()
    val includedProjects = result2.includedProjects()
    val expectedProjects = listOf(
      project.rootProject.settingsScript.rootProjectName,
      ":rotoscope",
      ":rotoscope:rotoscope",
      ":rotoscope:hysteria",
      ":rotoscope:sew-me-up",
    )
    assertThat(includedProjects).containsExactlyElementsIn(expectedProjects)
    val ccReport = result2.ccReport()
    assertThat(ccReport.inputs).containsExactlyElementsIn(listOf(
      CCDiagnostic.Input(type="system property", name="spotlight.enabled"),
      CCDiagnostic.Input(type="system property", name="idea.sync.active"),
      CCDiagnostic.Input(type="directory content", name="rotoscope"),
      CCDiagnostic.Input(type="directory content", name="rotoscope/sew-me-up"),
      CCDiagnostic.Input(type="directory content", name="rotoscope/hysteria"),
      CCDiagnostic.Input(type="directory content", name="rotoscope/rotoscope"),
      CCDiagnostic.Input(type="file system entry", name="gradle/spotlight-rules.json"),
    ))
  }

  @Test
  fun `can be disabled with property`() {
    // Given
    val project = SpiritboxProject().build()

    // When
    val result = project.build(":help", "-Dspotlight.enabled=false")

    // Then
    val includedProjects = result.includedProjects()
    val allProjects = project.allProjects.readLines() +
      project.rootProject.settingsScript.rootProjectName
    assertThat(includedProjects).containsExactlyElementsIn(allProjects)

    assertThat(result.output).contains("Spotlight is disabled")
    val ccReport = result.ccReport()
    assertThat(ccReport.inputs).containsExactlyElementsIn(listOf(
      CCDiagnostic.Input(type="system property", name="spotlight.enabled"),
      CCDiagnostic.Input(type="file system entry", name="gradle/all-projects.txt"),
      CCDiagnostic.Input(type="file", name="gradle/all-projects.txt"),
    ))
  }
}