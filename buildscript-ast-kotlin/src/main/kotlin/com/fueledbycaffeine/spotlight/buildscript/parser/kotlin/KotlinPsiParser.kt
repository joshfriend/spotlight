package com.fueledbycaffeine.spotlight.buildscript.parser.kotlin

import com.fueledbycaffeine.spotlight.buildscript.GRADLE_SCRIPT_KOTLIN
import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.graph.DependencyRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule
import com.fueledbycaffeine.spotlight.buildscript.graph.TypeSafeProjectAccessorRule
import com.fueledbycaffeine.spotlight.buildscript.parser.BuildscriptParser
import com.fueledbycaffeine.spotlight.buildscript.parser.internal.computeImplicitParentProjects
import com.fueledbycaffeine.spotlight.buildscript.parser.internal.removeTypeSafeAccessorJunk
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * PSI-based parser for Kotlin build scripts (build.gradle.kts).
 * Provides accurate parsing by using the Kotlin compiler's PSI (Program Structure Interface).
 */
public object KotlinPsiParser : BuildscriptParser {
  private val disposable = Disposer.newDisposable(KotlinPsiParser::class.java.name)

  // Using createForProduction which is part of K1 API (deprecated in favor of K2 Analysis API).
  @OptIn(K1Deprecation::class)
  private val environment by lazy {
    KotlinCoreEnvironment.createForProduction(
      disposable,
      CompilerConfiguration(),
      EnvironmentConfigFiles.JVM_CONFIG_FILES
    )
  }

  private val psiFactory: KtPsiFactory by lazy {
    KtPsiFactory(environment.project, markGenerated = false)
  }
  
  override fun parse(
    project: GradlePath,
    rules: Set<DependencyRule>,
  ): Set<GradlePath> {
    // Check if Kotlin build file exists - file I/O is OK here because we're inside parse() which is called from ValueSource
    val kotlinBuildFile = project.projectDir.resolve(GRADLE_SCRIPT_KOTLIN)
    if (!kotlinBuildFile.exists()) {
      // No Kotlin build file, return empty (Groovy parser or Regex parser will handle it)
      return emptySet()
    }

    val dependencies = mutableSetOf<GradlePath>()
    val fileContent = kotlinBuildFile.readText()
    val ktFile = parseKotlinFile(fileContent, kotlinBuildFile.fileName.toString())

    ktFile.accept(object : KtTreeVisitorVoid() {
      override fun visitCallExpression(expression: KtCallExpression) {
        handleCallExpression(expression, project, rules, dependencies)
        super.visitCallExpression(expression)
      }

      override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
        handleDotQualifiedExpression(expression, rules, dependencies)
        super.visitDotQualifiedExpression(expression)
      }
    })
    
    return dependencies + computeImplicitParentProjects(project)
  }
  
  private fun parseKotlinFile(content: String, fileName: String): KtFile {
    return psiFactory.createFile(fileName, content)
  }
  
  private fun handleCallExpression(
    expression: KtCallExpression,
    project: GradlePath,
    rules: Set<DependencyRule>,
    dependencies: MutableSet<GradlePath>,
  ) {
    val callName = expression.calleeExpression?.text ?: return
    
    // Check for project(":path") calls
    if (callName == "project") {
      val args = expression.valueArguments
      if (args.isNotEmpty()) {
        val firstArg = args[0].getArgumentExpression()
        if (firstArg is KtStringTemplateExpression) {
          val projectPath = firstArg.entries.joinToString("") { it.text }
          dependencies.add(GradlePath(project.root, projectPath))
        }
      }
    }
    
    // Check for type-safe project accessors in call arguments (e.g., implementation(projects.foo.bar))
    val typeSafeRule = rules.filterIsInstance<TypeSafeProjectAccessorRule>().firstOrNull()
    if (typeSafeRule != null) {
      // Check arguments for type-safe accessors
      expression.valueArguments.forEach { arg ->
        val argExpr = arg.getArgumentExpression()
        if (argExpr is KtDotQualifiedExpression) {
          val accessor = extractTypeSafeAccessor(argExpr)
          if (accessor != null) {
            val cleanAccessor = accessor.removeTypeSafeAccessorJunk(typeSafeRule.rootProjectAccessor)
            val resolvedProject = typeSafeRule.typeSafeAccessorMap[cleanAccessor]
              ?: throw NoSuchElementException("Unknown type-safe project accessor: $cleanAccessor")
            dependencies.add(resolvedProject)
          }
        }
      }
      
      // Also check if this call expression itself is part of a type-safe accessor chain
      val parent = expression.parent
      if (parent is KtDotQualifiedExpression) {
        val accessor = extractTypeSafeAccessor(parent)
        if (accessor != null) {
          val cleanAccessor = accessor.removeTypeSafeAccessorJunk(typeSafeRule.rootProjectAccessor)
          val resolvedProject = typeSafeRule.typeSafeAccessorMap[cleanAccessor]
            ?: throw NoSuchElementException("Unknown type-safe project accessor: $cleanAccessor")
          dependencies.add(resolvedProject)
        }
      }
    }
    
    // Check implicit dependency rules
    val buildscriptRules = rules.filterIsInstance<ImplicitDependencyRule.BuildscriptMatchRule>()
    buildscriptRules.forEach { rule ->
      val expressionText = expression.text
      if (expressionText.contains(rule.regex)) {
        dependencies.addAll(rule.includedProjects)
      }
    }
  }
  
  private fun handleDotQualifiedExpression(
    expression: KtDotQualifiedExpression,
    rules: Set<DependencyRule>,
    dependencies: MutableSet<GradlePath>,
  ) {
    val typeSafeRule = rules.filterIsInstance<TypeSafeProjectAccessorRule>().firstOrNull()
      ?: return
    
    val accessor = extractTypeSafeAccessor(expression)
    if (accessor != null) {
      val cleanAccessor = accessor.removeTypeSafeAccessorJunk(typeSafeRule.rootProjectAccessor)
      typeSafeRule.typeSafeAccessorMap[cleanAccessor]?.let { dependencies.add(it) }
    }
  }
  
  private fun extractTypeSafeAccessor(dotExpr: KtDotQualifiedExpression): String? {
    // Build the full accessor chain from the expression
    val fullText = dotExpr.text
    
    // Check if it starts with "projects."
    if (fullText.startsWith("projects.")) {
      return fullText
    }
    
    return null
  }
}
