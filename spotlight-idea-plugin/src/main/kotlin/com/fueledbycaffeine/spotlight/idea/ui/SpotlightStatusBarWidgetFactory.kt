package com.fueledbycaffeine.spotlight.idea.ui

import com.fueledbycaffeine.spotlight.idea.SpotlightBundle
import com.fueledbycaffeine.spotlight.idea.SpotlightProjectService
import com.fueledbycaffeine.spotlight.idea.spotlightService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.util.Consumer
import java.awt.Component
import java.awt.event.MouseEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext

private const val ID = "com.fueledbycaffeine.spotlight"

class SpotlightStatusBarWidgetFactory(
  private val scope: CoroutineScope
) : StatusBarWidgetFactory {
  override fun getId(): String = ID

  override fun getDisplayName(): String = SpotlightBundle.message("statusbar.widget.name")

  override fun isAvailable(project: Project): Boolean {
    return true
  }

  override fun createWidget(project: Project): StatusBarWidget {
    return SpotlightStatusBarWidget(project, scope)
  }

  override fun disposeWidget(widget: StatusBarWidget) {
    Disposer.dispose(widget)
  }

  override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}

private class SpotlightStatusBarWidget(
  private val project: Project,
  parentScope: CoroutineScope
) : StatusBarWidget, StatusBarWidget.TextPresentation {

  private var statusBar: StatusBar? = null
  private val job = SupervisorJob()
  private val cs = parentScope + job

  @Volatile
  private var ideProjectsCount = 0

  override fun install(statusBar: StatusBar) {
    this.statusBar = statusBar

    // start collecting service state
    cs.launch {
      project.service<SpotlightProjectService>()
        .ideProjects
        .map { it.size }
        .collectLatest { count ->
          ideProjectsCount = count
          // must update on EDT; VFS events already on EDT but collection may not be
          withContext(Dispatchers.Main) { statusBar.updateWidget(ID()) }
        }
    }
  }

  override fun ID(): String = ID

  override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

  override fun getTooltipText(): String {
    return SpotlightBundle.message("statusbar.widget.tooltip", ideProjectsCount)
  }

  override fun getClickConsumer(): Consumer<MouseEvent>? {
    return Consumer {
      // Open ide-projects.txt
      project.spotlightService.openIdeProjectsInEditor()
    }
  }

  override fun getText(): @NlsContexts.Label String {
    return SpotlightBundle.message(
      if (ideProjectsCount > 0) {
        "statusbar.widget.text.active"
      } else {
        "statusbar.widget.text.inactive"
      }
    )
  }

  override fun getAlignment(): Float {
    return Component.LEFT_ALIGNMENT
  }

}