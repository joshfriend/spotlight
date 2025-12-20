package com.fueledbycaffeine.spotlight.buildscript.parser

import com.fueledbycaffeine.spotlight.buildscript.GRADLE_SCRIPT
import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.graph.DependencyRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule
import com.fueledbycaffeine.spotlight.buildscript.graph.TypeSafeProjectAccessorRule
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.CodeVisitorSupport
import org.codehaus.groovy.ast.builder.AstBuilder
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.CompilerConfiguration
import java.nio.file.Path
import java.text.ParseException
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.name
import kotlin.io.path.readText

/**
 * AST-based parser for Groovy build scripts.
 * Provides more accurate parsing than regex by understanding the actual structure of the code.
 */
public object GroovyAstParser : BuildScriptParser {
  // Cache parsed AST nodes by file path and modification time
  private data class CacheKey(val path: Path, val lastModified: Long)
  private val astCache = ConcurrentHashMap<CacheKey, List<ASTNode>>()
  
  // Aggressive compiler configuration - minimize all work
  private val compilerConfig = CompilerConfiguration().apply {
    optimizationOptions = mapOf(
      "indy" to false,      // No invokedynamic
      "int" to false,        // No primitive optimizations
      "asmResolving" to false // Skip ASM resolution
    )
    debug = false
    verbose = false
    warningLevel = 0
    targetDirectory = null  // Don't write anything to disk
  }
  private val astBuilder = AstBuilder()

  override fun parse(
    project: GradlePath,
    rules: Set<DependencyRule>,
  ): Set<GradlePath> {
    check(project.buildFilePath.name == GRADLE_SCRIPT) {
      "Not a Groovy build script: ${project.buildFilePath}"
    }

    val dependencies = mutableSetOf<GradlePath>()
    
    try {
      // Check cache first
      val cacheKey = CacheKey(project.buildFilePath, project.buildFilePath.getLastModifiedTime().toMillis())
      val nodes = astCache.getOrPut(cacheKey) {
        val fileContent = project.buildFilePath.readText()
        
        // Use CONVERSION phase for reliable AST
        // statementsOnly=true skips package/import nodes
        astBuilder.buildFromString(CompilePhase.CONVERSION, true, fileContent)
      }
      
      // Optimized visitor with pre-computed rule checks
      val visitor = createOptimizedVisitor(project, rules, dependencies)
      nodes.forEach { node ->
        node.visit(visitor)
      }
    } catch (e: Exception) {
      // If AST parsing fails, return empty and let the fallback handle it
      throw BuildScriptParser.ParserException("Could not parse buildscript ${project.buildFilePath}", e)
    }
    
    // Add implicit dependencies based on project path
    val projectPathRules = rules.filterIsInstance<ImplicitDependencyRule.ProjectPathMatchRule>()
    projectPathRules
      .filter { it.regex.containsMatchIn(project.path) }
      .flatMapTo(dependencies) { it.includedProjects }
    
    return dependencies + computeImplicitParentProjects(project)
  }
  
  private fun createOptimizedVisitor(
    project: GradlePath,
    rules: Set<DependencyRule>,
    dependencies: MutableSet<GradlePath>,
  ): CodeVisitorSupport {
    // Pre-compute rule checks outside visitor for efficiency
    val typeSafeRule = rules.filterIsInstance<TypeSafeProjectAccessorRule>().firstOrNull()
    val buildscriptRules = rules.filterIsInstance<ImplicitDependencyRule.BuildscriptMatchRule>()
    val hasTypeSafeRule = typeSafeRule != null
    val hasBuildscriptRules = buildscriptRules.isNotEmpty()
    
    return object : CodeVisitorSupport() {
      override fun visitMethodCallExpression(call: MethodCallExpression) {
        handleMethodCall(call, project, dependencies, typeSafeRule, buildscriptRules, 
                        hasTypeSafeRule, hasBuildscriptRules)
        super.visitMethodCallExpression(call)
      }
      
      override fun visitPropertyExpression(expression: PropertyExpression) {
        if (hasTypeSafeRule) {
          handlePropertyExpression(expression, dependencies, typeSafeRule!!)
        }
        super.visitPropertyExpression(expression)
      }
    }
  }
  
  @Suppress("UNCHECKED_CAST")
  private fun handleMethodCall(
    call: MethodCallExpression,
    project: GradlePath,
    dependencies: MutableSet<GradlePath>,
    typeSafeRule: TypeSafeProjectAccessorRule?,
    buildscriptRules: List<ImplicitDependencyRule.BuildscriptMatchRule>,
    hasTypeSafeRule: Boolean,
    hasBuildscriptRules: Boolean,
  ) {
    // Check for project(":path") calls
    val methodName = call.methodAsString
    if (methodName == "project") {
      val args = call.arguments
      if (args is ArgumentListExpression) {
        val expressions = args.expressions as List<Expression>
        if (expressions.isNotEmpty()) {
          val firstArg = expressions[0]
          if (firstArg is ConstantExpression && firstArg.value is String) {
            dependencies.add(GradlePath(project.root, firstArg.value as String))
          }
        }
      }
    }
    
    // Check for type-safe project accessors (projects.foo.bar)
    if (hasTypeSafeRule) {
      val accessor = extractTypeSafeAccessor(call)
      if (accessor != null) {
        val cleanAccessor = accessor.removeTypeSafeAccessorJunk(typeSafeRule!!.rootProjectAccessor)
        val project = typeSafeRule.typeSafeAccessorMap[cleanAccessor]
          ?: throw NoSuchElementException("Unknown type-safe project accessor: $cleanAccessor")
        dependencies.add(project)
      }
    }
    
    // Check implicit dependency rules
    if (hasBuildscriptRules) {
      val callText = call.text
      buildscriptRules.forEach { rule ->
        if (callText.contains(rule.regex)) {
          dependencies.addAll(rule.includedProjects)
        }
      }
    }
  }
  
  private fun handlePropertyExpression(
    expression: PropertyExpression,
    dependencies: MutableSet<GradlePath>,
    typeSafeRule: TypeSafeProjectAccessorRule,
  ) {
    val accessor = buildPropertyAccessorChain(expression)
    if (accessor != null) {
      val cleanAccessor = accessor.removeTypeSafeAccessorJunk(typeSafeRule.rootProjectAccessor)
      val project = typeSafeRule.typeSafeAccessorMap[cleanAccessor]
        ?: throw NoSuchElementException("Unknown type-safe project accessor: $cleanAccessor")
      dependencies.add(project)
    }
  }
  
  private fun extractTypeSafeAccessor(expr: MethodCallExpression): String? {
    val objectExpr = expr.objectExpression
    if (objectExpr is PropertyExpression) {
      return buildPropertyAccessorChain(objectExpr)
    }
    return null
  }
  
  private fun buildPropertyAccessorChain(prop: PropertyExpression): String? {
    val parts = mutableListOf<String>()
    var current: Expression = prop
    
    while (current is PropertyExpression) {
      val propName = current.propertyAsString ?: return null
      parts.add(0, propName)
      current = current.objectExpression
    }
    
    if (current is VariableExpression) {
      val varName = current.name
      if (varName == "projects") {
        return "projects." + parts.joinToString(".")
      }
    }
    
    return null
  }
  
  /**
   * Clears the AST cache. Useful for benchmarking scenarios where files are modified.
   */
  public fun clearCache() {
    astCache.clear()
  }
}
