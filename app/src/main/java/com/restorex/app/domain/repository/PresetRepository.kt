package com.restorex.app.domain.repository

import com.restorex.app.domain.model.SavedPreset

interface PresetRepository {
    fun load(): List<SavedPreset>
    fun save(presets: List<SavedPreset>)
}
