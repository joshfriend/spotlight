package com.fueledbycaffeine.spotlight.functionaltest.fixtures

import com.autonomousapps.kit.GradleBuilder
import com.autonomousapps.kit.GradleProject
import org.gradle.testkit.runner.BuildResult
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.gradle.util.GradleVersion
import java.io.File

private val gradleVersion: GradleVersion get() = GradleVersion.version(
  System.getProperty("gradleVersion").ifBlank { GradleVersion.current().version }
)

fun GradleProject.build(rootDir: File, vararg args: String): BuildResult =
  GradleBuilder.build(gradleVersion, rootDir, *args, "--info")

fun GradleProject.build(vararg args: String): BuildResult =
  GradleBuilder.build(gradleVersion, rootDir, *args, "--info")

fun GradleProject.setGradleProperties(vararg props: Pair<String, String>) {
  rootDir.resolve("gradle.properties")
    .appendText(buildString {
      appendLine()
      props.map { (k, v) -> appendLine("$k=$v") }
    })
}

data class SyncResult(
  val projects: List<BasicGradleProject>,
  val stdout: String,
  val stderr: String,
)

fun GradleProject.sync(): SyncResult = sync(gradleVersion)

fun GradleProject.sync(gradleVersion: GradleVersion): SyncResult =
  GradleConnector.newConnector()
    .useGradleVersion(gradleVersion.version)
    .forProjectDirectory(rootDir)
    .connect().use {
      val stdout = TeeOutputStream(System.out)
      val stderr = TeeOutputStream(System.err)
      val projects = it.action(GetIncludedProjectsBuildAction())
        .setStandardOutput(stdout)
        .setStandardError(stderr)
        .addArguments("--info")
        .addJvmArguments("-Didea.sync.active=true")
        .run()
      stdout.close()
      stderr.close()
      SyncResult(projects, stdout.output, stderr.output)
    }