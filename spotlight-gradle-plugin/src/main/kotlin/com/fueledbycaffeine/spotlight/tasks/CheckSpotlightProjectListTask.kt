package com.fueledbycaffeine.spotlight.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Has no outputs")
@UntrackedTask(because = "Has no outputs")
public abstract class CheckSpotlightProjectListTask : DefaultTask() {
  public companion object {
    public const val NAME: String = "checkAllProjectsList"
  }

  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  internal abstract val projectsFile: RegularFileProperty

  @TaskAction
  internal fun checkSorted() {
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
}
