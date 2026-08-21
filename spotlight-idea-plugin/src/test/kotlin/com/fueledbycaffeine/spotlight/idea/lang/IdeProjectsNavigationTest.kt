package com.fueledbycaffeine.spotlight.idea.lang

import com.fueledbycaffeine.spotlight.idea.completion.SpotlightTestFixtures
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Tests for Cmd+Click navigation from entries in ide-projects.txt to project build files.
 */
class IdeProjectsNavigationTest : BasePlatformTestCase() {

  private fun elementAt(file: PsiFile, offset: Int): PsiElement =
    file.findElementAt(offset) ?: error("no element at offset $offset")

  private fun referencesAt(file: PsiFile, offset: Int) =
    ReferenceProvidersRegistry.getReferencesFromProviders(elementAt(file, offset))

  fun testPathReferenceResolvesToBuildFile() {
    SpotlightTestFixtures.createProjectsOnDisk(project, ":foo:bar")

    val file = myFixture.addFileToProject("gradle/ide-projects.txt", ":foo:bar")
    val references = referencesAt(file, 2)

    assertThat(references).hasLength(1)
    val resolved = references.single().resolve()
    assertThat(resolved).isInstanceOf(PsiFile::class.java)
    assertThat((resolved as PsiFile).name).isEqualTo("build.gradle")
  }

  fun testNoReferenceForComments() {
    SpotlightTestFixtures.createProjectsOnDisk(project, ":foo:bar")

    val file = myFixture.addFileToProject("gradle/ide-projects.txt", "# :foo:bar")
    assertThat(referencesAt(file, 4)).isEmpty()
  }

  fun testNoReferenceForGlobPatterns() {
    SpotlightTestFixtures.createProjectsOnDisk(project, ":foo:bar")

    val file = myFixture.addFileToProject("gradle/ide-projects.txt", ":foo:*")
    assertThat(referencesAt(file, 2)).isEmpty()
  }

  fun testNoReferenceWhenBuildFileMissing() {
    val file = myFixture.addFileToProject("gradle/ide-projects.txt", ":does:not:exist")
    assertThat(referencesAt(file, 2)).isEmpty()
  }

  fun testGotoDeclarationTargetsBuildFile() {
    SpotlightTestFixtures.createProjectsOnDisk(project, ":foo:bar")

    val file = myFixture.addFileToProject("gradle/ide-projects.txt", ":foo:bar")
    myFixture.configureFromExistingVirtualFile(file.virtualFile)

    val handler = IdeProjectsGotoDeclarationHandler()
    val element = myFixture.file.findElementAt(2)
    val targets = handler.getGotoDeclarationTargets(element, 2, myFixture.editor)

    assertThat(targets).isNotNull()
    assertThat(targets!!.toList()).hasSize(1)
    assertThat((targets.single() as PsiFile).name).isEqualTo("build.gradle")
  }

  fun testGotoDeclarationReturnsNothingForComments() {
    SpotlightTestFixtures.createProjectsOnDisk(project, ":foo:bar")

    val file = myFixture.addFileToProject("gradle/ide-projects.txt", "# :foo:bar")
    myFixture.configureFromExistingVirtualFile(file.virtualFile)

    val handler = IdeProjectsGotoDeclarationHandler()
    val element = myFixture.file.findElementAt(4)
    assertThat(handler.getGotoDeclarationTargets(element, 4, myFixture.editor)).isNull()
  }
}
