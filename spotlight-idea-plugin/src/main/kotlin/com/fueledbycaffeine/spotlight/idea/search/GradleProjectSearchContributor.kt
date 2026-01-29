package com.fueledbycaffeine.spotlight.idea.search

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.idea.completion.FuzzyMatchingUtils
import com.fueledbycaffeine.spotlight.idea.spotlightService
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributorFactory
import com.intellij.ide.actions.searcheverywhere.WeightedSearchEverywhereContributor
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.Processor
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

/**
 * A Search Everywhere contributor that matches Gradle project paths.
 * Allows users to search for projects using:
 * - Full Gradle paths (e.g., ":feature-flags:api")
 * - Fuzzy abbreviated paths (e.g., ":ffapi" matches ":feature-flags:api")
 * - Type-safe accessor style (e.g., "featureFlags.api")
 *
 * When selected, opens the project's build.gradle(.kts) file.
 */
class GradleProjectSearchContributor(event: AnActionEvent) :
  WeightedSearchEverywhereContributor<GradleProjectItem> {

  private val project: Project? = event.project

  override fun getSearchProviderId(): String = CONTRIBUTOR_ID

  override fun getGroupName(): String = "Gradle Projects"

  override fun getSortWeight(): Int = 50 // Show before files for better visibility

  override fun showInFindResults(): Boolean = true

  override fun isShownInSeparateTab(): Boolean = false

  override fun fetchWeightedElements(
    pattern: String,
    progressIndicator: ProgressIndicator,
    consumer: Processor<in FoundItemDescriptor<GradleProjectItem>>
  ) {
    val proj = project ?: return
    val spotlightService = proj.spotlightService
    val allProjects = spotlightService.allProjects.value

    if (allProjects.isEmpty() || pattern.isBlank()) return

    // Find matching projects using the same fuzzy matching as completions
    val matches = findMatchingProjects(pattern, allProjects)

    for ((gradlePath, priority) in matches) {
      if (progressIndicator.isCanceled) return

      val item = GradleProjectItem(gradlePath, proj)
      // Lower priority number = higher weight in results
      // Priority 0-3 are good matches, 4-6 are weaker
      val weight = when (priority) {
        0 -> 10000  // Exact match
        1 -> 9000   // Path starts with prefix
        2 -> 8000   // First segment starts with prefix
        3 -> 7000   // Fuzzy match across segments
        4 -> 5000   // Any segment starts with prefix
        5 -> 3000   // Any segment contains prefix
        else -> 1000
      }
      consumer.process(FoundItemDescriptor(item, weight))
    }
  }

  private fun findMatchingProjects(
    pattern: String,
    allProjects: Set<GradlePath>
  ): List<Pair<GradlePath, Int>> {
    val normalizedPattern = pattern.trim()
    
    // Check if it looks like a Gradle path (starts with :) or type-safe accessor (contains .)
    val looksLikeGradlePath = normalizedPattern.startsWith(":")
    val looksLikeAccessor = normalizedPattern.contains(".")
    
    // If it doesn't look like a Gradle path or accessor, require at least 2 characters
    if (!looksLikeGradlePath && !looksLikeAccessor && normalizedPattern.length < 2) {
      return emptyList()
    }

    return allProjects
      .filter { it.hasBuildFile }
      .mapNotNull { gradlePath ->
        // Try matching against the Gradle path
        val pathPriority = FuzzyMatchingUtils.calculatePathMatchPriority(
          gradlePath.path,
          normalizedPattern
        )
        
        // Also try matching against the type-safe accessor
        val accessorPattern = normalizedPattern.removePrefix("projects.")
        val accessorPriority = FuzzyMatchingUtils.calculateAccessorMatchPriority(
          gradlePath.typeSafeAccessorName,
          accessorPattern
        )
        
        val bestPriority = minOf(pathPriority, accessorPriority)
        
        // Only include if there's a reasonable match
        // Priority <= 3 means fuzzy match succeeded
        // Priority 4-5 means segment match
        // Priority 6 means contains match
        if (bestPriority <= 6) {
          gradlePath to bestPriority
        } else {
          null
        }
      }
      .sortedBy { it.second }
      .take(MAX_RESULTS)
  }

  override fun getElementsRenderer(): ListCellRenderer<in GradleProjectItem> {
    return GradleProjectSearchRenderer()
  }

  override fun getDataForItem(element: GradleProjectItem, dataId: String): Any? = null

  override fun processSelectedItem(
    selected: GradleProjectItem,
    modifiers: Int,
    searchText: String
  ): Boolean {
    val proj = project ?: return false
    val buildFile = selected.virtualFile ?: return false

    ApplicationManager.getApplication().invokeLater {
      FileEditorManager.getInstance(proj).openFile(buildFile, true)
    }
    return true
  }

  override fun dispose() {}

  companion object {
    const val CONTRIBUTOR_ID = "GradleProjectSearchContributor"
    private const val MAX_RESULTS = 50
  }
}

