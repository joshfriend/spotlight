package com.fueledbycaffeine.spotlight.buildscript

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
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
    assertThat(rules.implicitRules).containsExactlyInAnyOrder(
      ProjectPathMatchRule(":foo", setOf(GradlePath(tempDir, ":bar"))),
      BuildscriptMatchRule("example", setOf(GradlePath(tempDir, ":foo"))),
    )
    assertThat(rules.projectName).isNull()
    assertThat(rules.typeSafeAccessorInference).isNull()
  }

  @Test
  fun `can read rules from file`() {
    val rulesFile = tempDir.resolve(SpotlightRulesList.SPOTLIGHT_RULES_LOCATION)
    rulesFile.createParentDirectories()
    rulesFile.writeText(
      """
      {
        "projectName": "example-project",
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
        ],
        "typeSafeAccessorInference": "STRICT"
      }
      """.trimIndent()
    )
    val rules = SpotlightRulesList(tempDir).read()
    assertThat(rules.projectName).isEqualTo("example-project")
    assertThat(rules.implicitRules).containsExactlyInAnyOrder(
      ProjectPathMatchRule(":foo", setOf(GradlePath(tempDir, ":bar"))),
      BuildscriptMatchRule("example", setOf(GradlePath(tempDir, ":foo"))),
    )
    assertThat(rules.typeSafeAccessorInference).isEqualTo(TypeSafeAccessorInference.STRICT)
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
}