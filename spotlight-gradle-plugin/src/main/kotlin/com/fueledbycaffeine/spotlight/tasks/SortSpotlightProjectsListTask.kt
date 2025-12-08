package com.fueledbycaffeine.spotlight.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Has no outputs")
@UntrackedTask(because = "Has no outputs")
public abstract class SortSpotlightProjectsListTask : DefaultTask() {
  public companion object {
    public const val NAME: String = "sortAllProjectsList"
  }

  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  internal abstract val projectList: RegularFileProperty

  @TaskAction
  internal fun sort() {
    projectList.asFile.get().apply {
      writeText(readLines().sorted().joinToString("\n"))
    }
  }
}
