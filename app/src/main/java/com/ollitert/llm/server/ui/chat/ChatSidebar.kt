/*
 * Copyright 2025-2026 @NightMean (https://github.com/NightMean)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ollitert.llm.server.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.data.db.ChatConversationEntity
import com.ollitert.llm.server.data.db.ProjectWithConversations
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary

@Composable
fun ChatSidebar(
  projects: List<ProjectWithConversations>,
  currentProjectId: String?,
  currentConversationId: String?,
  onSelectConversation: (String) -> Unit,
  onNewConversation: () -> Unit,
  onNewProject: (String) -> Unit,
  onDeleteConversation: (String) -> Unit,
  onDeleteProject: (String) -> Unit,
  onClose: () -> Unit,
) {
  var expandedSet by remember { mutableStateOf<Set<String>>(emptySet()) }
  var newProjectName by remember { mutableStateOf("") }
  var showNewProject by remember { mutableStateOf(false) }
  var pendingDeleteConv by remember { mutableStateOf<String?>(null) }
  var pendingDeleteProject by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(projects) {
    expandedSet = expandedSet + projects.map { it.project.id }.toSet()
  }

  Surface(
    Modifier.fillMaxHeight().widthIn(max = 320.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
  ) {
    Column(Modifier.fillMaxHeight()) {
      Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("对话", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onClose) {
          Icon(
            Icons.Outlined.Close,
            contentDescription = "关闭",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      HorizontalDivider()
      Column(
        Modifier
          .weight(1f)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(8.dp),
      ) {
        projects.forEach { pwc ->
          val expanded = pwc.project.id in expandedSet
          Row(
            Modifier
              .fillMaxWidth()
              .clickable {
                expandedSet =
                  if (expanded) expandedSet - pwc.project.id else expandedSet + pwc.project.id
              }.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              Icons.Outlined.Folder,
              contentDescription = null,
              Modifier.size(18.dp),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
              pwc.project.name,
              style = MaterialTheme.typography.labelLarge,
              modifier = Modifier.weight(1f),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            IconButton(
              onClick = onNewConversation,
              modifier = Modifier.size(26.dp),
            ) {
              Icon(
                Icons.Outlined.Add,
                contentDescription = "新建对话",
                Modifier.size(18.dp),
                tint = OlliteRTPrimary,
              )
            }
            IconButton(
              onClick = { pendingDeleteProject = pwc.project.id },
              modifier = Modifier.size(26.dp),
            ) {
              Icon(
                Icons.Outlined.Delete,
                contentDescription = "删除项目",
                Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Icon(
              if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
              contentDescription = null,
              Modifier.size(18.dp),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          if (expanded) {
            pwc.conversations.forEach { conv ->
              ConversationItem(
                conv = conv,
                selected = conv.id == currentConversationId,
                onSelect = { onSelectConversation(conv.id) },
                onDelete = { pendingDeleteConv = conv.id },
              )
            }
          }
        }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = {
          newProjectName = ""
          showNewProject = true
        }, Modifier.fillMaxWidth()) {
          Icon(Icons.Outlined.Add, contentDescription = null, Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("新建项目")
        }
      }
    }
  }

  if (showNewProject) {
    AlertDialog(
      onDismissRequest = { showNewProject = false },
      title = { Text("新建项目") },
      text = {
        OutlinedTextField(
          value = newProjectName,
          onValueChange = { newProjectName = it },
          placeholder = { Text("项目名称") },
          singleLine = true,
        )
      },
      confirmButton = {
        TextButton(onClick = {
          onNewProject(newProjectName.trim())
          showNewProject = false
        }) { Text("创建") }
      },
      dismissButton = {
        TextButton(onClick = { showNewProject = false }) { Text("取消") }
      },
    )
  }

  if (pendingDeleteConv != null) {
    AlertDialog(
      onDismissRequest = { pendingDeleteConv = null },
      title = { Text("删除对话") },
      text = { Text("确定删除这个对话？此操作不可恢复。") },
      confirmButton = {
        TextButton(onClick = {
          onDeleteConversation(pendingDeleteConv!!)
          pendingDeleteConv = null
        }) { Text("删除") }
      },
      dismissButton = {
        TextButton(onClick = { pendingDeleteConv = null }) { Text("取消") }
      },
    )
  }

  if (pendingDeleteProject != null) {
    AlertDialog(
      onDismissRequest = { pendingDeleteProject = null },
      title = { Text("删除项目") },
      text = { Text("确定删除这个项目及其下所有对话？此操作不可恢复。") },
      confirmButton = {
        TextButton(onClick = {
          onDeleteProject(pendingDeleteProject!!)
          pendingDeleteProject = null
        }) { Text("删除") }
      },
      dismissButton = {
        TextButton(onClick = { pendingDeleteProject = null }) { Text("取消") }
      },
    )
  }
}

@Composable
private fun ConversationItem(
  conv: ChatConversationEntity,
  selected: Boolean,
  onSelect: () -> Unit,
  onDelete: () -> Unit,
) {
  Row(
    Modifier
      .fillMaxWidth()
      .clickable(onClick = onSelect)
      .background(
        if (selected) OlliteRTPrimary.copy(alpha = 0.14f) else Color.Transparent,
        RoundedCornerShape(8.dp),
      ).padding(horizontal = 12.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      Icons.Outlined.ChatBubbleOutline,
      contentDescription = null,
      Modifier.size(16.dp),
      tint = if (selected) OlliteRTPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.width(8.dp))
    Text(
      conv.title.ifBlank { "新对话" },
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.weight(1f),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      color = if (selected) OlliteRTPrimary else MaterialTheme.colorScheme.onSurface,
    )
    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
      Icon(
        Icons.Outlined.Delete,
        contentDescription = "删除对话",
        Modifier.size(16.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
