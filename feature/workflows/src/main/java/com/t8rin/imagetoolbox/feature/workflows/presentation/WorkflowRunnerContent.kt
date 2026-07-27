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

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.t8rin.imagetoolbox.core.resources.Icons
import com.t8rin.imagetoolbox.core.resources.R
import com.t8rin.imagetoolbox.core.resources.icons.ArrowBack
import com.t8rin.imagetoolbox.core.resources.icons.CheckCircle
import com.t8rin.imagetoolbox.core.resources.icons.VectorPolyline
import com.t8rin.imagetoolbox.core.resources.icons.WarningAmber
import com.t8rin.imagetoolbox.core.ui.utils.content_pickers.rememberImagePicker
import com.t8rin.imagetoolbox.core.ui.utils.navigation.Screen
import com.t8rin.imagetoolbox.core.ui.widget.enhanced.EnhancedAlertDialog
import com.t8rin.imagetoolbox.core.ui.widget.enhanced.EnhancedButton
import com.t8rin.imagetoolbox.core.ui.widget.enhanced.EnhancedIconButton
import com.t8rin.imagetoolbox.core.ui.widget.enhanced.EnhancedTopAppBar
import com.t8rin.imagetoolbox.core.ui.widget.text.marquee
import com.t8rin.imagetoolbox.feature.workflows.domain.WorkflowValidationIssue
import com.t8rin.imagetoolbox.feature.workflows.domain.model.WorkflowToolKeys
import com.t8rin.imagetoolbox.feature.workflows.presentation.screenLogic.RunnerState
import com.t8rin.imagetoolbox.feature.workflows.presentation.screenLogic.WorkflowRunnerComponent

@Composable
fun WorkflowRunnerContent(
    component: WorkflowRunnerComponent
) {
    var showStopDialog by rememberSaveable { mutableStateOf(false) }

    val imagePicker = rememberImagePicker { uris: List<Uri> ->
        component.startRun(uris)
    }

    val state = component.state
    val workflow = component.workflow

    val onBack: () -> Unit = {
        if (state is RunnerState.Running) {
            showStopDialog = true
        } else {
            component.onGoBack()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            EnhancedTopAppBar(
                title = {
                    Text(
                        text = workflow?.name ?: stringResource(R.string.workflow_runner),
                        modifier = Modifier.marquee()
                    )
                },
                navigationIcon = {
                    EnhancedIconButton(
                        onClick = onBack
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.Center
        ) {
            when (val s = state) {
                RunnerState.Loading -> CircularProgressIndicator()

                RunnerState.AwaitingImages -> AwaitingImagesContent(
                    onPickImages = imagePicker::pickImage
                )

                is RunnerState.Invalid -> InvalidContent(
                    issues = s.issues,
                    onEdit = {
                        component.onNavigate(Screen.WorkflowEditor(component.workflowId))
                    },
                    onOpenAiTools = {
                        component.onNavigate(Screen.AiTools())
                    }
                )

                is RunnerState.Running -> RunningContent(
                    state = s
                )

                is RunnerState.Done -> DoneContent(
                    state = s,
                    onShare = component::shareResults,
                    onRunAgain = component::runAgain,
                    onClose = component.onGoBack
                )
            }
        }
    }

    EnhancedAlertDialog(
        visible = showStopDialog,
        onDismissRequest = { showStopDialog = false },
        icon = {
            Icon(
                imageVector = Icons.Rounded.WarningAmber,
                contentDescription = null
            )
        },
        title = {
            Text(stringResource(R.string.running_workflow))
        },
        text = {
            Text(stringResource(R.string.workflow_stop_confirmation))
        },
        confirmButton = {
            EnhancedButton(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                onClick = {
                    showStopDialog = false
                    component.onGoBack()
                }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            EnhancedButton(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                onClick = { showStopDialog = false }
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun AwaitingImagesContent(
    onPickImages: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.VectorPolyline,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        EnhancedButton(
            onClick = onPickImages
        ) {
            Text(stringResource(R.string.pick_images))
        }
    }
}

@Composable
private fun InvalidContent(
    issues: List<WorkflowValidationIssue>,
    onEdit: () -> Unit,
    onOpenAiTools: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(32.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Icon(
            imageVector = Icons.Rounded.WarningAmber,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(12.dp))
        issues.forEach { issue ->
            Text(
                text = issue.message(),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EnhancedButton(
                onClick = onEdit
            ) {
                Text(stringResource(R.string.edit))
            }
            if (issues.any { it is WorkflowValidationIssue.MissingAiModel }) {
                EnhancedButton(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    onClick = onOpenAiTools
                ) {
                    Text(stringResource(R.string.ai_tools))
                }
            }
        }
    }
}

@Composable
private fun WorkflowValidationIssue.message(): String = when (this) {
    is WorkflowValidationIssue.MissingAiModel -> stringResource(
        R.string.workflow_model_missing,
        modelName
    )

    WorkflowValidationIssue.EmptyWorkflow,
    WorkflowValidationIssue.OcrNotLast,
    is WorkflowValidationIssue.UnknownStep -> stringResource(R.string.workflow_invalid)
}

@Composable
private fun RunningContent(
    state: RunnerState.Running
) {
    val screen = toolKeyScreen(state.toolKey)
    val overallProgress = remember(state.imageIndex, state.stepIndex, state.imageCount, state.stepCount) {
        val completedSteps = state.imageIndex * state.stepCount + state.stepIndex
        val totalSteps = (state.imageCount * state.stepCount).coerceAtLeast(1)
        completedSteps / totalSteps.toFloat()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(32.dp)
            .fillMaxWidth()
    ) {
        screen?.icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(12.dp))
        }
        Text(
            text = screen?.let { stringResource(it.title) } ?: state.toolKey,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(
                R.string.workflow_image_of,
                state.imageIndex + 1,
                state.imageCount
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(
                R.string.workflow_step_of,
                state.stepIndex + 1,
                state.stepCount
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        LinearWavyProgressIndicator(
            progress = { overallProgress },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        val stepPercent = state.stepPercent
        if (stepPercent != null) {
            Text("$stepPercent%")
            LinearWavyProgressIndicator(
                progress = { stepPercent / 100f },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            LinearWavyProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun DoneContent(
    state: RunnerState.Done,
    onShare: () -> Unit,
    onRunAgain: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(32.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.workflow_done),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(12.dp))
        state.savedPaths.forEach { path ->
            Text(
                text = stringResource(R.string.workflow_saved_to, path),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
        if (state.failureCount > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.workflow_failed_images, state.failureCount),
                color = MaterialTheme.colorScheme.error
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.savedUris.isNotEmpty()) {
                EnhancedButton(
                    onClick = onShare
                ) {
                    Text(stringResource(R.string.share))
                }
            }
            EnhancedButton(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                onClick = onRunAgain
            ) {
                Text(stringResource(R.string.run_again))
            }
            EnhancedButton(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                onClick = onClose
            ) {
                Text(stringResource(R.string.close))
            }
        }
    }
}

private fun toolKeyScreen(toolKey: String): Screen? = when (toolKey) {
    WorkflowToolKeys.RESIZE_CONVERT -> Screen.ResizeAndConvert()
    WorkflowToolKeys.AI_TOOLS -> Screen.AiTools()
    WorkflowToolKeys.RECOGNIZE_TEXT -> Screen.RecognizeText()
    else -> null
}
