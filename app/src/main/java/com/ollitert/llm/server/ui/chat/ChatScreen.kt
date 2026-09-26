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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary
import kotlinx.coroutines.launch

/**
 * Hosts the in-app chat. Talks to the locally running model over the loopback API.
 *
 * Conversations and projects are persisted via [ChatViewModel] (Room), so the tree
 * survives app restarts. Generation can be force-stopped at any time via the send
 * button (which becomes a stop button while streaming) — [ChatViewModel.stop]
 * cancels the coroutine AND disconnects the open HTTP connection.
 */
@Composable
fun ChatScreen(
  hosts: List<String>,
  port: Int,
  bearerToken: String,
  serverRunning: Boolean,
  activeModelName: String?,
  onOpenModels: () -> Unit,
  viewModel: ChatViewModel = hiltViewModel(),
) {
  val scope = rememberCoroutineScope()
  val drawerState = rememberDrawerState(DrawerValue.Closed)

  LaunchedEffect(hosts, port, bearerToken, serverRunning, activeModelName) {
    viewModel.configure(hosts, port, bearerToken, serverRunning, activeModelName)
  }

  val projects by viewModel.projectsWithConversations.collectAsStateWithLifecycle(emptyList())
  val currentProjectId by viewModel.currentProjectId.collectAsStateWithLifecycle(null)
  val currentConvId by viewModel.currentConversationId.collectAsStateWithLifecycle(null)
  val messages by viewModel.currentMessages.collectAsStateWithLifecycle(emptyList())
  val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle(false)
  val errorText by viewModel.errorText.collectAsStateWithLifecycle(null)

  var input by remember { mutableStateOf("") }
  val listState = rememberLazyListState()

  // The old effect keyed on the streaming content length, so every single token
  // restarted it and hard-jumped the list — the answer visibly strobed as it arrived.
  // Now: a new message animates into view once, and the growing reply only follows
  // the caret while the user is already parked at the bottom (reading back through a
  // long answer no longer gets yanked to the end).
  val atBottom by remember {
    derivedStateOf {
      val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
      lastVisible >= messages.lastIndex
    }
  }

  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) {
      runCatching { listState.animateScrollToItem(messages.lastIndex) }
    }
  }

  LaunchedEffect(messages.lastOrNull()?.content?.length) {
    if (atBottom && messages.isNotEmpty()) {
      runCatching { listState.scrollToItem(messages.lastIndex) }
    }
  }

  ModalNavigationDrawer(
    drawerState = drawerState,
    drawerContent = {
      ChatSidebar(
        projects = projects,
        currentProjectId = currentProjectId,
        currentConversationId = currentConvId,
        onSelectConversation = { id ->
          viewModel.stop()
          viewModel.selectConversation(id)
        },
        onNewConversation = {
          scope.launch { drawerState.close() }
          viewModel.newConversation()
        },
        onNewProject = { name -> viewModel.newProject(name) },
        onDeleteConversation = viewModel::deleteConversation,
        onDeleteProject = viewModel::deleteProject,
        onClose = { scope.launch { drawerState.close() } },
      )
    },
  ) {
    Column(
      Modifier
        .fillMaxSize()
        .imePadding()
        .navigationBarsPadding(),
    ) {
      // ── header: drawer toggle + model name + new conversation ──
      Row(
        Modifier
          .fillMaxWidth()
          .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = { scope.launch { drawerState.open() } }) {
          Icon(Icons.Outlined.Menu, contentDescription = "对话列表")
        }
        Text(
          text = if (isGenerating) "生成中…" else (activeModelName ?: "未加载模型"),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { viewModel.newConversation() }) {
          Icon(Icons.Outlined.Add, contentDescription = "新建对话")
        }
      }

      // ── messages ──
      LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (messages.isEmpty()) {
          item {
            ChatEmptyState(
              serverRunning = serverRunning,
              onOpenModels = onOpenModels,
              onQuickSend = { viewModel.send(it) },
            )
          }
        }
        items(messages, key = { it.id }) { msg ->
          MessageRow(msg, onRegenerate = viewModel::regenerate)
        }
      }

      errorText?.let { err ->
        Row(
          Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(
              MaterialTheme.colorScheme.errorContainer,
              RoundedCornerShape(10.dp),
            ).padding(10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            err,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
          )
          IconButton(
            onClick = viewModel::clearError,
            modifier = Modifier.size(20.dp),
          ) {
            Icon(
              Icons.Outlined.Close,
              contentDescription = "关闭",
              Modifier.size(16.dp),
              tint = MaterialTheme.colorScheme.onErrorContainer,
            )
          }
        }
      }

      // ── input: send button becomes a forced-stop button while streaming ──
      Row(
        Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = input,
          onValueChange = { input = it },
          modifier = Modifier.weight(1f),
          placeholder = { Text("问点什么…") },
          maxLines = 4,
          shape = RoundedCornerShape(16.dp),
        )
        IconButton(
          onClick = {
            if (isGenerating) {
              viewModel.stop()
            } else {
              viewModel.send(input)
              input = ""
            }
          },
          enabled = isGenerating || input.isNotBlank(),
          modifier =
            Modifier
              .clip(RoundedCornerShape(50))
              .background(
                if (isGenerating) {
                  MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
                } else if (input.isNotBlank()) {
                  OlliteRTPrimary.copy(alpha = 0.20f)
                } else {
                  MaterialTheme.colorScheme.surfaceContainerHigh
                },
              ),
        ) {
          Icon(
            if (isGenerating) Icons.Outlined.Stop else Icons.AutoMirrored.Outlined.Send,
            contentDescription = if (isGenerating) "停止生成" else "发送",
            tint =
              if (isGenerating) {
                MaterialTheme.colorScheme.error
              } else {
                OlliteRTPrimary
              },
          )
        }
      }
    }
  }
}

