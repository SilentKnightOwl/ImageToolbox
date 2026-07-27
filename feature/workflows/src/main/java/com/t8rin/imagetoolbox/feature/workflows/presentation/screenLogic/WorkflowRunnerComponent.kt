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

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import com.arkivanov.decompose.ComponentContext
import com.t8rin.imagetoolbox.core.domain.coroutines.DispatchersHolder
import com.t8rin.imagetoolbox.core.domain.image.ShareProvider
import com.t8rin.imagetoolbox.core.domain.saving.updateProgress
import com.t8rin.imagetoolbox.core.resources.R
import com.t8rin.imagetoolbox.core.ui.utils.BaseComponent
import com.t8rin.imagetoolbox.core.ui.utils.helper.AppToastHost
import com.t8rin.imagetoolbox.core.ui.utils.navigation.Screen
import com.t8rin.imagetoolbox.core.utils.getString
import com.t8rin.imagetoolbox.feature.workflows.domain.WorkflowEngine
import com.t8rin.imagetoolbox.feature.workflows.domain.WorkflowRepository
import com.t8rin.imagetoolbox.feature.workflows.domain.WorkflowRunEvent
import com.t8rin.imagetoolbox.feature.workflows.domain.WorkflowValidationIssue
import com.t8rin.imagetoolbox.feature.workflows.domain.model.Workflow
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

sealed interface RunnerState {
    data object Loading : RunnerState
    data object AwaitingImages : RunnerState
    data class Invalid(val issues: List<WorkflowValidationIssue>) : RunnerState
    data class Running(
        val imageIndex: Int,
        val imageCount: Int,
        val stepIndex: Int,
        val stepCount: Int,
        val toolKey: String,
        val stepPercent: Int?
    ) : RunnerState

    data class Done(
        val savedPaths: List<String>,
        val savedUris: List<String>,
        val failureCount: Int
    ) : RunnerState
}

class WorkflowRunnerComponent @AssistedInject internal constructor(
    @Assisted componentContext: ComponentContext,
    @Assisted val workflowId: String,
    @Assisted val initialUris: List<Uri>?,
    @Assisted val onGoBack: () -> Unit,
    @Assisted val onNavigate: (Screen) -> Unit,
    private val workflowRepository: WorkflowRepository,
    private val engine: WorkflowEngine,
    private val shareProvider: ShareProvider,
    dispatchersHolder: DispatchersHolder
) : BaseComponent(dispatchersHolder, componentContext) {

    private val _state = mutableStateOf<RunnerState>(RunnerState.Loading)
    val state: RunnerState by _state

    private val _workflow = mutableStateOf<Workflow?>(null)
    val workflow: Workflow? by _workflow

    private var lastUris: List<Uri> = emptyList()
    private var runJob: Job? = null

    init {
        componentScope.launch {
            val loaded = workflowRepository.getById(workflowId)
            if (loaded == null) {
                AppToastHost.showToast(R.string.workflow_invalid)
                onGoBack()
                return@launch
            }
            _workflow.value = loaded

            val uris = initialUris
            if (!uris.isNullOrEmpty()) {
                startRun(uris)
            } else {
                _state.value = RunnerState.AwaitingImages
            }
        }
    }

    fun startRun(uris: List<Uri>) {
        val wf = _workflow.value ?: return
        lastUris = uris

        val issues = engine.validate(wf)
        if (issues.isNotEmpty()) {
            _state.value = RunnerState.Invalid(issues)
            return
        }

        runJob?.cancel()
        runJob = trackProgress {
            val savedPaths = mutableListOf<String>()
            val savedUris = mutableListOf<String>()

            engine.run(wf, uris.map { it.toString() }).collect { event ->
                when (event) {
                    is WorkflowRunEvent.StepStarted -> {
                        _state.value = RunnerState.Running(
                            imageIndex = event.imageIndex,
                            imageCount = event.imageCount,
                            stepIndex = event.stepIndex,
                            stepCount = event.stepCount,
                            toolKey = event.toolKey,
                            stepPercent = null
                        )
                        updateProgress(
                            title = getString(R.string.running_workflow),
                            done = event.imageIndex * event.stepCount + event.stepIndex,
                            total = (event.imageCount * event.stepCount).coerceAtLeast(1)
                        )
                    }

                    is WorkflowRunEvent.StepProgress -> {
                        (_state.value as? RunnerState.Running)?.let { running ->
                            _state.value = running.copy(stepPercent = event.percent)
                        }
                    }

                    is WorkflowRunEvent.ImageFailed -> Unit

                    is WorkflowRunEvent.ImageDone -> {
                        event.savedPath?.let { savedPaths += it }
                        event.savedUri?.let { savedUris += it }
                    }

                    is WorkflowRunEvent.Finished -> {
                        _state.value = RunnerState.Done(
                            savedPaths = event.savedPaths,
                            savedUris = event.savedUris,
                            failureCount = event.failureCount
                        )
                    }
                }
            }
        }
    }

    fun shareResults() {
        val done = _state.value as? RunnerState.Done ?: return
        if (done.savedUris.isEmpty()) return
        componentScope.launch {
            shareProvider.shareUris(done.savedUris)
        }
    }

    fun runAgain() {
        if (lastUris.isNotEmpty()) startRun(lastUris)
    }

    @AssistedFactory
    fun interface Factory {
        operator fun invoke(
            componentContext: ComponentContext,
            workflowId: String,
            initialUris: List<Uri>?,
            onGoBack: () -> Unit,
            onNavigate: (Screen) -> Unit,
        ): WorkflowRunnerComponent
    }

}
