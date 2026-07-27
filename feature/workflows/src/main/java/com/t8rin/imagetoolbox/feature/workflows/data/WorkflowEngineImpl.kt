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

package com.t8rin.imagetoolbox.feature.workflows.data

import android.graphics.Bitmap
import com.t8rin.imagetoolbox.core.domain.coroutines.DispatchersHolder
import com.t8rin.imagetoolbox.core.domain.image.ImageCompressor
import com.t8rin.imagetoolbox.core.domain.image.ImageGetter
import com.t8rin.imagetoolbox.core.domain.image.ImageScaler
import com.t8rin.imagetoolbox.core.domain.image.ImageTransformer
import com.t8rin.imagetoolbox.core.domain.image.model.ImageExportProfile
import com.t8rin.imagetoolbox.core.domain.image.model.ImageFormat
import com.t8rin.imagetoolbox.core.domain.image.model.ImageInfo
import com.t8rin.imagetoolbox.core.domain.json.JsonParser
import com.t8rin.imagetoolbox.core.domain.model.MimeType
import com.t8rin.imagetoolbox.core.domain.saving.FileController
import com.t8rin.imagetoolbox.core.domain.saving.FilenameCreator
import com.t8rin.imagetoolbox.core.domain.saving.model.FileSaveTarget
import com.t8rin.imagetoolbox.core.domain.saving.model.ImageSaveTarget
import com.t8rin.imagetoolbox.core.domain.saving.model.SaveResult
import com.t8rin.imagetoolbox.core.domain.utils.runSuspendCatching
import com.t8rin.imagetoolbox.feature.ai_tools.domain.AiProgressListener
import com.t8rin.imagetoolbox.feature.ai_tools.domain.AiToolsRepository
import com.t8rin.imagetoolbox.feature.ai_tools.domain.model.AiToolsPresetPayload
import com.t8rin.imagetoolbox.feature.ai_tools.domain.model.NeuralParams
import com.t8rin.imagetoolbox.feature.recognize.text.domain.ImageTextReader
import com.t8rin.imagetoolbox.feature.recognize.text.domain.OCRLanguage
import com.t8rin.imagetoolbox.feature.recognize.text.domain.OcrEngineMode
import com.t8rin.imagetoolbox.feature.recognize.text.domain.OcrPresetPayload
import com.t8rin.imagetoolbox.feature.recognize.text.domain.PaddleOCRModel
import com.t8rin.imagetoolbox.feature.recognize.text.domain.RecognitionEngine
import com.t8rin.imagetoolbox.feature.recognize.text.domain.RecognitionType
import com.t8rin.imagetoolbox.feature.recognize.text.domain.SegmentationMode
import com.t8rin.imagetoolbox.feature.recognize.text.domain.TessParams
import com.t8rin.imagetoolbox.feature.recognize.text.domain.TextRecognitionResult
import com.t8rin.imagetoolbox.feature.workflows.domain.WorkflowEngine
import com.t8rin.imagetoolbox.feature.workflows.domain.WorkflowFailureReason
import com.t8rin.imagetoolbox.feature.workflows.domain.WorkflowRunEvent
import com.t8rin.imagetoolbox.feature.workflows.domain.WorkflowValidationIssue
import com.t8rin.imagetoolbox.feature.workflows.domain.model.OcrStepPayload
import com.t8rin.imagetoolbox.feature.workflows.domain.model.Workflow
import com.t8rin.imagetoolbox.feature.workflows.domain.model.WorkflowStep
import com.t8rin.imagetoolbox.feature.workflows.domain.model.WorkflowToolKeys
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

