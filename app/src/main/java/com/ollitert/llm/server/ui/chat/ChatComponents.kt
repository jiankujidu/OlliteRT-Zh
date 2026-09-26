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

import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.TextView
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.widget.Toast
import com.ollitert.llm.server.data.db.ChatMessageEntity
import com.ollitert.llm.server.ui.common.MarkdownText
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary

enum class SegType { TEXT, CODE, THINK }

data class Segment(
  val type: SegType,
  val text: String,
  val lang: String = "",
)

/** Tags that only ever appear inside a real HTML document/fragment. */
private val HTML_DOC_HINT = Regex("(?i)<(html|!doctype|body|head|style|script)\\b")

/** A paired container tag, e.g. `<div class="x">…</div>`. */
private val HTML_PAIR =
  Regex("(?is)<(div|table|h1|h2|h3|p|span|button|ul|ol|li|section|svg|form)\\b[^>]*>.*?</\\1\\s*>")

private val HTML_TAG = Regex("(?is)<[^>]+>")

/**
 * True when [code] is HTML rather than prose.
 *
 * Local models usually emit the markup **without** a ``` fence, so anything that
 * looks like HTML has to be offered a preview even outside fenced blocks.
 */
internal fun looksLikeHtml(code: String): Boolean =
  HTML_DOC_HINT.containsMatchIn(code) ||
    HTML_PAIR.containsMatchIn(code) ||
    (code.contains("<svg", ignoreCase = true) && code.contains("</svg>"))

/**
 * Returns [text] when it is an HTML answer worth showing as source + preview, or
 * null when it is ordinary prose.
 *
 * The `>` / `<` guard rejects half-streamed markup: without it a message would flip
 * between markdown and a code card on every token while the answer arrives.
 */
internal fun extractHtmlCandidate(text: String): String? {
  val t = text.trim()
  if (t.length < 16) return null
  if (!looksLikeHtml(t)) return null
  val lastOpen = t.lastIndexOf('<')
  val lastClose = t.lastIndexOf('>')
  if (lastOpen > lastClose) return null
  if (HTML_TAG.replace(t, "").trim().length > 40) return null
  return t
}

