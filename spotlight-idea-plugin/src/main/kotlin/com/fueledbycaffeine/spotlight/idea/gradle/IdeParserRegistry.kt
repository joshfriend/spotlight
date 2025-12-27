package com.fueledbycaffeine.spotlight.idea.gradle

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.graph.DependencyRule
import com.fueledbycaffeine.spotlight.buildscript.parser.BuildscriptParser
import com.fueledbycaffeine.spotlight.buildscript.parser.ParserRegistry

/**
 * IDE-specific parser registry.
 *
 * We currently receive provider *metadata* from Gradle sync (not provider instances),
 * because Tooling API models must be deserializable in the IDE with a restricted classpath.
 *
 * A follow-up improvement could either:
 *  - instantiate parsers directly in the IDE (shipping implementations with the IDEA plugin), or
 *  - invoke parsing via a dedicated Gradle BuildAction/tooling call.
 */
class IdeParserRegistry(
  private val parsersService: SpotlightParsersService
) : ParserRegistry {

  override fun findParser(project: GradlePath, rules: Set<DependencyRule>): BuildscriptParser? {
    // Keep referencing the synced metadata so it remains observable/useful, but we can't
    // build actual parsers from it in-process.
    val providers = parsersService.providers.value
    if (providers.isEmpty()) return null

    return null
  }
}
