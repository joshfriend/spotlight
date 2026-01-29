package com.fueledbycaffeine.spotlight.idea.gradle

import com.fueledbycaffeine.spotlight.buildscript.models.SpotlightModel
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension

/**
 * Gradle project resolver extension that requests the [SpotlightModel] during sync.
 */
class SpotlightProjectResolverExtension : AbstractProjectResolverExtension() {

  // Grabs the model and sets the data during sync
  override fun populateModuleExtraModels(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
    val spotlightModel = resolverCtx.getExtraProject(gradleModule, SpotlightModel::class.java)
    if (spotlightModel != null) {
      // Convert proxy to concrete data class before storing in DataNode
      // (Gradle Tooling API returns proxies that can't be serialized by IntelliJ)
      val modelData = SpotlightIdeModelData(spotlightModel.includedProjectPaths)
      ideModule.createChild(SpotlightIdeModelData.KEY, modelData)
    }
    super.populateModuleExtraModels(gradleModule, ideModule)
  }

  // Tells the IDE which classes are available for the Gradle Tooling API to query
  override fun getExtraProjectModelClasses() = setOf(SpotlightModel::class.java)
}
