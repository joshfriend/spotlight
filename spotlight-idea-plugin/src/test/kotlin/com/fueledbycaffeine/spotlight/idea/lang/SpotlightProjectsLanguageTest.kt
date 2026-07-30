package com.fueledbycaffeine.spotlight.idea.lang

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.psi.TokenType
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Tests for SpotlightProjects language support: file type recognition,
 * parsing, commenting, and inspection suppression.
 */
class SpotlightProjectsLanguageTest : BasePlatformTestCase() {

  fun testIdeProjectsFileTypeRecognized() {
    val file = myFixture.addFileToProject("gradle/ide-projects.txt", ":foo:bar\n")
    assertThat(file.fileType.name).isEqualTo(SpotlightProjectsFileType.NAME)
    assertThat(file.language).isEqualTo(SpotlightProjectsLanguage)
  }

  fun testAllProjectsFileTypeRecognized() {
    val file = myFixture.addFileToProject("gradle/all-projects.txt", ":foo:bar\n")
    assertThat(file.fileType.name).isEqualTo(SpotlightProjectsFileType.NAME)
    assertThat(file.language).isEqualTo(SpotlightProjectsLanguage)
  }

  fun testParserSplitsLinesIntoTokens() {
    val file = myFixture.addFileToProject("gradle/ide-projects.txt", "# comment\n:foo:bar\n")
    // Newline tokens are declared whitespace, so the PSI tree remaps them to WHITE_SPACE
    val tokens = file.node.getChildren(null).filter { it.elementType != TokenType.WHITE_SPACE }
    assertThat(tokens.map { it.elementType }).containsExactly(
      SpotlightProjectsTokenTypes.COMMENT,
      SpotlightProjectsTokenTypes.LINE,
    ).inOrder()
    assertThat(tokens.map { it.text }).containsExactly("# comment", ":foo:bar").inOrder()
  }

  fun testCommenterTogglesLineComment() {
    val file = myFixture.addFileToProject("gradle/ide-projects.txt", ":foo:bar")
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    myFixture.editor.caretModel.moveToOffset(0)

    myFixture.performEditorAction(IdeActions.ACTION_COMMENT_LINE)
    assertThat(myFixture.editor.document.text.trim()).isEqualTo("# :foo:bar")

    myFixture.editor.caretModel.moveToOffset(0)
    myFixture.performEditorAction(IdeActions.ACTION_COMMENT_LINE)
    assertThat(myFixture.editor.document.text.trim()).isEqualTo(":foo:bar")
  }

  fun testInspectionsSuppressedInSpotlightProjectFiles() {
    val suppressor = SpotlightInspectionSuppressor()

    val spotlightFile = myFixture.addFileToProject("gradle/ide-projects.txt", ":foo:bar")
    assertThat(suppressor.isSuppressedFor(spotlightFile.firstChild, "SpellCheckingInspection")).isTrue()

    val otherFile = myFixture.addFileToProject("notes.txt", "hello")
    assertThat(suppressor.isSuppressedFor(otherFile.firstChild, "SpellCheckingInspection")).isFalse()
  }
}
