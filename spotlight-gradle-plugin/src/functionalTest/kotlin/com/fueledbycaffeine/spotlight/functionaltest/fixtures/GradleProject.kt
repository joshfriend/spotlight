package com.fueledbycaffeine.spotlight.functionaltest.fixtures

import com.autonomousapps.kit.GradleBuilder
import com.autonomousapps.kit.GradleProject
import com.fueledbycaffeine.spotlight.buildscript.models.SpotlightModel
import org.gradle.testkit.runner.BuildResult
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildController
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.model.gradle.ProjectPublications
import org.gradle.util.GradleVersion
import java.io.File

/**
 * The Gradle version under test: the `gradleVersion` system property when set (version matrix in
 * CI), otherwise the version running this build.
 */
internal val testGradleVersion: GradleVersion get() = GradleVersion.version(
  System.getProperty("gradleVersion").ifBlank { GradleVersion.current().version }
)

fun GradleProject.build(rootDir: File, vararg args: String): BuildResult =
  GradleBuilder.build(testGradleVersion, rootDir, *args, "--info")

fun GradleProject.build(vararg args: String): BuildResult =
  GradleBuilder.build(testGradleVersion, rootDir, *args, "--info")

fun GradleProject.buildAndFail(vararg args: String): BuildResult =
  GradleBuilder.buildAndFail(testGradleVersion, rootDir, *args, "--info")

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
  val model: SpotlightModel,
  override val stdout: String,
  override val stderr: String,
): ToolingResult

/**
 * Mimics an IDE sync: fetches a model for each project individually (like IntelliJ does), plus the
 * build-scoped [SpotlightModel]. Fetching per-project models is what allows isolated projects to
 * partially reuse a configuration cache entry when only some projects were invalidated.
 */
private class IdeSyncAction : BuildAction<SpotlightModel> {
  override fun execute(controller: BuildController): SpotlightModel {
    for (project in controller.buildModel.projects) {
      controller.findModel(project, ProjectPublications::class.java)
    }
    return controller.getModel(SpotlightModel::class.java)
  }
}

fun GradleProject.sync(): SyncResult = sync(testGradleVersion)

fun GradleProject.sync(gradleVersion: GradleVersion): SyncResult =
  GradleConnector.newConnector()
    .useGradleVersion(gradleVersion.version)
    .forProjectDirectory(rootDir)
    .connect().use {
      val stdout = TeeOutputStream(System.out)
      val stderr = TeeOutputStream(System.err)
      val model = it.action(IdeSyncAction())
        .setStandardOutput(stdout)
        .setStandardError(stderr)
        .addArguments("--info")
        .addJvmArguments(
          "-Didea.sync.active=true",
          "-Dorg.gradle.internal.isolated-projects.caching=tooling",
        )
        .run()
      stdout.close()
      stderr.close()
      SyncResult(model, stdout.output, stderr.output)
    }
