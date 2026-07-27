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
import androidx.datastore.preferences.core.Preferences
import com.t8rin.imagetoolbox.core.domain.image.ShareProvider
import com.t8rin.imagetoolbox.core.domain.json.JsonParser
import com.t8rin.imagetoolbox.core.domain.presets.ToolPresetsUseCase
import com.t8rin.imagetoolbox.core.domain.presets.ToolPresetsUseCaseFactory
import com.t8rin.imagetoolbox.core.domain.saving.FileController
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

internal class ToolPresetsUseCaseFactoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val fileController: FileController,
    private val jsonParser: JsonParser,
    private val shareProvider: ShareProvider
) : ToolPresetsUseCaseFactory {

    private val cache = ConcurrentHashMap<String, ToolPresetsUseCase<*>>()

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> create(
        toolKey: String,
        payloadType: Class<T>
    ): ToolPresetsUseCase<T> = cache.getOrPut(toolKey) {
        ToolPresetsUseCaseImpl(
            toolKey = toolKey,
            payloadType = payloadType,
            dataStore = dataStore,
            fileController = fileController,
            jsonParser = jsonParser,
            shareProvider = shareProvider
        )
    } as ToolPresetsUseCase<T>

}
