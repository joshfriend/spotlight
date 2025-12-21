package com.fueledbycaffeine.spotlight.buildscript.parser

/**
 * Defines how a [BuildscriptParserProvider] interacts with other providers in the chain.
 */
public enum class ParserMode {
  /**
   * REPLACE mode means this parser replaces lower-priority parsers.
   * When a REPLACE parser is found, the search stops and only this parser is used.
   * This is the default mode for standard parsers.
   */
  REPLACE,
  
  /**
   * ADDITIVE mode means this parser supplements other parsers.
   * When an ADDITIVE parser is found, it's added to the chain and the search continues
   * to find other parsers. All matching ADDITIVE parsers are executed and their results
   * are merged together.
   * 
   * Use this mode when you want to add custom parsing logic on top of existing parsers
   * without replacing them entirely.
   */
  ADDITIVE
}
