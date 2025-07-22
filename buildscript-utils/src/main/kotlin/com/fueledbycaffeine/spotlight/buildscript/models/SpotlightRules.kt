package com.fueledbycaffeine.spotlight.buildscript.models

import com.fueledbycaffeine.spotlight.buildscript.TypeSafeAccessorInference
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule

public data class SpotlightRules(
  val implicitRules: Set<ImplicitDependencyRule> = emptySet(),
  val typeSafeAccessorInference: TypeSafeAccessorInference? = null,
)