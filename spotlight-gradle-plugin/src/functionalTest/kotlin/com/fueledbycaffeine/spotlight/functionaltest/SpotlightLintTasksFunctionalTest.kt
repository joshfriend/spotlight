package com.fueledbycaffeine.spotlight.functionaltest

import com.autonomousapps.kit.GradleProject
import com.autonomousapps.kit.truth.TestKitTruth.Companion.assertThat
import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList
import com.fueledbycaffeine.spotlight.functionaltest.fixtures.SpiritboxProject
import com.fueledbycaffeine.spotlight.functionaltest.fixtures.build
import com.fueledbycaffeine.spotlight.functionaltest.fixtures.buildAndFail
import com.fueledbycaffeine.spotlight.functionaltest.fixtures.enableIsolatedProjects
import com.fueledbycaffeine.spotlight.functionaltest.fixtures.setGradleProperties
import com.fueledbycaffeine.spotlight.tasks.CheckSpotlightProjectListTask
import com.fueledbycaffeine.spotlight.tasks.FixSpotlightProjectListTask
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource

class SpotlightLintTasksFunctionalTest {
  @Test
  fun `check all-projects list fails when not sorted`() {
    // Given
    val project = SpiritboxProject().build()
    project.enableIsolatedProjects()
    val allProjects = project.rootDir.resolve(SpotlightProjectList.ALL_PROJECTS_LOCATION)
    allProjects.writeText(allProjects.readLines().sorted().reversed().joinToString(separator = "\n", postfix = "\n"))

    // When
    val result = project.buildAndFail(":${CheckSpotlightProjectListTask.NAME}")

    // Then
    assertThat(result).task(":${CheckSpotlightProjectListTask.NAME}").failed()
  }

  @Test
  fun `check all-projects list fails when sorted but not formatted`() {
    // Given
    val project = SpiritboxProject().build()
    project.enableIsolatedProjects()
    val allProjects = project.rootDir.resolve(SpotlightProjectList.ALL_PROJECTS_LOCATION)
    allProjects.writeText(allProjects.readLines().sorted().joinToString("\n"))

    // When
    val result = project.buildAndFail(":${CheckSpotlightProjectListTask.NAME}")

    // Then
    assertThat(result).task(":${CheckSpotlightProjectListTask.NAME}").failed()
  }

  @Test
  fun `check all-projects list succeeds when sorted and formatted`() {
    // Given
    val project = SpiritboxProject().build()
    project.enableIsolatedProjects()
    val allProjects = project.rootDir.resolve(SpotlightProjectList.ALL_PROJECTS_LOCATION)
    allProjects.writeText(allProjects.readLines().sorted().joinToString(separator = "\n", postfix = "\n"))

    // When
    val result = project.build(":${CheckSpotlightProjectListTask.NAME}")

    // Then
    assertThat(result).task(":${CheckSpotlightProjectListTask.NAME}").succeeded()
  }

  @Test
  fun `fix all-projects list sorts it`() {
    // Given
    val project = SpiritboxProject().build()
    project.enableIsolatedProjects()
    val allProjects = project.rootDir.resolve(SpotlightProjectList.ALL_PROJECTS_LOCATION)
    val allProjectsList = allProjects.readLines().sorted().reversed()
    allProjects.writeText(allProjectsList.joinToString(separator = "\n", postfix = "\n"))

    // When
    val result = project.build(":${FixSpotlightProjectListTask.NAME}")

    // Then
    assertThat(result).task(":${FixSpotlightProjectListTask.NAME}").succeeded()
    val projectList = allProjects.readLines()
    assertThat(projectList).isEqualTo(allProjectsList.reversed())
  }

