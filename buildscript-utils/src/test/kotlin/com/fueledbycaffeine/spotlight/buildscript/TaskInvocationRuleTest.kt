package com.fueledbycaffeine.spotlight.buildscript

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.fueledbycaffeine.spotlight.buildscript.models.TaskInvocationRule
import org.junit.jupiter.api.Test

class TaskInvocationRuleTest {
  private val rule = TaskInvocationRule(taskNames = setOf("buildHealth"), includeAllProjects = true)

  @Test
  fun `matches unqualified task name`() {
    assertThat(rule.matches(listOf("buildHealth"))).isTrue()
  }

  @Test
  fun `matches qualified task paths`() {
    assertThat(rule.matches(listOf(":buildHealth"))).isTrue()
    assertThat(rule.matches(listOf(":foo:buildHealth"))).isTrue()
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
}
