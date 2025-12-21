package com.fueledbycaffeine.spotlight.buildscript.parser.groovy

import com.fueledbycaffeine.spotlight.buildscript.GRADLE_SCRIPT
import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.graph.DependencyRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule
import com.fueledbycaffeine.spotlight.buildscript.graph.TypeSafeProjectAccessorRule
import com.fueledbycaffeine.spotlight.buildscript.parser.BuildScriptParser
import com.fueledbycaffeine.spotlight.buildscript.parser.computeImplicitParentProjects
import com.fueledbycaffeine.spotlight.buildscript.parser.removeTypeSafeAccessorJunk
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
import kotlin.io.path.name
import kotlin.io.path.readText

/**
 * AST-based parser for Groovy build scripts.
 * Provides more accurate parsing than regex by understanding the actual structure of the code.
 */
public object GroovyAstParser : BuildScriptParser {

  override fun parse(
    project: GradlePath,
    rules: Set<DependencyRule>,
  ): Set<GradlePath> {
    check(project.buildFilePath.name == GRADLE_SCRIPT) {
      "Not a Groovy build script: ${project.buildFilePath}"
    }

    val dependencies = mutableSetOf<GradlePath>()
    
    try {
      val fileContent = project.buildFilePath.readText()
      // Use AstBuilder with CONVERSION phase - fastest reliable AST generation
      val nodes = AstBuilder().buildFromString(CompilePhase.CONVERSION, true, fileContent)
      
      // Pre-filter rules to avoid repeated instanceof checks in visitor
      val typeSafeRule = rules.find { it is TypeSafeProjectAccessorRule } as? TypeSafeProjectAccessorRule
      val buildscriptRules = rules.filterIsInstance<ImplicitDependencyRule.BuildscriptMatchRule>()
      
      // Only create visitor if there's work to do
      if (nodes.isNotEmpty()) {
        val visitor = createOptimizedVisitor(project, dependencies, typeSafeRule, buildscriptRules)
        nodes.forEach { node -> node.visit(visitor) }
      }
    } catch (e: Exception) {
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
    dependencies: MutableSet<GradlePath>,
    typeSafeRule: TypeSafeProjectAccessorRule?,
    buildscriptRules: List<ImplicitDependencyRule.BuildscriptMatchRule>,
  ): CodeVisitorSupport {
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
        val expressions = args.expressions
        if (expressions.isNotEmpty()) {
          val firstArg = expressions[0]
          if (firstArg is ConstantExpression) {
            val value = firstArg.value
            if (value is String) {
              dependencies.add(GradlePath(project.root, value))
              return  // Early return to avoid checking other rules
            }
          }
        }
      }
    }
    
    // Check for type-safe project accessors (projects.foo.bar)
    if (hasTypeSafeRule && typeSafeRule != null) {
      val accessor = extractTypeSafeAccessor(call)
      if (accessor != null) {
        val cleanAccessor = accessor.removeTypeSafeAccessorJunk(typeSafeRule.rootProjectAccessor)
        typeSafeRule.typeSafeAccessorMap[cleanAccessor]?.let { dependencies.add(it) }
          ?: throw NoSuchElementException("Unknown type-safe project accessor: $cleanAccessor")
      }
    }
    
    // Check implicit dependency rules - only compute text if needed
    if (hasBuildscriptRules) {
      val callText by lazy { call.text }  // Lazy evaluation
      for (rule in buildscriptRules) {
        if (rule.regex.containsMatchIn(callText)) {
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
      // Only add if it exists - this handles both complete chains and ignores intermediate partial chains
      // For example, when parsing "projects.domain.forecastLogin", we visit both:
      // - "projects.domain.forecastLogin" (complete, exists in map) -> add it
      // - "projects.domain" (intermediate, doesn't exist) -> ignore it
      typeSafeRule.typeSafeAccessorMap[cleanAccessor]?.let { dependencies.add(it) }
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
    // Build chain from right to left, avoiding list allocations
    val parts = StringBuilder()
    var current: Expression = prop
    var depth = 0
    
    // First pass: collect parts
    val stack = ArrayDeque<String>(8)  // Pre-allocate for typical depth
    while (current is PropertyExpression) {
      val propName = current.propertyAsString ?: return null
      stack.addFirst(propName)
      current = current.objectExpression
      depth++
    }
    
    // Check if it starts with "projects"
    if (current is VariableExpression && current.name == "projects") {
      parts.append("projects")
      for (part in stack) {
        parts.append('.').append(part)
      }
      return parts.toString()
    }
    
    return null
  }
}