@Composable
private fun ChatEmptyState(
  serverRunning: Boolean,
  onOpenModels: () -> Unit,
  onQuickSend: (String) -> Unit,
) {
  val quickQuestions =
    listOf(
      "帮我写一段 Python 爬虫代码",
      "用 HTML 做一个个人名片卡片",
      "用一句话解释什么是量子纠缠",
    )
  Column(
    Modifier
      .fillMaxWidth()
      .padding(top = 28.dp, start = 16.dp, end = 16.dp),
  ) {
    Text(
      text = if (serverRunning) "有什么可以帮你的？" else "还没有运行模型",
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurface,
      fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(6.dp))
    Text(
      text =
        if (serverRunning) {
          "点下面的快捷提问，或直接输入。模型在手机上离线运行，不联网。"
        } else {
          "先去「模型」页启动一个模型，再回来对话。"
        },
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    if (serverRunning) {
      quickQuestions.forEach { q ->
        SuggestionChip(
          onClick = { onQuickSend(q) },
          label = { Text(q, style = MaterialTheme.typography.bodyMedium) },
          icon = {
            Icon(
              Icons.Outlined.AutoAwesome,
              contentDescription = null,
              Modifier.size(16.dp),
            )
          },
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(vertical = 4.dp),
        )
      }
    } else {
      Button(onClick = onOpenModels, shape = RoundedCornerShape(50)) {
        Text("去启动模型")
      }
    }
  }
}

/**
 * Hosts the chat should try, best first.
 *
 * [bindAddress] is the *advertised* address (the LAN IP shown to the user), not
 * the socket's bind address, so it can be unreachable from the app itself.
 * Loopback always works when the server listens on 0.0.0.0 or 127.0.0.1, so it
 * goes first and the advertised address stays as a fallback.
 */
fun chatConnectHosts(bindAddress: String?): List<String> {
  val advertised = bindAddress?.trim().orEmpty()
  return when (advertised) {
    "", "localhost", "0.0.0.0", "127.0.0.1" -> listOf("127.0.0.1")
    else -> listOf("127.0.0.1", advertised)
  }
}
