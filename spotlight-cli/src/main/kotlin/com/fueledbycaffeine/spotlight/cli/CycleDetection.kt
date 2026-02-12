package com.fueledbycaffeine.spotlight.cli

import com.fueledbycaffeine.spotlight.buildscript.GradlePath

/**
 * Result of cycle detection for a single module.
 */
data class CycleInfo(
  val cycleSize: Int,
  val cycleMembers: Set<String>,
  val penalty: Int,
)

/**
 * Detect strongly connected components (cycles) using Tarjan's algorithm.
 * Returns a map from each module in a non-trivial SCC (size > 1) to its CycleInfo.
 */
fun detectCycles(
  dependencyMap: Map<GradlePath, Set<GradlePath>>
): Map<String, CycleInfo> {
  val sccs = tarjanSCC(dependencyMap)

  // Only keep non-trivial SCCs (size > 1 = actual cycle)
  val nonTrivial = sccs.filter { it.size > 1 }

  val result = HashMap<String, CycleInfo>()
  for (scc in nonTrivial) {
    val members = scc.map { it.path }.toSet()
    // Penalty: scaled by cycle size, capped at 10
    // Bigger cycles are worse because they force more modules to recompile together
    val penalty = when {
      scc.size >= 10 -> 10
      scc.size >= 5 -> 7
      scc.size >= 3 -> 5
      else -> 3
    }
    val info = CycleInfo(scc.size, members, penalty)
    for (node in scc) {
      result[node.path] = info
    }
  }

  return result
}

/**
 * Tarjan's strongly connected components algorithm.
 * Returns all SCCs in the graph (including trivial ones of size 1).
 */
private fun tarjanSCC(
  adjacency: Map<GradlePath, Set<GradlePath>>
): List<List<GradlePath>> {
  var index = 0
  val nodeIndex = HashMap<GradlePath, Int>()
  val nodeLowlink = HashMap<GradlePath, Int>()
  val onStack = HashSet<GradlePath>()
  val stack = ArrayDeque<GradlePath>()
  val sccs = mutableListOf<List<GradlePath>>()

  fun strongConnect(v: GradlePath) {
    // Use an explicit work stack to avoid StackOverflow on deep graphs
    data class Frame(
      val node: GradlePath,
      val neighbors: Iterator<GradlePath>,
      var phase: Int = 0,
      var returnFrom: GradlePath? = null,
    )

    val workStack = ArrayDeque<Frame>()
    workStack.addLast(Frame(v, (adjacency[v] ?: emptySet()).iterator()))

    // Init v
    nodeIndex[v] = index
    nodeLowlink[v] = index
    index++
    stack.addLast(v)
    onStack.add(v)

    while (workStack.isNotEmpty()) {
      val frame = workStack.last()
      val node = frame.node

      if (frame.phase == 1) {
        // Returning from recursive call
        val w = frame.returnFrom!!
        nodeLowlink[node] = minOf(nodeLowlink[node]!!, nodeLowlink[w]!!)
        frame.phase = 0
      }

      var recursed = false
      while (frame.neighbors.hasNext()) {
        val w = frame.neighbors.next()
        if (w !in nodeIndex) {
          // Not yet visited — "recurse"
          nodeIndex[w] = index
          nodeLowlink[w] = index
          index++
          stack.addLast(w)
          onStack.add(w)

          frame.phase = 1
          frame.returnFrom = w
          workStack.addLast(Frame(w, (adjacency[w] ?: emptySet()).iterator()))
          recursed = true
          break
        } else if (w in onStack) {
          nodeLowlink[node] = minOf(nodeLowlink[node]!!, nodeIndex[w]!!)
        }
      }

      if (!recursed) {
        // All neighbors processed
        if (nodeLowlink[node] == nodeIndex[node]) {
          // Root of SCC — pop stack to form component
          val scc = mutableListOf<GradlePath>()
          while (true) {
            val w = stack.removeLast()
            onStack.remove(w)
            scc.add(w)
            if (w == node) break
          }
          sccs.add(scc)
        }

        workStack.removeLast()
      }
    }
  }

  for (node in adjacency.keys) {
    if (node !in nodeIndex) {
      strongConnect(node)
    }
  }

  return sccs
}
