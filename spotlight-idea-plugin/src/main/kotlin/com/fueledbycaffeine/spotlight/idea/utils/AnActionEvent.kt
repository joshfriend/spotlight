package com.fueledbycaffeine.spotlight.idea.utils

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore
import java.nio.file.Path
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

private val PsiElement.virtualFile: VirtualFile? get() = PsiUtilCore.getVirtualFile(this)
private val VirtualFile.javaPath: Path get() = Path.of(path)

private fun TreePath.segmentNames(): List<String> {
  return path.mapNotNull { seg ->
    val node = (seg as? DefaultMutableTreeNode)?.userObject ?: seg
    node.toString().trim().takeIf { it.isNotBlank() }
  }
}

private fun resolveTreePathToVirtualFile(
  treePath: TreePath,
  project: Project,
  base: Path,
  vfm: VirtualFileManager
): VirtualFile? {
  val rawSegments = treePath.segmentNames()
    .filter { it.isNotBlank() && it != "." && it != ".." }

  // Drop any leading segments that are just the project root label (often repeated)
  val segments = rawSegments.dropWhile { it == project.name || it == base.fileName.toString() }

  // Try all suffixes until we find something that is a real file under base.
  return segments.indices
    .asSequence()
    .map { start -> segments.subList(start, segments.size) }
    .map { rel -> rel.fold(base) { acc, s -> acc.resolve(s) }.normalize() }
    // Ensure we never escape the project base
    .filter { nio -> nio.startsWith(base) }
    .mapNotNull { nio -> vfm.findFileByNioPath(nio) }
    .firstOrNull()
}

private fun AnActionEvent.virtualFilesFromTreeSelection(project: Project): List<VirtualFile> {
  val component = getData(PlatformDataKeys.CONTEXT_COMPONENT)
  val tree = component as? JTree ?: return emptyList()

  val selected: Array<TreePath> = tree.selectionPaths ?: return emptyList()
  if (selected.isEmpty()) return emptyList()

  val base = Path.of(project.basePath!!).normalize()
  val vfm = VirtualFileManager.getInstance()

  return selected
    .asSequence()
    .mapNotNull { treePath -> resolveTreePathToVirtualFile(treePath, project, base, vfm) }
    .toList()
}

/**
 * Guess the selected gradle paths
 */
internal val AnActionEvent.gradlePathsSelected: Set<GradlePath> get() {
  val project = project ?: return emptySet()

  val virtualFiles = sequence {
    // PSI selection(s)
    val psiArray = getData(LangDataKeys.PSI_ELEMENT_ARRAY)
      ?.mapNotNull { it.virtualFile }.orEmpty()
    yieldAll(psiArray)

    val psiSingle = getData(CommonDataKeys.PSI_ELEMENT)?.virtualFile
    if (psiSingle != null) yield(psiSingle)

    // Virtual file selection(s)
    val vfArray = getData(CommonDataKeys.VIRTUAL_FILE_ARRAY).orEmpty().toList()
    yieldAll(vfArray)

    val vfSingle = getData(CommonDataKeys.VIRTUAL_FILE)
    if (vfSingle != null) yield(vfSingle)

    // Project Files view: resolve intermediate folder nodes via the JTree selection path
    yieldAll(virtualFilesFromTreeSelection(project))
  }

  return virtualFiles
    .mapNotNull { it.javaPath.dropBuildAndSrc() }
    .map { it.gradlePathRelativeTo(project) }
    .toSet()
}
