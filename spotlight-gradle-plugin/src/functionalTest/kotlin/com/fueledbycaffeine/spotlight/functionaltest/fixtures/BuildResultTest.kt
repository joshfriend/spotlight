package com.fueledbycaffeine.spotlight.functionaltest.fixtures

import com.fueledbycaffeine.spotlight.functionaltest.fixtures.CCDiagnostic.Input
import com.google.common.truth.Truth.assertThat
import org.gradle.util.GradleVersion
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Path
import kotlin.io.path.writeText

class BuildResultTest {
  @TempDir
  lateinit var tempDir: Path

  @ParameterizedTest
  @CsvSource(
    "8.8, false",
    "9.7.1, false",
    "9.8.0, false",
    "9.9.0-20260919063608+0000, true",
    "9.9.0, true",
  )
  fun `reads inputs and problem count from both report formats`(version: String, splitReport: Boolean) {
    val diagnostics = """
      [
        {"trace":[{"kind":"Unknown"}],"input":[{"text":"system property "},{"name":"spotlight.enabled"}]},
        {"trace":[{"kind":"BuildLogic","location":"build.gradle"}],"input":[{"text":"file "},{"name":"a\u003c/script>\u003cscript>.txt"}]},
        {"trace":[{"kind":"Unknown"}],"input":[{"text":"environment variable "},{"name":"GITHUB_DEPENDENCY_GRAPH_ENABLED"}]},
        {"trace":[{"kind":"Unknown"}],"input":[{"text":"system property "},{"name":"develocity.testing"}]}
      ]
    """.trimIndent()
    val html = if (splitReport) {
      """
        |<script type="application/json" id="diagnostics">
        |$diagnostics
        |</script>
        |<script type="application/json" id="configuration-cache-summary">
        |{"totalProblemCount":7,"uniqueProblemCount":5,"overflownProblemCount":2}
        |</script>
      """.trimMargin()
    } else {
      """
        |<script>
        |function configurationCacheProblems() { return (
        |// begin-report-data
        |{"diagnostics":$diagnostics,"totalProblemCount":7}
        |// end-report-data
        |); }
        |</script>
      """.trimMargin()
    }

    val report = readReport(html, version)

    assertThat(report.totalProblemCount).isEqualTo(7)
    assertThat(report.inputs).containsExactly(
      Input.SpotlightEnabled,
      Input(Input.Type.FILE, "a</script><script>.txt"),
    ).inOrder()
    assertThat(report.diagnostics[1].trace).containsExactly(
      CCDiagnostic.Trace("BuildLogic", "build.gradle"),
    )
  }

  @Test
  fun `missing report data fails with a useful error`() {
    val error = assertThrows<IllegalArgumentException> {
      readReport("<html></html>", "9.8.0")
    }
    assertThat(error).hasMessageThat().contains("missing // begin-report-data")
  }

  @Test
  fun `unterminated report data fails with a useful error`() {
    val error = assertThrows<IllegalArgumentException> {
      readReport("<script type=\"application/json\" id=\"diagnostics\">\n[]", "9.9.0")
    }
    assertThat(error).hasMessageThat().contains("missing </script>")
  }

  private fun readReport(html: String, version: String): CCReport {
    val reportPath = tempDir.resolve("configuration-cache-report.html")
    reportPath.writeText("<!DOCTYPE html>\n$html")
    return readConfigurationCacheReport(
      listOf("See the complete report at ${reportPath.toUri()}"),
      GradleVersion.version(version),
    )
  }
}
