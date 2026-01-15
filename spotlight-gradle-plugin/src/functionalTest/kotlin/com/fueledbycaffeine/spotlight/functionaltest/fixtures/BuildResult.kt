package com.fueledbycaffeine.spotlight.functionaltest.fixtures

import com.fueledbycaffeine.spotlight.functionaltest.fixtures.CCDiagnostic.Input.Type.*
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.gradle.testkit.runner.BuildResult
import java.net.URI
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
private val DEVELOCITY_PLUGIN_STUFF = listOf<String>(
  "com.gradle.scan.multi-application",
  "develocity.deprecation.muteWarnings",
  "develocity.deprecation.captureOrigin",
  "develocity.deprecation.displayFullStackTrace",
  "develocity.projectId",
  "DEVELOCITY_SERVER_HTTP_PROXY_HOST",
  "DEVELOCITY_SERVER_HTTP_PROXY_PORT",
  "DEVELOCITY_SERVER_HTTPS_PROXY_HOST",
  "DEVELOCITY_SERVER_HTTPS_PROXY_PORT",
  "DEVELOCITY_SERVER_SOCKS_PROXY_HOST",
  "DEVELOCITY_SERVER_SOCKS_PROXY_PORT",
  "develocity.server.publicOverride",
  "com.gradle.enterprise.server.publicOverride",
)

data class CCReport(
  val diagnostics: List<CCDiagnostic>,
  val totalProblemCount: Int,
) {
  // TODO: See note below about project properties not listed in some CC reports. They do show up
  //  in newer versions but since we treat them the same they are duplicated.
  val inputs: List<CCDiagnostic.Input> get() = diagnostics.map { it.input }.distinct()
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
    val type: Type,
    val name: String,
  ) {
    enum class Type(private val names: List<String>) {
      // Gradle versions prior to 9.1.0 apparently do not list project properties as inputs in the
      // configuration cache report. Spotlight checks both the project and system property for
      // stuff like `spotlight.enabled` so even though the project property isn't listed, it can
      // still be verified in tests. We're just going to treat these as equivalent even though they
      // technically aren't so that lots of gradle version checking isn't required in the tests.
      // TODO: Once minGradle version is 9.1.0, break these out separately
      PROPERTY(listOf("Gradle property", "system property")),
      FILE(listOf("file")),
      FILE_SYSTEM_ENTRY(listOf("file system entry")),
      DIRECTORY_CONTENT(listOf("directory content")),
      ENVIRONMENT_VARIABLE(listOf("environment variable")),
      CUSTOM_SOURCE(listOf("value from custom source")),
      ;

      companion object {
        fun of(name: String) = entries.first { name in it.names }
      }
    }


    companion object {
      val SpotlightValueSource = Input(CUSTOM_SOURCE, "SpotlightIncludedProjectsValueSource")
      val IdeSyncActive = Input(PROPERTY, "idea.sync.active")
      val SpotlightEnabled = Input(PROPERTY, "spotlight.enabled")

      val SPOTLIGHT_INPUTS = listOf(
        SpotlightEnabled,
        IdeSyncActive,
        SpotlightValueSource,
      )
    }
  }

  val input = Input(
    Input.Type.of(inputJunk.firstNotNullOf { it.type }.trim()),
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
    diagnostics = report.diagnostics
      .filter { it.input.name !in GITHUB_ACTIONS_STUFF }
      .filter { it.input.name !in DEVELOCITY_PLUGIN_STUFF }
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

val ToolingResult.configurationCacheReused: Boolean get() {
  return stdout.lines().any { "Configuration cache entry reused" in it }
}

val ToolingResult.configurationCacheStored: Boolean get() {
  return stdout.lines().any { "Configuration cache entry stored" in it }
}

val ToolingResult.configurationCacheUpdated: Boolean get() {
  return stdout.lines().any { "Configuration cache entry updated" in it }
}

val BuildResult.configurationCacheInvalidationReason: String get() {
  val match = output.lines().firstNotNullOf { CC_INVALIDATION_REASON.find(it) }
  val (reason) = match.destructured
  return reason
}

val ToolingResult.configurationCacheInvalidationReason: String get() {
  val match = stdout.lines().firstNotNullOf { CC_INVALIDATION_REASON.find(it) }
  val (reason) = match.destructured
  return reason
}
