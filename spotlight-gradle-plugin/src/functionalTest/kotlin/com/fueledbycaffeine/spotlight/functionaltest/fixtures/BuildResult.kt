package com.fueledbycaffeine.spotlight.functionaltest.fixtures

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.gradle.testkit.runner.BuildResult
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.readLines
import kotlin.io.path.toPath

fun BuildResult.includedProjects(): List<String> {
  val includeProjectsLine = output.lines()
    .first { it.startsWith("Included projects:") }

  return Regex("project '([^']+)'").findAll(includeProjectsLine)
    .map {
      val (path) = it.destructured
      path
    }
    .toList()
}

private val CC_INVALIDATION_REASON = "configuration cache cannot be reused because (.*)".toRegex()
private val CC_REPORT_REGEX = "See the complete report at (.*)".toRegex()
private val BEGIN_CC_REPORT_JSON = "// begin-report-data"
private val END_CC_REPORT_JSON = "// end-report-data"

// Things to ignore in CC reports that only happen in github actions because of the init scripts being added
// https://github.com/gradle/actions/tree/v4.4.0/sources/src/resources/init-scripts
private val GITHUB_ACTIONS_STUFF = listOf(
  "GRADLE_ACTIONS_SKIP_BUILD_RESULT_CAPTURE",
  "GITHUB_DEPENDENCY_GRAPH_ENABLED",
  "DEVELOCITY_INJECTION_INIT_SCRIPT_NAME",
  "develocity-injection.init-script-name",
  "DEVELOCITY_INJECTION_ENABLED",
  "develocity-injection.enabled",
)

data class CCReport(
  val diagnostics: List<CCDiagnostic>,
  val totalProblemCount: Int,
) {
  val inputs: List<CCDiagnostic.Input> get() = diagnostics.map { it.input }
}

data class CCDiagnostic(
  val trace: List<Trace>,
  @Json(name = "input")
  val inputJunk: List<InputInternal>,
) {
  data class Trace(
    val kind: String,
    val location: String = "unknown",
  )

  data class InputInternal(
    @Json(name = "text")
    val type: String?,
    val name: String?,
  )

  data class Input(
    val type: String,
    val name: String,
  )

  val input = Input(
    inputJunk.firstNotNullOf { it.type }.trim(),
    inputJunk.firstNotNullOf { it.name }.trim(),
  )
}

fun BuildResult.ccReport(): CCReport = readConfigurationCacheReport(output.lines())

fun SyncResult.ccReport(): CCReport = readConfigurationCacheReport(stdout.lines())

@OptIn(ExperimentalStdlibApi::class)
private fun readConfigurationCacheReport(logLines: List<String>): CCReport {
  val match = logLines.firstNotNullOf { CC_REPORT_REGEX.find(it, 0) }
  val (reportUrl) = match.destructured
  val reportPath = URI.create(reportUrl).toPath()

  val ccInputsJson = reportPath.readLines().run {
    get(indexOf(BEGIN_CC_REPORT_JSON) + 1)
  }

  val moshi = Moshi.Builder()
    .addLast(KotlinJsonAdapterFactory())
    .build()

  val adapter = moshi.adapter<CCReport>()

  val report = adapter.fromJson(ccInputsJson)!!

  return report.copy(
    diagnostics = report.diagnostics.filter { it.input.name !in GITHUB_ACTIONS_STUFF }
  )
}

val BuildResult.configurationCacheReused: Boolean get() {
  return output.lines().any { "Configuration cache entry reused" in it }
}

val BuildResult.configurationCacheStored: Boolean get() {
  return output.lines().any { "Configuration cache entry stored" in it }
}

val BuildResult.configurationCacheUpdated: Boolean get() {
  return output.lines().any { "Configuration cache entry updated" in it }
}

val SyncResult.configurationCacheReused: Boolean get() {
  return stdout.lines().any { "Configuration cache entry reused" in it }
}

val SyncResult.configurationCacheStored: Boolean get() {
  return stdout.lines().any { "Configuration cache entry stored" in it }
}

val SyncResult.configurationCacheUpdated: Boolean get() {
  return stdout.lines().any { "Configuration cache entry updated" in it }
}

val BuildResult.configurationCacheInvalidationReason: String get() {
  val match = output.lines().firstNotNullOf { CC_INVALIDATION_REASON.find(it) }
  val (reason) = match.destructured
  return reason
}

val SyncResult.configurationCacheInvalidationReason: String get() {
  val match = stdout.lines().firstNotNullOf { CC_INVALIDATION_REASON.find(it) }
  val (reason) = match.destructured
  return reason
}
