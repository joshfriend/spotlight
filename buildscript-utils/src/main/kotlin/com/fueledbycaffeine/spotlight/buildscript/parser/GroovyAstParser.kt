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
import java.text.ParseException
import kotlin.io.path.name
import kotlin.io.path.readText

/**
 * AST-based parser for Groovy build scripts.
 * Provides more accurate parsing than regex by understanding the actual structure of the code.
 */
public object GroovyAstParser : BuildScriptParser {
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
      val fileContent = project.buildFilePath.readText()
      
      val nodes = astBuilder.buildFromString(CompilePhase.CONVERSION, false, fileContent)
      
      nodes.forEach { node ->
        visitNode(node, project, rules, dependencies)
      }
    } catch (e: Exception) {
      // If AST parsing fails, return empty and let the fallback handle it
      throw BuildScriptParser.ParserException("Could not parse buildscript ${project.buildFilePath}", e)
    }
    
    return dependencies + computeImplicitParentProjects(project)
  }
  
  private fun visitNode(
    node: ASTNode,
    project: GradlePath,
    rules: Set<DependencyRule>,
    dependencies: MutableSet<GradlePath>,
  ) {
    node.visit(object : CodeVisitorSupport() {
      override fun visitMethodCallExpression(call: MethodCallExpression) {
        handleMethodCall(call, project, rules, dependencies)
        super.visitMethodCallExpression(call)
      }
      
      override fun visitPropertyExpression(expression: PropertyExpression) {
        handlePropertyExpression(expression, project, rules, dependencies)
        super.visitPropertyExpression(expression)
      }
    })
  }
  
  @Suppress("UNCHECKED_CAST")
  private fun handleMethodCall(
    call: MethodCallExpression,
    project: GradlePath,
    rules: Set<DependencyRule>,
    dependencies: MutableSet<GradlePath>,
  ) {
    // Check for project(":path") calls
    val methodName = call.methodAsString
    if (methodName == "project") {
      val args = call.arguments
      if (args is ArgumentListExpression) {
        val expressions = args.expressions as List<Expression>
        if (expressions.isNotEmpty()) {
          val firstArg = expressions[0]
          if (firstArg is ConstantExpression) {
            val value = firstArg.value
            if (value is String) {
              dependencies.add(GradlePath(project.root, value))
            }
          }
        }
      }
    }
    
    // Check for type-safe project accessors (projects.foo.bar)
    val typeSafeRule = rules.filterIsInstance<TypeSafeProjectAccessorRule>().firstOrNull()
    if (typeSafeRule != null) {
      val accessor = extractTypeSafeAccessor(call)
      if (accessor != null) {
        val cleanAccessor = accessor.removeTypeSafeAccessorJunk(typeSafeRule.rootProjectAccessor)
        typeSafeRule.typeSafeAccessorMap[cleanAccessor]?.let { dependencies.add(it) }
      }
    }
    
    // Check implicit dependency rules
    val buildscriptRules = rules.filterIsInstance<ImplicitDependencyRule.BuildscriptMatchRule>()
    buildscriptRules.forEach { rule ->
      val callText = call.text
      if (callText.contains(rule.regex)) {
        dependencies.addAll(rule.includedProjects)
      }
    }
  }
  
  private fun handlePropertyExpression(
    expression: PropertyExpression,
    project: GradlePath,
    rules: Set<DependencyRule>,
    dependencies: MutableSet<GradlePath>,
  ) {
    val typeSafeRule = rules.filterIsInstance<TypeSafeProjectAccessorRule>().firstOrNull()
      ?: return
    
    val accessor = buildPropertyAccessorChain(expression)
    if (accessor != null) {
      val cleanAccessor = accessor.removeTypeSafeAccessorJunk(typeSafeRule.rootProjectAccessor)
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
}
