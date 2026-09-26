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

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.ollitert.llm.server.data.db.ChatMessageEntity
import com.ollitert.llm.server.ui.common.MarkdownText
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary

enum class SegType { TEXT, CODE, THINK }

data class Segment(
  val type: SegType,
  val text: String,
  val lang: String = "",
)

/** Splits a raw assistant/user message into renderable segments: markdown text,
 *  fenced code blocks, and `<think>` reasoning blocks. */
internal fun parseMessage(raw: String): List<Segment> {
  val segs = mutableListOf<Segment>()
  var i = 0
  val n = raw.length
  while (i < n) {
    if (raw.startsWith("<think>", i)) {
      val end = raw.indexOf("</think>", i)
      val content =
        if (end >= 0) raw.substring(i + 7, end) else raw.substring(i + 7)
      segs.add(Segment(SegType.THINK, content.trim()))
      i = if (end >= 0) end + 8 else n
      continue
    }
    if (raw.startsWith("```", i)) {
      val nl = raw.indexOf('\n', i + 3)
      val lang =
        if (nl >= 0) raw.substring(i + 3, nl).trim() else raw.substring(i + 3).trim()
      val startBody = if (nl >= 0) nl + 1 else i + 3
      val endFence = raw.indexOf("```", startBody)
      val body =
        if (endFence >= 0) raw.substring(startBody, endFence) else raw.substring(startBody)
      segs.add(Segment(SegType.CODE, body.removeSuffix("\n"), lang))
      i = if (endFence >= 0) endFence + 3 else n
      continue
    }
    val next = nextSpecial(raw, i)
    val text = raw.substring(i, next)
    if (text.isNotBlank()) segs.add(Segment(SegType.TEXT, text))
    i = next
  }
  return segs
}

private fun nextSpecial(
  raw: String,
  from: Int,
): Int {
  val t = raw.indexOf("<think>", from)
  val c = raw.indexOf("```", from)
  return minOf(
    if (t < 0) Int.MAX_VALUE else t,
    if (c < 0) Int.MAX_VALUE else c,
    raw.length,
  )
}

