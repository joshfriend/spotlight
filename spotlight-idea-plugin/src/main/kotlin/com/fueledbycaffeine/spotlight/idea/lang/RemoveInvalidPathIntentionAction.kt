package com.fueledbycaffeine.spotlight.idea.lang

import com.fueledbycaffeine.spotlight.idea.SpotlightBundle
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * An intention action that removes an invalid path from the ide-projects.txt file.
 * Modifies the document directly to avoid VFS sync issues.
 * Uses LowPriorityAction for yellow bulb, Priority.LOW to appear after "Complete with...".
 */
class RemoveInvalidPathIntentionAction(private val pathToRemove: String) : IntentionAction, LowPriorityAction, PriorityAction, DumbAware {
  override fun getText(): String = SpotlightBundle.message("intention.remove.invalid.path")
  
  override fun getFamilyName(): String = SpotlightBundle.message("statusbar.widget.name")
  
  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = true
  
  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    editor ?: return
    
    val document = editor.document
    val text = document.text
    
    // Find and remove the line containing the path
    val lines = text.lines()
    val newLines = lines.filter { it.trim() != pathToRemove }
    val newText = newLines.joinToString("\n")
    
    WriteCommandAction.runWriteCommandAction(project) {
      document.setText(newText)
    }
  }
  
  override fun startInWriteAction(): Boolean = false
  
  override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.LOW
}
