package com.fueledbycaffeine.spotlight.buildscript

import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule.*
import java.io.FileNotFoundException
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.text.RegexOption.MULTILINE

public data class BuildFile(public val project: GradlePath) {
  public fun parseDependencies(
    rules: Set<ImplicitDependencyRule> = emptySet(),
  ): Set<GradlePath> = parseBuildFile(project, rules)
}

private val PROJECT_DEP_PATTERN = Regex("^(?:\\s+)?(\\w+)\\W+project\\([\"'](.*)[\"']\\)", MULTILINE)
private val TYPESAFE_PROJECT_DEP_PATTERN = Regex("^(?!\\s*//).*?(?:^|\\W)(\\w+)?\\(?\\s*(projects\\.[\\w.]+)", MULTILINE)

internal fun parseBuildFile(
  project: GradlePath,
  rules: Set<ImplicitDependencyRule>,
): Set<GradlePath> {
  val buildscriptContents = project.buildFilePath.readText()
  val directDependencies = PROJECT_DEP_PATTERN.findAll(buildscriptContents)
    .map { matchResult ->
      val (_, projectPath) = matchResult.destructured
      GradlePath(project.root, projectPath)
    }
    .toSet()

  val typeSafeProjectAccessorsRule = rules.filterIsInstance<TypeSafeProjectAccessorRule>().firstOrNull()
  val typeSafeProjectDependencies = if (typeSafeProjectAccessorsRule != null) {
    TYPESAFE_PROJECT_DEP_PATTERN.findAll(buildscriptContents)
      .map { matchResult ->
        val (_, typeSafeAccessor) = matchResult.destructured
        val cleanTypeSafeAccessor = typeSafeAccessor.removeTypeSafeAccessorJunk()
          .removePrefix("${typeSafeProjectAccessorsRule.rootProjectName}.")
        typeSafeProjectAccessorsRule.typeSafeAccessorMap[cleanTypeSafeAccessor]
          ?: throw FileNotFoundException(
            "Could not find project buildscript for type-safe project accessor \"$typeSafeAccessor\" " +
              "referenced by ${project.path}"
          )
      }
      .toSet()
  } else {
    emptySet()
  }

  val implicitDependencies = rules
    .filter { rule ->
      when (rule) {
        is BuildscriptMatchRule -> rule.pattern.find(buildscriptContents) != null
        is ProjectPathMatchRule -> rule.pattern.matches(project.path)
        is TypeSafeProjectAccessorRule -> true
      }
    }
    .flatMap { rule -> rule.includedProjects }

  val ktsImplicitDependencies = computeImplicitNestingDependencies(project)

  return directDependencies + typeSafeProjectDependencies + implicitDependencies + ktsImplicitDependencies
}

/**
 * Gradle automatically includes intermediate parent projects of nested projects.
 *
 * ```
 * :libs:foo:impl -> :libs:foo
 * ```
 *
 * For Groovy, this is sort of fine because it doesn't try to compile them
 * until they're executed. For Kotlin Gradle script, this is a problem
 * because Gradle eagerly compiles them during configuration. So, we must
 * treat them as implicit dependencies to work.
 */
private fun computeImplicitNestingDependencies(project: GradlePath): Set<GradlePath> {
  // Start with the grandparent directory of the build file
  // libs/foo/impl/build.gradle.kts -> libs/foo
  // Then iterate up to the root directory
  val parentProjectDir = project.buildFilePath.parent.parent

  val rootDir = project.root
  return generateSequence(parentProjectDir) { it.parent.takeUnless { it == rootDir } }
    .filter { it.resolve("build.gradle.kts").exists() }
    .mapTo(mutableSetOf()) { it.gradlePathRelativeTo(rootDir) }
}

private fun String.removeTypeSafeAccessorJunk(): String =
  this.removePrefix("projects.")
    .removeSuffix(".dependencyProject") // deprecated in gradle, to be removed in 9.0
    .removeSuffix(".path") // GeneratedClassCompilationException if you try to name a project `:path` lol