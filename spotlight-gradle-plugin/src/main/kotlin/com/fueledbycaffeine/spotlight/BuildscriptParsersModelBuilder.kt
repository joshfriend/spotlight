package com.fueledbycaffeine.spotlight

import com.fueledbycaffeine.spotlight.buildscript.parser.BuildscriptParserProvider
import com.fueledbycaffeine.spotlight.tooling.BuildscriptParserProviderInfo
import com.fueledbycaffeine.spotlight.tooling.BuildscriptParsersModel
import com.fueledbycaffeine.spotlight.tooling.BuildscriptParsersModelImpl
import org.gradle.api.Project
import org.gradle.tooling.provider.model.ToolingModelBuilder
import java.util.ServiceLoader

/**
 * Tooling API model builder that discovers buildscript parser providers via SPI
 * and transfers them to the IDE during Gradle sync.
 */
public object BuildscriptParsersModelBuilder : ToolingModelBuilder {

  override fun canBuild(modelName: String): Boolean =
    modelName == BuildscriptParsersModel::class.java.name

  override fun buildAll(modelName: String, project: Project): Any {
    val providers = ServiceLoader.load(
      BuildscriptParserProvider::class.java,
      BuildscriptParserProvider::class.java.classLoader
    ).sortedByDescending { it.priority }

    val providerInfo = providers.map {
      BuildscriptParserProviderInfo(
        implementationClassName = it.javaClass.name,
        priority = it.priority,
        mode = it.mode.name
      )
    }

    return BuildscriptParsersModelImpl(providerInfo)
  }
}
