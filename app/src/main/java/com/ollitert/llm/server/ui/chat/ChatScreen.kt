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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.net.HttpURLConnection
import java.net.URL

enum class ChatRole(val wire: String) {
  USER("user"),
  ASSISTANT("assistant"),
}

data class ChatMessage(
  val role: ChatRole,
  val content: String,
  val streaming: Boolean = false,
)

/**
 * In-process chat history. Kept as a singleton so leaving and re-entering the
 * chat screen does not lose the conversation.
 */
object ChatStore {
  val messages = mutableStateListOf<ChatMessage>()

  fun clear() {
    messages.clear()
  }
}

private val chatJson = Json {
  ignoreUnknownKeys = true
  isLenient = true
}

/** Hides `<think>` reasoning blocks, including the still-open one while streaming. */
internal fun displayedText(raw: String): String {
  var s = raw.replace(Regex("(?s)<think>.*?</think>"), "")
  val open = s.indexOf("<think>")
  if (open >= 0) s = s.substring(0, open)
  return s.trim()
}

/** Streams a chat completion from the on-device server, emitting text deltas. */
private fun streamChatCompletion(
  host: String,
  port: Int,
  bearerToken: String,
  modelName: String?,
  history: List<Pair<String, String>>,
): Flow<String> = channelFlow {
  withContext(Dispatchers.IO) {
    val url = URL("http://$host:$port/v1/chat/completions")
    val conn = (url.openConnection() as HttpURLConnection).apply {
      requestMethod = "POST"
      doOutput = true
      connectTimeout = 15_000
      readTimeout = 0
      setRequestProperty("Content-Type", "application/json")
      setRequestProperty("Accept", "text/event-stream")
      if (bearerToken.isNotBlank()) {
        setRequestProperty("Authorization", "Bearer $bearerToken")
      }
    }
    try {
      val body = buildString {
        append("{\"model\":")
        append(JsonPrimitive(modelName ?: "default").toString())
        append(",\"stream\":true,\"messages\":[")
        history.forEachIndexed { i, (role, content) ->
          if (i > 0) append(',')
          append("{\"role\":")
          append(JsonPrimitive(role).toString())
          append(",\"content\":")
          append(JsonPrimitive(content).toString())
          append('}')
        }
        append("]}")
      }
      conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

      val code = conn.responseCode
      if (code !in 200..299) {
        val err = (conn.errorStream ?: conn.inputStream)
          ?.bufferedReader()?.use { it.readText() }.orEmpty()
        throw IllegalStateException("服务返回 $code ${err.take(300)}")
      }

      conn.inputStream.bufferedReader().use { reader ->
        while (true) {
          val line = reader.readLine() ?: break
          if (line.isEmpty() || !line.startsWith("data:")) continue
          val payload = line.removePrefix("data:").trim()
          if (payload == "[DONE]") break
          val delta = extractDelta(payload)
          if (!delta.isNullOrEmpty()) trySend(delta)
        }
      }
    } finally {
      runCatching { conn.disconnect() }
    }
  }
}

private fun extractDelta(payload: String): String? = try {
  val choices = chatJson.parseToJsonElement(payload).jsonObject["choices"]?.jsonArray
  val first = choices?.firstOrNull()?.jsonObject
  val delta = first?.get("delta")?.jsonObject
  val content = delta?.get("content") as? JsonPrimitive
  content?.contentOrNull
} catch (_: Exception) {
  null
}

/** Turns an advertised bind address into a host the app itself can always reach. */
fun chatConnectHost(bindAddress: String?): String = when {
  bindAddress.isNullOrBlank() -> "127.0.0.1"
  bindAddress == "localhost" || bindAddress == "0.0.0.0" -> "127.0.0.1"
  else -> bindAddress
}

