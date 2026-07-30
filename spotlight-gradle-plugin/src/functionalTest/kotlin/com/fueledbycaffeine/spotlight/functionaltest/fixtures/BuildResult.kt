package com.fueledbycaffeine.spotlight.functionaltest.fixtures

import com.fueledbycaffeine.spotlight.functionaltest.fixtures.CCDiagnostic.Input.Type.*
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.ConfigurationCacheOutcome
import org.gradle.util.GradleVersion
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
// The Develocity plugin reads many of its own properties, environment variables, and files at
// configuration time (proxy settings, mTLS certs, artifact cache markers, etc). The exact set
// varies by plugin version, so match by prefix rather than exact name.
private fun CCDiagnostic.Input.isDevelocityInput(): Boolean {
  return name.startsWith("develocity.") ||
    name.startsWith("DEVELOCITY_") ||
    name.startsWith("com.gradle.") ||
    ".develocity/" in name
}

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
      .filter { !it.input.isDevelocityInput() }
  )
}

typealias CCOutcome = ConfigurationCacheOutcome

/** The Gradle version that added [BuildResult.getConfigurationCacheOutcome]. */
private val CC_OUTCOME_API_VERSION = GradleVersion.version("9.8")

private val targetHasCcOutcomeApi: Boolean
  get() = testGradleVersion.baseVersion >= CC_OUTCOME_API_VERSION

val BuildResult.ccOutcome: CCOutcome get() = when {
  targetHasCcOutcomeApi -> configurationCacheOutcome
  // Older target Gradle versions do not report the outcome to TestKit; parse console output instead
  else -> parseCcOutcome(output.lines())
}

// The Tooling API equivalent (OperationType.CONFIGURATION_CACHE progress events) also
// requires a 9.8+ client and target, so sync results parse output until then.
val ToolingResult.ccOutcome: CCOutcome get() = parseCcOutcome(stdout.lines())

private fun parseCcOutcome(logLines: List<String>): CCOutcome {
  val entryLine = logLines.lastOrNull { "Configuration cache entry" in it }
    ?: return CCOutcome.NOT_ENABLED
  return when {
    "reused" in entryLine -> CCOutcome.REUSED
    "updated" in entryLine -> CCOutcome.UPDATED
    "discarded" in entryLine -> CCOutcome.DISCARDED
    "not stored" in entryLine -> CCOutcome.NOT_STORED
    "stored" in entryLine -> CCOutcome.STORED
    else -> CCOutcome.UNDETERMINED
  }
}

/**
 * The extent of a [CCOutcome.UPDATED] partial cache hit: how many projects were invalidated and
 * re-configured versus reused as-is from the existing entry.
 */
data class CCUpdateStats(
  val updatedProjects: Int,
  val reusedProjects: Int,
)

// Message format is stable since Gradle 8.8:
// "Configuration cache entry updated for 1 project, 4 projects up-to-date."
// "Configuration cache entry updated for 2 projects with 1 problem, no projects up-to-date."
// Gradle 9.8's ConfigurationCacheEntryUpdatedResult does not expose these counts, so they must be
// parsed from output even when the native outcome API is available.
private val CC_UPDATED_STATS =
  "Configuration cache entry updated for (no projects|\\d+ projects?)(?: with [^,]+)?, (no projects|\\d+ projects?) up-to-date\\.".toRegex()

/**
 * The partial hit stats for this build, or null if the entry was not [CCOutcome.UPDATED].
 */
val BuildResult.ccUpdateStats: CCUpdateStats? get() = parseCcUpdateStats(output.lines())

val ToolingResult.ccUpdateStats: CCUpdateStats? get() = parseCcUpdateStats(stdout.lines())

private fun parseCcUpdateStats(logLines: List<String>): CCUpdateStats? {
  val match = logLines.firstNotNullOfOrNull { CC_UPDATED_STATS.find(it) } ?: return null
  val (updated, reused) = match.destructured
  return CCUpdateStats(
    updatedProjects = parseProjectCount(updated),
    reusedProjects = parseProjectCount(reused),
  )
}

private fun parseProjectCount(counter: String): Int =
  if (counter.startsWith("no ")) 0 else counter.substringBefore(' ').toInt()

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
