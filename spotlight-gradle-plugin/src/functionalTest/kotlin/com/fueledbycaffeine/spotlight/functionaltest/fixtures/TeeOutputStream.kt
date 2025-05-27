package com.fueledbycaffeine.spotlight.functionaltest.fixtures

import java.io.OutputStream

class TeeOutputStream(
  private val delegateStream: OutputStream,
) : OutputStream() {
  private val buffer = StringBuilder()

  override fun write(b: Int) {
    val c = b.toChar()
    buffer.append(c)
    delegateStream.write(b)
  }

  override fun write(b: ByteArray, off: Int, len: Int) {
    val str = String(b, off, len)
    buffer.append(str)
    delegateStream.write(b, off, len)
  }

  val output: String get() = buffer.toString()

  override fun flush() = delegateStream.flush()

  override fun close() = delegateStream.flush()
}