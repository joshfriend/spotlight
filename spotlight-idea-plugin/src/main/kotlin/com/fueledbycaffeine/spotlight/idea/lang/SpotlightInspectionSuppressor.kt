package com.fueledbycaffeine.spotlight.idea.lang

import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList.Companion.ALL_PROJECTS_LOCATION
import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList.Companion.IDE_PROJECTS_LOCATION
import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.psi.PsiElement

/**
 * Suppresses all inspections in ide-projects.txt and all-projects.txt files.
 * These files have a custom format where IntelliJ's built-in inspections
 * (like "Loose punctuation mark") are not relevant.
 */
class SpotlightInspectionSuppressor : InspectionSuppressor {
  override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
    val file = element.containingFile?.virtualFile ?: return false
    val path = file.path
    return path.endsWith(IDE_PROJECTS_LOCATION) || path.endsWith(ALL_PROJECTS_LOCATION)
  }

  override fun getSuppressActions(element: PsiElement?, toolId: String): Array<SuppressQuickFix> {
    return SuppressQuickFix.EMPTY_ARRAY
  }
}
