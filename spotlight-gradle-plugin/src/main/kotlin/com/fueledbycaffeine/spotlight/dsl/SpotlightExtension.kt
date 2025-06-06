package com.fueledbycaffeine.spotlight.dsl

import com.fueledbycaffeine.spotlight.SpotlightSettingsPlugin
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Configuration options for [SpotlightSettingsPlugin].
 */
@Suppress("UnstableApiUsage")
public abstract class SpotlightExtension @Inject constructor(
  objects: ObjectFactory,
) {
  public companion object {
    public const val NAME: String = "spotlight"

    @JvmStatic
    public fun ExtensionContainer.getSpotlightExtension(): SpotlightExtension {
      return try {
        getByType(SpotlightExtension::class.java)
      } catch (_: UnknownDomainObjectException) {
        create(NAME, SpotlightExtension::class.java)
      }
    }
  }

  /**
   * Sets the level of processing to be done to support type-safe project accessors.
   *
   * Defaults to [TypeSafeAccessorInference.STRICT]
   *
   * @see <a href="https://docs.gradle.org/current/userguide/declaring_dependencies_basics.html#sec:type-safe-project-accessors">Gradle type-safe project accessors docs</a>
   */
  public val typeSafeAccessorInference: Property<TypeSafeAccessorInference> =
    objects.property(TypeSafeAccessorInference::class.java).convention(TypeSafeAccessorInference.STRICT)
}
