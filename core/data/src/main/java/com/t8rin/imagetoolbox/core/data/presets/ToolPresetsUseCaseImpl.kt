/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/LICENSE-2.0>.
 */

package com.t8rin.imagetoolbox.core.data.presets

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.t8rin.imagetoolbox.core.domain.image.ShareProvider
import com.t8rin.imagetoolbox.core.domain.json.JsonParser
import com.t8rin.imagetoolbox.core.domain.presets.ToolPreset
import com.t8rin.imagetoolbox.core.domain.presets.ToolPresetImportResult
import com.t8rin.imagetoolbox.core.domain.presets.ToolPresetsUseCase
import com.t8rin.imagetoolbox.core.domain.saving.FileController
import com.t8rin.imagetoolbox.core.domain.utils.runSuspendCatching
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class ToolPresetsUseCaseImpl<T : Any>(
    private val toolKey: String,
    private val payloadType: Class<T>,
    private val dataStore: DataStore<Preferences>,
    private val fileController: FileController,
    private val jsonParser: JsonParser,
    private val shareProvider: ShareProvider
) : ToolPresetsUseCase<T> {

    private val prefsKey = stringPreferencesKey("TOOL_PRESETS_$toolKey")

    override val presets: Flow<List<ToolPreset<T>>> = dataStore.data.map { preferences ->
        preferences.readStored().presets.mapNotNull { it.toToolPreset() }.asReversed()
    }

    override suspend fun upsert(preset: ToolPreset<T>) {
        val name = preset.name.trim()
        if (name.isBlank()) return
        val stored = preset.copy(name = name).toStored() ?: return

        dataStore.edit { preferences ->
            val list = preferences.readStored().presets.toMutableList()
            val index = list.indexOfFirst { it.name.equals(name, ignoreCase = true) }
            if (index >= 0) {
                list[index] = stored.copy(name = list[index].name)
            } else {
                list.add(stored)
            }
            preferences.writeStored(StoredToolPresets(list))
        }
    }

    override suspend fun delete(preset: ToolPreset<T>) {
        dataStore.edit { preferences ->
            val current = preferences.readStored()
            preferences.writeStored(
                current.copy(
                    presets = current.presets.filterNot {
                        it.name.equals(preset.name, ignoreCase = true)
                    }
                )
            )
        }
    }

    override suspend fun export(
        preset: ToolPreset<T>,
        uri: String
    ) {
        preset.toEnvelopeJson()?.let { json ->
            fileController.writeBytes(uri) {
                it.writeBytes(json.encodeToByteArray())
            }
        }
    }

    override suspend fun share(preset: ToolPreset<T>) {
        preset.toEnvelopeJson()?.let { json ->
            shareProvider.shareData(
                filename = "${preset.name.safePresetFileName()}.itpreset",
                writeData = {
                    it.writeBytes(json.encodeToByteArray())
                }
            )
        }
    }

    override suspend fun import(uri: String): ToolPresetImportResult {
        val envelope = runSuspendCatching {
            fileController.readBytes(uri).decodeToString()
        }.mapCatching { json ->
            jsonParser.fromJson<ToolPresetFileEnvelope>(
                json = json,
                type = ToolPresetFileEnvelope::class.java
            )
        }.getOrNull() ?: return ToolPresetImportResult.Invalid

        if (envelope.toolKey != toolKey) return ToolPresetImportResult.WrongTool

        val preset = StoredToolPreset(
            name = envelope.name,
            version = envelope.version,
            payloadJson = envelope.payloadJson
        ).toToolPreset() ?: return ToolPresetImportResult.Invalid

        if (preset.name.isBlank()) return ToolPresetImportResult.Invalid

        upsert(preset)
        return ToolPresetImportResult.Success
    }

    private fun StoredToolPreset.toToolPreset(): ToolPreset<T>? {
        val payload = jsonParser.fromJson<T>(
            json = payloadJson,
            type = payloadType
        ) ?: return null

        return ToolPreset(
            name = name,
            payload = payload,
            version = version
        )
    }

    private fun ToolPreset<T>.toStored(): StoredToolPreset? {
        val payloadJson = jsonParser.toJson(
            obj = payload,
            type = payloadType
        ) ?: return null

        return StoredToolPreset(
            name = name,
            version = version,
            payloadJson = payloadJson
        )
    }

    private fun ToolPreset<T>.toEnvelopeJson(): String? = toStored()?.let { stored ->
        jsonParser.toJson(
            obj = ToolPresetFileEnvelope(
                toolKey = toolKey,
                version = stored.version,
                name = stored.name,
                payloadJson = stored.payloadJson
            ),
            type = ToolPresetFileEnvelope::class.java
        )
    }

    private fun Preferences.readStored(): StoredToolPresets {
        val json = this[prefsKey] ?: return StoredToolPresets()
        return jsonParser.fromJson<StoredToolPresets>(
            json = json,
            type = StoredToolPresets::class.java
        ) ?: StoredToolPresets()
    }

    private fun MutablePreferences.writeStored(presets: StoredToolPresets) {
        if (presets.presets.isEmpty()) {
            remove(prefsKey)
            return
        }
        jsonParser.toJson(
            obj = presets,
            type = StoredToolPresets::class.java
        )?.let { json ->
            this[prefsKey] = json
        }
    }

    private fun String.safePresetFileName(): String = trim()
        .replace(Regex("""[^\w.-]+"""), "_")
        .trim('_')
        .ifBlank { "preset" }

}

internal data class StoredToolPreset(
    val name: String = "",
    val version: Int = 1,
    val payloadJson: String = ""
)

internal data class StoredToolPresets(
    val presets: List<StoredToolPreset> = emptyList()
)

internal data class ToolPresetFileEnvelope(
    val toolKey: String = "",
    val version: Int = 1,
    val name: String = "",
    val payloadJson: String = ""
)
