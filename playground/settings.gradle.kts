// Playground build that mirrors the *functional test* SpiritboxProject fixture,
// but uses the local Spotlight build via composite include.

pluginManagement {
  // Make the Spotlight settings plugin available from this repo.
  includeBuild("..")

  repositories {
    gradlePluginPortal()
    mavenCentral()
  }
}

dependencyResolutionManagement {
  repositories {
    mavenCentral()
  }
}

plugins {
  id("com.fueledbycaffeine.spotlight")
}

rootProject.name = "playground"

