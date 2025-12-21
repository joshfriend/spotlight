package com.fueledbycaffeine.spotlight.buildscript.parser.groovy

import com.fueledbycaffeine.spotlight.buildscript.GRADLE_SCRIPT
import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import com.fueledbycaffeine.spotlight.buildscript.graph.DependencyRule
import com.fueledbycaffeine.spotlight.buildscript.graph.ImplicitDependencyRule
import com.fueledbycaffeine.spotlight.buildscript.graph.TypeSafeProjectAccessorRule
import com.fueledbycaffeine.spotlight.buildscript.parser.BuildScriptParser
import com.fueledbycaffeine.spotlight.buildscript.parser.computeImplicitParentProjects
import com.fueledbycaffeine.spotlight.buildscript.parser.removeTypeSafeAccessorJunk
import org.codehaus.groovy.ast.CodeVisitorSupport
import org.codehaus.groovy.ast.builder.AstBuilder
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.control.CompilePhase
import kotlin.io.path.exists
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
    // Check if Groovy build file exists - file I/O is OK here because we're inside parse() which is called from ValueSource
    val groovyBuildFile = project.projectDir.resolve(GRADLE_SCRIPT)
    if (!groovyBuildFile.exists()) {
      // No Groovy build file, return empty (Kotlin parser or Regex parser will handle it)
      return emptySet()
    }

    val dependencies = mutableSetOf<GradlePath>()
    val fileContent = groovyBuildFile.readText()
    // Use AstBuilder with CONVERSION phase - fastest reliable AST generation
    val nodes = AstBuilder().buildFromString(CompilePhase.CONVERSION, true, fileContent)

    // Pre-filter rules to avoid repeated instanceof checks in visitor
    val typeSafeRule = rules.filterIsInstance<TypeSafeProjectAccessorRule>().firstOrNull()
    val buildscriptRules = rules.filterIsInstance<ImplicitDependencyRule.BuildscriptMatchRule>()

    // Only create visitor if there's work to do
    if (nodes.isNotEmpty()) {
      val visitor = createVisitor(project, dependencies, typeSafeRule, buildscriptRules)
      nodes.forEach { node -> node.visit(visitor) }
    }
    
    return dependencies + computeImplicitParentProjects(project)
  }
  
  private fun createVisitor(
    project: GradlePath,
    dependencies: MutableSet<GradlePath>,
    typeSafeRule: TypeSafeProjectAccessorRule?,
    buildscriptRules: List<ImplicitDependencyRule.BuildscriptMatchRule>,
  ): CodeVisitorSupport {
    return object : CodeVisitorSupport() {
      override fun visitMethodCallExpression(call: MethodCallExpression) {
        handleMethodCall(call, project, dependencies, typeSafeRule, buildscriptRules)
        super.visitMethodCallExpression(call)
      }
      
      override fun visitPropertyExpression(expression: PropertyExpression) {
        if (typeSafeRule != null) {
          handlePropertyExpression(expression, dependencies, typeSafeRule)
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
    if (typeSafeRule != null) {
      val accessor = extractTypeSafeAccessor(call)
      if (accessor != null) {
        val cleanAccessor = accessor.removeTypeSafeAccessorJunk(typeSafeRule.rootProjectAccessor)
        typeSafeRule.typeSafeAccessorMap[cleanAccessor]?.let { dependencies.add(it) }
          ?: throw NoSuchElementException("Unknown type-safe project accessor: $cleanAccessor")
      }
    }

    if (buildscriptRules.isNotEmpty()) {
      val callText by lazy { call.text }  // Lazy evaluation
      val implicitDependencies = buildscriptRules
        .filter { rule -> rule.regex.containsMatchIn(callText) }
        .flatMap { rule -> rule.includedProjects }
      dependencies.addAll(implicitDependencies)
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
    return when (val objectExpr = expr.objectExpression) {
      is PropertyExpression -> buildPropertyAccessorChain(objectExpr)
      else -> null
    }
  }
  
  private fun buildPropertyAccessorChain(prop: PropertyExpression): String? {
    // Build chain from right to left, avoiding list allocations
    val parts = StringBuilder()
    var current: Expression = prop
    
    // First pass: collect parts
    val stack = ArrayDeque<String>(8)  // Pre-allocate for typical depth
    while (current is PropertyExpression) {
      val propName = current.propertyAsString ?: return null
      stack.addFirst(propName)
      current = current.objectExpression
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
