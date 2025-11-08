package com.fueledbycaffeine.spotlight.functionaltest.fixtures

import com.autonomousapps.kit.AbstractGradleProject
import com.autonomousapps.kit.GradleProject
import com.autonomousapps.kit.Source
import com.autonomousapps.kit.SourceType
import com.autonomousapps.kit.gradle.Dependency.Companion.implementation
import com.autonomousapps.kit.gradle.Plugin
import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList
import java.io.File

private val INCLUDE_PATTERN = Regex("include[(\\s]+?[\"'](\\S+)[\"']")

class SpiritboxProject : AbstractGradleProject() {
  fun build(dslKind: GradleProject.DslKind = GradleProject.DslKind.GROOVY): GradleProject {
    val project = newGradleProjectBuilder(dslKind)
      .withIncludedBuild("included-build") {
        withRootProject {
          withSettingsScript {
            rootProjectName = "included-build"
          }
          withBuildScript {
            plugins(Plugin.javaLibrary)
          }
          sources = mutableListOf(
            Source.java(
              """
              package com.spiritbox;
              public class Perennial {}
              """.trimIndent()
            ).withPath("com/spiritbox", "Perennial").build()
          )
        }
      }
      .withRootProject {
        withSettingsScript {
          rootProjectName = "spiritbox"
          plugins(Plugin("com.fueledbycaffeine.spotlight", PLUGIN_UNDER_TEST_VERSION))
          additions = "includeBuild(\"included-build\")"
        }
      }
      .withSubproject(":rotoscope:hysteria") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":rotoscope:rotoscope") {
        withBuildScript { plugins(Plugin.javaLibrary) }
        sources = mutableListOf(
          Source(SourceType.JAVA, "Rotoscope", "com/rotoscope",
            """
            package com.rotoscope;
            
            public class Rotoscope { }
            """.trimIndent()
          )
        )
      }
      .withSubproject(":rotoscope:sew-me-up") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":rotoscope") {
        withBuildScript {
          plugins(Plugin.javaLibrary)
          dependencies(
            implementation(":rotoscope:hysteria"),
            implementation(":rotoscope:rotoscope"),
            implementation(":rotoscope:sew-me-up"),
          )
        }
      }
      .withSubproject(":the-fear-of-fear:cellar-door") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":the-fear-of-fear:jaded") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":the-fear-of-fear:too-close-too-late") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":the-fear-of-fear:angel-eyes") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":the-fear-of-fear:the-void") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":the-fear-of-fear:ultraviolet") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":the-fear-of-fear") {
        withBuildScript {
          plugins(Plugin.javaLibrary)
          dependencies(
            implementation(":the-fear-of-fear:cellar-door"),
            implementation(":the-fear-of-fear:jaded"),
            implementation(":the-fear-of-fear:too-close-too-late"),
            implementation(":the-fear-of-fear:angel-eyes"),
            implementation(":the-fear-of-fear:the-void"),
            implementation(":the-fear-of-fear:ultraviolet"),
          )
        }
      }
      .withSubproject(":eternal-blue:sun-killer") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":eternal-blue:hurt-you") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":eternal-blue:yellowjacket") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":eternal-blue:the-summit") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":eternal-blue:secret-garden") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":eternal-blue:silk-in-the-strings") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":eternal-blue:holy-roller") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":eternal-blue:eternal-blue") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":eternal-blue:we-live-in-a-strange-world") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":eternal-blue:halcyon") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":eternal-blue:circle-with-me") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":eternal-blue:constance") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":eternal-blue") {
        withBuildScript {
          plugins(Plugin.javaLibrary)
          dependencies(
            implementation(":eternal-blue:sun-killer"),
            implementation(":eternal-blue:hurt-you"),
            implementation(":eternal-blue:yellowjacket"),
            implementation(":eternal-blue:the-summit"),
            implementation(":eternal-blue:secret-garden"),
            implementation(":eternal-blue:silk-in-the-strings"),
            implementation(":eternal-blue:holy-roller"),
            implementation(":eternal-blue:eternal-blue"),
            implementation(":eternal-blue:we-live-in-a-strange-world"),
            implementation(":eternal-blue:halcyon"),
            implementation(":eternal-blue:circle-with-me"),
            implementation(":eternal-blue:constance"),
          )
        }
      }
      .withSubproject(":tsunami-sea") { }
      .withSubproject(":tsunami-sea:fata-morgana") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":tsunami-sea:black-rainbow") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":tsunami-sea:perfect-soul") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":tsunami-sea:keep-sweet") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":tsunami-sea:soft-spine") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":tsunami-sea:tsunami-sea") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":tsunami-sea:a-haven-with-two-faces") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":tsunami-sea:no-loss-no-love") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":tsunami-sea:crystal-roses") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":tsunami-sea:ride-the-wave") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .withSubproject(":tsunami-sea:deep-end") {
        withBuildScript { plugins(Plugin.javaLibrary) }
      }
      .write()

    project.rootDir.resolve("gradle.properties")
      .appendText(
        """

        org.gradle.parallel=true
        org.gradle.configureondemand=true
        org.gradle.configuration-cache=true
        org.gradle.configuration-cache.parallel=true
        """.trimIndent()
      )

    val settings = project.rootDir.resolve(dslKind.settingsFile)
    val settingsContents = settings.readText()
    val projectPaths = INCLUDE_PATTERN.findAll(settingsContents)
      .map {
        val (path) = it.destructured
        path
      }
    val strippedSettings = settingsContents.lines()
      .filterNot { it.matches("include[( ][\"'].*[\"']\\)?".toRegex()) }
    settings.writeText(strippedSettings.joinToString("\n"))

    project.gradleDir.mkdirs()
    project.allProjects.createNewFile()
    project.ideProjects.createNewFile()
    project.allProjects.writeText(projectPaths.joinToString("\n"))

    return project
  }
}

val GradleProject.gradleDir: File get() = rootDir.resolve("gradle")
val GradleProject.allProjects: File get() = rootDir.resolve(SpotlightProjectList.ALL_PROJECTS_LOCATION)
val GradleProject.ideProjects: File get() = rootDir.resolve(SpotlightProjectList.IDE_PROJECTS_LOCATION)