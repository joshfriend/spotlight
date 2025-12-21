package com.fueledbycaffeine.spotlight.buildscript.parser

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.graph.DependencyRule
import com.fueledbycaffeine.spotlight.buildscript.graph.TypeSafeProjectAccessorRule
import kotlin.io.path.readText

// Regex patterns for parsing
private val PROJECT_DEP_PATTERN = Regex("""project\s*\((['"])(.*?)\1\)""")
private val TYPESAFE_PROJECT_DEP_PATTERN = Regex("""\b(projects\.[\w.]+)\b""")
private val STRING_LITERAL_PATTERN = Regex("\"[^\"]*\"|'[^']*'")
private val BLOCK_COMMENT_PATTERN = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)

/**
 * Fast regex-based parser for extracting dependencies from build scripts.
 * This is the legacy parsing approach that uses simple regex patterns.
 */
public object RegexBuildScriptParser : BuildScriptParser {
  override fun parse(
    project: GradlePath,
    rules: Set<DependencyRule>,
  ): Set<GradlePath> {
    val fileContent = project.buildFilePath.readText()
    val contentWithoutBlockComments = BLOCK_COMMENT_PATTERN.replace(fileContent, "")
    val buildscriptContents = contentWithoutBlockComments.lines()
      .map { it.substringBefore("//") }

    return computeDirectDependencies(project, buildscriptContents) +
      computeTypeSafeProjectDependencies(project, buildscriptContents, rules) +
      computeImplicitParentProjects(project)
  }

  private fun computeDirectDependencies(project: GradlePath, buildscriptContents: List<String>): Set<GradlePath> {
    return buildscriptContents
      .flatMap { PROJECT_DEP_PATTERN.findAll(it) }
      .map { matchResult ->
        val (_, projectPath) = matchResult.destructured
        GradlePath(project.root, projectPath)
      }
      .toSet()
  }

  private fun computeTypeSafeProjectDependencies(
    project: GradlePath,
    buildscriptContents: List<String>,
    rules: Set<DependencyRule>,
  ): Set<GradlePath> {
    val rule = rules.filterIsInstance<TypeSafeProjectAccessorRule>().firstOrNull()
      ?: return emptySet()

    return buildscriptContents
      .asSequence()
      .map { it.replace(STRING_LITERAL_PATTERN, "") }
      .flatMap { line -> TYPESAFE_PROJECT_DEP_PATTERN.findAll(line) }
      .map { matchResult ->
        val (typeSafeAccessor) = matchResult.destructured
        val cleanTypeSafeAccessor = typeSafeAccessor.removeTypeSafeAccessorJunk(rule.rootProjectAccessor)
        rule.typeSafeAccessorMap[cleanTypeSafeAccessor] ?: throw NoSuchElementException(
          "Could not find project mapping for type-safe project accessor \"$typeSafeAccessor\" " +
            "referenced by ${project.path}"
        )
      }
      .toSet()
  }
}
