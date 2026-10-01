package com.fueledbycaffeine.buildlogic

import org.gradle.api.Action
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension
import org.gradle.plugin.devel.PluginDeclaration
import javax.inject.Inject

abstract class GradlePluginConventionsExtension @Inject constructor(
  private val development: GradlePluginDevelopmentExtension,
  private val publishing: PublishingExtension,
) {
  fun gradlePlugin(name: String, configure: Action<in PluginDeclaration>) {
    val plugin = development.plugins.create(name, configure)
    val testPublication = publishing.publications.getByName("functionalTest") as MavenPublication
    val testGroup = testPublication.groupId
    val testArtifact = testPublication.artifactId
    val testVersion = testPublication.version
    val markerName = "functionalTest${name.replaceFirstChar { it.titlecase() }}Marker"
    publishing.publications.create(markerName, MavenPublication::class.java) { marker ->
      marker.groupId = plugin.id
      marker.artifactId = "${plugin.id}.gradle.plugin"
      marker.pom.withXml {
        val dependency = it.asNode().appendNode("dependencies").appendNode("dependency")
        dependency.appendNode("groupId", testGroup)
        dependency.appendNode("artifactId", testArtifact)
        dependency.appendNode("version", testVersion)
      }
    }
  }
}
