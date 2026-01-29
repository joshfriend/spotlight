package com.fueledbycaffeine.spotlight.idea.completion

/**
 * Shared fuzzy matching utilities for intelligent completion.
 * Supports camelCase acronyms (e.g., "ff" → "featureFlags") and
 * kebab-case acronyms (e.g., "ff" → "feature-flags").
 */
object FuzzyMatchingUtils {
  
  /**
   * Calculates match priority for a path against a typed prefix.
   * Lower numbers = better match.
   * Supports fuzzy matching (e.g., ":ffapi" matches ":feature-flags:api")
   */
  fun calculatePathMatchPriority(path: String, typedPrefix: String): Int {
    if (typedPrefix.isEmpty()) return 100
    
    val normalizedPrefix = typedPrefix.lowercase().removePrefix(":")
    val normalizedPath = path.lowercase()
    
    // Split path into segments (e.g., ":feature-flags:api" -> ["feature-flags", "api"])
    val segments = path.split(":").filter { it.isNotEmpty() }
    
    return when {
      // Exact match
      path == typedPrefix -> 0
      // Full path starts with typed prefix (e.g., ":feature-f" matches ":feature-flags:api")
      normalizedPath.startsWith(typedPrefix.lowercase()) -> 1
      // First segment starts with prefix (e.g., "feature" matches ":feature-flags:api")
      segments.firstOrNull()?.lowercase()?.startsWith(normalizedPrefix) == true -> 2
      // Fuzzy match across segments (e.g., ":ffapi" -> ":feature-flags:api")
      fuzzyMatchesPath(normalizedPrefix, segments) -> 3
      // Any segment starts with prefix (e.g., "api" matches ":feature-flags:api")
      segments.any { it.lowercase().startsWith(normalizedPrefix) } -> 4
      // Any segment contains prefix
      segments.any { it.lowercase().contains(normalizedPrefix) } -> 5
      // Full path contains prefix
      normalizedPath.contains(normalizedPrefix) -> 6
      // No match
      else -> 100
    }
  }
  
  /**
   * Calculates match priority for a type-safe accessor against a typed prefix.
   * Lower numbers = better match.
   * Supports fuzzy matching (e.g., "ffapi" matches "featureFlags.api")
   */
  fun calculateAccessorMatchPriority(accessor: String, typedPrefix: String): Int {
    if (typedPrefix.isEmpty()) return 100
    
    val normalizedPrefix = typedPrefix.lowercase()
    val normalizedAccessor = accessor.lowercase()
    
    // Split accessor into segments (e.g., "featureFlags.api" -> ["featureFlags", "api"])
    val segments = accessor.split(".").filter { it.isNotEmpty() }
    
    return when {
      // Exact match
      accessor == typedPrefix -> 0
      // Full accessor starts with typed prefix
      normalizedAccessor.startsWith(normalizedPrefix) -> 1
      // First segment starts with prefix
      segments.firstOrNull()?.lowercase()?.startsWith(normalizedPrefix) == true -> 2
      // Fuzzy match across segments (e.g., "ffapi" -> "featureFlags.api")
      fuzzyMatchesAccessor(normalizedPrefix, segments) -> 3
      // Any segment starts with prefix
      segments.any { it.lowercase().startsWith(normalizedPrefix) } -> 4
      // Any segment contains prefix
      segments.any { it.lowercase().contains(normalizedPrefix) } -> 5
      // Full accessor contains prefix
      normalizedAccessor.contains(normalizedPrefix) -> 6
      // No match
      else -> 100
    }
  }
  
  /**
   * Fuzzy matches a prefix against accessor segments (camelCase).
   * Examples: "ffapi" matches ["featureFlags", "api"]
   */
  fun fuzzyMatchesAccessor(prefix: String, segments: List<String>): Boolean {
    if (prefix.isEmpty() || segments.isEmpty()) return false
    
    var prefixIndex = 0
    var segmentIndex = 0
    
    while (prefixIndex < prefix.length && segmentIndex < segments.size) {
      val segment = segments[segmentIndex]
      val remainingPrefix = prefix.substring(prefixIndex)
      
      // Check if remaining prefix exactly matches a later segment (prioritize this)
      val exactMatchInLaterSegment = segments.drop(segmentIndex).any {
        it.equals(remainingPrefix, ignoreCase = true)
      }
      
      if (exactMatchInLaterSegment && segmentIndex < segments.size - 1) {
        // Skip this segment to allow exact match later
        segmentIndex++
        continue
      }
      
      val matched = matchPrefixToSegment(prefix, prefixIndex, segment)
      
      if (matched > 0) {
        prefixIndex += matched
        segmentIndex++
      } else {
        segmentIndex++
      }
    }
    
    return prefixIndex >= prefix.length
  }
  
