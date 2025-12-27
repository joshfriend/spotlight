package com.fueledbycaffeine.spotlight.functionaltest.fixtures

import com.autonomousapps.kit.GradleBuilder
import com.autonomousapps.kit.GradleProject
import com.fueledbycaffeine.spotlight.tooling.BuildscriptParsersModel
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

fun GradleProject.buildAndFail(vararg args: String): BuildResult =
  GradleBuilder.buildAndFail(gradleVersion, rootDir, *args, "--info")

fun GradleProject.setGradleProperties(vararg props: Pair<String, String>) {
  rootDir.resolve("gradle.properties")
    .appendText(buildString {
      appendLine()
      props.map { (k, v) -> appendLine("$k=$v") }
    })
}

interface ToolingResult {
  val stdout: String
  val stderr: String
}

data class SyncResult(
  val projects: List<BasicGradleProject>,
  override val stdout: String,
  override val stderr: String,
): ToolingResult

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

data class ParsersModelSyncResult(
  val parsersModel: BuildscriptParsersModel,
  override val stdout: String,
  override val stderr: String,
): ToolingResult

fun GradleProject.syncWithParsersModel(): ParsersModelSyncResult =
  GradleConnector.newConnector()
    .useGradleVersion(gradleVersion.version)
    .forProjectDirectory(rootDir)
    .connect().use {
      val stdout = TeeOutputStream(System.out)
      val stderr = TeeOutputStream(System.err)
      val model = it.model(BuildscriptParsersModel::class.java)
        .setStandardOutput(stdout)
        .setStandardError(stderr)
        .addArguments("--info")
        .addJvmArguments("-Didea.sync.active=true")
        .get()
      stdout.close()
      stderr.close()
      ParsersModelSyncResult(model, stdout.output, stderr.output)
    }

