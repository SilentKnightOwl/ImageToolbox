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

package com.t8rin.imagetoolbox.feature.workflows.presentation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.t8rin.imagetoolbox.core.domain.image.model.ImageExportProfile
import com.t8rin.imagetoolbox.core.domain.presets.ToolPreset
import com.t8rin.imagetoolbox.core.resources.Icons
import com.t8rin.imagetoolbox.core.resources.R
import com.t8rin.imagetoolbox.core.resources.icons.Add
import com.t8rin.imagetoolbox.core.resources.icons.ArrowBack
import com.t8rin.imagetoolbox.core.resources.icons.Cube
import com.t8rin.imagetoolbox.core.resources.icons.Delete
import com.t8rin.imagetoolbox.core.resources.icons.DragHandle
import com.t8rin.imagetoolbox.core.resources.icons.Loyalty
import com.t8rin.imagetoolbox.core.resources.icons.Save
import com.t8rin.imagetoolbox.core.resources.icons.Stacks
import com.t8rin.imagetoolbox.core.ui.utils.navigation.Screen
import com.t8rin.imagetoolbox.core.ui.widget.controls.ResizeImageField
import com.t8rin.imagetoolbox.core.ui.widget.controls.selection.ImageFormatSelector
import com.t8rin.imagetoolbox.core.ui.widget.controls.selection.QualitySelector
import com.t8rin.imagetoolbox.core.ui.widget.enhanced.EnhancedButton
import com.t8rin.imagetoolbox.core.ui.widget.enhanced.EnhancedChip
import com.t8rin.imagetoolbox.core.ui.widget.enhanced.EnhancedIconButton
import com.t8rin.imagetoolbox.core.ui.widget.enhanced.EnhancedModalBottomSheet
import com.t8rin.imagetoolbox.core.ui.widget.enhanced.EnhancedSliderItem
import com.t8rin.imagetoolbox.core.ui.widget.enhanced.EnhancedTopAppBar
import com.t8rin.imagetoolbox.core.ui.widget.enhanced.derivative.OnlyAllowedSliderItem
import com.t8rin.imagetoolbox.core.ui.widget.enhanced.press
import com.t8rin.imagetoolbox.core.ui.widget.modifier.ShapeDefaults
import com.t8rin.imagetoolbox.core.ui.widget.other.ExpandableItem
import com.t8rin.imagetoolbox.core.ui.widget.preferences.PreferenceItem
import com.t8rin.imagetoolbox.core.ui.widget.preferences.PreferenceItemDefaults
import com.t8rin.imagetoolbox.core.ui.widget.preferences.PreferenceRowSwitch
import com.t8rin.imagetoolbox.core.ui.widget.text.RoundedTextField
import com.t8rin.imagetoolbox.core.ui.widget.text.TitleItem
import com.t8rin.imagetoolbox.core.ui.widget.text.marquee
import com.t8rin.imagetoolbox.feature.ai_tools.domain.model.AiToolsPresetPayload
import com.t8rin.imagetoolbox.feature.recognize.text.domain.OcrPresetPayload
import com.t8rin.imagetoolbox.feature.workflows.domain.model.OcrStepPayload
import com.t8rin.imagetoolbox.feature.workflows.domain.model.WorkflowStep
import com.t8rin.imagetoolbox.feature.workflows.domain.model.WorkflowToolKeys
import com.t8rin.imagetoolbox.feature.workflows.presentation.screenLogic.WorkflowEditorComponent
import kotlinx.collections.immutable.toPersistentMap
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.roundToInt

