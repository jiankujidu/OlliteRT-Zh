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

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ollitert.llm.server.data.db.ChatConversationEntity
import com.ollitert.llm.server.data.db.ChatDao
import com.ollitert.llm.server.data.db.ChatMessageEntity
import com.ollitert.llm.server.data.db.ChatProjectEntity
import com.ollitert.llm.server.data.db.ProjectWithConversations
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.inject.Inject

private val chatJson =
  Json {
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

/** Rewrites raw socket/HTTP failures into something a user can act on. */
internal fun friendlyChatError(e: Exception, port: Int): String {
  val raw = e.message.orEmpty()
  return when {
    raw.contains("Cleartext", ignoreCase = true) ||
      raw.contains("not permitted", ignoreCase = true) ->
      "系统拦截了明文 HTTP 请求，请确认已安装最新版本。"
    e is java.net.ConnectException ||
      raw.contains("refused", ignoreCase = true) ||
      raw.contains("ECONNREFUSED", ignoreCase = true) ->
      "连不上本地服务（端口 $port），请确认模型已启动。"
    e is java.net.SocketTimeoutException || raw.contains("timeout", ignoreCase = true) ->
      "连接超时，模型可能还在加载，稍后再试。"
    raw.isBlank() -> "请求失败：${e.javaClass.simpleName}"
    else -> raw
  }
}

@HiltViewModel
class ChatViewModel
@Inject
constructor(
  private val chatDao: ChatDao,
  @ApplicationContext private val appContext: Context,
) : ViewModel() {

  // ── inference params (pushed in from NavGraph via ChatScreen) ──────────────
  var hosts: List<String> = listOf("127.0.0.1")
  var port: Int = 0
  var bearerToken: String = ""
  var activeModelName: String? = null
  var serverRunning: Boolean = false

  fun configure(
    hosts: List<String>,
    port: Int,
    bearerToken: String,
    serverRunning: Boolean,
    activeModelName: String?,
  ) {
    this.hosts = hosts
    this.port = port
    this.bearerToken = bearerToken
    this.serverRunning = serverRunning
    this.activeModelName = activeModelName
  }

  // ── project / conversation tree (sidebar) ────────────────────────────────
  val projectsWithConversations: kotlinx.coroutines.flow.StateFlow<List<ProjectWithConversations>> =
    chatDao
      .observeProjectsWithConversations()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  private val _currentProjectId = MutableStateFlow<String?>(null)
  val currentProjectId: kotlinx.coroutines.flow.StateFlow<String?> = _currentProjectId.asStateFlow()

  private val _currentConversationId = MutableStateFlow<String?>(null)
  val currentConversationId: kotlinx.coroutines.flow.StateFlow<String?> =
    _currentConversationId.asStateFlow()

  val currentMessages: kotlinx.coroutines.flow.StateFlow<List<ChatMessageEntity>> =
    _currentConversationId
      .flatMapLatest { cid ->
        if (cid == null) flowOf(emptyList()) else chatDao.observeMessages(cid)
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  private val _isGenerating = MutableStateFlow(false)
  val isGenerating: kotlinx.coroutines.flow.StateFlow<Boolean> = _isGenerating.asStateFlow()

  private val _errorText = MutableStateFlow<String?>(null)
  val errorText: kotlinx.coroutines.flow.StateFlow<String?> = _errorText.asStateFlow()

  private var generateJob: Job? = null

  @Volatile private var currentConnection: HttpURLConnection? = null

  init {
    viewModelScope.launch { ensureDefaultProjectAndConversation() }
  }

  private suspend fun ensureDefaultProjectAndConversation() {
    val projects = chatDao.observeProjects().first()
    val projectId =
      if (projects.isEmpty()) {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        chatDao.insertProject(ChatProjectEntity(id, "我的对话", now, now, 0))
        id
      } else {
        projects.first().id
      }
    _currentProjectId.value = projectId

    val convs = chatDao.observeConversations(projectId).first()
    val convId =
      if (convs.isEmpty()) {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        chatDao.insertConversation(ChatConversationEntity(id, projectId, "新对话", now, now, 0))
        id
      } else {
        convs.first().id
      }
    _currentConversationId.value = convId
  }

  // ── actions ───────────────────────────────────────────────────────────────
  fun send(text: String) {
    val content = text.trim()
    if (content.isEmpty() || _isGenerating.value) return
    val cid = _currentConversationId.value ?: return
    if (!serverRunning) {
      _errorText.value = "模型服务未运行，先去「模型」页启动一个模型。"
      return
    }
    _errorText.value = null
    generateJob =
      viewModelScope.launch {
        val existing = chatDao.getMessages(cid)
        val isFirst = existing.isEmpty()
        val now = System.currentTimeMillis()
        chatDao.insertMessage(
          ChatMessageEntity(UUID.randomUUID().toString(), cid, "user", content, now, existing.size, false),
        )
        val conv = chatDao.getConversation(cid)
        if (conv != null) {
          val title =
            if (isFirst) content.take(14).ifBlank { "新对话" } else conv.title
          chatDao.updateConversation(conv.copy(title = title, updatedAt = now))
        }
        val history =
          existing
            .filter { it.content.isNotBlank() }
            .map { it.role to (displayedText(it.content).ifBlank { it.content }) }
            .toMutableList()
        history += "user" to content
        generate(cid, history)
      }
  }

  /** Re-runs the last assistant reply from the messages before it. */
  fun regenerate() {
    val cid = _currentConversationId.value ?: return
    if (_isGenerating.value) return
    if (!serverRunning) {
      _errorText.value = "模型服务未运行，先去「模型」页启动一个模型。"
      return
    }
    _errorText.value = null
    generateJob =
      viewModelScope.launch {
        val msgs = chatDao.getMessages(cid)
        val lastAssistant = msgs.lastOrNull { it.role == "assistant" } ?: return@launch
        chatDao.deleteMessage(lastAssistant.id)
        val history =
          msgs
            .takeWhile { it.id != lastAssistant.id }
            .filter { it.content.isNotBlank() }
            .map { it.role to (displayedText(it.content).ifBlank { it.content }) }
        generate(cid, history)
      }
  }

  private suspend fun generate(cid: String, history: List<Pair<String, String>>) {
    val assistantId = UUID.randomUUID().toString()
    val now = System.currentTimeMillis()
    val sort = chatDao.getMessages(cid).size
    chatDao.insertMessage(
      ChatMessageEntity(assistantId, cid, "assistant", "", now, sort, streaming = true),
    )
    _isGenerating.value = true
    val sb = StringBuilder()
    try {
      streamChatCompletionCancellable(
        hosts = hosts,
        port = port,
        bearerToken = bearerToken,
        modelName = activeModelName,
        history = history,
        onConn = { conn -> currentConnection = conn },
        onDelta = { delta ->
          sb.append(delta)
          viewModelScope.launch {
            chatDao.updateMessage(
              ChatMessageEntity(assistantId, cid, "assistant", sb.toString(), now, sort, streaming = true),
            )
          }
        },
      )
      chatDao.updateMessage(
        ChatMessageEntity(assistantId, cid, "assistant", sb.toString(), now, sort, streaming = false),
      )
    } catch (e: Exception) {
      if (e is CancellationException) {
        chatDao.updateMessage(
          ChatMessageEntity(assistantId, cid, "assistant", sb.toString(), now, sort, streaming = false),
        )
      } else {
        _errorText.value = friendlyChatError(e, port)
        if (sb.isEmpty()) chatDao.deleteMessage(assistantId)
        else {
          chatDao.updateMessage(
            ChatMessageEntity(assistantId, cid, "assistant", sb.toString(), now, sort, streaming = false),
          )
        }
      }
    } finally {
      currentConnection = null
      _isGenerating.value = false
    }
  }

  /** Stops the on-going generation. Cancels the coroutine AND disconnects the
   *  open HTTP connection so the blocked readLine() is interrupted immediately —
   *  this is a forced stop, not a soft pause. */
  fun stop() {
    generateJob?.cancel()
    runCatching { currentConnection?.disconnect() }
    currentConnection = null
  }

  fun newProject(name: String) {
    viewModelScope.launch {
      val sort = (chatDao.maxProjectSort() ?: -1) + 1
      val id = UUID.randomUUID().toString()
      val now = System.currentTimeMillis()
      chatDao.insertProject(ChatProjectEntity(id, name.ifBlank { "未命名项目" }, now, now, sort))
      val convId = UUID.randomUUID().toString()
      chatDao.insertConversation(ChatConversationEntity(convId, id, "新对话", now, now, 0))
      _currentProjectId.value = id
      _currentConversationId.value = convId
      _errorText.value = null
    }
  }

  fun newConversation(projectId: String? = _currentProjectId.value) {
    viewModelScope.launch {
      val pid = projectId ?: return@launch
      val id = UUID.randomUUID().toString()
      val now = System.currentTimeMillis()
      val sort = (chatDao.maxConvSort(pid) ?: -1) + 1
      chatDao.insertConversation(ChatConversationEntity(id, pid, "新对话", now, now, sort))
      _currentConversationId.value = id
      _errorText.value = null
    }
  }

  fun selectConversation(id: String) {
    if (_currentConversationId.value == id) return
    _currentConversationId.value = id
    _errorText.value = null
    viewModelScope.launch { chatDao.clearStreaming(id) }
  }

  fun deleteConversation(id: String) {
    viewModelScope.launch {
      val pid = chatDao.getConversation(id)?.projectId ?: return@launch
      chatDao.deleteMessages(id)
      chatDao.deleteConversation(id)
      if (_currentConversationId.value == id) {
        val next = chatDao.firstConversation(pid)
        if (next != null) _currentConversationId.value = next.id
        else newConversation(pid)
      }
    }
  }

  fun deleteProject(id: String) {
    viewModelScope.launch {
      val convs = chatDao.allConversations(id)
      convs.forEach { chatDao.deleteMessages(it.id) }
      chatDao.deleteProject(id)
      val projects = chatDao.observeProjects().first()
      if (projects.isEmpty()) {
        ensureDefaultProjectAndConversation()
      } else {
        _currentProjectId.value = projects.first().id
        val next = chatDao.firstConversation(projects.first().id)
        if (next != null) _currentConversationId.value = next.id
        else newConversation(projects.first().id)
      }
    }
  }

  fun clearError() {
    _errorText.value = null
  }
}

/**
 * Streams a chat completion from the on-device server, emitting text deltas.
 *
 * Unlike the old implementation this is *cancellable*: [onConn] hands the open
 * [HttpURLConnection] to the caller so a forced stop can [HttpURLConnection.disconnect]
 * it, which makes the blocked [java.io.BufferedReader.readLine] throw and unwind
 * immediately. The loop also checks [kotlinx.coroutines.isActive] so a plain
 * coroutine cancel is honored at the next line boundary.
 */
private suspend fun streamChatCompletionCancellable(
  hosts: List<String>,
  port: Int,
  bearerToken: String,
  modelName: String?,
  history: List<Pair<String, String>>,
  onConn: (HttpURLConnection) -> Unit,
  onDelta: (String) -> Unit,
) {
  withContext(Dispatchers.IO) {
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

    var lastError: Exception? = null
    for (host in hosts) {
      var emitted = false
      try {
        val url = URL("http://$host:$port/v1/chat/completions")
        val conn =
          (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 8_000
            readTimeout = 0
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "text/event-stream")
            if (bearerToken.isNotBlank()) {
              setRequestProperty("Authorization", "Bearer $bearerToken")
            }
          }
        onConn(conn)
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

        val code = conn.responseCode
        if (code !in 200..299) {
          val err =
            (conn.errorStream ?: conn.inputStream)
              ?.bufferedReader()
              ?.use { it.readText() }
              .orEmpty()
          throw IllegalStateException("服务返回 $code ${err.take(300)}")
        }

        conn.inputStream.bufferedReader().use { reader ->
          while (isActive) {
            val line = reader.readLine() ?: break
            if (line.isEmpty() || !line.startsWith("data:")) continue
            val payload = line.removePrefix("data:").trim()
            if (payload == "[DONE]") break
            val delta = extractDelta(payload)
            if (!delta.isNullOrEmpty()) {
              emitted = true
              onDelta(delta)
            }
          }
        }
        return@withContext
      } catch (e: Exception) {
        // If we were cancelled (forced stop), unwind quietly.
        if (!isActive) return@withContext
        if (emitted || e is IllegalStateException) throw e
        lastError = e
      }
    }
    throw lastError ?: IllegalStateException("无法连接本地模型服务")
  }
}

private fun extractDelta(payload: String): String? =
  try {
    val choices = chatJson.parseToJsonElement(payload).jsonObject["choices"]?.jsonArray
    val first = choices?.firstOrNull()?.jsonObject
    val delta = first?.get("delta")?.jsonObject
    val content = delta?.get("content") as? JsonPrimitive
    content?.contentOrNull
  } catch (_: Exception) {
    null
  }
