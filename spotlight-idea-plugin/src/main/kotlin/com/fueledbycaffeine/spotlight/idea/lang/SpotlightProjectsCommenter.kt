package com.fueledbycaffeine.spotlight.idea.lang

import com.intellij.lang.Commenter
import com.intellij.openapi.project.DumbAware

/**
 * Commenter implementation for Spotlight project files.
 * Enables Cmd+/ (or Ctrl+/) to toggle line comments using # prefix.
 * Implements DumbAware to work during indexing.
 */
class SpotlightProjectsCommenter : Commenter, DumbAware {
  override fun getLineCommentPrefix(): String = "# "
  
  override fun getBlockCommentPrefix(): String? = null
  
  override fun getBlockCommentSuffix(): String? = null
  
  override fun getCommentedBlockCommentPrefix(): String? = null
  
  override fun getCommentedBlockCommentSuffix(): String? = null
}
