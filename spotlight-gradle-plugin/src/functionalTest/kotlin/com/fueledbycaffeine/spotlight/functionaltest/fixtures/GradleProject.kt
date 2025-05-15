package com.fueledbycaffeine.spotlight.functionaltest.fixtures

import com.autonomousapps.kit.GradleBuilder
import com.autonomousapps.kit.GradleProject
import org.gradle.testkit.runner.BuildResult

fun GradleProject.build(vararg args: String): BuildResult =
  GradleBuilder.build(rootDir, *args)

fun GradleProject.sync(vararg args: String): BuildResult =
  GradleBuilder.build(rootDir, "help", "-Didea.sync.active=true", *args)