  /**
   * Fuzzy matches a prefix against path segments (kebab-case).
   * Examples: "ffapi" matches ["feature-flags", "api"]
   */
  fun fuzzyMatchesPath(prefix: String, segments: List<String>): Boolean {
    if (prefix.isEmpty() || segments.isEmpty()) return false
    
    var prefixIndex = 0
    var segmentIndex = 0
    
    while (prefixIndex < prefix.length && segmentIndex < segments.size) {
      val segment = segments[segmentIndex].lowercase()
      val matched = matchPrefixToKebabSegment(prefix, prefixIndex, segment)
      
      if (matched > 0) {
        prefixIndex += matched
        segmentIndex++
      } else {
        segmentIndex++
      }
    }
    
    return prefixIndex >= prefix.length
  }
  
  /**
   * Matches as much of the prefix as possible against a camelCase segment.
   */
  private fun matchPrefixToSegment(prefix: String, startIndex: Int, segment: String): Int {
    if (startIndex >= prefix.length) return 0
    
    val remainingPrefix = prefix.substring(startIndex).lowercase()
    val lowerSegment = segment.lowercase()
    
    // Try exact/prefix match first
    if (lowerSegment.startsWith(remainingPrefix)) {
      return remainingPrefix.length
    }
    
    // Try camelCase acronym matching
    val acronymMatch = matchCamelCaseAcronym(remainingPrefix, segment)
    if (acronymMatch > 0) return acronymMatch
    
    // Try matching start of segment
    var matched = 0
    while (matched < remainingPrefix.length && 
           matched < lowerSegment.length && 
           remainingPrefix[matched] == lowerSegment[matched]) {
      matched++
    }
    
    return matched
  }
  
  /**
   * Matches acronym-style input against camelCase text using two strategies:
   * 1. Prefix-per-word: consecutive chars match prefixes of camelCase words (e.g., "caos" → "cashOs")
   * 2. Strict acronym: each char matches first char of a camelCase word (e.g., "ff" → "featureFlags")
   */
  private fun matchCamelCaseAcronym(prefix: String, text: String): Int {
    if (prefix.isEmpty() || text.isEmpty()) return 0
    
    // Split camelCase into words
    val words = splitCamelCase(text)
    if (words.size < 2) return 0
    
    // Try prefix-per-word matching first
    val prefixMatch = matchPrefixesAcrossWords(prefix, words)
    if (prefixMatch > 0) return prefixMatch
    
    // Fall back to strict acronym matching
    return matchStrictCamelCaseAcronym(prefix, text)
  }
  
  /**
   * Splits camelCase text into lowercase words.
   * Example: "featureFlags" → ["feature", "flags"]
   */
  private fun splitCamelCase(text: String): List<String> {
    val words = mutableListOf<String>()
    val currentWord = StringBuilder()
    
    for (char in text) {
      if (char.isUpperCase() && currentWord.isNotEmpty()) {
        words.add(currentWord.toString().lowercase())
        currentWord.clear()
      }
      currentWord.append(char)
    }
    
    if (currentWord.isNotEmpty()) {
      words.add(currentWord.toString().lowercase())
    }
    
    return words
  }
  
