package com.fueledbycaffeine.spotlight.buildscript.fixtures

import com.fueledbycaffeine.spotlight.buildscript.BUILDSCRIPTS
import com.fueledbycaffeine.spotlight.buildscript.GRADLE_SCRIPT
import com.fueledbycaffeine.spotlight.buildscript.GRADLE_SCRIPT_KOTLIN
import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.createDirectories
import kotlin.io.path.name
import kotlin.io.path.outputStream

/**
 * Test fixture for creating benchmark projects from the project-skeleton.zip.
 */
public class ProjectFixture(
  public val buildFileType: String = "kts",
) {
  public lateinit var tempDir: Path
    private set
  
  public lateinit var root: Path
    private set
  
  public lateinit var originalProjectFiles: Map<Path, ByteArray>
    private set
  
  public lateinit var app: GradlePath
    private set

  /**
   * Sets up the project fixture by extracting and preparing the project skeleton.
   */
  public fun setup() {
    tempDir = Files.createTempDirectory("ProjectFixture")
    val zipStream = javaClass.classLoader.getResourceAsStream("project-skeleton.zip")
      ?: throw IllegalStateException("project-skeleton.zip not found in resources")
    
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

  /**
   * Restores all build files to their original state.
   */
  public fun restoreFiles() {
    originalProjectFiles.forEach { (path, content) ->
      Files.write(path, content)
    }
  }

  /**
   * Converts all project() calls to type-safe accessor syntax.
   * Returns a mapping of accessor names to GradlePath objects for ALL projects in the structure.
   */
  public fun convertToTypeSafeAccessors(): Map<String, GradlePath> {
    // First, build a complete mapping of ALL projects in the structure
    val mapping = mutableMapOf<String, GradlePath>()
    Files.walk(root).use { stream ->
      stream.filter { it.name in BUILDSCRIPTS }.forEach { buildFilePath ->
        val relativePath = buildFilePath.parent.toString().removePrefix(root.toString()).removePrefix("/")
        val gradlePathString = if (relativePath.isEmpty()) ":" else ":${relativePath.replace("/", ":")}"
        val gradlePath = GradlePath(root, gradlePathString)
        val typeSafeAccessor = gradlePath.typeSafeAccessorName
        mapping[typeSafeAccessor] = gradlePath
      }
    }
    
    // Then, convert all project() calls to use type-safe accessors
    Files.walk(root).use { stream ->
      stream.filter { it.name in BUILDSCRIPTS }.forEach { path ->
        val content = String(Files.readAllBytes(path), StandardCharsets.UTF_8)
        val newContent = content.replace("project\\((['\"])(.*?)\\1\\)".toRegex()) {
          val projectPath = it.groupValues[2]
          val gradlePath = GradlePath(root, projectPath)
          val typeSafeAccessor = gradlePath.typeSafeAccessorName
          "projects.$typeSafeAccessor"
        }
        Files.write(path, newContent.toByteArray(StandardCharsets.UTF_8))
      }
    }
    return mapping
  }

  /**
   * Cleans up temporary files.
   */
  public fun teardown() {
    tempDir.toFile().deleteRecursively()
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
