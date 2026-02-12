package com.fueledbycaffeine.spotlight.cli

import com.fueledbycaffeine.spotlight.buildscript.GradlePath

enum class InliningCategory(val label: String) {
  FAKE_INLINE("fake -> impl debug src"),
  TESTING_FIXTURE("testing -> test fixtures"),
  WIRING_MERGE("wiring -> merge into impl"),
  EMPTY_MODULE("empty module -> remove"),
  NONE("not a candidate"),
}

data class InliningResult(
  val category: InliningCategory,
  val penalty: Int,
  val mergeTarget: String?,
)

/**
 * Detect whether a module is a candidate for structural elimination.
 * Returns the inlining category, penalty score (0-10), and suggested merge target.
 */
fun detectInliningCandidate(
  project: GradlePath,
  moduleType: ModuleType,
  sloc: Int,
  dependents: Set<GradlePath>,
  reverseDeps: Map<GradlePath, Set<GradlePath>>,
): InliningResult {
  val directDependents = reverseDeps[project] ?: emptySet()
  val parentPath = project.path.substringBeforeLast(":")
  val siblingImpl = "$parentPath:impl"

  return when (moduleType) {
    ModuleType.FAKE -> {
      // Check if all direct dependents are sibling impl or wiring modules
      val allSiblingOrWiring = directDependents.all { dep ->
        val depType = detectModuleType(dep)
        val isSibling = dep.path.startsWith(parentPath)
        isSibling && (depType == ModuleType.IMPL || depType == ModuleType.WIRING)
      }
      if (directDependents.isEmpty()) {
        InliningResult(InliningCategory.EMPTY_MODULE, 5, null)
      } else if (allSiblingOrWiring) {
        InliningResult(InliningCategory.FAKE_INLINE, 8, "$siblingImpl (debug src)")
      } else {
        InliningResult(InliningCategory.FAKE_INLINE, 3, "$siblingImpl (debug src, has external consumers)")
      }
    }

    ModuleType.TESTING, ModuleType.TESTING_ANDROID -> {
      val target = "$parentPath:public (test fixtures)"
      val penalty = if (directDependents.size <= 5) 6 else 4
      InliningResult(InliningCategory.TESTING_FIXTURE, penalty, target)
    }

    ModuleType.WIRING -> {
      val lastSegment = project.path.substringAfterLast(":")
      val isRobots = lastSegment.endsWith("-robots")
      val mergeTarget = if (isRobots) {
        // Robots wiring like :impl-anvil-robots -> merge into sibling impl
        val implName = lastSegment.removeSuffix("-robots")
        "$parentPath:$implName"
      } else {
        siblingImpl
      }
      if (sloc < 200) {
        InliningResult(InliningCategory.WIRING_MERGE, 7, mergeTarget)
      } else {
        InliningResult(InliningCategory.WIRING_MERGE, 2, "$mergeTarget (complex wiring)")
      }
    }

    else -> {
      // App modules are intentionally 0-SLOC DI aggregation points, not empty waste
      if (sloc == 0 && moduleType != ModuleType.APP) {
        InliningResult(InliningCategory.EMPTY_MODULE, 5, null)
      } else {
        InliningResult(InliningCategory.NONE, 0, null)
      }
    }
  }
}
