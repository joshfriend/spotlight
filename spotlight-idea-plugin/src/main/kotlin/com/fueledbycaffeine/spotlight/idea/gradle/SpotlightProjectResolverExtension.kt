package com.fueledbycaffeine.spotlight.idea.gradle

import com.fueledbycaffeine.spotlight.buildscript.models.SpotlightModel
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.project.ModuleData
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension

/**
 * Gradle project resolver extension that requests the [SpotlightModel] during sync.
 */
class SpotlightProjectResolverExtension : AbstractProjectResolverExtension() {
  internal companion object {
    val SPOTLIGHT_MODEL_KEY = Key.create(SpotlightModel::class.java, 1)
  }

  // Grabs the model and sets the data during sync
  override fun populateModuleExtraModels(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
    val spotlightModel = resolverCtx.getExtraProject(gradleModule, SpotlightModel::class.java)
    if (spotlightModel != null) {
      ideModule.createChild(SPOTLIGHT_MODEL_KEY, spotlightModel)
    }
    super.populateModuleExtraModels(gradleModule, ideModule)
  }

  // Tells the IDE which classes are available for the Gradle Tooling API to query
  override fun getExtraProjectModelClasses() = setOf(SpotlightModel::class.java)
}