@Composable
internal fun ThinkingBlock(
  thinking: String,
  defaultExpanded: Boolean = false,
  title: String = "思考过程",
) {
  var expanded by remember { mutableStateOf(defaultExpanded) }
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    shape = RoundedCornerShape(10.dp),
    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
  ) {
    Column(Modifier.padding(10.dp)) {
      Row(
        Modifier.fillMaxWidth().clickable { expanded = !expanded },
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.Psychology,
          contentDescription = null,
          Modifier.size(16.dp),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Text(
          title,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (expanded) {
        Spacer(Modifier.height(6.dp))
        Text(
          thinking,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontStyle = FontStyle.Italic,
        )
      }
    }
  }
}

@Composable
internal fun CodeBlockCard(
  lang: String,
  code: String,
) {
  val clipboard = LocalClipboardManager.current
  var copied by remember { mutableStateOf(false) }
  val isHtml = lang.equals("html", ignoreCase = true) || lang.equals("htm", ignoreCase = true)
  var showPreview by remember { mutableStateOf(false) }

  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerHighest,
    shape = RoundedCornerShape(10.dp),
    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
  ) {
    Column(Modifier.padding(10.dp)) {
      Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          lang.ifBlank { "代码" },
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f, fill = false),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (isHtml) {
            IconButton(
              onClick = { showPreview = true },
              modifier = Modifier.size(28.dp),
            ) {
              Icon(
                Icons.Outlined.Visibility,
                contentDescription = "预览",
                Modifier.size(18.dp),
                tint = OlliteRTPrimary,
              )
            }
          }
          IconButton(
            onClick = {
              clipboard.setText(AnnotatedString(code))
              copied = true
            },
            modifier = Modifier.size(28.dp),
          ) {
            Icon(
              if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
              contentDescription = "复制代码",
              Modifier.size(18.dp),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
      Spacer(Modifier.height(6.dp))
      Box(Modifier.horizontalScroll(rememberScrollState())) {
        Text(
          code,
          fontFamily = FontFamily.Monospace,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
    }
  }

  if (showPreview) {
    HtmlPreviewDialog(code, onDismiss = { showPreview = false })
  }
}

@Composable
internal fun HtmlPreviewDialog(
  html: String,
  onDismiss: () -> Unit,
) {
  val clipboard = LocalClipboardManager.current
  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(16.dp),
      color = MaterialTheme.colorScheme.surface,
      modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
    ) {
      Column {
        Row(
          Modifier.fillMaxWidth().padding(12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text("HTML 预览", style = MaterialTheme.typography.titleSmall)
          IconButton(onClick = onDismiss) {
            Icon(
              Icons.Outlined.Close,
              contentDescription = "关闭",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        HorizontalDivider()
        AndroidView(
          modifier = Modifier.weight(1f).fillMaxWidth(),
          factory = { ctx ->
            WebView(ctx).apply {
              settings.javaScriptEnabled = true
              settings.domStorageEnabled = true
              loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            }
          },
          update = { it.loadDataWithBaseURL(null, html, "text/html", "utf-8", null) },
        )
        Row(Modifier.padding(12.dp)) {
          TextButton(onClick = { clipboard.setText(AnnotatedString(html)) }) {
            Text("复制 HTML")
          }
        }
      }
    }
  }
}

@Composable
internal fun MessageRow(
  msg: ChatMessageEntity,
  onRegenerate: () -> Unit,
) {
  val isUser = msg.role == "user"
  val segments = remember(msg.content) { parseMessage(msg.content) }
  val hasBody = segments.any { it.type != SegType.THINK && it.text.isNotBlank() }
  // Thinking models (e.g. Agents-A1) stream their reasoning inside <think> … </think>
  // on the same channel as the answer, and often never emit a closing tag or a final
  // answer. Hiding it wholesale left an empty bubble that looked like "no reply at all",
  // so when reasoning is *all* we got, we surface it instead of swallowing the turn.
  val thinkOnly =
    !hasBody && !isUser && segments.any { it.type == SegType.THINK && it.text.isNotBlank() }

  Row(
    Modifier.fillMaxWidth(),
    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
  ) {
    Box(
      Modifier
        .widthIn(max = 320.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(
          if (isUser) {
            OlliteRTPrimary.copy(alpha = 0.18f)
          } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
          },
        ).padding(horizontal = 13.dp, vertical = 10.dp),
    ) {
      Column {
        if (!isUser) {
          Text(
            "助手",
            style = MaterialTheme.typography.labelSmall,
            color = OlliteRTPrimary,
            fontWeight = FontWeight.SemiBold,
          )
          Spacer(Modifier.height(3.dp))
        }

        if (hasBody) {
          segments.forEach { seg ->
            when (seg.type) {
              SegType.THINK -> ThinkingBlock(seg.text)
              SegType.CODE -> CodeBlockCard(seg.lang, seg.text)
              SegType.TEXT ->
                MarkdownText(seg.text, modifier = Modifier.fillMaxWidth())
            }
          }
        } else if (thinkOnly) {
          // Only reasoning came back — show it expanded so the reply is never empty,
          // and explain why there is no final answer.
          segments.filter { it.type == SegType.THINK }.forEach { seg ->
            ThinkingBlock(seg.text, defaultExpanded = true, title = "模型思考内容")
          }
          Spacer(Modifier.height(4.dp))
          Text(
            "模型只输出了思考内容、没有生成正文。可在「模型」页把该模型的「思考」关掉，再点重新生成。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else if (msg.streaming) {
          Text(
            "思考中…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else if (!isUser) {
          Text(
            "模型没有返回内容，可直接重新生成或换个模型再试。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        if (!isUser && !msg.streaming) {
          Spacer(Modifier.height(6.dp))
          Row {
            TextButton(
              onClick = onRegenerate,
              contentPadding = PaddingValues(0.dp),
            ) {
              Icon(
                Icons.Outlined.Refresh,
                contentDescription = null,
                Modifier.size(14.dp),
                tint = OlliteRTPrimary,
              )
              Spacer(Modifier.width(2.dp))
              Text(
                "重新生成",
                style = MaterialTheme.typography.labelSmall,
                color = OlliteRTPrimary,
              )
            }
          }
        }
      }
    }
  }
}