  @Test
  fun `fix creates all-projects list when it does not exist`() {
    // Given
    val project = SpiritboxProject().build()
    project.enableIsolatedProjects()
    val allProjects = project.rootDir.resolve(SpotlightProjectList.ALL_PROJECTS_LOCATION)
    val expectedProjects = (allProjects.readLines() + ":test-project").sorted()
    val settingsFile = project.rootDir.resolve("settings.gradle")

    // Create a test project with a build file and include it via settings
    val testProject = project.rootDir.resolve("test-project")
    testProject.mkdirs()
    testProject.resolve("build.gradle").createNewFile()
    settingsFile.appendText("\ninclude ':test-project'\n")

    // Remove the all-projects.txt file to reproduce the missing-file scenario
    assertThat(allProjects.delete()).isTrue()
    assertThat(allProjects.exists()).isFalse()

    // When
    val result = project.build(":${FixSpotlightProjectListTask.NAME}")

    // Then
    assertThat(result).task(":${FixSpotlightProjectListTask.NAME}").succeeded()
    assertThat(allProjects.exists()).isTrue()
    assertThat(allProjects.readLines()).containsExactlyElementsIn(expectedProjects).inOrder()

    val checkResult = project.build(":${CheckSpotlightProjectListTask.NAME}")
    assertThat(checkResult).task(":${CheckSpotlightProjectListTask.NAME}").succeeded()
  }

  @Test
  fun `check runs check all-projects task`() {
    // Given
    val project = SpiritboxProject().build()
    project.enableIsolatedProjects()
    val allProjects = project.rootDir.resolve(SpotlightProjectList.ALL_PROJECTS_LOCATION)
    allProjects.writeText(allProjects.readLines().sorted().joinToString(separator = "\n", postfix = "\n"))

    // When
    val result = project.build(":check")

    // Then
    assertThat(result).task(":${CheckSpotlightProjectListTask.NAME}").succeeded()
  }

  @ParameterizedTest
  @EnumSource(GradleProject.DslKind::class)
  fun `check all-projects list fails when settings has include statements`(dslKind: GradleProject.DslKind) {
    // Given
    val project = SpiritboxProject().build(dslKind = dslKind)
    project.enableIsolatedProjects()

    // Ensure all-projects list is sorted first
    val allProjects = project.rootDir.resolve(SpotlightProjectList.ALL_PROJECTS_LOCATION)
    allProjects.writeText(allProjects.readLines().sorted().joinToString(separator = "\n", postfix = "\n"))

    // Create a build file for a project that would be included
    val someProject = project.rootDir.resolve("some-project")
    someProject.mkdirs()
    someProject.resolve(dslKind.buildFile).writeText("// empty build file")

    // Add include statement to settings
    val settingsFile = project.rootDir.resolve(dslKind.settingsFile)
    val currentContent = settingsFile.readText()
    val includeStatement = when (dslKind) {
      GradleProject.DslKind.GROOVY -> "\ninclude ':some-project'\n"
      GradleProject.DslKind.KOTLIN -> "\ninclude(\":some-project\")\n"
    }
    settingsFile.writeText(currentContent + includeStatement)

    // When
    val result = project.buildAndFail(":${CheckSpotlightProjectListTask.NAME}")

    // Then
    assertThat(result).task(":${CheckSpotlightProjectListTask.NAME}").failed()
    assertThat(result.output).contains("Found 'include' statements in ${dslKind.settingsFile}:")
  }

  @ParameterizedTest
  @EnumSource(GradleProject.DslKind::class)
  fun `check all-projects list succeeds when no include statements present`(dslKind: GradleProject.DslKind) {
    // Given
    val project = SpiritboxProject().build(dslKind = dslKind)
    project.enableIsolatedProjects()
    val allProjects = project.rootDir.resolve(SpotlightProjectList.ALL_PROJECTS_LOCATION)
    allProjects.writeText(allProjects.readLines().sorted().joinToString(separator = "\n", postfix = "\n"))

    val settingsFile = project.rootDir.resolve(dslKind.settingsFile)
    val currentContent = settingsFile.readText()
    val warningAboutIncludeStatements = when (dslKind) {
      GradleProject.DslKind.GROOVY -> "\n// DON'T DO THIS: include ':commented-out-project'\n"
      GradleProject.DslKind.KOTLIN -> "\n// DON'T DO THIS: include(\":commented-out-project\")\n"
    }
    settingsFile.writeText(currentContent + warningAboutIncludeStatements)

    // When
    val result = project.build(":${CheckSpotlightProjectListTask.NAME}")

    // Then
    assertThat(result).task(":${CheckSpotlightProjectListTask.NAME}").succeeded()
  }

