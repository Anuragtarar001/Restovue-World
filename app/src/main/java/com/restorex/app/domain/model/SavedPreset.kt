package com.restorex.app.domain.model

data class SavedPreset(
    val id: Long,
    val name: String,
    val adjustments: Adjustments,
    val filterPresetId: String,
    val filterIntensity: Float,
) { init { require(name.isNotBlank() && name.length <= 60 && filterIntensity in 0f..1f) } }
