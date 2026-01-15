package com.fueledbycaffeine.spotlight.tooling

import com.fueledbycaffeine.spotlight.buildscript.models.SpotlightModel
import com.fueledbycaffeine.spotlight.utils.isRootProject
import org.gradle.api.Project
import org.gradle.tooling.provider.model.ToolingModelBuilder

/**
 * Tooling model builder that provides [SpotlightModel] to the IDE via Gradle Tooling API.
 */
public class SpotlightModelBuilder : ToolingModelBuilder {
  override fun canBuild(modelName: String): Boolean {
    return modelName == SpotlightModel::class.java.name
  }

  override fun buildAll(modelName: String, project: Project): SpotlightModel? {
    if (!project.isRootProject) return null
    return DefaultSpotlightModel(
      includedProjectPaths = project.allprojects.map { it.path }.toSet()
    )
  }
}
