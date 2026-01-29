package com.fueledbycaffeine.spotlight.idea.listener

import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList.Companion.ALL_PROJECTS_LOCATION
import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList.Companion.IDE_PROJECTS_LOCATION
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener

private val SPOTLIGHT_PROJECT_LIST_FILES = listOf(IDE_PROJECTS_LOCATION, ALL_PROJECTS_LOCATION)

/**
 * Auto-saves spotlight project list files when navigating away so edits take effect immediately.
 */
class SpotlightProjectsListAutoSaveListener : FileEditorManagerListener {
  override fun selectionChanged(event: FileEditorManagerEvent) {
    val oldFile = event.oldFile ?: return
    if (SPOTLIGHT_PROJECT_LIST_FILES.none { oldFile.path.endsWith(it) }) return

    val documentManager = FileDocumentManager.getInstance()
    val document = documentManager.getDocument(oldFile) ?: return
    if (document.isWritable && documentManager.isDocumentUnsaved(document)) {
      runWriteAction {
        documentManager.saveDocument(document)
      }
    }
  }
}
