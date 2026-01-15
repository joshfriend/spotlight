package com.fueledbycaffeine.spotlight.idea

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList
import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList.Companion.ALL_PROJECTS_LOCATION
import com.fueledbycaffeine.spotlight.buildscript.SpotlightProjectList.Companion.IDE_PROJECTS_LOCATION
import com.fueledbycaffeine.spotlight.buildscript.SpotlightRulesList
import com.fueledbycaffeine.spotlight.buildscript.SpotlightRulesList.Companion.SPOTLIGHT_RULES_LOCATION
import com.fueledbycaffeine.spotlight.buildscript.computeSpotlightRules
import com.fueledbycaffeine.spotlight.buildscript.graph.BreadthFirstSearch
import com.fueledbycaffeine.spotlight.buildscript.models.SpotlightRules
import com.fueledbycaffeine.spotlight.idea.gradle.SpotlightGradleProjectsService
import com.fueledbycaffeine.spotlight.idea.utils.vfsEventsFlow
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
@Service(Service.Level.PROJECT)
class SpotlightProjectService(
  private val project: Project,
  scope: CoroutineScope,
) : Disposable {

  private val rootDir = Path.of(project.basePath!!)
  private val gradleProjectsService = SpotlightGradleProjectsService.getInstance(project)
  private val allProjectsList = SpotlightProjectList.allProjects(rootDir)
  private val _allProjects = MutableStateFlow<Set<GradlePath>>(emptySet())
  val allProjects: StateFlow<Set<GradlePath>> = _allProjects

  private val ideProjectsList = SpotlightProjectList.ideProjects(rootDir, allProjects::value)
  private val _ideProjects = MutableStateFlow<Set<GradlePath>>(emptySet())
  val ideProjects: StateFlow<Set<GradlePath>> = _ideProjects

  private val rulesList = SpotlightRulesList(rootDir)


  private val rules = MutableStateFlow(SpotlightRules.EMPTY)

  init {
    // Connect to the message bus and listen for file ide-projects.txt
    scope.launch {
      // Set initial values
      // Rules triggers a load of ide-projects.txt and all-projects.txt
      readAndEmit(SpotlightFileChangeType.RULES)

      vfsEventsFlow(project)
        .flatMapMerge { events ->
          events.mapNotNull { event ->
            SpotlightFileChangeType.entries.find { event.path.endsWith(it.suffix) }
          }
            .asFlow()
        }
        .collectLatest { changedFileType ->
          readAndEmit(changedFileType)
        }
    }
  }

  private suspend fun readAndEmit(
    changeType: SpotlightFileChangeType
  ) {
    // VFS CHANGES are delivered on EDT+write; move read off EDT
    withContext(Dispatchers.IO) {
      when (changeType) {
        SpotlightFileChangeType.IDE_PROJECTS -> {
          // When ide-projects.txt changes, the Gradle sync results become stale
          // (they were based on the old configuration). Clear them to fall back to file-based mode.
          gradleProjectsService.clearProjects()

          val paths = ideProjectsList.read()
          val currentRules = rules.value
          val implicitRules = currentRules.implicitRules
          val ruleSet =
            computeSpotlightRules(rootDir, project.name, implicitRules) { allProjects.value }

          // Use default ServiceLoader-based discovery (built-in parsers)
          val allPaths = BreadthFirstSearch.flatten(paths, ruleSet)

          _ideProjects.emit(allPaths)
        }

        SpotlightFileChangeType.ALL_PROJECTS -> {
          val paths = allProjectsList.read()
          _allProjects.emit(paths)
        }

        SpotlightFileChangeType.RULES -> {
          // When rules change, the set of projects included in the next sync might differ.
          // Clear Gradle projects to fall back to file-based mode until next sync.
          gradleProjectsService.clearProjects()

          // Read the rules once and re-read other lists
          rules.emit(rulesList.read())
          // Read ALL_PROJECTS first so IDE_PROJECTS can resolve glob patterns against it
          readAndEmit(SpotlightFileChangeType.ALL_PROJECTS)
          readAndEmit(SpotlightFileChangeType.IDE_PROJECTS)
        }
      }
    }
  }

  fun addIdeProjects(paths: Iterable<GradlePath>) {
    ideProjectsList.add(paths)
    refreshIdeProjectsFile()
  }

  fun removeIdeProjects(pathsInBuild: Iterable<GradlePath>) {
    ideProjectsList.remove(pathsInBuild)
    refreshIdeProjectsFile()
  }

  fun isInIdeProjectsFile(path: GradlePath): Boolean {
    return path in ideProjectsList
  }

  private fun ideProjectsFile(): VirtualFile? {
    return VirtualFileManager.getInstance().findFileByNioPath(ideProjectsList.projectList)
  }

  fun openIdeProjectsInEditor() {
    ideProjectsList.ensureFileExists()
    val virtualFile = ideProjectsFile() ?: return
    FileEditorManager.getInstance(project)
      .openFile(virtualFile, true)
    refreshIdeProjectsFile(virtualFile)
    virtualFile.refresh(false, false)
  }

  fun refreshIdeProjectsFile(virtualFile: VirtualFile? = ideProjectsFile()) {
    virtualFile?.refresh(false, false)
  }

  override fun dispose() {

  }

  private enum class SpotlightFileChangeType(val suffix: String) {
    IDE_PROJECTS(IDE_PROJECTS_LOCATION),
    ALL_PROJECTS(ALL_PROJECTS_LOCATION),
    RULES(SPOTLIGHT_RULES_LOCATION),
  }
}

val Project.spotlightService: SpotlightProjectService
  get() = service<SpotlightProjectService>()
