package com.fueledbycaffeine.spotlight.idea.gradle

import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.util.GradleConstants

internal object GradleSystemUtils {
  fun sync(project: Project) {
    val importSpec = ImportSpecBuilder(project, GradleConstants.SYSTEM_ID).build()
    ExternalSystemUtil.refreshProject(project.basePath!!, importSpec)
  }
}

/**
 * Indicates if the task ID is for a Gradle sync (resolve project) task.
 */
internal val ExternalSystemTaskId.isGradleResolveProjectTask: Boolean
  get() = projectSystemId == GradleConstants.SYSTEM_ID && type == ExternalSystemTaskType.RESOLVE_PROJECT
