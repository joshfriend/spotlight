package com.fueledbycaffeine.spotlight.cli

import com.fueledbycaffeine.spotlight.buildscript.GradlePath
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile

data class AbiSurfaceInfo(
  /** Number of public/open declarations (classes, functions, properties). */
  val publicDeclarations: Int,
  /** Ratio of public declarations to total SLOC. Higher = more ABI-exposed. */
  val abiDensity: Double,
)

private val SOURCE_EXTENSIONS = setOf("kt", "java")

// Kotlin: matches lines declaring public API members (line-by-line, no backtracking risk)
private val KT_DECL_KEYWORDS = setOf("fun ", "val ", "var ", "class ", "interface ", "object ", "typealias ")
private val KT_NON_PUBLIC_PREFIXES = setOf("internal ", "private ", "protected ")

// Java: lines starting with "public " that declare something
private val JAVA_DECL_AFTER_PUBLIC = setOf("class ", "interface ", "enum ", "@interface ", "static ", "final ", "abstract ")

/**
 * Estimate the ABI surface of a module by scanning for public declarations
 * in its main source set. Uses line-by-line matching to avoid regex backtracking.
 */
fun estimateAbiSurface(project: GradlePath, sloc: Int): AbiSurfaceInfo {
  val mainDir = project.projectDir.resolve("src/main")
  if (!mainDir.exists()) return AbiSurfaceInfo(0, 0.0)

  var publicCount = 0

  Files.walk(mainDir).use { stream ->
    stream.filter { it.isRegularFile() && it.extension in SOURCE_EXTENSIONS }
      .forEach { file ->
        val ext = file.extension
        Files.lines(file).use { lines ->
          lines.forEach { line ->
            val trimmed = line.trimStart()
            if (ext == "kt") {
              publicCount += countKotlinPublicDecl(trimmed)
            } else {
              publicCount += countJavaPublicDecl(trimmed)
            }
          }
        }
      }
  }

  val density = if (sloc > 0) publicCount.toDouble() / sloc else 0.0
  return AbiSurfaceInfo(publicCount, density)
}

/**
 * Count public declarations in a single Kotlin line.
 * In Kotlin, declarations without a visibility modifier are public by default.
 * We count lines that start with a declaration keyword (fun/val/var/class/etc.)
 * and are NOT prefixed with internal/private/protected.
 */
private fun countKotlinPublicDecl(trimmed: String): Int {
  if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) return 0

  // Check if line starts with a non-public modifier
  for (prefix in KT_NON_PUBLIC_PREFIXES) {
    if (trimmed.startsWith(prefix)) return 0
  }

  // Strip optional modifiers: public, open, abstract, override, suspend, inline, actual, expect, data, sealed, enum, annotation
  var rest = trimmed
  while (true) {
    val next = stripKotlinModifier(rest) ?: break
    rest = next
  }

  // Check if what remains starts with a declaration keyword
  for (keyword in KT_DECL_KEYWORDS) {
    if (rest.startsWith(keyword)) return 1
  }
  return 0
}

private val KT_MODIFIERS = listOf(
  "public ", "open ", "abstract ", "override ", "suspend ", "inline ",
  "actual ", "expect ", "data ", "sealed ", "enum ", "annotation ",
  "external ", "infix ", "operator ", "tailrec ", "crossinline ", "noinline ",
  "reified ", "vararg ", "const ",
)

private fun stripKotlinModifier(s: String): String? {
  for (mod in KT_MODIFIERS) {
    if (s.startsWith(mod)) return s.substring(mod.length).trimStart()
  }
  return null
}

/**
 * Count public declarations in a single Java line.
 * In Java, only lines starting with "public" are public API.
 */
private fun countJavaPublicDecl(trimmed: String): Int {
  if (!trimmed.startsWith("public ")) return 0
  val afterPublic = trimmed.substring(7).trimStart()

  // Must be followed by a type declaration or a return type (method/field)
  // Heuristic: if it contains '(' it's a method, if it contains a known keyword it's a type
  for (keyword in JAVA_DECL_AFTER_PUBLIC) {
    if (afterPublic.startsWith(keyword)) return 1
  }
  // Otherwise it's likely a public method or field (has a return type then name)
  if (afterPublic.isNotEmpty() && afterPublic[0].isLetter()) return 1
  return 0
}
