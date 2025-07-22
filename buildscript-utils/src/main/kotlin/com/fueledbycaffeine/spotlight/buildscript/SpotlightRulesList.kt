package com.fueledbycaffeine.spotlight.buildscript

import com.fueledbycaffeine.spotlight.buildscript.graph.DependencyRule
import com.fueledbycaffeine.spotlight.buildscript.graph.FullModeTypeSafeProjectAccessorRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule
import com.fueledbycaffeine.spotlight.buildscript.graph.StrictModeTypeSafeProjectAccessorRule
import com.fueledbycaffeine.spotlight.buildscript.models.SpotlightRules
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dev.zacsweers.moshix.sealed.reflect.MoshiSealedJsonAdapterFactory
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
      .add(MoshiSealedJsonAdapterFactory())
      .add(GradlePathAdapter(root))
      .addLast(KotlinJsonAdapterFactory())
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

/**
 * Computes the set of all [DependencyRule] that should be applied to a given project.
 */
public fun computeSpotlightRules(
  rootDir: Path,
  projectName: String,
  implicitRules: Set<ImplicitDependencyRule> = emptySet(),
  typeSafeInferenceLevel: TypeSafeAccessorInference = TypeSafeAccessorInference.DISABLED,
  allProjects: () -> Set<GradlePath> = { emptySet() },
): Set<DependencyRule> {
  return if (typeSafeInferenceLevel != TypeSafeAccessorInference.DISABLED) {
    val rootProjectTypeSafeAccessor = GradlePath(rootDir, projectName).typeSafeAccessorName
    val typeSafeAccessorRule = if (typeSafeInferenceLevel == TypeSafeAccessorInference.FULL) {
      val mapping = allProjects().associateBy { it.typeSafeAccessorName }
      FullModeTypeSafeProjectAccessorRule(rootProjectTypeSafeAccessor, mapping)
    } else {
      StrictModeTypeSafeProjectAccessorRule(rootProjectTypeSafeAccessor)
    }
    implicitRules + typeSafeAccessorRule
  } else {
    implicitRules
  }
}
