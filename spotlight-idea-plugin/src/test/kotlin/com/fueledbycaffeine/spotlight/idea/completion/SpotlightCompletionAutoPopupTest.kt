package com.fueledbycaffeine.spotlight.idea.completion

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester
import com.intellij.util.ThrowableRunnable

/**
 * Tests that the completion popup automatically appears while typing in ide-projects.txt.
 * Regression coverage for https://github.com/joshfriend/spotlight/issues/126
 */
class SpotlightCompletionAutoPopupTest : BasePlatformTestCase() {

  private lateinit var tester: CompletionAutoPopupTester

  override fun setUp() {
    super.setUp()
    tester = CompletionAutoPopupTester(myFixture)
  }

  override fun runInDispatchThread(): Boolean = false

  override fun runTestRunnable(testRunnable: ThrowableRunnable<Throwable>) {
    tester.runWithAutoPopupEnabled(testRunnable)
  }

  fun testAutoPopupAppearsWhileTypingProjectPath() {
    SpotlightTestFixtures.writeAllProjects(project, ":foo:bar", ":foo:baz")

    ApplicationManager.getApplication().invokeAndWait {
      val file = myFixture.addFileToProject("gradle/ide-projects.txt", "")
      myFixture.configureFromExistingVirtualFile(file.virtualFile)
    }

    tester.typeWithPauses(":fo")

    assertNotNull("completion auto-popup should appear while typing a project path", tester.lookup)
  }
}
