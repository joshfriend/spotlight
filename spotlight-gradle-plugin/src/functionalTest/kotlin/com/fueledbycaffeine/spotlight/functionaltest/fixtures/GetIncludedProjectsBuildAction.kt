package com.fueledbycaffeine.spotlight.functionaltest.fixtures

import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.gradle.BasicGradleProject

class GetIncludedProjectsBuildAction : BuildAction<List<BasicGradleProject>> {
  override fun execute(controller: BuildController): List<BasicGradleProject> {
    val actions = controller.buildModel.projects.map { FetchModelForProject(it) }
    return controller.run(actions)
  }

  private class FetchModelForProject(private val project: BasicGradleProject) : BuildAction<BasicGradleProject> {
    override fun execute(controller: BuildController): BasicGradleProject {
      // This query always returns null, but we have to run _something_ otherwise isolated projects knows that no work
      // was done and will reuse the tooling model request result from a previous invocation
      controller.findModel(project, BasicGradleProject::class.java)
      return project
    }
  }
}