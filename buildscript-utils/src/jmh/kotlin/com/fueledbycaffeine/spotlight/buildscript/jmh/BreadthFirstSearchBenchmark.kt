package com.fueledbycaffeine.spotlight.buildscript.jmh

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.TypeSafeAccessorInference
import com.fueledbycaffeine.spotlight.buildscript.graph.BreadthFirstSearch
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule
import com.fueledbycaffeine.spotlight.buildscript.graph.TypeSafeProjectAccessorRule
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
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
import kotlin.io.path.outputStream

@BenchmarkMode(Mode.AverageTime)
@State(Scope.Benchmark)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.MICROSECONDS)
@Measurement(iterations = 100, time = 1, timeUnit = TimeUnit.MICROSECONDS)
@Fork(1)
@Suppress("unused")
open class BreadthFirstSearchBenchmark {
  private lateinit var tempDir: Path
  private lateinit var app: GradlePath
  private lateinit var typeSafeApp: GradlePath
  private lateinit var typeSafeAccessorRule: TypeSafeProjectAccessorRule
  private lateinit var buildscriptMatchRules: Set<ImplicitDependencyRule>

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

    val regularProjectDir = tempDir.resolve("regular")
    regularProjectDir.createDirectories()
    unzip(tempZipFile, regularProjectDir)
    val root = regularProjectDir.resolve("project-skeleton")
    app = GradlePath(root, ":app:app")

    buildscriptMatchRules = (0..10).map {
      ImplicitDependencyRule.BuildscriptMatchRule(
        "id 'plugins.conventions.$it'",
        setOf(app),
      )
    }.toSet()

    val typeSafeProjectDir = tempDir.resolve("typesafe")
    typeSafeProjectDir.createDirectories()
    unzip(tempZipFile, typeSafeProjectDir)
    val typeSafeRoot = typeSafeProjectDir.resolve("project-skeleton")
    val mapping = convertToTypeSafeAccessors(typeSafeRoot)
      .mapValues { GradlePath(typeSafeRoot, it.value) }
    typeSafeApp = GradlePath(typeSafeRoot, ":app:app")

    typeSafeAccessorRule = TypeSafeProjectAccessorRule("test", mapping)

    tempZipFile.delete()
  }

  @TearDown
  fun teardown() {
    tempDir.toFile().deleteRecursively()
  }

  @Benchmark
  fun BreadthFirstSearch_flatten(blackhole: Blackhole) {
    val result = BreadthFirstSearch.flatten(listOf(app))
    blackhole.consume(result)
  }

  @Benchmark
  fun BreadthFirstSearch_flatten_10_BuildscriptMatchRule(blackhole: Blackhole) {
    val result = BreadthFirstSearch.flatten(listOf(app), buildscriptMatchRules)
    blackhole.consume(result)
  }

  @Benchmark
  fun BreadthFirstSearch_flatten_typeSafe(blackhole: Blackhole) {
    val result = BreadthFirstSearch.flatten(listOf(typeSafeApp), setOf(typeSafeAccessorRule))
    blackhole.consume(result)
  }

  private fun convertToTypeSafeAccessors(projectDir: Path): Map<String, String> {
    val mapping = mutableMapOf<String, String>()
    Files.walk(projectDir).use { stream ->
      stream.filter { it.fileName.toString().endsWith(".gradle") }.forEach { path ->
        val content = String(Files.readAllBytes(path), StandardCharsets.UTF_8)
        val newContent = content.replace("project\\((['\"])(.*?)\\1\\)".toRegex()) {
          val projectPath = it.groupValues[2]
          mapping.computeIfAbsent(projectPath) {
            "projects.${projectPath.removePrefix(":").replace(':', '.')}"
          }
        }
        Files.write(path, newContent.toByteArray(StandardCharsets.UTF_8))
      }
    }
    return mapping
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
