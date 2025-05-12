package com.fueledbycaffeine.spotlight.fixtures

import com.autonomousapps.kit.AbstractGradleProject
import com.autonomousapps.kit.GradleProject
import com.autonomousapps.kit.gradle.Dependency.Companion.implementation
import com.autonomousapps.kit.gradle.Plugin
import com.autonomousapps.kit.gradle.Plugins

class SpiritboxProject : AbstractGradleProject() {
  fun build(): GradleProject {
    return newGradleProjectBuilder()
      .withRootProject {
        settingsScript.rootProjectName = "spiritbox"
        settingsScript.plugins = Plugins(
          Plugin("com.fueledbycaffeine.spotlight", PLUGIN_UNDER_TEST_VERSION)
        )
      }
      .withSubproject("hysteria") {
        withBuildScript {
          plugins(Plugin.javaLibrary)
        }
      }
      .withSubproject("rotoscope") {
        withBuildScript {
          plugins(Plugin.javaLibrary)
        }
      }
      .withSubproject("sew-me-up") {
        withBuildScript {
          plugins(Plugin.javaLibrary)
        }
      }
      .withSubproject("rotoscope-ep") {
        withBuildScript {
          plugins(Plugin.javaLibrary)
          dependencies(
            implementation(":hysteria"),
            implementation(":rotoscope"),
            implementation(":sew-me-up"),
          )
        }
      }
      .withSubproject("cellar-door") {
        withBuildScript {
          plugins(Plugin.javaLibrary)
        }
      }
      .withSubproject("jaded") {
        withBuildScript {
          plugins(Plugin.javaLibrary)
        }
      }
      .withSubproject("too-close-too-late") {
        withBuildScript {
          plugins(Plugin.javaLibrary)
        }
      }
      .withSubproject("the-fear-of-fear") {
        withBuildScript {
          plugins(Plugin.javaLibrary)
          dependencies(
            implementation(":cellar-door"),
            implementation(":jaded"),
            implementation(":too-close-too-late"),
          )
        }
      }
      .withSubproject("eternal-blue") { }
      .withSubproject("tsunami-sea") { }
      .write()
  }
}