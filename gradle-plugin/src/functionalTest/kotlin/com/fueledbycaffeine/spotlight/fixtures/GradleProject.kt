package com.fueledbycaffeine.spotlight.fixtures

import com.autonomousapps.kit.GradleBuilder
import com.autonomousapps.kit.GradleProject
import org.gradle.testkit.runner.BuildResult

fun GradleProject.build(vararg args: String): BuildResult = GradleBuilder.build(rootDir, *args)