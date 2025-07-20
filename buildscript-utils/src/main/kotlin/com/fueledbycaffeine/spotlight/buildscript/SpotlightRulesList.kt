package com.fueledbycaffeine.spotlight.buildscript

import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule
import com.squareup.moshi.FromJson
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dev.zacsweers.moshix.sealed.reflect.MoshiSealedJsonAdapterFactory
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import okio.IOException

public class SpotlightRulesList(private val root: Path) {
  public companion object {
    public const val SPOTLIGHT_RULES_LOCATION: String = "gradle/spotlight-rules.json"
  }

  public fun read(): Set<ImplicitDependencyRule> {
    val rulesPath = root.resolve(SPOTLIGHT_RULES_LOCATION)
    return if (rulesPath.exists()) {
      try {
        ruleSetAdapter.fromJson(rulesPath.readText()) ?: emptySet()
      } catch (e: IOException) {
        throw InvalidSpotlightRules("Spotlight rules at $SPOTLIGHT_RULES_LOCATION were invalid", e)
      }
    } else {
      emptySet()
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
  private val ruleSetAdapter = moshi.adapter<Set<ImplicitDependencyRule>>()
}

public class InvalidSpotlightRules(message: String, cause: Throwable) : Exception(message, cause)

@Suppress("unused")
private class GradlePathAdapter(private val root: Path) {
  @ToJson fun gradlePathToJson(path: GradlePath): String = path.path
  @FromJson fun gradlePathFromJson(pathString: String): GradlePath = GradlePath(root, pathString)
}
