package com.fueledbycaffeine.spotlight.idea.gradle

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * Tests for [SpotlightSyncState], the state machine that decides whether the IDE should show
 * Spotlight banners / re-sync. The key behavior under test is that once a sync proves the Spotlight
 * Gradle plugin is NOT applied, no banners should be shown even if a stale `ide-projects.txt` is
 * left over.
 */
class SpotlightSyncStateTest {

  @get:Rule
  val tmpFolder = TemporaryFolder()

  private fun rootDir(): Path = tmpFolder.root.toPath()

  private fun writeIdeProjects(vararg lines: String) {
    val file = rootDir().resolve("gradle/ide-projects.txt")
    file.parent.createDirectories()
    file.writeText(lines.joinToString("\n"))
  }

  private fun writeRules(content: String) {
    val file = rootDir().resolve("gradle/spotlight-rules.json")
    file.parent.createDirectories()
    file.writeText(content)
  }

  /** Write all-projects.txt, the committed file that indicates Spotlight is set up in the repo. */
  private fun writeAllProjects(vararg lines: String) {
    val file = rootDir().resolve("gradle/all-projects.txt")
    file.parent.createDirectories()
    file.writeText(lines.joinToString("\n"))
  }

  /** Create a project dir with a build file so [updateProjects] keeps the path. */
  private fun createProject(gradlePath: String) {
    val relative = gradlePath.removePrefix(":").replace(":", "/")
    val dir = rootDir().resolve(relative)
    dir.createDirectories()
    dir.resolve("build.gradle").writeText("")
  }

  // ===== Tri-state plugin detection =====

  @Test
  fun `pluginStatus is UNKNOWN before any sync when config files exist`() {
    // all-projects.txt is committed when Spotlight is set up, so pre-sync we can't yet confirm
    // the plugin is applied to this build, but it's likely configured.
    writeAllProjects(":app")
    val state = SpotlightSyncState(rootDir())
    assertThat(state.pluginStatus).isEqualTo(SpotlightPluginStatus.UNKNOWN)
  }

  @Test
  fun `pluginStatus is NOT_APPLIED before any sync when no config files exist`() {
    // No all-projects.txt or spotlight-rules.json means Spotlight isn't set up in this project.
    val state = SpotlightSyncState(rootDir())
    assertThat(state.pluginStatus).isEqualTo(SpotlightPluginStatus.NOT_APPLIED)
  }

  @Test
  fun `pluginStatus is UNKNOWN before any sync when only spotlight-rules_json exists`() {
    writeRules("""{ "implicit": [] }""")
    val state = SpotlightSyncState(rootDir())
    assertThat(state.pluginStatus).isEqualTo(SpotlightPluginStatus.UNKNOWN)
  }

  @Test
  fun `pluginStatus is APPLIED after a sync that produced a model`() {
    createProject(":app")
    val state = SpotlightSyncState(rootDir())

    state.updateProjects(setOf(":app"))

    assertThat(state.pluginStatus).isEqualTo(SpotlightPluginStatus.APPLIED)
    assertThat(state.includedProjects.value.map { it.path }).containsExactly(":app")
  }

  @Test
  fun `pluginStatus is NOT_APPLIED after a sync with no model`() {
    val state = SpotlightSyncState(rootDir())

    state.markSpotlightNotApplied()

    assertThat(state.pluginStatus).isEqualTo(SpotlightPluginStatus.NOT_APPLIED)
    assertThat(state.includedProjects.value).isEmpty()
  }

  // ===== The bug: stale ide-projects.txt with the plugin not applied =====

  @Test
  fun `configured but not yet synced shows initial-sync banner`() {
    // Spotlight is set up (all-projects.txt committed) and the user has selected projects, but no
    // sync has run yet. The initial-sync banner is expected; the fix must NOT regress this.
    writeAllProjects(":app")
    writeIdeProjects(":app")
    val state = SpotlightSyncState(rootDir())

    assertThat(state.pluginStatus).isEqualTo(SpotlightPluginStatus.UNKNOWN)
    assertThat(state.isSyncStale()).isTrue()
  }