  @Test
  fun `check all-projects list fails when project has no build file`() {
    // Given
    val project = SpiritboxProject().build()
    project.enableIsolatedProjects()
    val allProjects = project.rootDir.resolve(SpotlightProjectList.ALL_PROJECTS_LOCATION)

    // Add a project path that doesn't have a build file
    val projectsList = allProjects.readLines().toMutableList()
    projectsList.add(":missing-build-file")
    allProjects.writeText(projectsList.sorted().joinToString(separator = "\n", postfix = "\n"))

    // Create the directory but no build file
    project.rootDir.resolve("missing-build-file").mkdirs()

    // When
    val result = project.buildAndFail(":${CheckSpotlightProjectListTask.NAME}")

    // Then
    assertThat(result).task(":${CheckSpotlightProjectListTask.NAME}").failed()
    assertThat(result.output).contains("Found invalid projects in ${SpotlightProjectList.ALL_PROJECTS_LOCATION}")
    assertThat(result.output).contains(":missing-build-file")
    assertThat(result.output).contains("do not have a build.gradle(.kts) file")
  }

  @Test
  fun `check all-projects list fails when build files are not listed`() {
    // Given
    val project = SpiritboxProject().build()
    project.enableIsolatedProjects()
    val allProjects = project.rootDir.resolve(SpotlightProjectList.ALL_PROJECTS_LOCATION)
    allProjects.writeText(allProjects.readLines().sorted().joinToString(separator = "\n", postfix = "\n"))
    val expectedProjects = (allProjects.readLines() + listOf(":unlisted-groovy", ":unlisted-kotlin")).sorted()
    listOf("unlisted-groovy/build.gradle", "unlisted-kotlin/build.gradle.kts").forEach { path ->
      project.rootDir.resolve(path).apply {
        parentFile.mkdirs()
        createNewFile()
      }
    }

    // When
    val result = project.buildAndFail(":${CheckSpotlightProjectListTask.NAME}")

    // Then
    assertThat(result).task(":${CheckSpotlightProjectListTask.NAME}").failed()
    assertThat(result.output).contains("Found unlisted projects:")
    assertThat(result.output).contains("\n  :unlisted-groovy\n")
    assertThat(result.output).contains("\n  :unlisted-kotlin\n")

    val fixResult = project.build(":${FixSpotlightProjectListTask.NAME}")
    assertThat(fixResult).task(":${FixSpotlightProjectListTask.NAME}").succeeded()
    assertThat(allProjects.readLines()).containsExactlyElementsIn(expectedProjects).inOrder()
    val checkResult = project.build(":${CheckSpotlightProjectListTask.NAME}")
    assertThat(checkResult).task(":${CheckSpotlightProjectListTask.NAME}").succeeded()

    val fixedContents = allProjects.readText()
    project.build(":${FixSpotlightProjectListTask.NAME}")
    assertThat(allProjects.readText()).isEqualTo(fixedContents)
  }

  @ParameterizedTest
  @EnumSource(GradleProject.DslKind::class)
  fun `unlisted project error suggests working exclusions for the settings DSL`(dslKind: GradleProject.DslKind) {
    val project = SpiritboxProject().build(dslKind = dslKind)
    project.enableIsolatedProjects()
    // Use the opposite root build DSL to verify the example follows settings instead.
    assertThat(project.rootDir.resolve(dslKind.buildFile).delete()).isTrue()
    val otherBuildFile = if (dslKind == GradleProject.DslKind.KOTLIN) "build.gradle" else "build.gradle.kts"
    project.rootDir.resolve(otherBuildFile).createNewFile()
    val allProjects = project.rootDir.resolve(SpotlightProjectList.ALL_PROJECTS_LOCATION)
    allProjects.writeText(allProjects.readLines().sorted().joinToString(separator = "\n", postfix = "\n"))
    val originalContents = allProjects.readText()
    listOf("unlisted-groovy/build.gradle", "unlisted-kotlin/build.gradle.kts").forEach { path ->
      project.rootDir.resolve(path).apply {
        parentFile.mkdirs()
        createNewFile()
      }
    }

    val result = project.buildAndFail(":${CheckSpotlightProjectListTask.NAME}")

    assertThat(result.output).contains("configure the directory scan in ${dslKind.settingsFile}:")
    assertThat(result.output).contains("spotlight {")
    // Extract the complete example, including all three nested closing braces.
    val snippet = result.output.lineSequence()
      .dropWhile { it.trim() != "spotlight {" }
      .takeWhile { it.isNotBlank() }
      .joinToString("\n") { it.removePrefix("  ") }
    when (dslKind) {
      GradleProject.DslKind.GROOVY -> {
        assertThat(snippet).contains("excludeProjectPaths ':unlisted-groovy'")
        assertThat(snippet).contains("excludeProjectPaths ':unlisted-kotlin'")
      }
      GradleProject.DslKind.KOTLIN -> {
        assertThat(snippet).contains("excludeProjectPaths(\":unlisted-groovy\")")
        assertThat(snippet).contains("excludeProjectPaths(\":unlisted-kotlin\")")
      }
    }

    // Apply the actual error-message snippet and verify it configures both tasks.
    project.rootDir.resolve(dslKind.settingsFile).appendText("\n$snippet\n")
    val fixResult = project.build(":${FixSpotlightProjectListTask.NAME}")
    assertThat(fixResult).task(":${FixSpotlightProjectListTask.NAME}").succeeded()
    assertThat(allProjects.readText()).isEqualTo(originalContents)
    val checkResult = project.build(":${CheckSpotlightProjectListTask.NAME}")
    assertThat(checkResult).task(":${CheckSpotlightProjectListTask.NAME}").succeeded()
  }