/**
 * Represents a Gradle project in search results.
 */
class GradleProjectItem(
  val gradlePath: GradlePath,
  private val project: Project
) : NavigationItem {

  val virtualFile: VirtualFile?
    get() = try {
      VirtualFileManager.getInstance().findFileByNioPath(gradlePath.buildFilePath)
    } catch (_: Exception) {
      null
    }

  val psiFile: PsiFile?
    get() = virtualFile?.let { PsiManager.getInstance(project).findFile(it) }

  override fun getName(): String = gradlePath.path

  override fun getPresentation(): ItemPresentation = object : ItemPresentation {
    override fun getPresentableText(): String = gradlePath.path

    override fun getLocationString(): String = virtualFile?.name ?: "build.gradle"

    override fun getIcon(unused: Boolean): Icon = AllIcons.Nodes.Module
  }

  override fun navigate(requestFocus: Boolean) {
    val buildFile = virtualFile ?: return
    FileEditorManager.getInstance(project).openFile(buildFile, requestFocus)
  }

  override fun canNavigate(): Boolean = virtualFile != null

  override fun canNavigateToSource(): Boolean = canNavigate()
}

/**
 * Factory for creating the Gradle project search contributor.
 */
class GradleProjectSearchContributorFactory : SearchEverywhereContributorFactory<GradleProjectItem> {
  override fun createContributor(initEvent: AnActionEvent): SearchEverywhereContributor<GradleProjectItem> {
    return GradleProjectSearchContributor(initEvent)
  }
}

/**
 * Renderer for Gradle project items in Search Everywhere results.
 */
private class GradleProjectSearchRenderer : ListCellRenderer<GradleProjectItem> {
  private val defaultRenderer = DefaultListCellRenderer()

  override fun getListCellRendererComponent(
    list: JList<out GradleProjectItem>?,
    value: GradleProjectItem?,
    index: Int,
    isSelected: Boolean,
    cellHasFocus: Boolean
  ): Component {
    if (value == null) {
      return defaultRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
    }

    val panel = JPanel(BorderLayout(8, 0))
    panel.isOpaque = true

    if (isSelected) {
      panel.background = list?.selectionBackground
      panel.foreground = list?.selectionForeground
    } else {
      panel.background = list?.background
      panel.foreground = list?.foreground
    }

    // Icon
    val iconLabel = JLabel(AllIcons.Nodes.Module)
    panel.add(iconLabel, BorderLayout.WEST)

    // Main text - the Gradle path
    val mainText = JLabel(value.gradlePath.path)
    mainText.foreground = panel.foreground
    panel.add(mainText, BorderLayout.CENTER)

    // Location text - the build file name
    val locationText = JLabel(value.virtualFile?.name ?: "build.gradle")
    locationText.foreground = if (isSelected) {
      panel.foreground
    } else {
      list?.foreground?.let { 
        java.awt.Color(it.red, it.green, it.blue, 128) 
      } ?: panel.foreground
    }
    panel.add(locationText, BorderLayout.EAST)

    return panel
  }
}
