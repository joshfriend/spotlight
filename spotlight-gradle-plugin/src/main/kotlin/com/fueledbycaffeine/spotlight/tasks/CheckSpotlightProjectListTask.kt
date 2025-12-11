package com.fueledbycaffeine.spotlight.tasks

import com.fueledbycaffeine.spotlight.buildscript.SETTINGS_SCRIPT
import com.fueledbycaffeine.spotlight.buildscript.SETTINGS_SCRIPT_KOTLIN
import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList
import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Has no outputs")
public abstract class CheckSpotlightProjectListTask : DefaultTask() {
  public companion object {
    public const val NAME: String = "checkAllProjectsList"
    private val INCLUDE_PATTERN = Regex("""^\s*include[ \t(]""")
  }

  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  internal abstract val projectsFile: RegularFileProperty

  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  internal abstract val rootDirectory: DirectoryProperty

  @TaskAction
  internal fun action() {
    checkSorted()
    checkForIncludeStatements()
  }

  private fun checkSorted() {
    val file = projectsFile.asFile.get()
    val current = file.readLines()

    if (current != current.sorted()) {
      throw InvalidUserDataException(
        """
        Spotlight's list of all projects is not sorted: ${file.path}
        Run :${SortSpotlightProjectsListTask.NAME} to fix it.
        """.trimIndent().trim()
      )
    }
  }

  private fun checkForIncludeStatements() {
    val rootDir = rootDirectory.asFile.get()

    val settingsFile = rootDir.resolve(SETTINGS_SCRIPT).takeIf { it.exists() }
      ?: rootDir.resolve(SETTINGS_SCRIPT_KOTLIN).takeIf { it.exists() }
      ?: return

    val includeLines = settingsFile.readLines()
      .withIndex()
      .filter { (_, line) -> INCLUDE_PATTERN.containsMatchIn(line) }

    if (includeLines.isNotEmpty()) {
      val errorMessage = buildString {
        appendLine("Found 'include' statements in ${settingsFile.name}:")
        appendLine()
        includeLines.forEach { (index, line) ->
          // Format as "filename:line: message" for IDE parsing
          appendLine("  ${settingsFile.name}:${index + 1}: ${line.trim()}")
        }
        appendLine()
        appendLine("Spotlight manages project inclusion automatically.")
        appendLine(
          "Please remove these 'include' statements and add the project paths to " +
            "${SpotlightProjectList.ALL_PROJECTS_LOCATION} instead."
        )
      }
      throw InvalidUserDataException(errorMessage)
    }
  }
}
