package com.fueledbycaffeine.spotlight.idea.settings

import com.fueledbycaffeine.spotlight.idea.SpotlightBundle
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.toNullableProperty

import javax.swing.DefaultListCellRenderer

/**
 * Settings page for Spotlight plugin configuration.
 * Located under Settings > Tools > Spotlight.
 */
class SpotlightConfigurable : BoundConfigurable(
    SpotlightBundle.message("settings.displayName")
) {
    private val settings = SpotlightSettings.getInstance()

    override fun createPanel(): DialogPanel = panel {
        group(SpotlightBundle.message("settings.indexing.group")) {
            row(SpotlightBundle.message("settings.exclusion.policy.label")) {
                comboBox(ExclusionPolicyMode.entries)
                    .bindItem(settings::exclusionPolicyMode.toNullableProperty())
                    .applyToComponent {
                        renderer = ExclusionPolicyModeRenderer()
                    }
            }
            row {
                comment(SpotlightBundle.message("settings.exclusion.policy.description"))
            }
        }
    }

    override fun apply() {
        super.apply()
        // Trigger re-indexing for all open projects when settings change
        refreshAllProjectIndexes()
    }

    private fun refreshAllProjectIndexes() {
        ProjectManager.getInstance().openProjects.forEach { project ->
            if (!project.isDisposed) {
                ProjectRootManagerEx.getInstanceEx(project)
                    .makeRootsChange({}, RootsChangeRescanningInfo.RESCAN_DEPENDENCIES_IF_NEEDED)
            }
        }
    }
}

/**
 * Custom renderer to display user-friendly names for exclusion policy modes.
 */
private class ExclusionPolicyModeRenderer : DefaultListCellRenderer() {
    override fun getListCellRendererComponent(
        list: javax.swing.JList<*>?,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): java.awt.Component {
        val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        if (value is ExclusionPolicyMode) {
            text = when (value) {
                ExclusionPolicyMode.ACTIVE_PROJECTS_ONLY ->
                    SpotlightBundle.message("settings.exclusion.policy.active.only")
                ExclusionPolicyMode.ALL_PROJECTS_EXCLUDE_BUILD ->
                    SpotlightBundle.message("settings.exclusion.policy.all.exclude.build")
                ExclusionPolicyMode.DISABLED ->
                    SpotlightBundle.message("settings.exclusion.policy.disabled")
            }
        }
        return component
    }
}
