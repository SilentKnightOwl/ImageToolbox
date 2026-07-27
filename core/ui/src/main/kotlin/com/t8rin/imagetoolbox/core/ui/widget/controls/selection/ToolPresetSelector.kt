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

package com.t8rin.imagetoolbox.core.ui.widget.controls.selection

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.t8rin.imagetoolbox.core.domain.model.MimeType
import com.t8rin.imagetoolbox.core.domain.presets.ToolPreset
import com.t8rin.imagetoolbox.core.resources.Icons
import com.t8rin.imagetoolbox.core.resources.R
import com.t8rin.imagetoolbox.core.resources.icons.Delete
import com.t8rin.imagetoolbox.core.resources.icons.DownloadFile
import com.t8rin.imagetoolbox.core.resources.icons.Loyalty
import com.t8rin.imagetoolbox.core.resources.icons.MoreVert
import com.t8rin.imagetoolbox.core.resources.icons.Save
import com.t8rin.imagetoolbox.core.resources.icons.Share
import com.t8rin.imagetoolbox.core.resources.icons.UploadFile
import com.t8rin.imagetoolbox.core.ui.utils.ToolPresetsHolder
import com.t8rin.imagetoolbox.core.ui.utils.content_pickers.rememberFileCreator
import com.t8rin.imagetoolbox.core.ui.utils.content_pickers.rememberFilePicker
import com.t8rin.imagetoolbox.core.ui.widget.enhanced.EnhancedAlertDialog
import com.t8rin.imagetoolbox.core.ui.widget.enhanced.EnhancedButton
import com.t8rin.imagetoolbox.core.ui.widget.enhanced.EnhancedChip
import com.t8rin.imagetoolbox.core.ui.widget.enhanced.EnhancedDropdownMenu
import com.t8rin.imagetoolbox.core.ui.widget.enhanced.EnhancedIconButton
import com.t8rin.imagetoolbox.core.ui.widget.enhanced.EnhancedModalBottomSheet
import com.t8rin.imagetoolbox.core.ui.widget.enhanced.hapticsClickable
import com.t8rin.imagetoolbox.core.ui.widget.modifier.ShapeDefaults
import com.t8rin.imagetoolbox.core.ui.widget.modifier.animateContentSizeNoClip
import com.t8rin.imagetoolbox.core.ui.widget.modifier.clearFocusOnTap
import com.t8rin.imagetoolbox.core.ui.widget.modifier.container
import com.t8rin.imagetoolbox.core.ui.widget.other.RevealDirection
import com.t8rin.imagetoolbox.core.ui.widget.other.RevealValue
import com.t8rin.imagetoolbox.core.ui.widget.other.SwipeToReveal
import com.t8rin.imagetoolbox.core.ui.widget.other.rememberRevealState
import com.t8rin.imagetoolbox.core.ui.widget.preferences.PreferenceItemOverload
import com.t8rin.imagetoolbox.core.ui.widget.text.RoundedTextField
import com.t8rin.imagetoolbox.core.ui.widget.text.TitleItem
import kotlinx.coroutines.launch

