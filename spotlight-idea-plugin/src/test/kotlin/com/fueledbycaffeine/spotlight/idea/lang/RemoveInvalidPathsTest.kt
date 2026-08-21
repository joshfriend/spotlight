package com.fueledbycaffeine.spotlight.idea.lang

import com.fueledbycaffeine.spotlight.idea.completion.SpotlightTestFixtures
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Tests for the invalid path quick fixes in ide-projects.txt.
 */
class RemoveInvalidPathsTest : BasePlatformTestCase() {

  fun testRemoveInvalidPathIntention() {
    SpotlightTestFixtures.writeAllProjects(project, ":foo:bar")

    val file = myFixture.addFileToProject("gradle/ide-projects.txt", ":foo:bar\n:bogus:nope")
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    myFixture.editor.caretModel.moveToOffset(myFixture.file.text.indexOf(":bogus"))

    val intention = myFixture.findSingleIntention("Remove invalid path")
    myFixture.launchAction(intention)

    val text = myFixture.editor.document.text
    assertThat(text).contains(":foo:bar")
    assertThat(text).doesNotContain(":bogus:nope")
  }

  fun testRemoveAllInvalidPathsAction() {
    SpotlightTestFixtures.writeAllProjects(project, ":foo:bar")

    val file = myFixture.addFileToProject(
      "gradle/ide-projects.txt",
      "# keep this comment\n:foo:bar\n:bogus:nope\n:also:bad"
    )
    myFixture.configureFromExistingVirtualFile(file.virtualFile)

    myFixture.testAction(RemoveAllInvalidPathsAction())

    assertThat(myFixture.editor.document.text).isEqualTo("# keep this comment\n:foo:bar")
  }

  fun testRemoveAllInvalidPathsActionDisabledOutsideSpotlightFiles() {
    myFixture.configureByText("notes.txt", "hello")

    val presentation = myFixture.testAction(RemoveAllInvalidPathsAction())

    assertThat(presentation.isEnabled).isFalse()
  }
}
