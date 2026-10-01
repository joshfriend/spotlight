package com.fueledbycaffeine.buildlogic

import com.autonomousapps.GradleTestKitPlugin
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.MavenPublishPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenLocal
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.plugins.signing.SigningExtension
import org.gradle.plugins.signing.SigningPlugin

class PublishingConventionPlugin : Plugin<Project> {
  override fun apply(project: Project): Unit = with(project) {
    pluginManager.apply(MavenPublishPlugin::class.java)
    pluginManager.apply(GradleTestKitPlugin::class.java)

    group = "com.fueledbycaffeine.spotlight"
    version = providers.gradleProperty("VERSION_NAME").get()

    extensions.getByType(MavenPublishBaseExtension::class.java).apply {
      publishToMavenCentral(true)
      pom { pom ->
        pom.inceptionYear.set("2025")
        pom.url.set("https://github.com/joshfriend/spotlight/")
        pom.licenses { licenses ->
          licenses.license {
            it.name.set("MIT License")
            it.url.set("https://choosealicense.com/licenses/mit/")
            it.distribution.set("https://choosealicense.com/licenses/mit/")
          }
        }
        pom.developers { developers ->
          developers.developer {
            it.id.set("joshfriend")
            it.name.set("Josh Friend")
            it.url.set("https://github.com/joshfriend/")
          }
        }
        pom.scm {
          it.url.set("https://github.com/joshfriend/spotlight/")
          it.connection.set("scm:git:git://github.com/joshfriend/spotlight.git")
          it.developerConnection.set("scm:git:ssh://git@github.com/joshfriend/spotlight.git")
        }
      }
    }

    // Keep release signatures out of the publications used by TestKit.
    val publications = extensions.getByType(PublishingExtension::class.java).publications
    publications.create("functionalTest", MavenPublication::class.java) {
      it.from(components.getByName("java"))
    }

    // Keep TestKit's project dependencies, but don't schedule signed release publications.
    tasks.named("installForFunctionalTest").configure { install ->
      install.setDependsOn(install.dependsOn.filter { it != "publishAllPublicationsToFunctionalTestRepository" })
      install.dependsOn(tasks.withType(PublishToMavenRepository::class.java).matching {
        it.name.startsWith("publishFunctionalTest") && it.name.endsWith("ToFunctionalTestRepository")
      })
    }
    tasks.withType(PublishToMavenRepository::class.java).configureEach {
      val correctRepository =
        it.name.endsWith("ToFunctionalTestRepository") == it.name.startsWith("publishFunctionalTest")
      it.onlyIf("publication belongs to this repository") { correctRepository }
    }
    tasks.withType(PublishToMavenLocal::class.java).configureEach {
      val releasePublication = !it.name.startsWith("publishFunctionalTest")
      it.onlyIf("only release publications are installed in Maven local") { releasePublication }
    }

    val signingKey = providers.gradleProperty("signingInMemoryKey")
    if (signingKey.isPresent) {
      pluginManager.apply(SigningPlugin::class.java)
      extensions.getByType(SigningExtension::class.java).apply {
        isRequired = !version.toString().endsWith("-SNAPSHOT")
        useInMemoryPgpKeys(
          providers.gradleProperty("signingInMemoryKeyId").orNull,
          signingKey.get(),
          providers.gradleProperty("signingInMemoryKeyPassword").getOrElse("")
        )
        sign(publications.matching {
          it is MavenPublication && !it.name.startsWith("functionalTest")
        })
      }
    }
  }
}
