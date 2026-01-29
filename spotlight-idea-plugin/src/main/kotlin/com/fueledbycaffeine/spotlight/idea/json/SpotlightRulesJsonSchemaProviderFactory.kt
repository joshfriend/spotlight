package com.fueledbycaffeine.spotlight.idea.json

import com.fueledbycaffeine.spotlight.buildscript.SpotlightRulesList
import com.fueledbycaffeine.spotlight.buildscript.SpotlightRulesList.Companion.SPOTLIGHT_RULES_LOCATION
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType

/**
 * Provides JSON schema for spotlight-rules.json files.
 * This enables validation, autocomplete for rule types, and documentation in the editor.
 */
class SpotlightRulesJsonSchemaProviderFactory : JsonSchemaProviderFactory, DumbAware {
  override fun getProviders(project: Project): List<JsonSchemaFileProvider> {
    return listOf(SpotlightRulesSchemaProvider())
  }
}

private class SpotlightRulesSchemaProvider() : JsonSchemaFileProvider {
  
  override fun isAvailable(file: VirtualFile): Boolean {
    return file.path.endsWith(SPOTLIGHT_RULES_LOCATION)
  }
  
  override fun getName(): String = "Spotlight Rules"
  
  override fun getSchemaFile(): VirtualFile? {
    // Load schema from buildscript-utils module classpath
    return SpotlightRulesList::class.java
      .getResource("/schemas/spotlight-rules-schema.json")
      ?.let { url ->
        VfsUtil.findFileByURL(url)
      }
  }
  
  override fun getSchemaType(): SchemaType = SchemaType.embeddedSchema
}
