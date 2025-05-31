package com.fueledbycaffeine.spotlight.cli.commands

import picocli.CommandLine

@CommandLine.Command(
  name = "spotlight",
  mixinStandardHelpOptions = true,
  description = ["Spotlight CLI utilities for Gradle project analysis"],
  subcommands = [
    ListDependenciesCommand::class,
    ReasonCommand::class,
    ConsumersCommand::class,
  ]
)
class SpotlightCommand : Runnable {
  @CommandLine.Option(
    names = ["--log-level"],
    description = ["Set logging level (ERROR, WARN, INFO, DEBUG, TRACE)"],
    defaultValue = "WARN",
    scope = CommandLine.ScopeType.INHERIT
  )
  var logLevel: String = "WARN"

  override fun run() {
    // If no subcommand is specified, show usage
    CommandLine.usage(this, System.out)
  }
}