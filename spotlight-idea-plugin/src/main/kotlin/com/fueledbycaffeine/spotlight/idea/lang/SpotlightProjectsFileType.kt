package com.fueledbycaffeine.spotlight.idea.lang

import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList.Companion.ALL_PROJECTS_LOCATION
import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList.Companion.IDE_PROJECTS_LOCATION
import com.fueledbycaffeine.spotlight.idea.SpotlightBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

/**
 * File type for Spotlight project files (ide-projects.txt, all-projects.txt).
 * Uses filename pattern matching since these files use fixed names.
 */
class SpotlightProjectsFileType : LanguageFileType(SpotlightProjectsLanguage) {
  override fun getName(): String = NAME
  
  override fun getDescription(): String = SpotlightBundle.message("filetype.description")
  
  override fun getDefaultExtension(): String = "txt"
  
  override fun getIcon(): Icon = AllIcons.FileTypes.Text
  
  companion object {
    const val NAME = "SpotlightProjects"
    
    /**
     * Gets the registered instance of this file type from the FileTypeManager.
     */
    val INSTANCE: SpotlightProjectsFileType
      get() = FileTypeManager.getInstance().findFileTypeByName(NAME) as? SpotlightProjectsFileType
        ?: SpotlightProjectsFileType()
    
    /**
     * Checks if the given virtual file is a Spotlight project file.
     * Matches files by path pattern (gradle/ide-projects.txt or gradle/all-projects.txt).
     */
    fun isSpotlightProjectFile(file: VirtualFile): Boolean {
      val path = file.path
      return path.endsWith(IDE_PROJECTS_LOCATION) || path.endsWith(ALL_PROJECTS_LOCATION)
    }
  }
}
