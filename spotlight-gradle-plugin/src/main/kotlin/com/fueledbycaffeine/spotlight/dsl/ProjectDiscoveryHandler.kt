package com.fueledbycaffeine.spotlight.dsl

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

/** Configures project discovery. Dependency-graph traversal always remains active. */
public abstract class ProjectDiscoveryHandler @Inject constructor(objects: ObjectFactory) {
  internal val directoryScanHandler = objects.newInstance(DirectoryScanHandler::class.java)

  /** Configures the additional directory scan used by :checkAllProjectsList and :fixAllProjectsList. */
  public fun directoryScan(action: Action<DirectoryScanHandler>) {
    action.execute(directoryScanHandler)
  }
}
