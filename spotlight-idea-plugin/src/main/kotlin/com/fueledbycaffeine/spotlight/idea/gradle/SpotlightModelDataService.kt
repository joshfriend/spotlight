package com.fueledbycaffeine.spotlight.idea.gradle

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorNotifications

/**
 * Data service that processes [SpotlightIdeModelData] after Gradle sync and updates the
 * [SpotlightGradleProjectsService] with the discovered projects.
 */
class SpotlightModelDataService : AbstractProjectDataService<SpotlightIdeModelData, Void>() {

  override fun getTargetDataKey(): Key<SpotlightIdeModelData> = SpotlightIdeModelData.KEY

  override fun importData(
    toImport: Collection<DataNode<SpotlightIdeModelData>>,
    projectData: ProjectData?,
    project: Project,
    modelsProvider: IdeModifiableModelsProvider
  ) {
    val projectsService = SpotlightGradleProjectsService.getInstance(project)
    when (val data = toImport.firstOrNull()?.data) {
      // No Spotlight model means the Gradle plugin isn't applied to this project
      null -> projectsService.markSpotlightNotApplied()
      else -> projectsService.updateProjects(data.includedProjectPaths)
    }
    // Recompute editor banners now that we know whether Spotlight is applied
    EditorNotifications.getInstance(project).updateAllNotifications()
  }
}
