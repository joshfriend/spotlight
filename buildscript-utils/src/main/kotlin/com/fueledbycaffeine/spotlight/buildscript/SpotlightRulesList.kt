package com.fueledbycaffeine.spotlight.buildscript

import com.fueledbycaffeine.spotlight.buildscript.graph.DependencyRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule
import com.fueledbycaffeine.spotlight.buildscript.graph.TypeSafeProjectAccessorRule
import com.fueledbycaffeine.spotlight.buildscript.models.SpotlightRules
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.adapter
import java.nio.file.Path
import kotlin.io.path.exists
import okio.IOException
import okio.buffer
import okio.source

public class SpotlightRulesList(private val root: Path) {
  public companion object {
    public const val SPOTLIGHT_RULES_LOCATION: String = "gradle/spotlight-rules.json"
  }

  public fun read(): SpotlightRules {
    val rulesPath = root.resolve(SPOTLIGHT_RULES_LOCATION)
    return if (rulesPath.exists()) {
      try {
        JsonReader.of(rulesPath.source().buffer()).use { reader ->
          reader.peekJson().use { reader ->
            try {
              rulesAdapter.fromJson(reader) ?: SpotlightRules.EMPTY
            } catch (_: JsonDataException) {
              // Try just parsing it as a set of rules (legacy)
              val ruleSet = ruleSetAdapter.fromJson(reader) ?: emptySet()
              SpotlightRules(implicitRules = ruleSet)
            }
          }
        }
      } catch (e: IOException) {
        throw InvalidSpotlightRules("Spotlight rules at $SPOTLIGHT_RULES_LOCATION were invalid", e)
      }
    } else {
      SpotlightRules.EMPTY
    }
  }

  private val moshi by lazy {
    Moshi.Builder()
      .add(GradlePathAdapter(root))
      .add(RegexAdapter)
      .build()
  }

  @OptIn(ExperimentalStdlibApi::class) // Not actually experimental anymore!
  private val rulesAdapter = moshi.adapter<SpotlightRules>()

  @OptIn(ExperimentalStdlibApi::class) // Not actually experimental anymore!
  private val ruleSetAdapter = moshi.adapter<Set<ImplicitDependencyRule>>()
}

public class InvalidSpotlightRules(message: String, cause: Throwable) : Exception(message, cause)

@Suppress("unused")
private class GradlePathAdapter(private val root: Path) {
  @ToJson fun gradlePathToJson(path: GradlePath): String = path.path
  @FromJson fun gradlePathFromJson(pathString: String): GradlePath = GradlePath(root, pathString)
}

@Suppress("unused")
private object RegexAdapter {
  @ToJson fun regexToJson(regex: Regex): String = regex.pattern
  @FromJson fun regexFromJson(pattern: String): Regex = pattern.toRegex()
}

/**
 * Computes the set of all [DependencyRule] that should be applied to a given project.
 *
 * Always enables full type-safe project accessor inference.
 */
public fun computeSpotlightRules(
  rootDir: Path,
  projectName: String,
  implicitRules: Set<ImplicitDependencyRule> = emptySet(),
  allProjects: () -> Set<GradlePath> = { emptySet() },
): Set<DependencyRule> {
  val rootProjectTypeSafeAccessor = GradlePath(rootDir, projectName).typeSafeAccessorName
  val mapping = allProjects().associateBy { it.typeSafeAccessorName }
  val typeSafeAccessorRule = TypeSafeProjectAccessorRule(rootProjectTypeSafeAccessor, mapping)
  return implicitRules + typeSafeAccessorRule
}
