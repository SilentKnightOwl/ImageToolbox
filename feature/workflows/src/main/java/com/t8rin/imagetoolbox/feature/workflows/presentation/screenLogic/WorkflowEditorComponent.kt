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

package com.t8rin.imagetoolbox.feature.workflows.presentation.screenLogic

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import com.arkivanov.decompose.ComponentContext
import com.t8rin.imagetoolbox.core.domain.coroutines.DispatchersHolder
import com.t8rin.imagetoolbox.core.domain.image.ImageExportProfilesUseCase
import com.t8rin.imagetoolbox.core.domain.image.model.ImageExportProfile
import com.t8rin.imagetoolbox.core.domain.json.JsonParser
import com.t8rin.imagetoolbox.core.domain.presets.ToolPreset
import com.t8rin.imagetoolbox.core.domain.presets.ToolPresetsUseCaseFactory
import com.t8rin.imagetoolbox.core.ui.utils.BaseComponent
import com.t8rin.imagetoolbox.core.ui.utils.navigation.Screen
import com.t8rin.imagetoolbox.feature.ai_tools.domain.model.AI_TOOLS_PRESETS_TOOL_KEY
import com.t8rin.imagetoolbox.feature.ai_tools.domain.model.AiToolsPresetPayload
import com.t8rin.imagetoolbox.feature.recognize.text.domain.OCR_PRESETS_TOOL_KEY
import com.t8rin.imagetoolbox.feature.recognize.text.domain.OcrPresetPayload
import com.t8rin.imagetoolbox.feature.workflows.domain.WorkflowRepository
import com.t8rin.imagetoolbox.feature.workflows.domain.model.OcrStepPayload
import com.t8rin.imagetoolbox.feature.workflows.domain.model.Workflow
import com.t8rin.imagetoolbox.feature.workflows.domain.model.WorkflowStep
import com.t8rin.imagetoolbox.feature.workflows.domain.model.WorkflowToolKeys
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class WorkflowEditorComponent @AssistedInject internal constructor(
    @Assisted componentContext: ComponentContext,
    @Assisted val workflowId: String?,
    @Assisted val onGoBack: () -> Unit,
    @Assisted val onNavigate: (Screen) -> Unit,
    private val workflowRepository: WorkflowRepository,
    private val jsonParser: JsonParser,
    toolPresetsUseCaseFactory: ToolPresetsUseCaseFactory,
    imageExportProfilesUseCase: ImageExportProfilesUseCase,
    dispatchersHolder: DispatchersHolder
) : BaseComponent(dispatchersHolder, componentContext) {

    private var id: String = workflowId ?: UUID.randomUUID().toString()

    private val _name = mutableStateOf("")
    val name: String by _name

    private val _steps = mutableStateOf<List<WorkflowStep>>(emptyList())
    val steps: List<WorkflowStep> by _steps

    private val _isLoaded = mutableStateOf(workflowId == null)
    val isLoaded: Boolean by _isLoaded

    private val _nameError = mutableStateOf(false)
    val nameError: Boolean by _nameError

    val resizePresets: StateFlow<List<ImageExportProfile>> = imageExportProfilesUseCase.profiles
        .stateIn(componentScope, SharingStarted.Eagerly, emptyList())

    private val aiPresetsUseCase = toolPresetsUseCaseFactory.create(
        toolKey = AI_TOOLS_PRESETS_TOOL_KEY,
        payloadType = AiToolsPresetPayload::class.java
    )
    val aiPresets: StateFlow<List<ToolPreset<AiToolsPresetPayload>>> = aiPresetsUseCase.presets
        .stateIn(componentScope, SharingStarted.Eagerly, emptyList())

    private val ocrPresetsUseCase = toolPresetsUseCaseFactory.create(
        toolKey = OCR_PRESETS_TOOL_KEY,
        payloadType = OcrPresetPayload::class.java
    )
    val ocrPresets: StateFlow<List<ToolPreset<OcrPresetPayload>>> = ocrPresetsUseCase.presets
        .stateIn(componentScope, SharingStarted.Eagerly, emptyList())

    init {
        workflowId?.let { wfId ->
            componentScope.launch {
                workflowRepository.getById(wfId)?.let { workflow ->
                    id = workflow.id
                    _name.value = workflow.name
                    _steps.value = workflow.steps
                }
                _isLoaded.value = true
            }
        }
    }

    fun updateName(value: String) {
        _name.value = value
        if (value.isNotBlank()) _nameError.value = false
    }

    fun addStep(toolKey: String) {
        if (toolKey == WorkflowToolKeys.RECOGNIZE_TEXT &&
            _steps.value.any { it.toolKey == WorkflowToolKeys.RECOGNIZE_TEXT }
        ) {
            return
        }

        val payloadJson = when (toolKey) {
            WorkflowToolKeys.RESIZE_CONVERT -> jsonParser.toJson(
                obj = ImageExportProfile(),
                type = ImageExportProfile::class.java
            )

            WorkflowToolKeys.AI_TOOLS -> jsonParser.toJson(
                obj = AiToolsPresetPayload(),
                type = AiToolsPresetPayload::class.java
            )

            WorkflowToolKeys.RECOGNIZE_TEXT -> jsonParser.toJson(
                obj = OcrStepPayload(),
                type = OcrStepPayload::class.java
            )

            else -> null
        }.orEmpty()

        _steps.value = _steps.value + WorkflowStep(toolKey = toolKey, payloadJson = payloadJson)
        normalizeOcrLast()
    }

    fun removeStep(index: Int) {
        _steps.value = _steps.value.toMutableList().apply {
            if (index in indices) removeAt(index)
        }
    }

    fun moveStep(from: Int, to: Int) {
        if (from !in _steps.value.indices || to !in _steps.value.indices) return
        _steps.value = _steps.value.toMutableList().apply {
            add(to, removeAt(from))
        }
        normalizeOcrLast()
    }

    private fun normalizeOcrLast() {
        val list = _steps.value
        val ocrIndex = list.indexOfFirst { it.toolKey == WorkflowToolKeys.RECOGNIZE_TEXT }
        if (ocrIndex in list.indices && ocrIndex != list.lastIndex) {
            _steps.value = list.toMutableList().apply {
                add(removeAt(ocrIndex))
            }
        }
    }

    private fun updateStepAt(index: Int, transform: (WorkflowStep) -> WorkflowStep) {
        _steps.value = _steps.value.toMutableList().apply {
            if (index in indices) this[index] = transform(this[index])
        }
    }

    fun applyResizePreset(index: Int, profile: ImageExportProfile) {
        updateStepAt(index) {
            it.copy(
                payloadJson = jsonParser.toJson(
                    obj = profile,
                    type = ImageExportProfile::class.java
                ).orEmpty(),
                presetName = profile.name
            )
        }
    }

    fun applyAiPreset(index: Int, preset: ToolPreset<AiToolsPresetPayload>) {
        updateStepAt(index) {
            it.copy(
                payloadJson = jsonParser.toJson(
                    obj = preset.payload,
                    type = AiToolsPresetPayload::class.java
                ).orEmpty(),
                presetName = preset.name
            )
        }
    }

    fun applyOcrPreset(index: Int, preset: ToolPreset<OcrPresetPayload>) {
        updateStepAt(index) { step ->
            val current = ocrPayload(step) ?: OcrStepPayload()
            step.copy(
                payloadJson = jsonParser.toJson(
                    obj = current.copy(preset = preset.payload),
                    type = OcrStepPayload::class.java
                ).orEmpty(),
                presetName = preset.name
            )
        }
    }

    fun updateResizePayload(index: Int, profile: ImageExportProfile) {
        updateStepAt(index) {
            it.copy(
                payloadJson = jsonParser.toJson(
                    obj = profile,
                    type = ImageExportProfile::class.java
                ).orEmpty(),
                presetName = null
            )
        }
    }

    fun updateAiPayload(index: Int, payload: AiToolsPresetPayload) {
        updateStepAt(index) {
            it.copy(
                payloadJson = jsonParser.toJson(
                    obj = payload,
                    type = AiToolsPresetPayload::class.java
                ).orEmpty(),
                presetName = null
            )
        }
    }

    fun resizePayload(step: WorkflowStep): ImageExportProfile? = runCatching {
        jsonParser.fromJson<ImageExportProfile>(
            json = step.payloadJson,
            type = ImageExportProfile::class.java
        )
    }.getOrNull()

    fun aiPayload(step: WorkflowStep): AiToolsPresetPayload? = runCatching {
        jsonParser.fromJson<AiToolsPresetPayload>(
            json = step.payloadJson,
            type = AiToolsPresetPayload::class.java
        )
    }.getOrNull()

    fun ocrPayload(step: WorkflowStep): OcrStepPayload? = runCatching {
        jsonParser.fromJson<OcrStepPayload>(
            json = step.payloadJson,
            type = OcrStepPayload::class.java
        )
    }.getOrNull()

    fun save() {
        if (_name.value.isBlank()) {
            _nameError.value = true
            return
        }
        componentScope.launch {
            workflowRepository.upsert(
                Workflow(
                    id = id,
                    name = _name.value.trim(),
                    steps = _steps.value
                )
            )
            onGoBack()
        }
    }

    @AssistedFactory
    fun interface Factory {
        operator fun invoke(
            componentContext: ComponentContext,
            workflowId: String?,
            onGoBack: () -> Unit,
            onNavigate: (Screen) -> Unit,
        ): WorkflowEditorComponent
    }

}
