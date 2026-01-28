package com.fueledbycaffeine.spotlight.idea.gradle

import com.fueledbycaffeine.spotlight.buildscript.GradlePath

/**
 * Shared utilities for parsing and validating Gradle project paths.
 * Uses the same patterns as RegexBuildscriptParser in buildscript-utils.
 */
object GradleProjectPathUtils {
  // Same patterns as RegexBuildscriptParser
  val PROJECT_CALL_PATTERN = Regex("""project\s*\((['"])(.*?)\1\)""")
  // Match projects.xxx.yyy - no trailing \b to allow dots within the accessor
  val TYPE_SAFE_ACCESSOR_PATTERN = Regex("""\b(projects\.[\w.]+)""")
  
  /**
   * Checks if a filename is a Gradle build file.
   */
  fun isGradleBuildFile(filename: String?): Boolean {
    return filename?.endsWith(".gradle") == true || filename?.endsWith(".gradle.kts") == true
  }
  
  /**
   * Builds a map of type-safe accessor names to GradlePath objects.
   */
  fun buildAccessorMap(allProjects: Set<GradlePath>): Map<String, GradlePath> {
    return allProjects.associateBy { it.typeSafeAccessorName }
  }
  
  /**
   * Cleans a type-safe accessor by removing common prefixes and suffixes.
   * Matches the logic in RegexBuildscriptParser.removeTypeSafeAccessorJunk()
   */
  fun cleanTypeSafeAccessor(accessor: String, rootProjectAccessor: String = ""): String {
    return accessor
      .removePrefix("projects.")
      .removePrefix("$rootProjectAccessor.")
      .removeSuffix(".dependencyProject") // deprecated in gradle, to be removed in 9.0
      .removeSuffix(".path") // GeneratedClassCompilationException if you try to name a project `:path`
  }
  
  /**
   * Checks if an accessor is valid - either an exact match or a valid intermediate namespace.
   * For example, "di" is valid if "di.scoping" exists in the map.
   */
  fun isValidAccessor(accessor: String, accessorMap: Map<String, GradlePath>): Boolean {
    // Exact match
    if (accessorMap.containsKey(accessor)) return true
    
    // Check if it's a valid prefix (intermediate namespace)
    val prefix = "$accessor."
    return accessorMap.keys.any { it.startsWith(prefix) }
  }
  
  /**
   * Checks if a project path is valid - either an exact match or a valid prefix path.
   */
  fun isValidProjectPath(path: String, allProjects: Set<GradlePath>): Boolean {
    // Exact match
    if (allProjects.any { it.path == path }) return true
    
    // Check if it's a valid prefix path
    val prefix = "$path:"
    return allProjects.any { it.path.startsWith(prefix) }
  }
  
  /**
   * Finds the best matching project path for autocomplete, ranked by fuzzy similarity.
   * Uses the same matching algorithm that IntelliJ's completion uses.
   */
  fun findBestMatchingPath(prefix: String, allProjects: Set<GradlePath>): GradlePath? {
    if (allProjects.isEmpty()) return null
    
    val normalizedPrefix = prefix.removePrefix(":").lowercase()
    
    return allProjects
      .filter { it.path != ":" } // Exclude root project
      .map { it to calculateFuzzyScore(it.path.removePrefix(":").lowercase(), normalizedPrefix) }
      .filter { it.second > 0 } // Only include matches
      .sortedWith(compareBy(
        // Higher score = better match
        { -it.second },
        // Then alphabetically
        { it.first.path }
      ))
      .firstOrNull()?.first
  }
  
  /**
   * Finds the best matching accessor for autocomplete, ranked by fuzzy similarity.
   */
  fun findBestMatchingAccessor(prefix: String, accessorMap: Map<String, GradlePath>): GradlePath? {
    if (accessorMap.isEmpty()) return null
    
    val normalizedPrefix = prefix.lowercase()
    
    return accessorMap.entries
      .map { it to calculateFuzzyScore(it.key.lowercase(), normalizedPrefix) }
      .filter { it.second > 0 } // Only include matches
      .sortedWith(compareBy(
        // Higher score = better match
        { -it.second },
        // Then alphabetically
        { it.first.key }
      ))
      .firstOrNull()?.first?.value
  }
  
