package com.fueledbycaffeine.spotlight.buildscript.models

import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
public data class SpotlightRules(
  val implicitRules: Set<ImplicitDependencyRule> = emptySet(),
  val taskInvocationRules: Set<TaskInvocationRule> = emptySet(),
) {
  public companion object {
    public val EMPTY: SpotlightRules = SpotlightRules()
  }
}
