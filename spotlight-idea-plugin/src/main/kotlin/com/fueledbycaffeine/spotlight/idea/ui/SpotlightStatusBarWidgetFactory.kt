package com.fueledbycaffeine.spotlight.idea.ui

import com.fueledbycaffeine.spotlight.idea.SpotlightBundle
import com.fueledbycaffeine.spotlight.idea.SpotlightProjectService
import com.fueledbycaffeine.spotlight.idea.gradle.SpotlightGradleProjectsService
import com.fueledbycaffeine.spotlight.idea.spotlightService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.util.Consumer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.awt.Component
import java.awt.event.MouseEvent

private const val ID = "com.fueledbycaffeine.spotlight"

class SpotlightStatusBarWidgetFactory : StatusBarWidgetFactory, DumbAware {
  override fun getId(): String = ID

  override fun getDisplayName(): String = SpotlightBundle.message("statusbar.widget.name")

  override fun isAvailable(project: Project): Boolean {
    return true
  }

  override fun createWidget(project: Project): StatusBarWidget {
    return SpotlightStatusBarWidget(project)
  }

  override fun disposeWidget(widget: StatusBarWidget) {
    Disposer.dispose(widget)
  }

  override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}

private class SpotlightStatusBarWidget(
  private val project: Project
) : StatusBarWidget, StatusBarWidget.TextPresentation {

  private var statusBar: StatusBar? = null
  private val cs = CoroutineScope(SupervisorJob())

  private val gradleService = project.service<SpotlightGradleProjectsService>()
  private val projectService = project.service<SpotlightProjectService>()

  // Initialize with current StateFlow values so getText() returns correct value before install()
  @Volatile
  private var ideProjectsCount = computeCount(
    gradleService.includedProjects.value,
    projectService.ideProjects.value
  )

  override fun install(statusBar: StatusBar) {
    this.statusBar = statusBar

    // Combine both data sources:
    // - Use Gradle-synced projects (authoritative) when available
    // - Fall back to file-based projects when sync hasn't completed or failed
    cs.launch {
      combine(
        gradleService.includedProjects,
        projectService.ideProjects,
        ::computeCount
      )
        .collectLatest { count ->
          // Update count and text atomically
          // Must happen on EDT for UI updates
          if (ApplicationManager.getApplication().isDispatchThread) {
            updateCountAndText(count)
          } else {
            ApplicationManager.getApplication().invokeLater {
              updateCountAndText(count)
            }
          }
        }
    }
  }

  private fun updateCountAndText(count: Int) {
    ideProjectsCount = count
    statusBar?.updateWidget(ID())
  }

  override fun ID(): String = ID

  override fun getText(): String = SpotlightBundle.message(
    when (ideProjectsCount) {
      0 -> "statusbar.widget.text.inactive"
      else -> "statusbar.widget.text.active"
    }
  )

  override fun getTooltipText(): String {
    return SpotlightBundle.message("statusbar.widget.tooltip", ideProjectsCount)
  }

  override fun getClickConsumer(): Consumer<MouseEvent> {
    return Consumer {
      // Open ide-projects.txt
      project.spotlightService.openIdeProjectsInEditor()
    }
  }

  override fun getAlignment(): Float = Component.LEFT_ALIGNMENT

  override fun dispose() {
    cs.cancel()
  }

  private companion object {
    // Prefer Gradle-synced projects if available, otherwise use file-based
    fun computeCount(gradleSynced: Set<*>, fileBased: Set<*>): Int =
      if (gradleSynced.isNotEmpty()) gradleSynced.size else fileBased.size
  }
}
