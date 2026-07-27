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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.t8rin.imagetoolbox.core.resources.Icons
import com.t8rin.imagetoolbox.core.resources.R
import com.t8rin.imagetoolbox.core.resources.icons.Add
import com.t8rin.imagetoolbox.core.resources.icons.ArrowBack
import com.t8rin.imagetoolbox.core.resources.icons.ContentCopy
import com.t8rin.imagetoolbox.core.resources.icons.Delete
import com.t8rin.imagetoolbox.core.resources.icons.MoreVert
import com.t8rin.imagetoolbox.core.resources.icons.Start
import com.t8rin.imagetoolbox.core.resources.icons.VectorPolyline
import com.t8rin.imagetoolbox.core.ui.utils.navigation.Screen
import com.t8rin.imagetoolbox.core.ui.widget.enhanced.EnhancedAlertDialog
import com.t8rin.imagetoolbox.core.ui.widget.enhanced.EnhancedButton
import com.t8rin.imagetoolbox.core.ui.widget.enhanced.EnhancedDropdownMenu
import com.t8rin.imagetoolbox.core.ui.widget.enhanced.EnhancedFloatingActionButton
import com.t8rin.imagetoolbox.core.ui.widget.enhanced.EnhancedIconButton
import com.t8rin.imagetoolbox.core.ui.widget.enhanced.EnhancedTopAppBar
import com.t8rin.imagetoolbox.core.ui.widget.modifier.ShapeDefaults
import com.t8rin.imagetoolbox.core.ui.widget.other.TopAppBarEmoji
import com.t8rin.imagetoolbox.core.ui.widget.preferences.PreferenceItemOverload
import com.t8rin.imagetoolbox.core.ui.widget.text.marquee
import com.t8rin.imagetoolbox.feature.workflows.domain.model.Workflow
import com.t8rin.imagetoolbox.feature.workflows.presentation.screenLogic.WorkflowsComponent

@Composable
fun WorkflowsContent(
    component: WorkflowsComponent
) {
    val workflows by component.workflows.collectAsStateWithLifecycle()

    var workflowToDelete by remember { mutableStateOf<Workflow?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            EnhancedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.workflows),
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
                    TopAppBarEmoji()
                }
            )
        },
        floatingActionButton = {
            EnhancedFloatingActionButton(
                onClick = {
                    component.onNavigate(Screen.WorkflowEditor(workflowId = null))
                }
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.create_workflow)
                )
            }
        }
    ) { contentPadding ->
        if (workflows.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_workflows),
                    modifier = Modifier.padding(32.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding + PaddingValues(12.dp)
            ) {
                itemsIndexed(
                    items = workflows,
                    key = { _, item -> item.id }
                ) { index, workflow ->
                    WorkflowItem(
                        index = index,
                        itemCount = workflows.size,
                        workflow = workflow,
                        onClick = {
                            component.onNavigate(Screen.WorkflowEditor(workflowId = workflow.id))
                        },
                        onRun = {
                            component.onNavigate(
                                Screen.WorkflowRunner(workflowId = workflow.id, uris = null)
                            )
                        },
                        onDuplicate = {
                            component.duplicateWorkflow(workflow)
                        },
                        onWantDelete = {
                            workflowToDelete = workflow
                        }
                    )
                }
            }
        }
    }

    EnhancedAlertDialog(
        visible = workflowToDelete != null,
        onDismissRequest = { workflowToDelete = null },
        icon = {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = null
            )
        },
        title = {
            Text(stringResource(R.string.delete_workflow))
        },
        text = {
            Text(
                stringResource(
                    R.string.delete_workflow_warning,
                    workflowToDelete?.name.orEmpty()
                )
            )
        },
        confirmButton = {
            EnhancedButton(
                onClick = {
                    workflowToDelete?.let { component.deleteWorkflow(it.id) }
                    workflowToDelete = null
                }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            EnhancedButton(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                onClick = { workflowToDelete = null }
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun LazyItemScope.WorkflowItem(
    index: Int,
    itemCount: Int,
    workflow: Workflow,
    onClick: () -> Unit,
    onRun: () -> Unit,
    onDuplicate: () -> Unit,
    onWantDelete: () -> Unit
) {
    var showMenu by rememberSaveable(workflow.id) { mutableStateOf(false) }

    PreferenceItemOverload(
        title = workflow.name,
        subtitle = "${workflow.steps.size} " + stringResource(R.string.workflow),
        onClick = onClick,
        startIcon = {
            Icon(
                imageVector = Icons.Outlined.VectorPolyline,
                contentDescription = null
            )
        },
        endIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EnhancedIconButton(
                    onClick = onRun
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Start,
                        contentDescription = stringResource(R.string.workflow_runner)
                    )
                }
                Box {
                    EnhancedIconButton(
                        onClick = { showMenu = true }
                    ) {
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
                            EnhancedButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    showMenu = false
                                    onDuplicate()
                                },
                                shape = ShapeDefaults.top,
                                containerColor = MaterialTheme.colorScheme.primary
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = null
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.duplicate))
                            }
                            EnhancedButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    showMenu = false
                                    onWantDelete()
                                },
                                shape = ShapeDefaults.bottom,
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = null
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.delete))
                            }
                        }
                    }
                }
            }
        },
        shape = ShapeDefaults.byIndex(index, itemCount),
        modifier = Modifier.animateItem()
    )
}
