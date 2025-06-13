@file:JvmName("SpotlightCli")

package com.fueledbycaffeine.spotlight.cli

import com.fueledbycaffeine.spotlight.cli.commands.SpotlightCommand
import picocli.CommandLine
import kotlin.system.exitProcess

fun main(args: Array<String>) {
  // Parse args to extract log level before creating the command
  val logLevelIndex = args.indexOf("--log-level")
  val logLevel = if (logLevelIndex >= 0 && logLevelIndex < args.size - 1) {
    args[logLevelIndex + 1]
  } else {
    "WARN"
  }

  // Configure SLF4J Simple Logger before any logging happens
  System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", logLevel.lowercase())
  System.setProperty("org.slf4j.simpleLogger.log.com.fueledbycaffeine", logLevel.lowercase())

  val exitCode = CommandLine(SpotlightCommand()).execute(*args)
  exitProcess(exitCode)
}
