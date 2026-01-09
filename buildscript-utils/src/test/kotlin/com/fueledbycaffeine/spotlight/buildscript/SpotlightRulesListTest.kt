package com.fueledbycaffeine.spotlight.buildscript

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule.BuildscriptCaptureRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule.BuildscriptMatchRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule.ProjectPathMatchRule
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir

class SpotlightRulesListTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun `can read legacy rules from file`() {
    val rulesFile = tempDir.resolve(SpotlightRulesList.SPOTLIGHT_RULES_LOCATION)
    rulesFile.createParentDirectories()
    rulesFile.writeText(
      """
      [
        {
          "type": "project-path-match-rule",
          "pattern": ":foo",
          "includedProjects": [":bar"]
        },
        {
          "type": "buildscript-match-rule",
          "pattern": "example",
          "includedProjects": [":foo"]
        }
      ]
      """.trimIndent()
    )
    val rules = SpotlightRulesList(tempDir).read()
    assertThat(rules.implicitRules).hasSize(2)
    val projectPathRule = rules.implicitRules.filterIsInstance<ProjectPathMatchRule>().single()
    assertThat(projectPathRule.pattern.pattern).isEqualTo(":foo")
    assertThat(projectPathRule.includedProjects).isEqualTo(setOf(GradlePath(tempDir, ":bar")))
    val buildscriptRule = rules.implicitRules.filterIsInstance<BuildscriptMatchRule>().single()
    assertThat(buildscriptRule.pattern.pattern).isEqualTo("example")
    assertThat(buildscriptRule.includedProjects).isEqualTo(setOf(GradlePath(tempDir, ":foo")))
  }

  @Test
  fun `can read rules from file`() {
    val rulesFile = tempDir.resolve(SpotlightRulesList.SPOTLIGHT_RULES_LOCATION)
    rulesFile.createParentDirectories()
    rulesFile.writeText(
      """
      {
        "implicitRules": [
          {
            "type": "project-path-match-rule",
            "pattern": ":foo",
            "includedProjects": [":bar"]
          },
          {
            "type": "buildscript-match-rule",
            "pattern": "example",
            "includedProjects": [":foo"]
          }
        ]
      }
      """.trimIndent()
    )
    val rules = SpotlightRulesList(tempDir).read()
    assertThat(rules.implicitRules).hasSize(2)
    val projectPathRule = rules.implicitRules.filterIsInstance<ProjectPathMatchRule>().single()
    assertThat(projectPathRule.pattern.pattern).isEqualTo(":foo")
    assertThat(projectPathRule.includedProjects).isEqualTo(setOf(GradlePath(tempDir, ":bar")))
    val buildscriptRule = rules.implicitRules.filterIsInstance<BuildscriptMatchRule>().single()
    assertThat(buildscriptRule.pattern.pattern).isEqualTo("example")
    assertThat(buildscriptRule.includedProjects).isEqualTo(setOf(GradlePath(tempDir, ":foo")))
  }

  @Test
  fun `returns empty ruleset if file does not exist`() {
    val rules = SpotlightRulesList(tempDir).read()
    assertThat(rules.implicitRules).isEmpty()
  }

  @Test
  fun `returns empty ruleset if file is empty`() {
    val rulesFile = tempDir.resolve(SpotlightRulesList.SPOTLIGHT_RULES_LOCATION)
    rulesFile.createParentDirectories()
    rulesFile.writeText("[]")

    val rules = SpotlightRulesList(tempDir).read()
    assertThat(rules.implicitRules).isEmpty()
  }

  @Test
  fun `throws error if file is unreadable`() {
    val rulesFile = tempDir.resolve(SpotlightRulesList.SPOTLIGHT_RULES_LOCATION)
    rulesFile.createParentDirectories()
    rulesFile.createFile()
    rulesFile.writeText("")

    assertThrows<InvalidSpotlightRules> { SpotlightRulesList(tempDir).read() }
  }

  @Test
  fun `can read buildscript-capture-rule from file`() {
    val rulesFile = tempDir.resolve(SpotlightRulesList.SPOTLIGHT_RULES_LOCATION)
    rulesFile.createParentDirectories()
    rulesFile.writeText(
      """
      {
        "implicitRules": [
          {
            "type": "buildscript-capture-rule",
            "pattern": "targetProjectPath\\s*=\\s*[\"']([^\"']+)[\"']",
            "projectTemplate": "${'$'}1"
          }
        ]
      }
      """.trimIndent()
    )
    val rules = SpotlightRulesList(tempDir).read()
    val captureRule = rules.implicitRules.single()
    assertThat(captureRule).isInstanceOf<BuildscriptCaptureRule>()
    captureRule as BuildscriptCaptureRule
    assertThat(captureRule.pattern.pattern).isEqualTo("""targetProjectPath\s*=\s*["']([^"']+)["']""")
    assertThat(captureRule.projectTemplate).isEqualTo("\$1")
  }
}