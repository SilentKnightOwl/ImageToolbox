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

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.t8rin.imagetoolbox.core.domain.json.JsonParser
import com.t8rin.imagetoolbox.feature.workflows.domain.WorkflowRepository
import com.t8rin.imagetoolbox.feature.workflows.domain.model.Workflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal class WorkflowRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val jsonParser: JsonParser
) : WorkflowRepository {

    override val workflows: Flow<List<Workflow>> = dataStore.data.map { preferences ->
        preferences.readStored().workflows
    }

    override suspend fun getById(id: String): Workflow? = workflows.first().find { it.id == id }

    override suspend fun upsert(workflow: Workflow) {
        val name = workflow.name.trim()
        if (name.isBlank()) return
        val normalized = workflow.copy(name = name)

        dataStore.edit { preferences ->
            val list = preferences.readStored().workflows.toMutableList()
            val index = list.indexOfFirst { it.id == normalized.id }
            if (index >= 0) {
                list[index] = normalized
            } else {
                list.add(normalized)
            }
            preferences.writeStored(StoredWorkflows(list))
        }
    }

    override suspend fun delete(id: String) {
        dataStore.edit { preferences ->
            val current = preferences.readStored()
            preferences.writeStored(
                current.copy(
                    workflows = current.workflows.filterNot { it.id == id }
                )
            )
        }
    }

    private fun Preferences.readStored(): StoredWorkflows {
        val json = this[WorkflowsKey] ?: return StoredWorkflows()
        return jsonParser.fromJson<StoredWorkflows>(
            json = json,
            type = StoredWorkflows::class.java
        ) ?: StoredWorkflows()
    }

    private fun MutablePreferences.writeStored(workflows: StoredWorkflows) {
        if (workflows.workflows.isEmpty()) {
            remove(WorkflowsKey)
            return
        }
        jsonParser.toJson(
            obj = workflows,
            type = StoredWorkflows::class.java
        )?.let { json ->
            this[WorkflowsKey] = json
        }
    }

}

internal data class StoredWorkflows(
    val workflows: List<Workflow> = emptyList()
)

private val WorkflowsKey = stringPreferencesKey("WORKFLOWS_KEY")
