package com.fueledbycaffeine.spotlight.idea.lang

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.idea.SpotlightBundle
import com.fueledbycaffeine.spotlight.idea.spotlightService
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import java.nio.file.Path

/**
 * An intention action that removes an invalid path from the ide-projects.txt file.
 */
class RemoveInvalidPathIntentionAction(private val pathToRemove: String) : IntentionAction, PriorityAction, DumbAware {
  override fun getText(): String = SpotlightBundle.message("intention.remove.invalid.path")
  
  override fun getFamilyName(): String = SpotlightBundle.message("statusbar.widget.name")
  
  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = true
  
  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    val spotlightService = project.spotlightService
    val rootDir = Path.of(project.basePath!!)
    val gradlePath = GradlePath(rootDir, pathToRemove)
    spotlightService.removeIdeProjects(listOf(gradlePath))
  }
  
  override fun startInWriteAction(): Boolean = false
  
  override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.HIGH
}
