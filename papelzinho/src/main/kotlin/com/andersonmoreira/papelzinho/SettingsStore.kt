package com.andersonmoreira.papelzinho

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

data class NoteSettings(
    val palette: NotePalette = NotePalette.PAPER,
    val height: Int = NoteDimensions.STANDARD_HEIGHT,
    val targetPackage: String = "com.whatsapp",
    val successfulVersion: String? = null,
)

class SettingsStore(
    private val context: Context,
) {
    private val paletteKey = stringPreferencesKey("palette")
    private val heightKey = intPreferencesKey("height")
    private val targetKey = stringPreferencesKey("target_package")

    private fun versionKey(target: String) = stringPreferencesKey("successful_version_$target")

    val settings =
        context.settingsDataStore.data.map { values ->
            val target = values[targetKey] ?: "com.whatsapp"
            NoteSettings(
                palette = NotePalette.entries.firstOrNull { it.name == values[paletteKey] } ?: NotePalette.PAPER,
                height =
                    values[heightKey]?.takeIf {
                        it in
                            listOf(
                                NoteDimensions.STANDARD_HEIGHT,
                                NoteDimensions.TALL_HEIGHT,
                            )
                    }
                        ?: NoteDimensions.STANDARD_HEIGHT,
                targetPackage = target,
                successfulVersion = values[versionKey(target)],
            )
        }

    suspend fun save(settings: NoteSettings) {
        context.settingsDataStore.edit {
            it[paletteKey] = settings.palette.name
            it[heightKey] = settings.height
            it[targetKey] = settings.targetPackage
        }
    }

    suspend fun recordSuccess(
        targetPackage: String,
        version: String,
    ) {
        context.settingsDataStore.edit { it[versionKey(targetPackage)] = version }
    }
}