/** Wraps a bare HTML fragment in a full document so it renders with sane defaults. */
private fun wrapHtmlDocument(code: String): String {
  val s = code.trim()
  val viewport = "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
  val baseStyle =
    "<style>html,body{margin:0;padding:0;}body{font-family:sans-serif;padding:12px;line-height:1.5;}" +
      "img{max-width:100%;height:auto;}pre,code{white-space:pre-wrap;}</style>"
  if (!s.contains("<html", ignoreCase = true)) {
    return "<!DOCTYPE html><html><head>$viewport$baseStyle</head><body>$s</body></html>"
  }
  var out = if (s.startsWith("<!", ignoreCase = true)) s else "<!DOCTYPE html>$s"
  if (!out.contains("viewport", ignoreCase = true)) {
    // Splice the meta tag in manually: the replaceFirst(regex) { … } overload resolves
    // to the (String, String) variant here and fails to compile.
    val headTag =
      Regex("(?i)<head\\b[^>]*>").find(out)
        ?: Regex("(?i)<html\\b[^>]*>").find(out)
    if (headTag != null) {
      val cut = headTag.range.last + 1
      val insert = if (headTag.value.contains("<head", ignoreCase = true)) viewport else "<head>$viewport</head>"
      out = out.substring(0, cut) + insert + out.substring(cut)
    }
  }
  return out
}

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
  val context = LocalContext.current
  var copied by remember { mutableStateOf(false) }
  val isHtml =
    lang.equals("html", ignoreCase = true) ||
      lang.equals("htm", ignoreCase = true) ||
      looksLikeHtml(code)
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
              Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.size(28.dp),
          ) {
            Icon(
              if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
              contentDescription = "复制代码",
              Modifier.size(18.dp),
              tint = if (copied) OlliteRTPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
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
  val context = LocalContext.current
  val document = remember(html) { wrapHtmlDocument(html) }
  // The content is not reloaded on every recomposition — repeating loadData would
  // blank the view and restart rendering, which looked like a broken preview.
  var loaded by remember { mutableStateOf<String?>(null) }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      shape = RoundedCornerShape(16.dp),
      color = MaterialTheme.colorScheme.surface,
      // An explicit box height is required: a wrap-content Dialog leaves weight(1f)
      // with no finite constraint, and the WebView would measure 0 px tall.
      modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.88f),
    ) {
      Column {
        Row(
          Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text("HTML 预览", style = MaterialTheme.typography.titleSmall)
          Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
              onClick = {
                clipboard.setText(AnnotatedString(html))
                Toast.makeText(context, "已复制 HTML", Toast.LENGTH_SHORT).show()
              },
            ) {
              Text("复制", style = MaterialTheme.typography.labelMedium)
            }
            IconButton(onClick = onDismiss) {
              Icon(
                Icons.Outlined.Close,
                contentDescription = "关闭",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
        HorizontalDivider()
        AndroidView(
          modifier = Modifier.weight(1f).fillMaxWidth(),
          factory = { ctx ->
            try {
              WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadsImagesAutomatically = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                // Offline-only: external CDNs would otherwise stall the page load and
                // leave a blank screen while the request times out.
                settings.blockNetworkLoads = true
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                setBackgroundColor(0xFFFFFFFF.toInt())
                loaded = document
                loadDataWithBaseURL(null, document, "text/html", "utf-8", null)
              }
            } catch (e: Exception) {
              TextView(ctx).apply {
                text = "预览不可用：${e.localizedMessage}"
                setTextColor(0xFFB00020.toInt())
                setPadding(16, 16, 16, 16)
              }
            }
          },
          update = { view ->
            if (view is WebView && loaded != document) {
              loaded = document
              runCatching {
                view.loadDataWithBaseURL(null, document, "text/html", "utf-8", null)
              }
            }
          },
        )
        HorizontalDivider()
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
          Text(
            "离线渲染，外链资源不会加载",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
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
  // Two different renderings on purpose:
  //  * a streaming answer is shown as plain, cheap text — no per-token markdown
  //    re-parse, no re-layout of a rich tree;
  //  * once it is finished we parse markdown exactly once and show the rich version.
  // This also stops the text from twitching as emphasis/heading styling kicks in
  // mid-word during streaming.
  val segments =
    remember(msg.content, msg.streaming) {
      if (msg.streaming) emptyList() else parseMessage(msg.content)
    }
  val hasBody = segments.any { it.type != SegType.THINK && it.text.isNotBlank() }
  // Thinking models (e.g. Agents-A1) stream their reasoning inside <think> … </think>
  // on the same channel as the answer, and often never emit a closing tag or a final
  // answer. Hiding it wholesale left an empty bubble that looked like "no reply at all",
  // so when reasoning is *all* we got, we surface it instead of swallowing the turn.
  val thinkOnly =
    !hasBody && !isUser && segments.any { it.type == SegType.THINK && it.text.isNotBlank() }
  // Local models usually answer with raw markup instead of a ```html fence. Rendered as
  // markdown that showed the tags as plain text with no preview — "the HTML won't render".
  // Judged only once streaming stops, so the bubble cannot flip between markdown and a
  // code card while tokens are still arriving.
  val htmlCandidates =
    remember(msg.content, msg.streaming) {
      if (msg.streaming) {
        emptyMap()
      } else {
        val map = LinkedHashMap<Int, String>()
        segments.forEachIndexed { i, seg ->
          if (seg.type == SegType.TEXT) extractHtmlCandidate(seg.text)?.let { map[i] = it }
        }
        map
      }
    }

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

        if (msg.streaming) {
          // Plain text while the answer arrives: one Text node that only ever grows,
          // so nothing gets cleared and re-drawn between tokens.
          Text(
            if (msg.content.isBlank()) "思考中…" else msg.content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
        } else if (hasBody) {
          if (htmlCandidates.isNotEmpty()) {
            segments.forEachIndexed { index, seg ->
              when (seg.type) {
                SegType.THINK -> ThinkingBlock(seg.text)
                SegType.CODE -> CodeBlockCard(seg.lang, seg.text)
                SegType.TEXT -> {
                  val candidate = htmlCandidates[index]
                  if (candidate != null) {
                    CodeBlockCard("html", candidate)
                  } else {
                    MarkdownText(seg.text, modifier = Modifier.fillMaxWidth())
                  }
                }
              }
            }
          } else {
            segments.forEach { seg ->
              when (seg.type) {
                SegType.THINK -> ThinkingBlock(seg.text)
                SegType.CODE -> CodeBlockCard(seg.lang, seg.text)
                SegType.TEXT ->
                  MarkdownText(seg.text, modifier = Modifier.fillMaxWidth())
              }
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
