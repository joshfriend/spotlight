package com.fueledbycaffeine.spotlight.idea.completion

import com.fueledbycaffeine.spotlight.idea.spotlightService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.util.ui.UIUtil
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

internal object SpotlightTestFixtures {
  /**
   * Writes gradle/all-projects.txt to the real project root on disk and waits for
   * [com.fueledbycaffeine.spotlight.idea.SpotlightProjectService] to pick it up via VFS events.
   *
   * Works both on the EDT (default [com.intellij.testFramework.fixtures.BasePlatformTestCase])
   * and off it (auto-popup tests running with `runInDispatchThread() = false`).
   */
  fun writeAllProjects(project: Project, vararg paths: String) {
    val rootDir = Path.of(project.basePath!!)
    val gradleDir = rootDir.resolve("gradle").createDirectories()
    val allProjectsFile = gradleDir.resolve("all-projects.txt")
    // The service was constructed before this file existed and subscribes to VFS events
    // asynchronously, so keep rewriting + refreshing until it observes the change
    val app = ApplicationManager.getApplication()
    val expected = paths.toSet()
    val deadline = System.currentTimeMillis() + 30_000
    while (project.spotlightService.allProjects.value.map { it.path }.toSet() != expected) {
      if (System.currentTimeMillis() > deadline) {
        throw AssertionError("Timed out waiting for all-projects.txt to be loaded")
      }
      allProjectsFile.writeText(paths.joinToString("\n"))
      if (app.isDispatchThread) {
        LocalFileSystem.getInstance().refreshAndFindFileByPath(allProjectsFile.toString())
          ?.refresh(false, false)
        UIUtil.dispatchAllInvocationEvents()
        Thread.sleep(50)
        UIUtil.dispatchAllInvocationEvents()
      } else {
        app.invokeAndWait {
          LocalFileSystem.getInstance().refreshAndFindFileByPath(allProjectsFile.toString())
            ?.refresh(false, false)
        }
        Thread.sleep(50)
      }
    }
  }
}
