package com.fueledbycaffeine.spotlight

import com.autonomousapps.kit.truth.TestKitTruth.Companion.assertThat
import com.fueledbycaffeine.spotlight.fixtures.SpiritboxProject
import com.fueledbycaffeine.spotlight.fixtures.build
import org.junit.jupiter.api.Test

class FunctionalTest {
  @Test
  fun `verify build`() {
    // Given
    val project = SpiritboxProject().build()

    // When
    val result = project.build(":rotoscope-ep:assemble", "--info")

    // Then
    assertThat(result).task(":rotoscope-ep:assemble").succeeded()
    assertThat(result).task(":rotoscope:compileJava").noSource()
    assertThat(result).task(":hysteria:compileJava").noSource()
    assertThat(result).task(":sew-me-up:compileJava").noSource()
  }
}