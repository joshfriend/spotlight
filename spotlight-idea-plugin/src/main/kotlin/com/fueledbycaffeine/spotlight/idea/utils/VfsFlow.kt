package com.fueledbycaffeine.spotlight.idea.utils

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Provides a Flow that emits lists of [virtual file events][VFileEvent] whenever changes occur in the virtual
 * file system (VFS) associated with the given [project].
 */
internal fun vfsEventsFlow(
  project: Project,
): Flow<List<VFileEvent>> = callbackFlow {
  val connection = project.messageBus.connect(this)
  connection.subscribe(
    VirtualFileManager.VFS_CHANGES,
    object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        check(trySend(events).isSuccess)
      }
    }
  )
  awaitClose { connection.disconnect() }
}