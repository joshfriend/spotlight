package com.fueledbycaffeine.spotlight.idea.lang

import com.fueledbycaffeine.spotlight.idea.SpotlightBundle
import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
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
    file ?: return
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return
    
    WriteCommandAction.runWriteCommandAction(project) {
      removeInvalidPath(editor.document)
    }
  }
  
  override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
    // Preview documents must be modified directly, without a write command
    removeInvalidPath(editor.document)
    return IntentionPreviewInfo.DIFF
  }
  
  private fun removeInvalidPath(document: Document) {
    // Find and remove the line containing the path
    val newLines = document.text.lines().filter { it.trim() != pathToRemove }
    document.setText(newLines.joinToString("\n"))
  }
  
  override fun startInWriteAction(): Boolean = false
  
  override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.LOW
}
