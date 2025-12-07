@file:OptIn(ExperimentalPathApi::class)

package com.fueledbycaffeine.spotlight.functionaltest

import com.autonomousapps.kit.GradleProject
import com.autonomousapps.kit.truth.TestKitTruth.Companion.assertThat
import com.fueledbycaffeine.spotlight.functionaltest.fixtures.CCDiagnostic.Input.Companion.SPOTLIGHT_INPUTS
import com.fueledbycaffeine.spotlight.functionaltest.fixtures.SpiritboxProject
import com.fueledbycaffeine.spotlight.functionaltest.fixtures.allProjects
import com.fueledbycaffeine.spotlight.functionaltest.fixtures.build
import com.fueledbycaffeine.spotlight.functionaltest.fixtures.ccReport
import com.fueledbycaffeine.spotlight.functionaltest.fixtures.configurationCacheInvalidationReason
import com.fueledbycaffeine.spotlight.functionaltest.fixtures.configurationCacheReused
import com.fueledbycaffeine.spotlight.functionaltest.fixtures.configurationCacheStored
import com.fueledbycaffeine.spotlight.functionaltest.fixtures.includedProjects
import com.fueledbycaffeine.spotlight.functionaltest.fixtures.setGradleProperties
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
    assertThat(ccReport.inputs).containsExactlyElementsIn(SPOTLIGHT_INPUTS)
  }

  @Test
  fun `ignores arguments that are not project paths`() {
    // Given
    val project = SpiritboxProject().build()

    // When
    val result = project.build(":rotoscope:assemble", "--stacktrace")

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
    assertThat(ccReport.inputs).containsExactlyElementsIn(SPOTLIGHT_INPUTS)
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

    val ccReport = result.ccReport()
    assertThat(ccReport.inputs).containsExactlyElementsIn(SPOTLIGHT_INPUTS)
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

    val ccReport = result.ccReport()
    assertThat(ccReport.inputs).containsExactlyElementsIn(SPOTLIGHT_INPUTS)
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

  @Test
  fun `can include implicit dependencies by project path`() {
    // Given
    val project = SpiritboxProject().build()

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
    assertThat(ccReport.inputs).containsExactlyElementsIn(SPOTLIGHT_INPUTS)
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
    assertThat(ccReport.inputs).containsExactlyElementsIn(SPOTLIGHT_INPUTS)
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
    assertThat(ccReport.inputs).containsExactlyElementsIn(SPOTLIGHT_INPUTS)
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
    assertThat(ccReport.inputs).containsExactlyElementsIn(SPOTLIGHT_INPUTS)
  }

  @Test
  fun `can run a global task with a project task`() {
    // Given
    val project = SpiritboxProject().build()

    // When
    val result = project.build("clean", ":rotoscope:assemble")

    // Then
    assertThat(result).task(":rotoscope:clean").upToDate()
    assertThat(result).task(":the-fear-of-fear:clean").upToDate()
    assertThat(result).task(":eternal-blue:clean").upToDate()
    assertThat(result).task(":rotoscope:assemble").succeeded()
    val includedProjects = result.includedProjects()
    val allProjects = project.allProjects.readLines() +
      project.rootProject.settingsScript.rootProjectName
    assertThat(includedProjects).containsExactlyElementsIn(allProjects)
    val ccReport = result.ccReport()
    assertThat(ccReport.inputs).containsExactlyElementsIn(SPOTLIGHT_INPUTS)
  }

  @Test
  fun `can run a task with specific working directory`() {
    // Given
    val project = SpiritboxProject().build()

    // When
    val result = project.build(
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
    assertThat(ccReport.inputs).containsExactlyElementsIn(SPOTLIGHT_INPUTS)
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
    assertThat(ccReport.inputs).containsExactlyElementsIn(SPOTLIGHT_INPUTS)
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
    assertThat(ccReport.inputs).containsExactlyElementsIn(SPOTLIGHT_INPUTS)
  }

  @Test
  fun `empty target overrides runs all projects`() {
    // Given
    val project = SpiritboxProject().build()
    val settings = project.rootDir.resolve("settings.gradle")
    settings.appendText(
      """
      def targetProjects = providers.gradleProperty("target-projects")
      spotlight {
        targetsOverride = targetProjects
      }
    """.trimIndent()
    )

    // When
    val result = project.build("assemble", "-Ptarget-projects=")

    // Then
    assertThat(result).task(":rotoscope:assemble").succeeded()
    val includedProjects = result.includedProjects()
    val expectedProjects = listOf(
      project.rootProject.settingsScript.rootProjectName,
      ":eternal-blue",
      ":rotoscope",
      ":the-fear-of-fear",
      ":tsunami-sea",
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
      ":rotoscope:hysteria",
      ":rotoscope:rotoscope",
      ":rotoscope:sew-me-up",
      ":the-fear-of-fear:angel-eyes",
      ":the-fear-of-fear:cellar-door",
      ":the-fear-of-fear:jaded",
      ":the-fear-of-fear:the-void",
      ":the-fear-of-fear:too-close-too-late",
      ":the-fear-of-fear:ultraviolet",
      ":tsunami-sea:a-haven-with-two-faces",
      ":tsunami-sea:black-rainbow",
      ":tsunami-sea:crystal-roses",
      ":tsunami-sea:deep-end",
      ":tsunami-sea:fata-morgana",
      ":tsunami-sea:keep-sweet",
      ":tsunami-sea:no-loss-no-love",
      ":tsunami-sea:perfect-soul",
      ":tsunami-sea:ride-the-wave",
      ":tsunami-sea:soft-spine",
      ":tsunami-sea:tsunami-sea",
    )
    assertThat(includedProjects).containsExactlyElementsIn(expectedProjects)
    val ccReport = result.ccReport()

    assertThat(ccReport.inputs).containsExactlyElementsIn(SPOTLIGHT_INPUTS)
  }

  @Test
  fun `invalidates configuration cache when adding a project to directory`() {
    // Given
    val project = SpiritboxProject().build()

    // When
    val result1 = project.build(
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
    val result2 = project.build(
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
    assertThat(ccReport.inputs).containsExactlyElementsIn(SPOTLIGHT_INPUTS)
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
    val result1 = project.build(
      project.rootDir.resolve("rotoscope"),
      "assemble",
      "--info",
      "--configuration-cache",
    )
    projectToRemove.deleteRecursively()
    val result2 = project.build(
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
    assertThat(ccReport.inputs).containsExactlyElementsIn(SPOTLIGHT_INPUTS)
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
    assertThat(ccReport.inputs).containsExactlyElementsIn(SPOTLIGHT_INPUTS)
  }

  @Test
  fun `handles parent project with no buildfile`() {
    // Given
    val project = SpiritboxProject().build()
    project.rootDir.resolve("tsunami-sea/build.gradle").delete()

    // When
    val result = project.build(":tsunami-sea:deep-end:assemble")

    // Then
    assertThat(result).task(":tsunami-sea:deep-end:assemble").succeeded()
    val includedProjects = result.includedProjects()
    val expectedProjects = listOf(
      project.rootProject.settingsScript.rootProjectName,
      ":tsunami-sea",
      ":tsunami-sea:deep-end",
    )
    assertThat(includedProjects).containsExactlyElementsIn(expectedProjects)
    val ccReport = result.ccReport()
    assertThat(ccReport.inputs).containsExactlyElementsIn(SPOTLIGHT_INPUTS)
  }
}