package com.fueledbycaffeine.spotlight.buildscript.jmh

import com.fueledbycaffeine.spotlight.buildscript.BUILDSCRIPTS
import com.fueledbycaffeine.spotlight.buildscript.GRADLE_SCRIPT
import com.fueledbycaffeine.spotlight.buildscript.GRADLE_SCRIPT_KOTLIN
import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.TypeSafeAccessorInference
import com.fueledbycaffeine.spotlight.buildscript.graph.BreadthFirstSearch
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ParsingConfiguration
import com.fueledbycaffeine.spotlight.buildscript.graph.TypeSafeProjectAccessorRule
import com.fueledbycaffeine.spotlight.buildscript.parser.GroovyAstParser
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
import org.openjdk.jmh.infra.Blackhole
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import kotlin.collections.iterator
import kotlin.io.path.createDirectories
import kotlin.io.path.name
import kotlin.io.path.outputStream

@BenchmarkMode(Mode.AverageTime)
@State(Scope.Benchmark)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.MICROSECONDS)
@Measurement(iterations = 30, time = 1, timeUnit = TimeUnit.MICROSECONDS)
@Fork(1)
@Suppress("unused")
open class BreadthFirstSearchBenchmark {
  @Param("REGEX", "AST")
  lateinit var parsingMode: String
  
  @Param("kts", "groovy")
  lateinit var buildFileType: String
  
  @Param("false", "true")
  var useTypeSafeAccessors: Boolean = false
  
  private lateinit var tempDir: Path
  private lateinit var root: Path
  private lateinit var originalProjectFiles: Map<Path, ByteArray>
  private var typeSafeAccessorMapping: Map<String, GradlePath> = emptyMap()
  private lateinit var app: GradlePath
  private lateinit var buildscriptMatchRules: Set<ImplicitDependencyRule>
  
  private val config: ParsingConfiguration
    get() = ParsingConfiguration.valueOf(parsingMode)

  @Setup
  fun setup() {
    tempDir = Files.createTempDirectory(javaClass.simpleName)
    val zipStream = javaClass.classLoader.getResourceAsStream("project-skeleton.zip")!!
    val tempZipFile = Files.createTempFile(tempDir, "project-skeleton", ".zip").toFile()
    zipStream.use { input ->
      tempZipFile.outputStream().use { output ->
        input.copyTo(output)
      }
    }

    val projectDir = tempDir.resolve("project")
    projectDir.createDirectories()
    unzip(tempZipFile, projectDir)
    root = projectDir.resolve("project-skeleton")
    if (buildFileType == "groovy") {
      renameKtsToGroovy(root)
    }
    app = GradlePath(root, ":app:app")

    buildscriptMatchRules = (0..10).map {
      ImplicitDependencyRule.BuildscriptMatchRule(
        "id(\"plugins.conventions.$it\")",
        setOf(app),
      )
    }.toSet()

    // Save original project files for restoration
    originalProjectFiles = mutableMapOf<Path, ByteArray>().apply {
      Files.walk(root).use { stream ->
        stream.filter { it.name in BUILDSCRIPTS }.forEach { path ->
          put(path, Files.readAllBytes(path))
        }
      }
    }

    tempZipFile.delete()
  }

  @Setup(Level.Iteration)
  fun setupIteration() {
    // Clear parser caches to ensure fresh parsing
    GroovyAstParser.clearCache()
    
    // Restore original files before each iteration
    originalProjectFiles.forEach { (path, content) ->
      Files.write(path, content)
    }
    
    // Convert to type-safe accessors only when needed
    if (useTypeSafeAccessors) {
      typeSafeAccessorMapping = convertToTypeSafeAccessors(root)
    }
  }

  @TearDown
  fun teardown() {
    tempDir.toFile().deleteRecursively()
  }

  @Benchmark
  fun BreadthFirstSearch_flatten(blackhole: Blackhole) {
    val rules = if (useTypeSafeAccessors) {
      setOf(TypeSafeProjectAccessorRule("", typeSafeAccessorMapping))
    } else {
      emptySet()
    }
    val result = BreadthFirstSearch.flatten(listOf(app), rules, config)
    check(result.size == originalProjectFiles.size) { 
      "expected ${originalProjectFiles.size} projects in result set but there were ${result.size}"
    }
    blackhole.consume(result)
  }

  private fun convertToTypeSafeAccessors(root: Path): Map<String, GradlePath> {
    val mapping = mutableMapOf<String, GradlePath>()
    Files.walk(root).use { stream ->
      stream.filter { it.name in BUILDSCRIPTS }.forEach { path ->
        val content = String(Files.readAllBytes(path), StandardCharsets.UTF_8)
        val newContent = content.replace("project\\((['\"])(.*?)\\1\\)".toRegex()) {
          val projectPath = it.groupValues[2]
          val gradlePath = GradlePath(root, projectPath)
          // Use the same type-safe accessor name logic as GradlePath
          val typeSafeAccessor = gradlePath.typeSafeAccessorName
          // Map accessor name (without "projects.") to GradlePath
          mapping[typeSafeAccessor] = gradlePath
          "projects.$typeSafeAccessor"
        }
        Files.write(path, newContent.toByteArray(StandardCharsets.UTF_8))
      }
    }
    return mapping
  }

  private fun renameKtsToGroovy(projectDir: Path) {
    Files.walk(projectDir).use { stream ->
      stream.filter { it.name == GRADLE_SCRIPT_KOTLIN }.forEach { ktsFile ->
        val groovyFile = ktsFile.parent.resolve(GRADLE_SCRIPT)
        Files.move(ktsFile, groovyFile)
      }
    }
  }

  private fun unzip(zipFile: File, destination: Path) {
    ZipFile(zipFile).use { zip ->
      for (entry in zip.entries()) {
        val path = destination.resolve(entry.name).normalize()
        if (!path.startsWith(destination)) {
          throw SecurityException("Invalid zip entry")
        }
        if (entry.isDirectory) {
          path.createDirectories()
        } else {
          path.parent.createDirectories()
          zip.getInputStream(entry).use { input ->
            path.outputStream().use { output ->
              input.copyTo(output)
            }
          }
        }
      }
    }
  }
}
