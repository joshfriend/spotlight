package com.fueledbycaffeine.spotlight.buildscript.parser.kotlin.jmh

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.fixtures.ProjectFixture
import com.fueledbycaffeine.spotlight.buildscript.graph.BreadthFirstSearch
import com.fueledbycaffeine.spotlight.buildscript.graph.TypeSafeProjectAccessorRule
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown
import org.openjdk.jmh.annotations.Warmup
import java.util.concurrent.TimeUnit

/**
 * Benchmark for the Kotlin PSI parser.
 * This benchmarks only the Kotlin parser performance on Kotlin build scripts.
 */
@BenchmarkMode(Mode.AverageTime)
@State(Scope.Benchmark)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.MICROSECONDS)
@Measurement(iterations = 30, time = 1, timeUnit = TimeUnit.MICROSECONDS)
@Fork(1)
@Suppress("unused")
open class KotlinPsiParserBenchmark {
  @Param("false", "true")
  var useTypeSafeAccessors: Boolean = false
  
  private lateinit var fixture: ProjectFixture
  private var typeSafeAccessorMapping: Map<String, GradlePath> = emptyMap()
  private lateinit var app: GradlePath

  @Setup
  fun setup() {
    fixture = ProjectFixture(buildFileType = "kts")
    fixture.setup()
    app = fixture.app
  }

  @Setup(Level.Invocation)
  fun setupInvocation() {
    // Restore original files before each invocation
    fixture.restoreFiles()
    
    // Convert to type-safe accessors only when needed
    if (useTypeSafeAccessors) {
      typeSafeAccessorMapping = fixture.convertToTypeSafeAccessors()
    }
  }

  @TearDown
  fun teardown() {
    fixture.teardown()
  }

  @Benchmark
  fun parseKotlinBuildScripts() {
    val rules = if (useTypeSafeAccessors) {
      setOf(TypeSafeProjectAccessorRule("", typeSafeAccessorMapping))
    } else {
      emptySet()
    }
    
    val result = BreadthFirstSearch.flatten(listOf(app), rules)
    check(result.size == fixture.originalProjectFiles.size) { 
      "expected ${fixture.originalProjectFiles.size} projects in result set but there were ${result.size}"
    }
  }
}