@Composable
fun <T : Any> ToolPresetSelector(
    holder: ToolPresetsHolder<T>,
    modifier: Modifier = Modifier,
    subtitleProvider: (ToolPreset<T>) -> String? = { null }
) {
    val presets by holder.toolPresets.collectAsState()

    var showSheet by rememberSaveable { mutableStateOf(false) }
    var presetToDelete by remember { mutableStateOf<ToolPreset<T>?>(null) }
    val importPicker = rememberFilePicker(
        mimeType = MimeType.All,
        onSuccess = holder::importToolPreset
    )

    EnhancedChip(
        selected = false,
        onClick = { showSheet = true },
        selectedColor = MaterialTheme.colorScheme.primary,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Outlined.Loyalty,
            contentDescription = stringResource(R.string.tool_presets)
        )
    }

    EnhancedModalBottomSheet(
        visible = showSheet,
        onDismiss = { showSheet = it },
        title = {
            TitleItem(
                icon = Icons.Rounded.Loyalty,
                text = stringResource(R.string.tool_presets)
            )
        },
        confirmButton = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                EnhancedIconButton(
                    onClick = importPicker::pickFile,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Outlined.UploadFile,
                        contentDescription = stringResource(R.string.import_word)
                    )
                }
                EnhancedButton(
                    onClick = {
                        showSheet = false
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(12.dp),
            modifier = Modifier
                .animateContentSizeNoClip()
                .clearFocusOnTap()
        ) {
            item("AddToolPresetBlock") {
                AddToolPresetBlock(
                    onSave = holder::saveToolPreset,
                    modifier = Modifier
                        .padding(
                            bottom = 4.dp
                        )
                        .animateItem()
                )
            }
            itemsIndexed(
                items = presets,
                key = { _, item -> item.name }
            ) { index, item ->
                ToolPresetItem(
                    index = index,
                    presetsCount = presets.size,
                    item = item,
                    subtitle = subtitleProvider(item),
                    onApplyPreset = holder::applyToolPreset,
                    onExportPreset = holder::exportToolPreset,
                    onSharePreset = holder::shareToolPreset,
                    onWantDelete = { presetToDelete = it }
                )
            }
        }
    }

    EnhancedAlertDialog(
        visible = presetToDelete != null,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = null
            )
        },
        title = {
            Text(stringResource(R.string.delete_tool_preset))
        },
        text = {
            Text(
                stringResource(
                    R.string.delete_tool_preset_sub,
                    presetToDelete?.name ?: ""
                )
            )
        },
        onDismissRequest = { presetToDelete = null },
        confirmButton = {
            EnhancedButton(
                onClick = {
                    presetToDelete?.let(holder::deleteToolPreset)
                    presetToDelete = null
                }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            EnhancedButton(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                onClick = { presetToDelete = null }
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
        placeAboveAll = true
    )
}

@Composable
private fun <T : Any> LazyItemScope.ToolPresetItem(
    index: Int,
    presetsCount: Int,
    item: ToolPreset<T>,
    subtitle: String?,
    onApplyPreset: (ToolPreset<T>) -> Unit,
    onExportPreset: (ToolPreset<T>, Uri) -> Unit,
    onSharePreset: (ToolPreset<T>) -> Unit,
    onWantDelete: (ToolPreset<T>) -> Unit
) {
    val scope = rememberCoroutineScope()
    val state = rememberRevealState()
    val shape = ShapeDefaults.byIndex(index, presetsCount)

    SwipeToReveal(
        state = state,
        directions = setOf(RevealDirection.EndToStart),
        modifier = Modifier.animateItem(),
        revealedContentEnd = {
            Box(
                Modifier
                    .fillMaxSize()
                    .container(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = shape,
                        autoShadowElevation = 0.dp,
                        resultPadding = 0.dp
                    )
                    .hapticsClickable {
                        scope.launch {
                            state.animateTo(RevealValue.Default)
                        }
                        onWantDelete(item)
                    }
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.delete),
                    modifier = Modifier
                        .padding(16.dp)
                        .padding(end = 8.dp)
                        .align(Alignment.CenterEnd),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        },
        swipeableContent = {
            PreferenceItemOverload(
                title = item.name,
                subtitle = subtitle,
                onClick = {
                    onApplyPreset(item)
                },
                drawStartIconContainer = false,
                modifier = Modifier.fillMaxWidth(),
                startIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Loyalty,
                        contentDescription = null
                    )
                },
                endIcon = {
                    ToolPresetItemMenu(
                        preset = item,
                        onExportPreset = onExportPreset,
                        onSharePreset = onSharePreset,
                        modifier = Modifier.offset(x = 8.dp)
                    )
                },
                shape = shape,
                containerColor = Color.Unspecified,
                contentColor = Color.Unspecified
            )
        }
    )
}

@Composable
private fun AddToolPresetBlock(
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var name by rememberSaveable { mutableStateOf("") }
    val canSave = name.isNotBlank()

    Column(
        modifier = modifier
            .container(
                shape = ShapeDefaults.default,
                resultPadding = 16.dp
            )
    ) {
        TitleItem(
            icon = Icons.Outlined.Loyalty,
            text = stringResource(R.string.save_tool_preset),
            modifier = Modifier
        )

        Spacer(Modifier.height(12.dp))

        RoundedTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.name),
            endIcon = {
                EnhancedIconButton(
                    onClick = {
                        onSave(name)
                        name = ""
                    },
                    enabled = canSave
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Save,
                        contentDescription = null
                    )
                }
            },
            singleLine = true
        )
    }
}

@Composable
private fun <T : Any> ToolPresetItemMenu(
    preset: ToolPreset<T>,
    onExportPreset: (ToolPreset<T>, Uri) -> Unit,
    onSharePreset: (ToolPreset<T>) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by rememberSaveable(preset.name) { mutableStateOf(false) }
    val exportPicker = rememberFileCreator(
        mimeType = MimeType.All,
        onSuccess = { uri ->
            onExportPreset(preset, uri)
        }
    )

    Box(
        modifier = modifier
    ) {
        EnhancedIconButton(onClick = { showMenu = true }) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = null
            )
        }
        EnhancedDropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            shape = ShapeDefaults.large
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .padding(horizontal = 8.dp)
            ) {
                ToolPresetMenuAction(
                    title = R.string.export,
                    icon = Icons.Outlined.DownloadFile,
                    shape = ShapeDefaults.top,
                    color = MaterialTheme.colorScheme.primary,
                    onClick = {
                        showMenu = false
                        exportPicker.make("${preset.name.safeFileName()}.itpreset")
                    }
                )
                ToolPresetMenuAction(
                    title = R.string.share,
                    icon = Icons.Outlined.Share,
                    shape = ShapeDefaults.bottom,
                    color = MaterialTheme.colorScheme.secondary,
                    onClick = {
                        showMenu = false
                        onSharePreset(preset)
                    }
                )
            }
        }
    }
}

@Composable
private fun ToolPresetMenuAction(
    title: Int,
    icon: ImageVector,
    shape: Shape,
    color: Color,
    onClick: () -> Unit
) {
    EnhancedButton(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = shape,
        containerColor = color
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null
        )
        Spacer(Modifier.width(8.dp))
        Text(stringResource(title))
    }
}

private fun String.safeFileName(): String = trim()
    .replace(Regex("""[^\w.-]+"""), "_")
    .trim('_')
    .ifBlank { "preset" }
