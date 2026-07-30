package com.fueledbycaffeine.spotlight.idea.completion

import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Fixture tests that invoke real completion in ide-projects.txt files.
 * Regression coverage for https://github.com/joshfriend/spotlight/issues/126
 */
class SpotlightCompletionContributorTest : BasePlatformTestCase() {

  fun testIdeProjectsCompletionOffersProjectPaths() {
    SpotlightTestFixtures.writeAllProjects(project, ":foo:bar", ":foo:baz", ":lib:core")

    val file = myFixture.addFileToProject("gradle/ide-projects.txt", ":fo")
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    myFixture.editor.caretModel.moveToOffset(myFixture.file.textLength)

    myFixture.completeBasic()
    val lookups = myFixture.lookupElementStrings ?: emptyList()

    assertThat(lookups).containsAtLeast(":foo:bar", ":foo:baz")
  }

  fun testIdeProjectsCompletionOnEmptyFileOffersAllProjects() {
    SpotlightTestFixtures.writeAllProjects(project, ":foo:bar", ":lib:core")

    val file = myFixture.addFileToProject("gradle/ide-projects.txt", "")
    myFixture.configureFromExistingVirtualFile(file.virtualFile)

    myFixture.completeBasic()
    val lookups = myFixture.lookupElementStrings ?: emptyList()

    assertThat(lookups).containsAtLeast(":foo:bar", ":lib:core")
  }

  fun testIdeProjectsCompletionDoesNotCompleteComments() {
    SpotlightTestFixtures.writeAllProjects(project, ":foo:bar")

    val file = myFixture.addFileToProject("gradle/ide-projects.txt", "# :fo")
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    myFixture.editor.caretModel.moveToOffset(myFixture.file.textLength)

    myFixture.completeBasic()
    val lookups = myFixture.lookupElementStrings ?: emptyList()

    assertThat(lookups).isEmpty()
  }
}
