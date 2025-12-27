package com.fueledbycaffeine.spotlight.idea.gradle

import com.fueledbycaffeine.spotlight.tooling.BuildscriptParsersModel
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import org.gradle.tooling.model.idea.IdeaProject
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension

/**
 * Gradle project resolver extension that requests the BuildscriptParsersModel
 * during IDE sync to receive parser implementations.
 */
class SpotlightProjectResolverExtension : AbstractProjectResolverExtension() {

  override fun getExtraProjectModelClasses(): Set<Class<out Any>> {
    return setOf(BuildscriptParsersModel::class.java)
  }

  override fun getToolingExtensionsClasses(): Set<Class<out Any>> {
    return setOf(BuildscriptParsersModel::class.java)
  }

  override fun populateProjectExtraModels(
    gradleProject: IdeaProject,
    ideProject: DataNode<ProjectData>
  ) {
    val parsersModel = resolverCtx.getExtraProject(
      null,
      BuildscriptParsersModel::class.java
    )

    if (parsersModel != null) {
      // Store the parser providers in the DataNode
      ideProject.createChild(
        SpotlightParsersData.KEY,
        SpotlightParsersData(parsersModel.providers)
      )
    }

    super.populateProjectExtraModels(gradleProject, ideProject)
  }
}

