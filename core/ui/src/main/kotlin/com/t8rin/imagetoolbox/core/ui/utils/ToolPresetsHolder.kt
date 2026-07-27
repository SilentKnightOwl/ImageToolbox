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

package com.t8rin.imagetoolbox.core.ui.utils

import android.net.Uri
import androidx.compose.runtime.Stable
import com.t8rin.imagetoolbox.core.domain.presets.ToolPreset
import com.t8rin.imagetoolbox.core.domain.presets.ToolPresetImportResult
import com.t8rin.imagetoolbox.core.domain.presets.ToolPresetsUseCase
import com.t8rin.imagetoolbox.core.resources.R
import com.t8rin.imagetoolbox.core.ui.utils.helper.AppToastHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Stable
interface ToolPresetsHolder<T : Any> {

    val toolPresets: StateFlow<List<ToolPreset<T>>>

    /** Override in the component: capture current settings and upsert. */
    fun saveToolPreset(name: String)

    /** Override in the component: restore settings from [preset.payload]. */
    fun applyToolPreset(preset: ToolPreset<T>)

    fun deleteToolPreset(preset: ToolPreset<T>)

    fun exportToolPreset(
        preset: ToolPreset<T>,
        uri: Uri
    )

    fun shareToolPreset(preset: ToolPreset<T>)

    fun importToolPreset(uri: Uri)

    companion object {
        operator fun <T : Any> invoke(
            useCase: ToolPresetsUseCase<T>,
            componentScope: CoroutineScope
        ): ToolPresetsHolder<T> = ToolPresetsHolderImpl(
            useCase = useCase,
            componentScope = componentScope
        )
    }

}

private class ToolPresetsHolderImpl<T : Any>(
    private val useCase: ToolPresetsUseCase<T>,
    private val componentScope: CoroutineScope
) : ToolPresetsHolder<T> {

    override val toolPresets: StateFlow<List<ToolPreset<T>>> = useCase.presets
        .stateIn(componentScope, SharingStarted.Eagerly, emptyList())

    override fun saveToolPreset(name: String) = Unit

    override fun applyToolPreset(preset: ToolPreset<T>) = Unit

    override fun deleteToolPreset(preset: ToolPreset<T>) {
        componentScope.launch {
            useCase.delete(preset)
        }
    }

    override fun exportToolPreset(
        preset: ToolPreset<T>,
        uri: Uri
    ) {
        componentScope.launch {
            useCase.export(
                preset = preset,
                uri = uri.toString()
            )
        }
    }

    override fun shareToolPreset(preset: ToolPreset<T>) {
        componentScope.launch {
            useCase.share(preset)
        }
    }

    override fun importToolPreset(uri: Uri) {
        componentScope.launch {
            when (useCase.import(uri.toString())) {
                ToolPresetImportResult.Success -> AppToastHost.showToast(
                    message = R.string.tool_preset_imported
                )

                ToolPresetImportResult.WrongTool -> AppToastHost.showFailureToast(
                    R.string.tool_preset_wrong_tool
                )

                ToolPresetImportResult.Invalid -> AppToastHost.showFailureToast(
                    R.string.tool_preset_invalid
                )
            }
        }
    }

}
