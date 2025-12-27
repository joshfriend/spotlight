package com.fueledbycaffeine.spotlight.functionaltest

import com.fueledbycaffeine.spotlight.functionaltest.fixtures.SpiritboxProject
import com.fueledbycaffeine.spotlight.functionaltest.fixtures.configurationCacheReused
import com.fueledbycaffeine.spotlight.functionaltest.fixtures.configurationCacheStored
import com.fueledbycaffeine.spotlight.functionaltest.fixtures.syncWithParsersModel
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Functional tests for the Tooling API model builder that transfers parser providers
 * from Gradle to IDE during sync.
 */
class ParserModelBuilderFunctionalTest {

  @Test
  fun `model builder finds parser providers`() {
    // Given
    val project = SpiritboxProject().build()

    // When
    val syncResult = project.syncWithParsersModel()

    // Then
    val providers = syncResult.parsersModel.providers

    // Contains the default parser
    assertThat(providers.size).isEqualTo(1)
    val provider = providers.first()
    val parser = provider.getParser()
    assertThat(parser).isNotNull()
  }

  @Test
  fun `model builder works with isolated projects`() {
    // Given
    val project = SpiritboxProject().build()
    project.rootDir.resolve("gradle.properties")
      .appendText("\norg.gradle.unsafe.isolated-projects=true\n")

    // When
    val syncResult1 = project.syncWithParsersModel()
    assertThat(syncResult1.configurationCacheStored).isTrue()
    assertThat(syncResult1.parsersModel.providers.size).isEqualTo(1)

    val syncResult2 = project.syncWithParsersModel()
    assertThat(syncResult2.configurationCacheReused).isTrue()
    assertThat(syncResult2.parsersModel.providers.size).isEqualTo(1)
    val provider = syncResult2.parsersModel.providers.first()
    val parser = provider.getParser()
    assertThat(parser).isNotNull()
  }
}