  /**
   * Calculates a fuzzy matching score between a candidate and prefix.
   * Higher scores indicate better matches. Uses acronym matching similar to IntelliJ.
   * 
   * Examples:
   * - "feature-flags:api" matches "ffapi" (ff=feature-flags, api=api)
   * - "featureFlags.api" matches "ffapi" (ff=featureFlags, api=api)
   */
  internal fun calculateFuzzyScore(candidate: String, prefix: String): Int {
    if (prefix.isEmpty()) return 1
    if (candidate.isEmpty()) return 0
    
    // Exact match = highest score
    if (candidate == prefix) return 10000
    
    // Starts with prefix = very high score
    if (candidate.startsWith(prefix)) return 9000 + prefix.length * 10 - candidate.length
    
    // Contains prefix = high score
    if (candidate.contains(prefix)) return 8000 + prefix.length * 10 - candidate.length
    
    // Split candidate into segments (by : or . or - or camelCase)
    val segments = candidate.split(Regex("[:.\\-]|(?<=[a-z])(?=[A-Z])"))
      .filter { it.isNotEmpty() }
    
    // Try acronym matching - each char in prefix matches start of a segment
    val acronymResult = matchAcronym(segments, prefix)
    if (acronymResult.matched) {
      // Base score + bonus for matched segments - penalty for skipped segments - penalty for path length
      val score = 7000 + 
        acronymResult.matchedSegments * 100 + 
        prefix.length * 10 - 
        acronymResult.skippedSegments * 50 - 
        segments.size * 5
      return score
    }
    
    // Try subsequence matching - chars in prefix appear in order in candidate
    val subsequenceScore = matchSubsequence(candidate, prefix)
    if (subsequenceScore > 0) return 5000 + subsequenceScore - candidate.length
    
    return 0
  }
  
  private data class AcronymMatchResult(
    val matched: Boolean,
    val matchedSegments: Int,
    val skippedSegments: Int
  )
  
  /**
   * Matches prefix as acronym against segment first letters.
   * E.g., "ff" matches ["feature", "flags"] (f=feature, f=flags)
   * Returns match info including number of skipped segments for scoring.
   */
  private fun matchAcronym(segments: List<String>, prefix: String): AcronymMatchResult {
    if (segments.isEmpty()) return AcronymMatchResult(false, 0, 0)
    
    var prefixIdx = 0
    var matchedSegments = 0
    var skippedSegments = 0
    var lastMatchedSegmentIdx = -1
    
    for ((segmentIdx, segment) in segments.withIndex()) {
      if (prefixIdx >= prefix.length) break
      if (segment.isEmpty()) continue
      
      val segmentLower = segment.lowercase()
      var charIdx = 0
      val startPrefixIdx = prefixIdx
      
      while (prefixIdx < prefix.length && charIdx < segmentLower.length) {
        if (segmentLower[charIdx] == prefix[prefixIdx]) {
          prefixIdx++
          charIdx++
        } else if (charIdx == 0) {
          // First char didn't match, skip this segment
          break
        } else {
          // Continue matching within segment
          charIdx++
        }
      }
      
      // Did we match anything in this segment?
      if (prefixIdx > startPrefixIdx) {
        // Count skipped segments between matches
        if (lastMatchedSegmentIdx >= 0) {
          skippedSegments += segmentIdx - lastMatchedSegmentIdx - 1
        }
        lastMatchedSegmentIdx = segmentIdx
        matchedSegments++
      }
    }
    
    return AcronymMatchResult(
      matched = prefixIdx == prefix.length,
      matchedSegments = matchedSegments,
      skippedSegments = skippedSegments
    )
  }
  
  /**
   * Matches prefix as subsequence of candidate.
   * E.g., "fapi" matches "feature-flags:api" (f...a...p...i)
   */
  private fun matchSubsequence(candidate: String, prefix: String): Int {
    var prefixIdx = 0
    var candidateIdx = 0
    
    while (prefixIdx < prefix.length && candidateIdx < candidate.length) {
      if (candidate[candidateIdx] == prefix[prefixIdx]) {
        prefixIdx++
      }
      candidateIdx++
    }
    
    return if (prefixIdx == prefix.length) prefix.length else 0
  }
}