@Composable
fun WorkflowEditorContent(
    component: WorkflowEditorComponent
) {
    val resizePresets by component.resizePresets.collectAsStateWithLifecycle()
    val aiPresets by component.aiPresets.collectAsStateWithLifecycle()
    val ocrPresets by component.ocrPresets.collectAsStateWithLifecycle()

    var showAddStepSheet by rememberSaveable { mutableStateOf(false) }
    var presetPickerIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            haptics.press()
            component.moveStep(from.index, to.index)
        }
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            EnhancedTopAppBar(
                title = {
                    Text(
                        text = component.name.ifBlank { stringResource(R.string.workflow_editor) },
                        modifier = Modifier.marquee()
                    )
                },
                navigationIcon = {
                    EnhancedIconButton(
                        onClick = component.onGoBack
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                actions = {
                    EnhancedIconButton(
                        onClick = component::save
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Save,
                            contentDescription = stringResource(R.string.save)
                        )
                    }
                }
            )
        }
    ) { contentPadding ->
        if (!component.isLoaded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding + PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "name") {
                    RoundedTextField(
                        value = component.name,
                        onValueChange = component::updateName,
                        label = stringResource(R.string.workflow_name),
                        isError = component.nameError,
                        supportingText = { isError ->
                            if (isError) {
                                Text(stringResource(R.string.workflow_name_required))
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                    )
                }

                itemsIndexed(
                    items = component.steps,
                    key = { index, _ -> index }
                ) { index, step ->
                    ReorderableItem(
                        state = reorderState,
                        key = index
                    ) { isDragging ->
                        WorkflowStepCard(
                            component = component,
                            index = index,
                            step = step,
                            isDragging = isDragging,
                            onPickPreset = { presetPickerIndex = index },
                            onRemove = { component.removeStep(index) }
                        )
                    }
                }

                item(key = "add_step") {
                    EnhancedButton(
                        onClick = { showAddStepSheet = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.add_step))
                    }
                }
            }
        }
    }

    AddStepSheet(
        visible = showAddStepSheet,
        onDismiss = { showAddStepSheet = it },
        hasOcrStep = component.steps.any { it.toolKey == WorkflowToolKeys.RECOGNIZE_TEXT },
        onPick = { toolKey ->
            component.addStep(toolKey)
            showAddStepSheet = false
        }
    )

    val pickerIndex = presetPickerIndex
    val pickerStep = pickerIndex?.let { component.steps.getOrNull(it) }

    PresetPickerSheet(
        visible = pickerStep != null,
        toolKey = pickerStep?.toolKey.orEmpty(),
        resizePresets = resizePresets,
        aiPresets = aiPresets,
        ocrPresets = ocrPresets,
        onDismiss = { presetPickerIndex = null },
        onPickResize = { profile ->
            pickerIndex?.let { component.applyResizePreset(it, profile) }
            presetPickerIndex = null
        },
        onPickAi = { preset ->
            pickerIndex?.let { component.applyAiPreset(it, preset) }
            presetPickerIndex = null
        },
        onPickOcr = { preset ->
            pickerIndex?.let { component.applyOcrPreset(it, preset) }
            presetPickerIndex = null
        }
    )
}

@Composable
private fun ReorderableCollectionItemScope.WorkflowStepCard(
    component: WorkflowEditorComponent,
    index: Int,
    step: WorkflowStep,
    isDragging: Boolean,
    onPickPreset: () -> Unit,
    onRemove: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val isOcr = step.toolKey == WorkflowToolKeys.RECOGNIZE_TEXT
    val screen = toolKeyScreen(step.toolKey)
    val scale by animateFloatAsState(if (isDragging) 1.02f else 1f)

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        ExpandableItem(
            modifier = Modifier
                .fillMaxWidth()
                .scale(scale),
            color = MaterialTheme.colorScheme.surfaceContainer,
            visibleContent = { _ ->
                screen?.icon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
                Text(
                    text = screen?.let { stringResource(it.title) } ?: step.toolKey,
                    style = PreferenceItemDefaults.TitleFontStyle,
                    modifier = Modifier.weight(1f)
                )
                EnhancedChip(
                    selected = step.presetName != null,
                    onClick = onPickPreset,
                    selectedColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Text(
                        text = step.presetName ?: stringResource(R.string.pick_preset),
                        maxLines = 1
                    )
                }
                EnhancedIconButton(
                    onClick = onRemove
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.remove_step)
                    )
                }
                if (!isOcr) {
                    Icon(
                        imageVector = Icons.Rounded.DragHandle,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .draggableHandle(
                                onDragStarted = { haptics.press() },
                                onDragStopped = {}
                            )
                    )
                }
            },
            expandableContent = { _ ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StepSettings(
                        component = component,
                        index = index,
                        step = step
                    )
                }
            }
        )
        if (isOcr) {
            Text(
                text = stringResource(R.string.ocr_must_be_last),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
            )
        }
    }
}

