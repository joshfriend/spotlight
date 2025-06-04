package com.gradle.scan.plugin.internal.com.fueledbycaffeine.spotlight.internal

import java.nio.charset.Charset
import java.nio.file.Path
import kotlin.io.path.readLines
import kotlin.io.path.readText

internal fun Path.ccHiddenReadText(charset: Charset = Charsets.UTF_8): String {
  return this.readText(charset)
}

internal fun Path.ccHiddenReadLines(charset: Charset = Charsets.UTF_8): List<String> {
  return this.readLines(charset)
}