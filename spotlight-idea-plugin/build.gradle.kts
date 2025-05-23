import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
  id("java") // Java support
  alias(libs.plugins.kotlin) // Kotlin support
  alias(libs.plugins.intelliJPlatform) // IntelliJ Platform Gradle Plugin
  alias(libs.plugins.qodana) // Gradle Qodana Plugin
}

group = "com.fueledbycaffene.spotlight"
val pluginVersion = "0.1"
version = pluginVersion

// Set the JVM language level used to build the project.
kotlin {
  jvmToolchain(21)
}

// Configure project's dependencies
repositories {
  mavenCentral()
  intellijPlatform {
    defaultRepositories()
  }
}

dependencies {
  implementation(project(":buildscript-utils"))

  intellijPlatform {
    intellijIdeaCommunity("2025.1.1.1")
    testFramework(TestFrameworkType.Platform)
  }
}

// https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html
intellijPlatform {
  pluginConfiguration {
    name = "Spotlight"
    version = pluginVersion

    ideaVersion {
      sinceBuild = "242"
      untilBuild = "252.*"
    }
  }

  signing {
    certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
    privateKey = providers.environmentVariable("PRIVATE_KEY")
    password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
  }

  publishing {
    token = providers.environmentVariable("PUBLISH_TOKEN")
    // The pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
    // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
    // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
    channels = listOf("EAP")
  }

  pluginVerification {
    ides {
      recommended()
    }
  }
}

intellijPlatformTesting {
  runIde {
    register("runIdeForUiTests") {
      task {
        jvmArgumentProviders += CommandLineArgumentProvider {
          listOf(
            "-Drobot-server.port=8082",
            "-Dide.mac.message.dialogs.as.sheets=false",
            "-Djb.privacy.policy.text=<!--999.999-->",
            "-Djb.consents.confirmation.enabled=false",
          )
        }
      }

      plugins {
        robotServerPlugin()
      }
    }
  }
}
