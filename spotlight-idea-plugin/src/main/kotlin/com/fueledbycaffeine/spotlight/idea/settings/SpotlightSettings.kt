package com.fueledbycaffeine.spotlight.idea.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Controls how Spotlight excludes directories from indexing.
 */
enum class ExclusionPolicyMode {
    /**
     * Only index active/loaded projects. Unloaded projects are completely excluded.
     * This is the default behavior.
     */
    ACTIVE_PROJECTS_ONLY,

    /**
     * Index all projects, but still exclude build/ folders from unloaded projects
     * to reduce indexing overhead while keeping source files searchable.
     */
    ALL_PROJECTS_EXCLUDE_BUILD,

    /**
     * Disable the exclusion policy entirely. All directories will be indexed normally.
     */
    DISABLED
}

/**
 * Application-level settings for Spotlight plugin.
 */
@State(
    name = "SpotlightSettings",
    storages = [Storage("spotlight.xml")]
)
class SpotlightSettings : PersistentStateComponent<SpotlightSettings.State> {

    data class State(
        var exclusionPolicyMode: ExclusionPolicyMode = ExclusionPolicyMode.ACTIVE_PROJECTS_ONLY
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    var exclusionPolicyMode: ExclusionPolicyMode
        get() = myState.exclusionPolicyMode
        set(value) {
            myState.exclusionPolicyMode = value
        }

    companion object {
        fun getInstance(): SpotlightSettings =
            ApplicationManager.getApplication().getService(SpotlightSettings::class.java)
    }
}