  /**
   * Strict camelCase acronym matching: each prefix char matches the start of a camelCase word.
   */
  private fun matchStrictCamelCaseAcronym(prefix: String, text: String): Int {
    var prefixIndex = 0
    var textIndex = 0
    
    // First character must match
    if (prefix[0] != text[0].lowercaseChar()) return 0
    prefixIndex++
    textIndex++
    
    // Match subsequent characters ONLY at camelCase word boundaries
    while (prefixIndex < prefix.length && textIndex < text.length) {
      val prefixChar = prefix[prefixIndex]
      
      // Look for next uppercase letter that matches
      var found = false
      while (textIndex < text.length) {
        val textChar = text[textIndex]
        
        if (textChar.isUpperCase() && textChar.lowercaseChar() == prefixChar) {
          found = true
          prefixIndex++
          textIndex++
          break
        }
        textIndex++
      }
      
      if (!found) break
    }
    
    // Only return match count if we matched at least 2 characters
    return if (prefixIndex >= 2) prefixIndex else 0
  }
  
  /**
   * Matches prefix against a kebab-case segment.
   * Examples: "ff" matches "feature-flags" (f→feature, f→flags)
   */
  private fun matchPrefixToKebabSegment(prefix: String, startIndex: Int, segment: String): Int {
    if (startIndex >= prefix.length) return 0
    
    val remainingPrefix = prefix.substring(startIndex)
    
    // Try exact/prefix match first
    if (segment.startsWith(remainingPrefix)) {
      return remainingPrefix.length
    }
    
    // Try kebab-case acronym matching
    val words = segment.split("-").filter { it.isNotEmpty() }
    val acronymMatch = matchKebabAcronym(remainingPrefix, words)
    if (acronymMatch > 0) return acronymMatch
    
    // Try matching start of segment
    var matched = 0
    while (matched < remainingPrefix.length && 
           matched < segment.length && 
           remainingPrefix[matched] == segment[matched]) {
      matched++
    }
    
    return matched
  }
  
  /**
   * Matches acronym against kebab-case words using two strategies:
   * 1. Strict acronym: each char matches first char of a word (e.g., "ff" → "feature-flags")
   * 2. Prefix-per-word: consecutive chars match prefixes of words (e.g., "caos" → "cash-os")
   */
  private fun matchKebabAcronym(prefix: String, words: List<String>): Int {
    if (prefix.isEmpty() || words.isEmpty()) return 0
    if (words.size < 2) return 0
    
    // Try prefix-per-word matching first (more flexible)
    val prefixMatch = matchPrefixesAcrossWords(prefix, words)
    if (prefixMatch > 0) return prefixMatch
    
    // Fall back to strict acronym matching
    return matchStrictAcronym(prefix, words)
  }
  
  /**
   * Matches by consuming prefix characters against the start of each word.
   * Example: "caos" matches ["cash", "os"] (ca→cash, os→os)
   */
  private fun matchPrefixesAcrossWords(prefix: String, words: List<String>): Int {
    var prefixIndex = 0
    var wordIndex = 0
    
    while (prefixIndex < prefix.length && wordIndex < words.size) {
      val word = words[wordIndex]
      if (word.isEmpty()) {
        wordIndex++
        continue
      }
      
      // Check if the word starts with the current prefix character
      if (word[0] != prefix[prefixIndex]) {
        wordIndex++
        continue
      }
      
      // Match as many characters as possible from this word
      var matchedInWord = 0
      while (prefixIndex + matchedInWord < prefix.length &&
             matchedInWord < word.length &&
             prefix[prefixIndex + matchedInWord] == word[matchedInWord]) {
        matchedInWord++
      }
      
      if (matchedInWord > 0) {
        prefixIndex += matchedInWord
        wordIndex++
      } else {
        wordIndex++
      }
    }
    
    // Must match across at least 2 words to be considered a valid fuzzy match
    return if (prefixIndex >= 2 && wordIndex >= 2) prefixIndex else 0
  }
  
  /**
   * Strict acronym matching: each prefix char matches first char of a different word.
   * Example: "ff" matches ["feature", "flags"] (f→feature, f→flags)
   */
  private fun matchStrictAcronym(prefix: String, words: List<String>): Int {
    var prefixIndex = 0
    var wordIndex = 0
    
    while (prefixIndex < prefix.length && wordIndex < words.size) {
      val prefixChar = prefix[prefixIndex]
      val word = words[wordIndex]
      
      if (word.isNotEmpty() && word[0] == prefixChar) {
        prefixIndex++
      }
      wordIndex++
    }
    
    // Only return match count if we matched at least 2 characters
    return if (prefixIndex >= 2) prefixIndex else 0
  }
}