internal class WorkflowEngineImpl @Inject constructor(
    private val imageGetter: ImageGetter<Bitmap>,
    private val imageScaler: ImageScaler<Bitmap>,
    private val imageTransformer: ImageTransformer<Bitmap>,
    private val imageCompressor: ImageCompressor<Bitmap>,
    private val fileController: FileController,
    private val filenameCreator: FilenameCreator,
    private val jsonParser: JsonParser,
    private val aiToolsRepository: AiToolsRepository<Bitmap>,
    private val imageTextReader: ImageTextReader,
    private val dispatchersHolder: DispatchersHolder
) : WorkflowEngine {

    override fun run(
        workflow: Workflow,
        uris: List<String>
    ): Flow<WorkflowRunEvent> = channelFlow {
        val savedPaths = mutableListOf<String>()
        val savedUris = mutableListOf<String>()
        var successCount = 0
        var failureCount = 0

        uris.forEachIndexed { imageIndex, uri ->
            val result = processImage(
                imageIndex = imageIndex,
                imageCount = uris.size,
                uri = uri,
                steps = workflow.steps
            )

            if (result != null) {
                val (savedPath, savedUri) = result
                savedPaths += savedPath
                savedUri?.let { savedUris += it }
                successCount++
            } else {
                failureCount++
            }
        }

        runCatching { aiToolsRepository.cleanup() }

        send(
            WorkflowRunEvent.Finished(
                successCount = successCount,
                failureCount = failureCount,
                savedPaths = savedPaths,
                savedUris = savedUris
            )
        )
    }.flowOn(dispatchersHolder.defaultDispatcher)

    override fun validate(workflow: Workflow): List<WorkflowValidationIssue> {
        val issues = mutableListOf<WorkflowValidationIssue>()

        if (workflow.steps.isEmpty()) {
            issues += WorkflowValidationIssue.EmptyWorkflow
            return issues
        }

        workflow.steps.forEachIndexed { index, step ->
            if (step.toolKey !in WorkflowToolKeys.ALL) {
                issues += WorkflowValidationIssue.UnknownStep(step.toolKey)
            }
            if (step.toolKey == WorkflowToolKeys.RECOGNIZE_TEXT && index != workflow.steps.lastIndex) {
                issues += WorkflowValidationIssue.OcrNotLast
            }
            if (step.toolKey == WorkflowToolKeys.AI_TOOLS) {
                val payload = runCatching {
                    jsonParser.fromJson<AiToolsPresetPayload>(
                        json = step.payloadJson,
                        type = AiToolsPresetPayload::class.java
                    )
                }.getOrNull()

                val modelName = payload?.modelName
                val isDownloaded = modelName != null &&
                        aiToolsRepository.downloadedModels.value.any { it.name == modelName }

                if (modelName == null || !isDownloaded) {
                    issues += WorkflowValidationIssue.MissingAiModel(modelName.orEmpty())
                }
            }
        }

        return issues
    }

    private suspend fun ProducerScope<WorkflowRunEvent>.processImage(
        imageIndex: Int,
        imageCount: Int,
        uri: String,
        steps: List<WorkflowStep>
    ): Pair<String, String?>? {
        val imageData = runSuspendCatching {
            imageGetter.getImage(
                uri = uri,
                originalSize = true,
                onFailure = null
            )
        }.getOrNull()

        if (imageData == null) {
            send(
                WorkflowRunEvent.ImageFailed(
                    imageIndex = imageIndex,
                    stepIndex = -1,
                    toolKey = "",
                    reason = WorkflowFailureReason.Error(
                        IllegalStateException("Failed to load image at $uri")
                    )
                )
            )
            return null
        }

        var bitmap = imageData.image
        var outInfo = imageData.imageInfo.copy(originalUri = uri)
        var aborted = false

        steps.forEachIndexed { stepIndex, step ->
            if (aborted) return@forEachIndexed

            send(
                WorkflowRunEvent.StepStarted(
                    imageIndex = imageIndex,
                    imageCount = imageCount,
                    stepIndex = stepIndex,
                    stepCount = steps.size,
                    toolKey = step.toolKey
                )
            )

            when (step.toolKey) {
                WorkflowToolKeys.RESIZE_CONVERT -> {
                    val outcome = processResizeConvertStep(
                        step = step,
                        bitmap = bitmap,
                        outInfo = outInfo,
                        uri = uri
                    )
                    if (outcome == null) {
                        send(
                            WorkflowRunEvent.ImageFailed(
                                imageIndex = imageIndex,
                                stepIndex = stepIndex,
                                toolKey = step.toolKey,
                                reason = WorkflowFailureReason.InvalidStep
                            )
                        )
                        aborted = true
                    } else {
                        bitmap = outcome.first
                        outInfo = outcome.second
                    }
                }

                WorkflowToolKeys.AI_TOOLS -> {
                    val payload = runCatching {
                        jsonParser.fromJson<AiToolsPresetPayload>(
                            json = step.payloadJson,
                            type = AiToolsPresetPayload::class.java
                        )
                    }.getOrNull()

                    if (payload == null) {
                        send(
                            WorkflowRunEvent.ImageFailed(
                                imageIndex = imageIndex,
                                stepIndex = stepIndex,
                                toolKey = step.toolKey,
                                reason = WorkflowFailureReason.InvalidStep
                            )
                        )
                        aborted = true
                        return@forEachIndexed
                    }

                    val model = aiToolsRepository.downloadedModels.value.find {
                        it.name == payload.modelName
                    }

                    if (model == null) {
                        send(
                            WorkflowRunEvent.ImageFailed(
                                imageIndex = imageIndex,
                                stepIndex = stepIndex,
                                toolKey = step.toolKey,
                                reason = WorkflowFailureReason.ModelNotDownloaded(
                                    payload.modelName.orEmpty()
                                )
                            )
                        )
                        aborted = true
                        return@forEachIndexed
                    }

                    runCatching {
                        aiToolsRepository.selectModel(model, forced = true)

                        val listener = object : AiProgressListener {
                            override fun onError(error: String) = Unit

                            override fun onProgress(
                                currentChunkIndex: Int,
                                totalChunks: Int
                            ) {
                                trySend(
                                    WorkflowRunEvent.StepProgress(
                                        imageIndex = imageIndex,
                                        stepIndex = stepIndex,
                                        percent = if (totalChunks > 0) {
                                            (currentChunkIndex * 100 / totalChunks).coerceIn(0, 100)
                                        } else {
                                            0
                                        }
                                    )
                                )
                            }
                        }

                        aiToolsRepository.processImage(
                            image = bitmap,
                            listener = listener,
                            params = NeuralParams(
                                strength = payload.strength,
                                chunkSize = payload.chunkSize,
                                overlap = payload.overlap,
                                enableChunking = payload.enableChunking,
                                parallelWorkers = payload.parallelWorkers
                            )
                        ) ?: error("AI processing returned no result")
                    }.onSuccess { result ->
                        bitmap = result
                        outInfo = outInfo.copy(
                            width = bitmap.width,
                            height = bitmap.height,
                            imageFormat = payload.imageFormatTitle
                                ?.let(ImageFormat::fromTitle)
                                ?: outInfo.imageFormat
                        )
                    }.onFailure { throwable ->
                        send(
                            WorkflowRunEvent.ImageFailed(
                                imageIndex = imageIndex,
                                stepIndex = stepIndex,
                                toolKey = step.toolKey,
                                reason = WorkflowFailureReason.Error(throwable)
                            )
                        )
                        aborted = true
                    }
                }

                WorkflowToolKeys.RECOGNIZE_TEXT -> {
                    val payload = runCatching {
                        jsonParser.fromJson<OcrStepPayload>(
                            json = step.payloadJson,
                            type = OcrStepPayload::class.java
                        )
                    }.getOrNull()

                    if (payload == null) {
                        send(
                            WorkflowRunEvent.ImageFailed(
                                imageIndex = imageIndex,
                                stepIndex = stepIndex,
                                toolKey = step.toolKey,
                                reason = WorkflowFailureReason.InvalidStep
                            )
                        )
                        // OCR failures never abort the pipeline: the image itself is
                        // still valid and must still be saved.
                        return@forEachIndexed
                    }

                    runCatching {
                        runOcrStep(
                            payload = payload.preset,
                            bitmap = bitmap
                        ) { percent ->
                            trySend(
                                WorkflowRunEvent.StepProgress(
                                    imageIndex = imageIndex,
                                    stepIndex = stepIndex,
                                    percent = percent
                                )
                            )
                        }
                    }.onSuccess { result ->
                        when (result) {
                            is TextRecognitionResult.Success -> {
                                runCatching {
                                    saveTextResult(
                                        text = result.data.text,
                                        uri = uri,
                                        imageIndex = imageIndex
                                    )
                                }
                            }

                            else -> {
                                send(
                                    WorkflowRunEvent.ImageFailed(
                                        imageIndex = imageIndex,
                                        stepIndex = stepIndex,
                                        toolKey = step.toolKey,
                                        reason = WorkflowFailureReason.Error(
                                            IllegalStateException("OCR could not extract text")
                                        )
                                    )
                                )
                            }
                        }
                    }.onFailure { throwable ->
                        send(
                            WorkflowRunEvent.ImageFailed(
                                imageIndex = imageIndex,
                                stepIndex = stepIndex,
                                toolKey = step.toolKey,
                                reason = WorkflowFailureReason.Error(throwable)
                            )
                        )
                    }
                }

                else -> {
                    send(
                        WorkflowRunEvent.ImageFailed(
                            imageIndex = imageIndex,
                            stepIndex = stepIndex,
                            toolKey = step.toolKey,
                            reason = WorkflowFailureReason.InvalidStep
                        )
                    )
                    aborted = true
                }
            }
        }

        if (aborted) return null

        val finalInfo = outInfo.copy(
            width = bitmap.width,
            height = bitmap.height
        )

        val saveResult = runCatching {
            fileController.save(
                saveTarget = ImageSaveTarget(
                    imageInfo = finalInfo,
                    originalUri = uri,
                    sequenceNumber = imageIndex + 1,
                    data = imageCompressor.compressAndTransform(
                        image = bitmap,
                        imageInfo = finalInfo
                    )
                ),
                keepOriginalMetadata = false
            )
        }.getOrElse { throwable ->
            send(
                WorkflowRunEvent.ImageFailed(
                    imageIndex = imageIndex,
                    stepIndex = steps.size,
                    toolKey = "",
                    reason = WorkflowFailureReason.Error(throwable)
                )
            )
            return null
        }

        return when (saveResult) {
            is SaveResult.Success -> {
                send(
                    WorkflowRunEvent.ImageDone(
                        imageIndex = imageIndex,
                        savedPath = saveResult.savingPath,
                        savedUri = saveResult.savedUri
                    )
                )
                saveResult.savingPath to saveResult.savedUri
            }

            else -> {
                send(
                    WorkflowRunEvent.ImageFailed(
                        imageIndex = imageIndex,
                        stepIndex = steps.size,
                        toolKey = "",
                        reason = WorkflowFailureReason.Error(
                            IllegalStateException("Failed to save the resulting image")
                        )
                    )
                )
                null
            }
        }
    }

    private suspend fun processResizeConvertStep(
        step: WorkflowStep,
        bitmap: Bitmap,
        outInfo: ImageInfo,
        uri: String
    ): Pair<Bitmap, ImageInfo>? {
        val profile = runCatching {
            jsonParser.fromJson<ImageExportProfile>(
                json = step.payloadJson,
                type = ImageExportProfile::class.java
            )
        }.getOrNull() ?: return null

        return runCatching {
            val base = outInfo.copy(width = bitmap.width, height = bitmap.height)
            var target = profile.toImageInfo(base)
            target = imageTransformer.applyPresetBy(
                image = bitmap,
                preset = profile.preset,
                currentInfo = target
            )

            var resultBitmap = imageScaler.scaleImage(
                image = bitmap,
                width = target.width,
                height = target.height,
                resizeType = target.resizeType,
                imageScaleMode = target.imageScaleMode
            )

            if (target.rotationDegrees != 0f) {
                resultBitmap = imageTransformer.rotate(resultBitmap, target.rotationDegrees)
            }
            if (target.isFlipped) {
                resultBitmap = imageTransformer.flip(resultBitmap, true)
            }

            resultBitmap to target.copy(
                width = resultBitmap.width,
                height = resultBitmap.height,
                rotationDegrees = 0f,
                isFlipped = false,
                originalUri = uri
            )
        }.getOrNull()
    }

    private suspend fun runOcrStep(
        payload: OcrPresetPayload,
        bitmap: Bitmap,
        onProgress: (Int) -> Unit
    ): TextRecognitionResult = imageTextReader.getTextFromImage(
        type = payload.recognitionType.toEnumOrDefault(RecognitionType.Standard),
        languageCode = payload.languageCodes
            .ifEmpty { listOf(OCRLanguage.Default.code) }
            .joinToString("+"),
        recognitionEngine = payload.recognitionEngine.toEnumOrDefault(RecognitionEngine.Tesseract),
        paddleOCRModel = payload.paddleOCRModel.toEnumOrDefault(PaddleOCRModel.CJK),
        segmentationMode = payload.segmentationMode.toEnumOrDefault(SegmentationMode.PSM_AUTO_OSD),
        ocrEngineMode = payload.ocrEngineMode.toEnumOrDefault(OcrEngineMode.DEFAULT),
        parameters = payload.toTessParams(),
        model = bitmap,
        onProgress = onProgress
    )

    private suspend fun saveTextResult(
        text: String,
        uri: String,
        imageIndex: Int
    ) {
        val filename = filenameCreator.constructImageFilename(
            saveTarget = ImageSaveTarget(
                imageInfo = ImageInfo(),
                originalUri = uri,
                sequenceNumber = imageIndex + 1,
                data = ByteArray(0),
                extension = "txt"
            ),
            forceNotAddSizeInFilename = true
        )

        fileController.save(
            saveTarget = FileSaveTarget(
                originalUri = uri,
                filename = filename,
                data = text.encodeToByteArray(),
                mimeType = MimeType.Txt,
                extension = "txt"
            ),
            keepOriginalMetadata = false
        )
    }

    private fun OcrPresetPayload.toTessParams(): TessParams {
        var params = TessParams.Default
        tessParams.forEach { (key, stored) ->
            params = params.update(key) { old ->
                when (old) {
                    is Boolean -> stored == "1" || stored.equals("true", ignoreCase = true)
                    is Int -> stored.toIntOrNull() ?: old
                    else -> old
                }
            }
        }
        return params.update(newCustomParams = tessCustomParams)
    }

    private inline fun <reified E : Enum<E>> String.toEnumOrDefault(default: E): E =
        runCatching { enumValueOf<E>(this) }.getOrDefault(default)

}