  @ParameterizedTest
  @EnumSource(GradleProject.DslKind::class)
  fun `disabling directory scans leaves dependency discovery active`(dslKind: GradleProject.DslKind) {
    val project = SpiritboxProject().build(dslKind = dslKind)
    project.enableIsolatedProjects()
    // Re-snapshot settings between builds rather than waiting for asynchronous file watcher events.
    project.setGradleProperties("org.gradle.vfs.watch" to "false")
    val allProjects = project.rootDir.resolve(SpotlightProjectList.ALL_PROJECTS_LOCATION)
    val expectedProjects = allProjects.readLines().sorted()
    allProjects.writeText(expectedProjects.filterNot { it == ":rotoscope:hysteria" }.joinToString("\n", postfix = "\n"))
    project.rootDir.resolve("unlisted-project").apply {
      mkdirs()
      resolve(dslKind.buildFile).createNewFile()
    }
    val setting = when (dslKind) {
      GradleProject.DslKind.GROOVY -> "discoverUnlistedProjects false"
      GradleProject.DslKind.KOTLIN -> "discoverUnlistedProjects(false)"
    }
    val settingsFile = project.rootDir.resolve(dslKind.settingsFile)
    settingsFile.appendText("\nspotlight { projectDiscovery { directoryScan { $setting } } }\n")

    val result = project.buildAndFail(":${CheckSpotlightProjectListTask.NAME}")
    assertThat(result.output).contains("discovered via dependency graph but are not listed")
    assertThat(result.output).contains("\n  :rotoscope:hysteria\n")
    assertThat(result.output).doesNotContain("Found unlisted projects:")

    val fixResult = project.build(":${FixSpotlightProjectListTask.NAME}")
    assertThat(fixResult).task(":${FixSpotlightProjectListTask.NAME}").succeeded()
    assertThat(allProjects.readLines()).containsExactlyElementsIn(expectedProjects).inOrder()
    project.build(":${CheckSpotlightProjectListTask.NAME}")
    val cachedResult = project.build(":${CheckSpotlightProjectListTask.NAME}")
    assertThat(cachedResult).task(":${CheckSpotlightProjectListTask.NAME}").succeeded()
    assertThat(cachedResult.output).contains("Reusing configuration cache.")

    settingsFile.writeText(settingsFile.readText().replace(setting, setting.replace("false", "true")))
    val enabledResult = project.buildAndFail(":${CheckSpotlightProjectListTask.NAME}")
    assertThat(enabledResult.output).contains("Found unlisted projects:")
    assertThat(enabledResult.output).contains("\n  :unlisted-project\n")
  }

