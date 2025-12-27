package com.fueledbycaffeine.spotlight.idea.gradle

import com.fueledbycaffeine.spotlight.tooling.BuildscriptParserProviderInfo
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Service that stores BuildScript parser provider metadata received during Gradle sync.
 */
@Service(Service.Level.PROJECT)
class SpotlightParsersService {

  private val _providers = MutableStateFlow<List<BuildscriptParserProviderInfo>>(emptyList())

  /**
   * Provider metadata received from Gradle sync (sorted by priority)
   */
  val providers: StateFlow<List<BuildscriptParserProviderInfo>> = _providers.asStateFlow()

  /**
   * Update the providers from Gradle sync.
   * Called by SpotlightParsersDataService during project import.
   */
  fun updateProviders(providers: List<BuildscriptParserProviderInfo>) {
    _providers.value = providers
  }

  /**
   * Check if providers are available from sync
   */
  fun hasProviders(): Boolean = _providers.value.isNotEmpty()

  companion object {
    @JvmStatic
    fun getInstance(project: Project): SpotlightParsersService = project.service()
  }
}