@Composable
private fun StepSettings(
    component: WorkflowEditorComponent,
    index: Int,
    step: WorkflowStep
) {
    when (step.toolKey) {
        WorkflowToolKeys.RESIZE_CONVERT -> {
            val profile = component.resizePayload(step) ?: ImageExportProfile()
            val imageInfo = profile.imageInfo

            ResizeImageField(
                imageInfo = imageInfo,
                originalSize = null,
                onWidthChange = {
                    component.updateResizePayload(
                        index,
                        profile.copy(imageInfo = imageInfo.copy(width = it))
                    )
                },
                onHeightChange = {
                    component.updateResizePayload(
                        index,
                        profile.copy(imageInfo = imageInfo.copy(height = it))
                    )
                }
            )
            QualitySelector(
                imageFormat = imageInfo.imageFormat,
                quality = imageInfo.quality,
                onQualityChange = {
                    component.updateResizePayload(
                        index,
                        profile.copy(imageInfo = imageInfo.copy(quality = it))
                    )
                }
            )
            ImageFormatSelector(
                value = imageInfo.imageFormat,
                quality = imageInfo.quality,
                onValueChange = {
                    component.updateResizePayload(
                        index,
                        profile.copy(imageInfo = imageInfo.copy(imageFormat = it))
                    )
                }
            )
        }

        WorkflowToolKeys.AI_TOOLS -> {
            val payload = component.aiPayload(step) ?: AiToolsPresetPayload()

            Text(
                text = payload.modelName ?: "\u2014",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            EnhancedSliderItem(
                value = payload.strength,
                title = stringResource(R.string.strength),
                valueRange = 0f..100f,
                steps = 100,
                internalStateTransformation = { it.roundToInt() },
                onValueChange = {
                    component.updateAiPayload(index, payload.copy(strength = it))
                }
            )

            val chunkPowers = remember {
                generateSequence(128) { it * 2 }.takeWhile { it <= 2048 }.toList()
            }
            val overlapPowers = remember {
                generateSequence(16) { it * 2 }.takeWhile { it <= 128 }.toList()
            }

            OnlyAllowedSliderItem(
                label = stringResource(R.string.chunk_size),
                icon = Icons.Outlined.Cube,
                value = payload.chunkSize,
                allowed = chunkPowers,
                onValueChange = {
                    component.updateAiPayload(index, payload.copy(chunkSize = it))
                }
            )
            OnlyAllowedSliderItem(
                label = stringResource(R.string.overlap_size),
                icon = Icons.Outlined.Stacks,
                value = payload.overlap,
                allowed = overlapPowers,
                maxAllowed = payload.chunkSize,
                onValueChange = {
                    component.updateAiPayload(index, payload.copy(overlap = it))
                }
            )
            PreferenceRowSwitch(
                title = stringResource(R.string.enable_chunking),
                checked = payload.enableChunking,
                onClick = {
                    component.updateAiPayload(index, payload.copy(enableChunking = it))
                }
            )

            val workerOptions = remember { listOf(0) + (1..8) }
            val workerIndex = workerOptions.indexOf(
                payload.parallelWorkers.coerceIn(0, 8)
            ).coerceAtLeast(0)
            val auto = stringResource(R.string.auto)

            EnhancedSliderItem(
                value = workerIndex,
                title = stringResource(R.string.parallel_workers),
                valueRange = 0f..workerOptions.lastIndex.toFloat(),
                steps = (workerOptions.size - 2).coerceAtLeast(0),
                internalStateTransformation = { it.roundToInt() },
                canInputValue = false,
                valuesPreviewMapping = remember(workerOptions, auto) {
                    buildMap {
                        workerOptions.forEachIndexed { i, workers ->
                            put(i.toFloat(), if (workers == 0) auto else workers.toString())
                        }
                    }.toPersistentMap()
                },
                onValueChange = {
                    val idx = it.roundToInt().coerceIn(workerOptions.indices)
                    component.updateAiPayload(
                        index,
                        payload.copy(parallelWorkers = workerOptions[idx])
                    )
                }
            )
        }

        WorkflowToolKeys.RECOGNIZE_TEXT -> {
            val ocrStep = component.ocrPayload(step) ?: OcrStepPayload()
            val preset = ocrStep.preset

            Text(
                text = preset.recognitionEngine.ifBlank { "\u2014" },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Text(
                text = stringResource(R.string.language) + ": " +
                        preset.languageCodes.joinToString().ifBlank { "\u2014" },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            PreferenceItem(
                enabled = false,
                title = stringResource(R.string.output_mode),
                subtitle = "TXT"
            )
        }
    }
}

@Composable
private fun AddStepSheet(
    visible: Boolean,
    onDismiss: (Boolean) -> Unit,
    hasOcrStep: Boolean,
    onPick: (String) -> Unit
) {
    EnhancedModalBottomSheet(
        visible = visible,
        onDismiss = onDismiss,
        title = {
            TitleItem(
                icon = Icons.Rounded.Add,
                text = stringResource(R.string.add_step)
            )
        },
        confirmButton = {
            EnhancedButton(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                onClick = { onDismiss(false) }
            ) {
                Text(stringResource(R.string.close))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            WorkflowToolKeys.ALL.forEachIndexed { i, toolKey ->
                val screen = toolKeyScreen(toolKey)
                val disabled = toolKey == WorkflowToolKeys.RECOGNIZE_TEXT && hasOcrStep

                PreferenceItem(
                    enabled = !disabled,
                    onClick = { if (!disabled) onPick(toolKey) },
                    startIcon = screen?.icon,
                    title = screen?.let { stringResource(it.title) } ?: toolKey,
                    shape = ShapeDefaults.byIndex(i, WorkflowToolKeys.ALL.size),
                    modifier = Modifier.alpha(if (disabled) 0.5f else 1f)
                )
            }
        }
    }
}

@Composable
private fun PresetPickerSheet(
    visible: Boolean,
    toolKey: String,
    resizePresets: List<ImageExportProfile>,
    aiPresets: List<ToolPreset<AiToolsPresetPayload>>,
    ocrPresets: List<ToolPreset<OcrPresetPayload>>,
    onDismiss: (Boolean) -> Unit,
    onPickResize: (ImageExportProfile) -> Unit,
    onPickAi: (ToolPreset<AiToolsPresetPayload>) -> Unit,
    onPickOcr: (ToolPreset<OcrPresetPayload>) -> Unit
) {
    EnhancedModalBottomSheet(
        visible = visible,
        onDismiss = onDismiss,
        title = {
            TitleItem(
                icon = Icons.Rounded.Loyalty,
                text = stringResource(R.string.pick_preset)
            )
        },
        confirmButton = {
            EnhancedButton(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                onClick = { onDismiss(false) }
            ) {
                Text(stringResource(R.string.close))
            }
        }
    ) {
        when (toolKey) {
            WorkflowToolKeys.RESIZE_CONVERT -> {
                if (resizePresets.isEmpty()) {
                    EmptyPresetsHint()
                } else {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        resizePresets.forEachIndexed { i, preset ->
                            PreferenceItem(
                                onClick = { onPickResize(preset) },
                                title = preset.name,
                                shape = ShapeDefaults.byIndex(i, resizePresets.size)
                            )
                        }
                    }
                }
            }

            WorkflowToolKeys.AI_TOOLS -> {
                if (aiPresets.isEmpty()) {
                    EmptyPresetsHint()
                } else {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        aiPresets.forEachIndexed { i, preset ->
                            PreferenceItem(
                                onClick = { onPickAi(preset) },
                                title = preset.name,
                                shape = ShapeDefaults.byIndex(i, aiPresets.size)
                            )
                        }
                    }
                }
            }

            WorkflowToolKeys.RECOGNIZE_TEXT -> {
                if (ocrPresets.isEmpty()) {
                    EmptyPresetsHint()
                } else {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ocrPresets.forEachIndexed { i, preset ->
                            PreferenceItem(
                                onClick = { onPickOcr(preset) },
                                title = preset.name,
                                shape = ShapeDefaults.byIndex(i, ocrPresets.size)
                            )
                        }
                    }
                }
            }

            else -> Unit
        }
    }
}

@Composable
private fun EmptyPresetsHint() {
    Text(
        text = stringResource(R.string.no_presets_available),
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

private fun toolKeyScreen(toolKey: String): Screen? = when (toolKey) {
    WorkflowToolKeys.RESIZE_CONVERT -> Screen.ResizeAndConvert()
    WorkflowToolKeys.AI_TOOLS -> Screen.AiTools()
    WorkflowToolKeys.RECOGNIZE_TEXT -> Screen.RecognizeText()
    else -> null
}