  @Test
  fun `invalid directory regex fails during settings configuration`() {
    val project = SpiritboxProject().build()
    project.enableIsolatedProjects()
    val allProjects = project.rootDir.resolve(SpotlightProjectList.ALL_PROJECTS_LOCATION)
    val originalContents = allProjects.readText()
    val settingsFile = project.rootDir.resolve("settings.gradle")
    settingsFile.appendText("\nspotlight { projectDiscovery { directoryScan { excludeDirectoriesMatchingRegex '[' } } }\n")
    val originalSettings = settingsFile.readText()

    val result = project.buildAndFail(":${FixSpotlightProjectListTask.NAME}")

    assertThat(result.output).contains("java.util.regex.PatternSyntaxException: Unclosed character class")
    assertThat(result.task(":${FixSpotlightProjectListTask.NAME}")).isNull()
    assertThat(allProjects.readText()).isEqualTo(originalContents)
    assertThat(settingsFile.readText()).isEqualTo(originalSettings)
  }

  @Test
  fun `check all-projects list ignores build files outside the main build`() {
    // Given
    val project = SpiritboxProject().build()
    project.enableIsolatedProjects()
    val allProjects = project.rootDir.resolve(SpotlightProjectList.ALL_PROJECTS_LOCATION)
    allProjects.writeText(allProjects.readLines().sorted().joinToString(separator = "\n", postfix = "\n"))
    val expectedContents = allProjects.readText()
    listOf(
      "build/functional-test/build.gradle",
      "buildSrc/build.gradle",
      "buildSrc/child/build.gradle.kts",
      "src/test/testData/build.gradle.kts",
      "src-gen/fixture/build.gradle",
      "scripts/tmp/generated-project/build.gradle",
      "separate-build/settings.gradle.kts",
      "separate-build/build.gradle.kts",
      "separate-build/child/build.gradle",
    ).forEach { path ->
      project.rootDir.resolve(path).apply {
        parentFile.mkdirs()
        createNewFile()
      }
    }

    // When
    project.build(":${FixSpotlightProjectListTask.NAME}")
    val result = project.build(":${CheckSpotlightProjectListTask.NAME}")

    // Then
    assertThat(result).task(":${CheckSpotlightProjectListTask.NAME}").succeeded()
    assertThat(allProjects.readText()).isEqualTo(expectedContents)
  }

  @Test
  fun `check all-projects list fails for a task-invoked unlisted project`() {
    val project = SpiritboxProject().build()
    project.enableIsolatedProjects()
    val allProjects = project.rootDir.resolve(SpotlightProjectList.ALL_PROJECTS_LOCATION)
    allProjects.writeText(allProjects.readLines().sorted().joinToString(separator = "\n", postfix = "\n"))
    project.rootDir.resolve("unlisted-project").apply {
      mkdirs()
      resolve("build.gradle").createNewFile()
    }

    val result = project.buildAndFail(":unlisted-project:help", ":${CheckSpotlightProjectListTask.NAME}")

    assertThat(result).task(":unlisted-project:help").succeeded()
    assertThat(result).task(":${CheckSpotlightProjectListTask.NAME}").failed()
    assertThat(result.output).contains("  :unlisted-project")
  }

  @Test
  fun `check all-projects list exclusions match exact paths only`() {
    val project = SpiritboxProject().build()
    project.enableIsolatedProjects()
    val allProjects = project.rootDir.resolve(SpotlightProjectList.ALL_PROJECTS_LOCATION)
    allProjects.writeText(allProjects.readLines().sorted().joinToString(separator = "\n", postfix = "\n"))
    listOf("optional-project", "optional-project/child", "optional-project-other").forEach { path ->
      project.rootDir.resolve(path).apply {
        mkdirs()
        resolve("build.gradle").createNewFile()
      }
    }
    val configuration = """
      spotlight {
        projectDiscovery {
          directoryScan {
            excludeProjectPaths ':optional-project'
          }
        }
      }
    """
    project.rootDir.resolve("settings.gradle").appendText("\n${configuration.trimIndent()}\n")

    val result = project.buildAndFail(":${CheckSpotlightProjectListTask.NAME}")

    assertThat(result.output).contains("Found unlisted projects:")
    assertThat(result.output).doesNotContain("\n  :optional-project\n")
    assertThat(result.output).contains("\n  :optional-project:child\n")
    assertThat(result.output).contains("\n  :optional-project-other\n")

    // Remove the child so dependency discovery does not pull in its excluded parent.
    project.rootDir.resolve("optional-project/child/build.gradle").delete()
    val expectedProjects = (allProjects.readLines() + ":optional-project-other").sorted()
    val fixResult = project.build(":${FixSpotlightProjectListTask.NAME}")
    assertThat(fixResult).task(":${FixSpotlightProjectListTask.NAME}").succeeded()
    assertThat(allProjects.readLines()).containsExactlyElementsIn(expectedProjects).inOrder()
    val passingResult = project.build(":${CheckSpotlightProjectListTask.NAME}")
    assertThat(passingResult).task(":${CheckSpotlightProjectListTask.NAME}").succeeded()
  }