  @Test
  fun `not configured and not yet synced shows no banner`() {
    // A leftover ide-projects.txt with no committed Spotlight config: Spotlight isn't set up here,
    // so even before a sync we infer NOT_APPLIED and suppress the banner.
    writeIdeProjects(":app")
    val state = SpotlightSyncState(rootDir())

    assertThat(state.pluginStatus).isEqualTo(SpotlightPluginStatus.NOT_APPLIED)
    assertThat(state.isSyncStale()).isFalse()
  }

  @Test
  fun `fix - sync without plugin suppresses stale banner even with leftover ide-projects_txt`() {
    // Leftover ide-projects.txt that would otherwise trigger the banner.
    writeIdeProjects(":app")
    val state = SpotlightSyncState(rootDir())

    // Sync completes but the Spotlight Gradle plugin is not applied (no model).
    state.markSpotlightNotApplied()

    // No banner should be shown because we now know the plugin isn't applied.
    assertThat(state.isSyncStale()).isFalse()
  }

  @Test
  fun `fix - sync without plugin suppresses stale banner even when rules file exists`() {
    writeIdeProjects(":app")
    writeRules("""{ "implicit": [] }""")
    val state = SpotlightSyncState(rootDir())

    state.markSpotlightNotApplied()

    assertThat(state.isSyncStale()).isFalse()
  }

  @Test
  fun `fix - sync without plugin and no config files shows no banner`() {
    // Tony's case: no ide-projects.txt at all, plugin not applied.
    val state = SpotlightSyncState(rootDir())

    state.markSpotlightNotApplied()

    assertThat(state.isSyncStale()).isFalse()
    assertThat(state.pluginStatus).isEqualTo(SpotlightPluginStatus.NOT_APPLIED)
  }

  // ===== Regression guard: normal stale detection still works when plugin IS applied =====

  @Test
  fun `synced with plugin - no changes is not stale`() {
    createProject(":app")
    writeIdeProjects(":app")
    val state = SpotlightSyncState(rootDir())

    state.updateProjects(setOf(":app"))

    assertThat(state.isSyncStale()).isFalse()
  }

  @Test
  fun `synced with plugin - new path added becomes stale`() {
    createProject(":app")
    writeIdeProjects(":app")
    val state = SpotlightSyncState(rootDir())
    state.updateProjects(setOf(":app"))

    // User adds a new project to ide-projects.txt after sync.
    writeIdeProjects(":app", ":lib")

    assertThat(state.isSyncStale()).isTrue()
    assertThat(state.getUnsyncedPaths()).containsExactly(":lib")
  }

  @Test
  fun `synced with plugin - changed rules becomes stale`() {
    createProject(":app")
    writeIdeProjects(":app")
    writeRules("""{ "implicit": [] }""")
    val state = SpotlightSyncState(rootDir())
    state.updateProjects(setOf(":app"))

    assertThat(state.isSyncStale()).isFalse()

    writeRules("""{ "implicit": [":app"] }""")

    assertThat(state.haveRulesChanged()).isTrue()
    assertThat(state.isSyncStale()).isTrue()
  }

  @Test
  fun `re-sync after plugin removed flips state back to not applied`() {
    createProject(":app")
    writeIdeProjects(":app")
    val state = SpotlightSyncState(rootDir())

    // First sync: plugin applied.
    state.updateProjects(setOf(":app"))
    assertThat(state.pluginStatus).isEqualTo(SpotlightPluginStatus.APPLIED)

    // Branch switch / plugin removed: next sync has no model.
    state.markSpotlightNotApplied()

    assertThat(state.pluginStatus).isEqualTo(SpotlightPluginStatus.NOT_APPLIED)
    assertThat(state.includedProjects.value).isEmpty()
    assertThat(state.isSyncStale()).isFalse()
  }
}
