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

package com.t8rin.imagetoolbox.core.ui.utils.navigation

/**
 * Lightweight, module-agnostic representation of a saved `Workflow` used to
 * surface workflows in [com.t8rin.imagetoolbox.core.ui.widget.sheets.ProcessImagesPreferenceSheet]
 * without core/ui depending on the feature/workflows module.
 */
data class WorkflowShareEntry(
    val id: String,
    val name: String
)
