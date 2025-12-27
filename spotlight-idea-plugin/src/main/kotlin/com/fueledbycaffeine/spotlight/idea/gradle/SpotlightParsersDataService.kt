package com.fueledbycaffeine.spotlight.idea.gradle

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService
import com.intellij.openapi.project.Project

/**
 * Data service that processes SpotlightParsersData during Gradle project import
 * and updates the SpotlightParsersService with the received parser instances.
 */
class SpotlightParsersDataService : AbstractProjectDataService<SpotlightParsersData, Void>() {

  override fun getTargetDataKey(): Key<SpotlightParsersData> = SpotlightParsersData.KEY

  override fun importData(
    toImport: MutableCollection<out DataNode<SpotlightParsersData>>,
    projectData: ProjectData?,
    project: Project,
    modelsProvider: IdeModifiableModelsProvider
  ) {
    if (toImport.isEmpty()) return

    val parsersService = SpotlightParsersService.getInstance(project)

    // Take the first one (should only be one per project)
    toImport.firstOrNull()?.data?.let { data ->
      parsersService.updateProviders(data.providers)
    }
  }
}