  @Test
  fun `fix rejects excluded dependencies of disk-discovered projects without changing files`() {
    val project = SpiritboxProject().build()
    project.enableIsolatedProjects()
    val allProjects = project.rootDir.resolve(SpotlightProjectList.ALL_PROJECTS_LOCATION)
    val originalContents = allProjects.readText()
    project.rootDir.resolve("excluded-transitive").apply {
      mkdirs()
      resolve("build.gradle").writeText("plugins { id 'java-library' }\n")
    }
    project.rootDir.resolve("excluded-project").apply {
      mkdirs()
      resolve("build.gradle").writeText(
        """
        plugins { id 'java-library' }
        dependencies { implementation project(':excluded-transitive') }
        """.trimIndent()
      )
    }
    project.rootDir.resolve("unlisted-project").apply {
      mkdirs()
      resolve("build.gradle").writeText(
        """
        plugins { id 'java-library' }
        dependencies { implementation project(':excluded-project') }
        """.trimIndent()
      )
    }
    val settingsFile = project.rootDir.resolve("settings.gradle")
    settingsFile.appendText("\ninclude ':excluded-transitive'\n")
    val configuration = """
      spotlight {
        projectDiscovery {
          directoryScan {
            excludeProjectPaths ':excluded-project', ':excluded-transitive'
          }
        }
      }
    """
    settingsFile.appendText("\n${configuration.trimIndent()}\n")
    val originalSettings = settingsFile.readText()

    val result = project.buildAndFail(":${FixSpotlightProjectListTask.NAME}")

    assertThat(result).task(":${FixSpotlightProjectListTask.NAME}").failed()
    assertThat(result.output).contains("Found excluded projects required by the dependency graph:")
    assertThat(result.output).contains("\n  :excluded-project\n")
    assertThat(result.output).contains("\n  :excluded-transitive\n")
    assertThat(allProjects.readText()).isEqualTo(originalContents)
    assertThat(settingsFile.readText()).isEqualTo(originalSettings)
  }

  @ParameterizedTest
  @ValueSource(strings = [
    "excludeProjectPaths ':rotoscope:hysteria'",
    "excludeDirectoriesMatchingRegex 'rotoscope/hysteria'",
    "excludeDirectoriesMatchingRegex 'rotoscope'",
  ])
  fun `check and fix reject excluded dependencies`(exclusion: String) {
    val project = SpiritboxProject().build()
    project.enableIsolatedProjects()
    val allProjects = project.rootDir.resolve(SpotlightProjectList.ALL_PROJECTS_LOCATION)
    allProjects.writeText(allProjects.readLines().sorted().joinToString(separator = "\n", postfix = "\n"))
    val originalContents = allProjects.readText()
    val configuration = """
      spotlight {
        projectDiscovery {
          directoryScan {
            $exclusion
          }
        }
      }
    """
    val settingsFile = project.rootDir.resolve("settings.gradle")
    settingsFile.appendText("\n${configuration.trimIndent()}\n")
    val originalSettings = settingsFile.readText()

    listOf(CheckSpotlightProjectListTask.NAME, FixSpotlightProjectListTask.NAME).forEach { taskName ->
      val result = project.buildAndFail(":$taskName")

      assertThat(result).task(":$taskName").failed()
      assertThat(result.output).contains("Found excluded projects required by the dependency graph:")
      assertThat(result.output).contains("\n  :rotoscope:hysteria\n")
      assertThat(allProjects.readText()).isEqualTo(originalContents)
      assertThat(settingsFile.readText()).isEqualTo(originalSettings)
    }
  }

