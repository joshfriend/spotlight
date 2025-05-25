package com.fueledbycaffeine.spotlight.dsl

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.file.BuildLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.SetProperty
import javax.inject.Inject

@Suppress("UnstableApiUsage")
public abstract class MatchRuleHandler @Inject constructor(
  private val name: String,
  private val layout: BuildLayout,
  objects: ObjectFactory,
) : Named {
  internal val includes: SetProperty<GradlePath> =
    objects.setProperty(GradlePath::class.java).convention(emptySet())

  public override fun getName(): String = name

  public val pattern: Regex = name.toRegex()

  /**
   * A project path to include when the buildscript graph parsing matches the regex [pattern]
   */
  public fun alsoInclude(vararg paths: String) {
    includes.addAll(paths.map { path ->
      val gradlePath = GradlePath(layout.settingsDirectory.asFile, path)
      if (!gradlePath.hasBuildFile) throw InvalidUserDataException("$path does not have a buildscript")
      gradlePath
    })
  }

  internal class Factory(
    private val layout: BuildLayout,
    private val objects: ObjectFactory,
  ) : NamedDomainObjectFactory<MatchRuleHandler> {
    override fun create(name: String): MatchRuleHandler =
      objects.newInstance(MatchRuleHandler::class.java, name, layout)
  }
}