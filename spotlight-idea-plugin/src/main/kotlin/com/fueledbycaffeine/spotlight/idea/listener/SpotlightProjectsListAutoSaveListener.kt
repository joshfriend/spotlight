package com.fueledbycaffeine.spotlight.idea.listener

import com.fueledbycaffeine.spotlight.idea.lang.IdeProjectsFileUtils
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener

/**
 * Auto-saves spotlight project list files when navigating away so edits take effect immediately.
 */
class SpotlightProjectsListAutoSaveListener : FileEditorManagerListener {
  override fun selectionChanged(event: FileEditorManagerEvent) {
    val oldFile = event.oldFile ?: return
    if (!IdeProjectsFileUtils.isSpotlightProjectsFile(oldFile.path)) return

    val documentManager = FileDocumentManager.getInstance()
    val document = documentManager.getDocument(oldFile) ?: return
    if (document.isWritable && documentManager.isDocumentUnsaved(document)) {
      runWriteAction {
        documentManager.saveDocument(document)
      }
    }
  }
}
