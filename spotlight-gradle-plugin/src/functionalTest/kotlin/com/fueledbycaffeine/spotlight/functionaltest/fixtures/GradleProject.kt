package com.fueledbycaffeine.spotlight.functionaltest.fixtures

import com.autonomousapps.kit.GradleBuilder
import com.autonomousapps.kit.GradleProject
import org.gradle.testkit.runner.BuildResult
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.model.gradle.BasicGradleProject

fun GradleProject.build(vararg args: String): BuildResult =
  GradleBuilder.build(rootDir, *args, "--info")

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

fun GradleProject.sync(): SyncResult =
  GradleConnector.newConnector()
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