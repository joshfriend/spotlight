package com.fueledbycaffeine.spotlight.idea.json

import com.fueledbycaffeine.spotlight.idea.completion.SpotlightTestFixtures
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Tests for project path support in gradle/spotlight-rules.json.
 */
class SpotlightRulesJsonTest : BasePlatformTestCase() {

  fun testIncludedProjectsCompletionOffersProjectPaths() {
    SpotlightTestFixtures.writeAllProjects(project, ":foo:bar", ":foo:baz", ":lib:core")

    val file = myFixture.addFileToProject(
      "gradle/spotlight-rules.json",
      """{ "implicitRules": [ { "pattern": "x", "includedProjects": [":fo"] } ] }"""
    )
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    val caretOffset = myFixture.editor.document.text.indexOf(""":fo""") + 3
    myFixture.editor.caretModel.moveToOffset(caretOffset)

    myFixture.completeBasic()
    val lookups = myFixture.lookupElementStrings ?: emptyList()

    assertThat(lookups).containsAtLeast(":foo:bar", ":foo:baz")
  }

  fun testIncludedProjectsReferenceResolvesToBuildFile() {
    SpotlightTestFixtures.createProjectsOnDisk(project, ":foo:bar")
    SpotlightTestFixtures.writeAllProjects(project, ":foo:bar")

    val file = myFixture.addFileToProject(
      "gradle/spotlight-rules.json",
      """{ "implicitRules": [ { "pattern": "x", "includedProjects": [":foo:bar"] } ] }"""
    )
    val offset = file.text.indexOf(":foo:bar") + 2
    val reference = file.findReferenceAt(offset)

    assertThat(reference).isNotNull()
    val resolved = reference!!.resolve()
    assertThat(resolved).isInstanceOf(PsiFile::class.java)
    assertThat((resolved as PsiFile).name).isEqualTo("build.gradle")
  }
}
