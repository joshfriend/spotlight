package com.gradle.scan.plugin.internal.com.fueledbycaffeine.spotlight.internal

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

internal fun Path.ccHiddenIsDirectory(vararg options: LinkOption): Boolean = Files.isDirectory(this, *options)