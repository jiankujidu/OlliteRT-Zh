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

package com.ollitert.llm.server.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.GitHubConfig
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary

/**
 * Donation / community dialog: WeChat donation QR code plus the community
 * (QQ group / Telegram / WeChat official account) links.
 * Shown automatically on every app launch, and also reachable from Settings.
 */
@Composable
fun DonateDialog(
  onDismiss: () -> Unit,
) {
  val uriHandler = LocalUriHandler.current
  val context = LocalContext.current
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.dialog_donate_title)) },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = stringResource(R.string.dialog_donate_body),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // ── WeChat QR code ────────────────────────────────────────────────
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(vertical = 12.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = stringResource(R.string.donate_qr_title),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Image(
            painter = painterResource(R.drawable.donate_qr_wechat),
            contentDescription = stringResource(R.string.community_qr_content_desc),
            contentScale = ContentScale.Fit,
            modifier = Modifier
              .fillMaxWidth(0.72f)
              .clip(RoundedCornerShape(8.dp)),
          )
          Text(
            text = stringResource(R.string.donate_qr_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ── Community links ───────────────────────────────────────────────
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        CommunityRow(
          icon = Icons.Outlined.Groups,
          title = stringResource(R.string.community_qq_group),
          subtitle = stringResource(R.string.community_qq_group_id),
          onClick = {
            onDismiss()
            uriHandler.openUri(GitHubConfig.COMMUNITY_QQ_GROUP_URL)
          },
        )
        CommunityRow(
          icon = Icons.AutoMirrored.Outlined.Send,
          title = stringResource(R.string.community_telegram),
          subtitle = null,
          onClick = {
            onDismiss()
            uriHandler.openUri(GitHubConfig.COMMUNITY_TELEGRAM_URL)
          },
        )
        CommunityRow(
          icon = Icons.AutoMirrored.Outlined.Chat,
          title = stringResource(R.string.community_wechat),
          subtitle = stringResource(R.string.community_wechat_hint),
          onClick = {
            onDismiss()
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("wechat", "一起瞎折腾"))
            Toast.makeText(context, context.getString(R.string.community_wechat), Toast.LENGTH_SHORT).show()
          },
        )
      }
    },
    confirmButton = {},
    dismissButton = {
      Button(
        onClick = onDismiss,
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
          contentColor = MaterialTheme.colorScheme.onSurface,
        ),
      ) {
        Text(stringResource(R.string.close))
      }
    },
  )
}

/** A tappable community link row with icon, title and optional subtitle. */
@Composable
private fun CommunityRow(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  subtitle: String?,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(10.dp))
      .background(MaterialTheme.colorScheme.surfaceContainerHighest)
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = OlliteRTPrimary,
      modifier = Modifier.size(20.dp),
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
      )
      if (!subtitle.isNullOrBlank()) {
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    Icon(
      imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(16.dp),
    )
  }
}

/**
 * Engagement prompt shown after the user has manually started the server N times.
 * Asks the user to support development, star the repo, or dismiss.
 *
 * @param onSupportDevelopment Called when the user taps "Support Development" — caller should open [DonateDialog].
 * @param onDismiss Called when the user taps "Not now" — caller records whether "Don't show again" was checked.
 * @param onStarOnGitHub Called when the user taps "Star on GitHub" — treated as a positive engagement (permanently suppresses).
 */
@Composable
fun EngagementPromptDialog(
  onSupportDevelopment: () -> Unit,
  onStarOnGitHub: () -> Unit,
  onDismiss: (permanentlyDismiss: Boolean) -> Unit,
) {
  var dontShowAgain by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = { onDismiss(dontShowAgain) },
    title = {
      Text(
        text = stringResource(R.string.dialog_engagement_title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )
    },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = stringResource(R.string.dialog_engagement_body),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Support Development button
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(OlliteRTPrimary.copy(alpha = 0.15f))
            .clickable { onSupportDevelopment() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.Favorite,
            contentDescription = null,
            tint = OlliteRTPrimary,
            modifier = Modifier.size(20.dp),
          )
          Text(
            text = stringResource(R.string.label_support_development),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = OlliteRTPrimary,
            modifier = Modifier.weight(1f),
          )
        }

        // Star on GitHub button
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable { onStarOnGitHub() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.StarOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
          )
          Text(
            text = stringResource(R.string.label_star_on_github),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
          )
          Icon(
            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
          )
        }

        // "Don't show this again" checkbox
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { dontShowAgain = !dontShowAgain }
            .padding(vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Checkbox(
            checked = dontShowAgain,
            onCheckedChange = { dontShowAgain = it },
            colors = CheckboxDefaults.colors(checkedColor = OlliteRTPrimary),
          )
          Text(
            text = stringResource(R.string.label_dont_show_again),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    },
    confirmButton = {},
    dismissButton = {
      TextButton(onClick = { onDismiss(dontShowAgain) }) {
        Text(
          text = stringResource(R.string.button_not_now),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
  )
}
