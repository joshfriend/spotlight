package com.fueledbycaffeine.spotlight.cli

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import java.io.FileNotFoundException
import kotlin.io.path.readText

/**
 * Parse convention plugin ID and DI configuration from a module's build file.
 */
data class PluginInfo(
  val conventionPluginId: String?,
  val hasKapt: Boolean,
  val hasAnvilWithServices: Boolean,
  val hasDaggerKapt: Boolean,
  val pluginCost: Int,
)

private val PLUGIN_ID_REGEX = Regex("""id\s*[('"]([^'"]+)['")]""")
private val ENABLE_KAPT_REGEX = Regex("""enableDaggerAnnotationProcessingWithKapt\s+true""")
private val ENABLE_ANVIL_SERVICES_REGEX = Regex("""enableAnvilWithServices\s+true""")

// Cost tiers for convention plugins
private val PLUGIN_COST_MAP = mapOf(
  "com.squareup.jvm.lib" to 0,
  "com.squareup.jvm.lib-java" to 0,
  "com.squareup.android.lib" to 5,
  "com.squareup.android.lib-java" to 5,
  "com.squareup.android.res" to 3,
  "com.squareup.android.lib.multiplatform" to 5,
  "com.squareup.android.wiring" to 7,
  "com.squareup.jvm.wiring" to 7,
  "com.squareup.anvil-vertical" to 7,
  "com.squareup.android.wiring-java" to 12,
  "com.squareup.demo-app" to 15,
  "com.squareup.android.app" to 15,
  "com.squareup.android.app-java" to 15,
  "com.squareup.android.test" to 10,
  "com.squareup.android.test-java" to 10,
)

fun parsePluginInfo(project: GradlePath): PluginInfo {
  val buildFileContent = try {
    project.buildFilePath.readText()
  } catch (_: FileNotFoundException) {
    return PluginInfo(null, false, false, false, 0)
  }

  // Find convention plugin ID
  val pluginIds = PLUGIN_ID_REGEX.findAll(buildFileContent).map { it.groupValues[1] }.toList()
  val conventionPluginId = pluginIds.firstOrNull { it.startsWith("com.squareup.") }

  // Check for raw kapt plugin
  val hasRawKapt = pluginIds.any { it == "kotlin-kapt" || it == "org.jetbrains.kotlin.kapt" }

  // Check DI configuration in square { di { ... } } block
  val hasDaggerKapt = ENABLE_KAPT_REGEX.containsMatchIn(buildFileContent)
  val hasAnvilWithServices = ENABLE_ANVIL_SERVICES_REGEX.containsMatchIn(buildFileContent)

  // Calculate cost
  var cost = conventionPluginId?.let { PLUGIN_COST_MAP[it] } ?: 2
  if (hasDaggerKapt) cost += 5
  if (hasAnvilWithServices) cost += 3
  if (hasRawKapt && !hasDaggerKapt) cost += 5

  return PluginInfo(
    conventionPluginId = conventionPluginId,
    hasKapt = hasRawKapt || hasDaggerKapt,
    hasAnvilWithServices = hasAnvilWithServices,
    hasDaggerKapt = hasDaggerKapt,
    pluginCost = cost.coerceAtMost(20),
  )
}