@Composable
fun ChatScreen(
  host: String,
  port: Int,
  bearerToken: String,
  serverRunning: Boolean,
  activeModelName: String?,
  onOpenModels: () -> Unit,
) {
  val scope = rememberCoroutineScope()
  val listState = rememberLazyListState()
  val messages = ChatStore.messages

  var input by remember { mutableStateOf("") }
  var streaming by remember { mutableStateOf(false) }
  var errorText by remember { mutableStateOf<String?>(null) }

  // Follow the newest message (and grow with streaming text).
  LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
    if (messages.isNotEmpty()) {
      runCatching { listState.scrollToItem(messages.lastIndex) }
    }
  }

  fun send() {
    val text = input.trim()
    if (text.isEmpty() || streaming) return
    if (!serverRunning) {
      errorText = "模型服务未运行，先去「模型」页启动一个模型。"
      return
    }
    input = ""
    errorText = null

    val history = messages
      .filter { it.content.isNotBlank() }
      .map { it.role.wire to displayedText(it.content).ifBlank { it.content } }
      .toMutableList()
    history += "user" to text

    messages += ChatMessage(ChatRole.USER, text)
    val idx = messages.size
    messages += ChatMessage(ChatRole.ASSISTANT, "", streaming = true)
    streaming = true

    scope.launch {
      try {
        streamChatCompletion(host, port, bearerToken, activeModelName, history).collect { delta ->
          if (idx < messages.size) {
            val cur = messages[idx]
            messages[idx] = cur.copy(content = cur.content + delta)
          }
        }
      } catch (e: Exception) {
        val msg = e.message ?: "请求失败"
        errorText = msg
        if (idx < messages.size && messages[idx].content.isBlank()) messages.removeAt(idx)
      } finally {
        if (idx < messages.size) messages[idx] = messages[idx].copy(streaming = false)
        streaming = false
      }
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .imePadding()
      .navigationBarsPadding(),
  ) {
    // ── header: model name + clear ─────────────────────────────────────────
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = activeModelName ?: "未加载模型",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier.weight(1f),
      )
      if (messages.isNotEmpty()) {
        TextButton(onClick = { ChatStore.clear(); errorText = null }) {
          Icon(
            imageVector = Icons.Outlined.DeleteSweep,
            contentDescription = null,
            modifier = Modifier.height(16.dp),
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text("清空", style = MaterialTheme.typography.labelMedium)
        }
      }
    }

    // ── messages ───────────────────────────────────────────────────────────
    LazyColumn(
      state = listState,
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth(),
      contentPadding = androidx.compose.foundation.layout.PaddingValues(
        horizontal = 14.dp,
        vertical = 8.dp,
      ),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      if (messages.isEmpty()) {
        item {
          Text(
            text = if (serverRunning) {
              "直接在这里向本地模型提问吧。\n（模型在手机上离线运行，不联网）"
            } else {
              "还没有运行模型。先去「模型」页启动一个模型，再回来对话。"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 24.dp),
          )
        }
        if (!serverRunning) {
          item {
            Button(onClick = onOpenModels, shape = RoundedCornerShape(50)) {
              Text("去启动模型")
            }
          }
        }
      }
      itemsIndexed(messages) { _, msg ->
        MessageBubble(msg)
      }
    }

    errorText?.let { err ->
      Text(
        text = err,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
      )
    }

    // ── input ──────────────────────────────────────────────────────────────
    Row(
      modifier = Modifier
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
        onClick = { send() },
        enabled = input.isNotBlank() && !streaming,
        modifier = Modifier
          .clip(RoundedCornerShape(50))
          .background(
            if (input.isNotBlank() && !streaming) OlliteRTPrimary.copy(alpha = 0.20f)
            else MaterialTheme.colorScheme.surfaceContainerHigh,
          ),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Outlined.Send,
          contentDescription = "发送",
          tint = OlliteRTPrimary,
        )
      }
    }
  }
}

@Composable
private fun MessageBubble(msg: ChatMessage) {
  val isUser = msg.role == ChatRole.USER
  val shown = if (isUser) msg.content else displayedText(msg.content)
  val body = when {
    shown.isNotBlank() -> shown
    msg.streaming -> "思考中…"
    else -> "…"
  }
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
  ) {
    Box(
      modifier = Modifier
        .widthIn(max = 320.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(
          if (isUser) OlliteRTPrimary.copy(alpha = 0.18f)
          else MaterialTheme.colorScheme.surfaceContainerHigh,
        )
        .padding(horizontal = 13.dp, vertical = 10.dp),
    ) {
      Column {
        if (!isUser) {
          Text(
            text = "助手",
            style = MaterialTheme.typography.labelSmall,
            color = OlliteRTPrimary,
            fontWeight = FontWeight.SemiBold,
          )
          Spacer(modifier = Modifier.height(3.dp))
        }
        Text(
          text = body,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
    }
  }
}
