@file:Suppress("UnstableApiUsage")

package com.fueledbycaffeine.spotlight.utils

import org.gradle.api.Project

internal val Project.isRootProject: Boolean get() = isolated == isolated.rootProject
