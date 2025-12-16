package com.fueledbycaffeine.spotlight.idea.utils

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore
import java.nio.file.Path

private val PsiElement.virtualFile: VirtualFile? get() = PsiUtilCore.getVirtualFile(this)
private val VirtualFile.javaPath: Path get() = Path.of(path)

/**
 * Guess the selected gradle paths
 */
internal val AnActionEvent.gradlePathsSelected: List<GradlePath>
  get() = project?.let { project ->
    getData(LangDataKeys.PSI_ELEMENT_ARRAY)
      ?.mapNotNull { it.virtualFile?.javaPath?.dropBuildAndSrc() }
      ?.map { it.gradlePathRelativeTo(project) }
  } ?: emptyList()