  @Test
  fun `check all-projects list fails when missing discovered projects`() {
    // Given
    val project = SpiritboxProject().build()
    project.enableIsolatedProjects()
    val allProjects = project.rootDir.resolve(SpotlightProjectList.ALL_PROJECTS_LOCATION)

    // Remove all child projects of :rotoscope but keep :rotoscope itself
    // Since :rotoscope depends on its children, BFS should discover them as missing
    val projectsList = allProjects.readLines()
      .filterNot { it.startsWith(":rotoscope:") }
      .sorted() // Keep it sorted so checkSorted() passes
    allProjects.writeText(projectsList.joinToString(separator = "\n", postfix = "\n"))

    // When
    val result = project.buildAndFail(":${CheckSpotlightProjectListTask.NAME}")

    // Then
    assertThat(result).task(":${CheckSpotlightProjectListTask.NAME}").failed()
    assertThat(result.output).contains("Found projects missing from ${SpotlightProjectList.ALL_PROJECTS_LOCATION}")
    assertThat(result.output).contains("discovered via dependency graph but are not listed")
  }

  @Test
  fun `fix removes invalid and adds missing projects in one pass`() {
    // Given
    val project = SpiritboxProject().build()
    project.enableIsolatedProjects()
    val allProjects = project.rootDir.resolve(SpotlightProjectList.ALL_PROJECTS_LOCATION)

    // Add an invalid project and remove the three children required by :rotoscope.
    val expectedProjects = allProjects.readLines().sorted()
    val projectsList = expectedProjects.filterNot { it.startsWith(":rotoscope:") } + ":invalid-project"
    allProjects.writeText(projectsList.sortedDescending().joinToString("\n"))

    // Create the invalid directory but no build file
    project.rootDir.resolve("invalid-project").mkdirs()

    // When
    val result = project.build(":${FixSpotlightProjectListTask.NAME}")

    // Then
    assertThat(result).task(":${FixSpotlightProjectListTask.NAME}").succeeded()
    assertThat(allProjects.readLines()).containsExactlyElementsIn(expectedProjects).inOrder()
    assertThat(result.output).contains("removed 1 invalid project(s)")
    assertThat(result.output).contains("added 3 missing project(s)")

    val checkResult = project.build(":${CheckSpotlightProjectListTask.NAME}")
    assertThat(checkResult).task(":${CheckSpotlightProjectListTask.NAME}").succeeded()
  }

  @Test
  fun `fix migrates include statements from settings to all-projects`() {
    // Given
    val project = SpiritboxProject().build()
    project.enableIsolatedProjects()
    val allProjects = project.rootDir.resolve(SpotlightProjectList.ALL_PROJECTS_LOCATION)
    val settingsFile = project.rootDir.resolve("settings.gradle")

    // Create test projects with build files
    val testProject1 = project.rootDir.resolve("test-project-1")
    testProject1.mkdirs()
    testProject1.resolve("build.gradle").createNewFile()

    val testProject2 = project.rootDir.resolve("test-project-2")
    testProject2.mkdirs()
    testProject2.resolve("build.gradle").createNewFile()

    // Add include statements to settings file
    val currentContent = settingsFile.readText()
    val includeStatements = """
        |
        |include ':test-project-1'
        |include(":test-project-2")
      """.trimMargin()
    settingsFile.writeText(currentContent + includeStatements)

    // When
    val result = project.build(":${FixSpotlightProjectListTask.NAME}")

    // Then
    assertThat(result).task(":${FixSpotlightProjectListTask.NAME}").succeeded()

    // Verify projects were added to all-projects.txt
    val updatedProjectsList = allProjects.readLines()
    assertThat(updatedProjectsList).contains(":test-project-1")
    assertThat(updatedProjectsList).contains(":test-project-2")
    assertThat(updatedProjectsList).isEqualTo(updatedProjectsList.sorted())

    // Verify include statements were removed from settings
    val updatedSettingsContent = settingsFile.readText()
    val hasIncludeStatements = updatedSettingsContent.lines()
      .any { line -> line.trim().startsWith("include(") || line.trim().startsWith("include ") }
    assertThat(hasIncludeStatements).isFalse()

    // Verify check passes after fix
    val checkResult = project.build(":${CheckSpotlightProjectListTask.NAME}")
    assertThat(checkResult).task(":${CheckSpotlightProjectListTask.NAME}").succeeded()
  }
}
