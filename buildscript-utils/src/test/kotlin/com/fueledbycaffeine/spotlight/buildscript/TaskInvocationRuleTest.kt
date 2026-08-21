package com.fueledbycaffeine.spotlight.buildscript

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.fueledbycaffeine.spotlight.buildscript.models.TaskInvocationRule
import java.nio.file.Path
import org.junit.jupiter.api.Test

class TaskInvocationRuleTest {
  private val rule = TaskInvocationRule(taskNames = setOf("buildHealth"), includeAllProjects = true)

  @Test
  fun `matches unqualified task name`() {
    assertThat(rule.matches(listOf("buildHealth"))).isTrue()
  }

  @Test
  fun `unqualified task name ignores project path`() {
    assertThat(rule.matches(listOf(":buildHealth"))).isTrue()
    assertThat(rule.matches(listOf(":foo:buildHealth"))).isTrue()
  }

  @Test
  fun `qualified task path only matches exact path`() {
    val qualifiedRule = TaskInvocationRule(
      taskNames = setOf(":foo:bar:aggregateReports"),
      includeAllProjects = true,
    )

    assertThat(qualifiedRule.matches(listOf(":foo:bar:aggregateReports"))).isTrue()
    assertThat(qualifiedRule.matches(listOf(":other:aggregateReports"))).isFalse()
    assertThat(qualifiedRule.matches(listOf("aggregateReports"))).isFalse()
  }

  @Test
  fun `qualified root task path does not match subproject tasks`() {
    val rootRule = TaskInvocationRule(
      taskNames = setOf(":buildHealth"),
      includeAllProjects = true,
    )

    assertThat(rootRule.matches(listOf(":buildHealth"))).isTrue()
    assertThat(rootRule.matches(listOf("buildHealth"), projectPath(":"))).isTrue()
    assertThat(rootRule.matches(listOf(":foo:buildHealth"), projectPath(":"))).isFalse()
    assertThat(rootRule.matches(listOf("buildHealth"), projectPath(":foo"))).isFalse()
  }

  @Test
  fun `qualified task path matches selectors relative to default project`() {
    val qualifiedRule = TaskInvocationRule(
      taskNames = setOf(":foo:bar:aggregateReports"),
      includeAllProjects = true,
    )

    assertThat(qualifiedRule.matches(listOf("aggregateReports"), projectPath(":foo:bar"))).isTrue()
    assertThat(qualifiedRule.matches(listOf("aggregateReports"), projectPath(":foo"))).isTrue()
    assertThat(qualifiedRule.matches(listOf("bar:aggregateReports"), projectPath(":foo"))).isTrue()
    assertThat(qualifiedRule.matches(listOf("aggregateReports"), projectPath(":"))).isTrue()
    assertThat(qualifiedRule.matches(listOf("aggregateReports"), projectPath(":other"))).isFalse()
  }

  @Test
  fun `matches any requested task`() {
    assertThat(rule.matches(listOf(":app:assemble", "buildHealth"))).isTrue()
  }

  @Test
  fun `ignores command line options`() {
    assertThat(rule.matches(listOf("--stacktrace"))).isFalse()
    assertThat(rule.matches(listOf("assemble", "--tests", "SomeTest"))).isFalse()
  }

  @Test
  fun `does not match other tasks`() {
    assertThat(rule.matches(listOf(":app:assemble"))).isFalse()
    assertThat(rule.matches(listOf("help"))).isFalse()
    assertThat(rule.matches(emptyList())).isFalse()
  }

  private fun projectPath(path: String) = GradlePath(Path.of(""), path)
}
