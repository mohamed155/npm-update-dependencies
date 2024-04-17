package com.github.warningimhack3r.npmupdatedependencies.backend.engine

import com.github.warningimhack3r.npmupdatedependencies.backend.data.Deprecation
import com.github.warningimhack3r.npmupdatedependencies.backend.data.ScanResult
import com.github.warningimhack3r.npmupdatedependencies.ui.statusbar.StatusBarHelper
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class NUDState {
    companion object {
        @JvmStatic
        fun getInstance(project: Project): NUDState = project.service()
    }

    val availableUpdates = mutableMapOf<String, ScanResult>()
    val deprecations = mutableMapOf<String, Deprecation>()
    val packageRegistries = mutableMapOf<String, String>()

    var totalPackages = 0
        set(value) {
            field = value
            StatusBarHelper.updateWidget()
        }
    var isScanningForUpdates = false
        set(value) {
            field = value
            StatusBarHelper.updateWidget()
        }
    var scannedUpdates = 0
        set(value) {
            field = value
            StatusBarHelper.updateWidget()
        }
    var isScanningForDeprecations = false
        set(value) {
            field = value
            StatusBarHelper.updateWidget()
        }
    var scannedDeprecations = 0
        set(value) {
            field = value
            StatusBarHelper.updateWidget()
        }
    var isScanningForRegistries = false
        set(value) {
            field = value
            StatusBarHelper.updateWidget()
        }
}
