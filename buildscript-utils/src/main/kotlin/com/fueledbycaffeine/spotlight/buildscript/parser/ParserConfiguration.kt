package com.fueledbycaffeine.spotlight.buildscript.parser

/**
 * Gradle-agnostic configuration source for parsers.
 *
 * This is intentionally a tiny abstraction so `:buildscript-utils` doesn't need to depend
 * on Gradle's Provider APIs. The Gradle plugin (or IDE) can adapt its own configuration
 * mechanism into this interface.
 */
public fun interface ParserConfiguration {
  /**
   * @return the value for [key], or null if not present.
   */
  public fun get(key: String): String?

  public companion object {
    /** An empty configuration that returns null for all keys. */
    public val EMPTY: ParserConfiguration = ParserConfiguration { null }

    /** Convenience for tests and simple setups. */
    public fun fromMap(values: Map<String, String>): ParserConfiguration = ParserConfiguration { values[it] }
  }
}

public fun ParserConfiguration.getBoolean(key: String, default: Boolean = false): Boolean =
  get(key)?.toBooleanStrictOrNull() ?: default

public fun ParserConfiguration.getInt(key: String, default: Int = 0): Int =
  get(key)?.toIntOrNull() ?: default


