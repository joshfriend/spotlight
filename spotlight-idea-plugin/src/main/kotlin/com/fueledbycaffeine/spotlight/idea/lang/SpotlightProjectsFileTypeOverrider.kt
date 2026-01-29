package com.fueledbycaffeine.spotlight.idea.lang

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.impl.FileTypeOverrider
import com.intellij.openapi.vfs.VirtualFile

/**
 * Overrides the default TEXT file type for Spotlight project files.
 * This ensures ide-projects.txt and all-projects.txt in the gradle/ directory
 * are treated as Spotlight projects files, enabling Cmd+/ commenting.
 */
class SpotlightProjectsFileTypeOverrider : FileTypeOverrider {
  override fun getOverriddenFileType(file: VirtualFile): FileType? {
    return if (SpotlightProjectsFileType.isSpotlightProjectFile(file)) {
      SpotlightProjectsFileType.INSTANCE
    } else {
      null
    }
  }
}
