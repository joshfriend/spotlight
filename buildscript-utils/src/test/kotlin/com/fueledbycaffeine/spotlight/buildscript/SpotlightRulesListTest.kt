package com.fueledbycaffeine.spotlight.buildscript

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule.*
import org.junit.jupiter.api.assertThrows
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories

class SpotlightRulesListTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun `can read rules from file`() {
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
    assertThat(rules).containsExactlyInAnyOrder(
      ProjectPathMatchRule(":foo", setOf(GradlePath(tempDir, ":bar"))),
      BuildscriptMatchRule("example", setOf(GradlePath(tempDir, ":foo"))),
    )
  }

  @Test
  fun `returns empty ruleset if file does not exist`() {
    val rules = SpotlightRulesList(tempDir).read()
    assertThat(rules).isEmpty()
  }

  @Test
  fun `returns empty ruleset if file is empty`() {
    val rulesFile = tempDir.resolve(SpotlightRulesList.SPOTLIGHT_RULES_LOCATION)
    rulesFile.createParentDirectories()
    rulesFile.writeText("[]")

    val rules = SpotlightRulesList(tempDir).read()
    assertThat(rules).isEmpty()
  }

  @Test
  fun `throws error if file is unreadable`() {
    val rulesFile = tempDir.resolve(SpotlightRulesList.SPOTLIGHT_RULES_LOCATION)
    rulesFile.createParentDirectories()
    rulesFile.createFile()
    rulesFile.writeText("")

    assertThrows<InvalidSpotlightRules> { SpotlightRulesList(tempDir).read() }
  }
}