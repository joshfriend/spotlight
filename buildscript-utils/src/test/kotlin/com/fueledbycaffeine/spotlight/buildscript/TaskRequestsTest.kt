package com.fueledbycaffeine.spotlight.buildscript

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import com.fueledbycaffeine.spotlight.buildscript.parser.TaskRequestParser
import com.fueledbycaffeine.spotlight.buildscript.parser.impl.TaskRequestParserProvider
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import org.junit.jupiter.api.Test
import kotlin.io.path.createDirectory

class TaskRequestsTest {

  @Test
  fun `can return all projects with the appropriate task request`() {
    val projects = TestTaskRequestParserProvider.PROJECTS

    val taskNames = setOf(":rootAll")
    val taskRequests = TaskRequests(taskNames, projects)

    assertThat(taskRequests.parseDependencies()).containsExactlyInAnyOrder(*projects.toTypedArray())
  }

  @Test
  fun `can return the 'foo' project with the appropriate task request`() {
    val projects = TestTaskRequestParserProvider.PROJECTS

    val taskNames = setOf(":rootTwo")
    val taskRequests = TaskRequests(taskNames, projects)

    assertThat(taskRequests.parseDependencies()).containsExactlyInAnyOrder(projects.single { it.path == ":foo" })
  }

  @Test
  fun `can return the 'bar' project with the appropriate task request`() {
    val projects = TestTaskRequestParserProvider.PROJECTS

    val taskNames = setOf(":rootThree")
    val taskRequests = TaskRequests(taskNames, projects)

    assertThat(taskRequests.parseDependencies()).containsExactlyInAnyOrder(projects.single { it.path == ":bar" })
  }

  @Test
  fun `can return the 'baz' project with the appropriate task request`() {
    val projects = TestTaskRequestParserProvider.PROJECTS

    val taskNames = setOf(":rootFour")
    val taskRequests = TaskRequests(taskNames, projects)

    assertThat(taskRequests.parseDependencies()).containsExactlyInAnyOrder(projects.single { it.path == ":baz" })
  }

  @Test
  fun `can return the no projects with a task request we don't care about`() {
    val projects = TestTaskRequestParserProvider.PROJECTS

    val taskNames = setOf(":rootSomethingElse")
    val taskRequests = TaskRequests(taskNames, projects)

    assertThat(taskRequests.parseDependencies()).isEmpty()
  }
}

// This provider is registered in src/test/resources/META-INF/services/
class TestTaskRequestParserProvider : TaskRequestParserProvider {

  companion object {
    private val FS = Jimfs.newFileSystem(Configuration.unix())
    private val BUILD_ROOT = FS.getPath("/test-build").createDirectory()
    val PROJECTS = BUILD_ROOT.createProjects(":foo", ":bar", ":baz")
  }

  override fun getParser(): TaskRequestParser {
    return TestTaskRequestParser(PROJECTS)
  }
}

class TestTaskRequestParser(val dependencies: Set<GradlePath>) : TaskRequestParser {
  override fun parse(taskNames: Set<String>): Set<GradlePath> {
    return if (":rootAll" in taskNames) {
      dependencies
    } else if (":rootTwo" in taskNames) {
      dependencies.filterTo(sortedSetOf()) { it.path == ":foo" }
    } else if (":rootThree" in taskNames) {
      dependencies.filterTo(sortedSetOf()) { it.path == ":bar" }
    } else if (":rootFour" in taskNames) {
      dependencies.filterTo(sortedSetOf()) { it.path == ":baz" }
    } else {
      emptySet()
    }
  }
}
