package com.fueledbycaffeine.spotlight.cli

import com.fueledbycaffeine.spotlight.buildscript.GradlePath

enum class ModuleType(val label: String) {
  PUBLIC("public"),
  IMPL("impl"),
  FAKE("fake"),
  WIRING("wiring"),
  INTERNAL("internal"),
  DEMO("demo"),
  TESTING("testing"),
  TESTING_ANDROID("testingAndroid"),
  TEST_SUITE("test-suite"),
  TEST_APP("test-app"),
  APP("app"),
  OTHER("other");
}

fun detectModuleType(project: GradlePath): ModuleType {
  val lastSegment = project.path.substringAfterLast(":")

  return when {
    lastSegment == "public" -> ModuleType.PUBLIC
    lastSegment == "testingAndroid" -> ModuleType.TESTING_ANDROID
    lastSegment == "testing" -> ModuleType.TESTING
    lastSegment == "test-suite" || lastSegment.startsWith("test-suite-") -> ModuleType.TEST_SUITE
    lastSegment == "test-app" || lastSegment.startsWith("test-app-") -> ModuleType.TEST_APP
    lastSegment == "demo" || lastSegment.startsWith("demo-") -> ModuleType.DEMO
    lastSegment == "internal" || lastSegment.startsWith("internal-") -> ModuleType.INTERNAL
    lastSegment.endsWith("-wiring") || lastSegment.endsWith("-robots") -> ModuleType.WIRING
    lastSegment == "fake" || lastSegment.startsWith("fake-") -> ModuleType.FAKE
    lastSegment == "impl" || lastSegment.startsWith("impl-") -> ModuleType.IMPL
    project.path.startsWith(":apps:") -> ModuleType.APP
    else -> ModuleType.OTHER
  }
}
