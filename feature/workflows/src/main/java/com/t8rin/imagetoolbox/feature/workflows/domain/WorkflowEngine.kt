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

package com.t8rin.imagetoolbox.feature.workflows.domain

import com.t8rin.imagetoolbox.feature.workflows.domain.model.Workflow
import kotlinx.coroutines.flow.Flow

interface WorkflowEngine {

    fun run(workflow: Workflow, uris: List<String>): Flow<WorkflowRunEvent>

    fun validate(workflow: Workflow): List<WorkflowValidationIssue>

}

sealed interface WorkflowRunEvent {
    data class StepStarted(
        val imageIndex: Int,   // 0-based
        val imageCount: Int,
        val stepIndex: Int,    // 0-based
        val stepCount: Int,
        val toolKey: String
    ) : WorkflowRunEvent

    data class StepProgress(          // optional intra-step %, used by AI/OCR steps
        val imageIndex: Int,
        val stepIndex: Int,
        val percent: Int
    ) : WorkflowRunEvent

    data class ImageFailed(
        val imageIndex: Int,
        val stepIndex: Int,
        val toolKey: String,
        val reason: WorkflowFailureReason
    ) : WorkflowRunEvent

    data class ImageDone(
        val imageIndex: Int,
        val savedPath: String?,       // human-readable saved location, null if unknown
        val savedUri: String? = null  // actual content/file uri of the saved result, for sharing
    ) : WorkflowRunEvent

    data class Finished(
        val successCount: Int,
        val failureCount: Int,
        val savedPaths: List<String>,
        val savedUris: List<String> = emptyList()
    ) : WorkflowRunEvent
}

sealed interface WorkflowFailureReason {
    data class ModelNotDownloaded(val modelName: String) : WorkflowFailureReason
    data class Error(val throwable: Throwable) : WorkflowFailureReason
    data object InvalidStep : WorkflowFailureReason
}

sealed interface WorkflowValidationIssue {
    data class MissingAiModel(val modelName: String) : WorkflowValidationIssue
    data object EmptyWorkflow : WorkflowValidationIssue
    data object OcrNotLast : WorkflowValidationIssue
    data class UnknownStep(val toolKey: String) : WorkflowValidationIssue
}
