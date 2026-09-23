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

package com.ollitert.llm.server.common

/**
 * Single source of truth for the OlliteRT GitHub repository location.
 * All repo-derived URLs are built from [OWNER] and [REPO] so that a repo
 * rename or transfer only requires updating these two values.
 */
object GitHubConfig {
  const val OWNER = "NightMean"
  const val REPO = "OlliteRT"

  /** Repository homepage (e.g. for "Learn More" links). */
  const val REPO_URL = "https://github.com/$OWNER/$REPO"

  /** GitHub Releases page. */
  const val RELEASES_URL = "$REPO_URL/releases"

  /** Base URL for the GitHub REST API for this repo. */
  const val API_BASE = "https://api.github.com/repos/$OWNER/$REPO"

  /** URL to open a new bug report issue with the YAML template. */
  const val NEW_BUG_REPORT_URL = "$REPO_URL/issues/new?template=01_bug_report.yml"

  /** Privacy Policy hosted in the repo docs folder. */
  const val PRIVACY_POLICY_URL = "$REPO_URL/blob/main/docs/PRIVACY_POLICY.md"

  // ---------------------------------------------------------------------------
  // Community (一起瞎折腾)
  // ---------------------------------------------------------------------------

  /** QQ group invite link for the "一起瞎折腾" group (465594080). */
  const val COMMUNITY_QQ_GROUP_URL =
    "https://qun.qq.com/universal-share/share?ac=1&authKey=7%2FQK38R4%2B%2FHuNw7ZclexIkFbvD1loZz7SCBcVs6CwKDRBHWIqN%2BCo30GmvQhDu2o&busi_data=eyJncm91cENvZGUiOiI0NjU1OTQwODAiLCJ0b2tlbiI6ImxyM3lhWDBuL3gvejNubThCSnlqd1plSlpkUm9PV2hESy9pUmxTbXhTMnJjcUxmZ0ZQNFJ5NVNYN3hmTk9uQ1giLCJ1aW4iOiIzNjQxNTIzOTI1In0%3D&data=3OtvhIVJBi3yAnFgpF0UUP0dG_PD3IfjkHyYpxm5xlrTO_Nnb86dl19M-yVUxuyviNTPk84y5b-4UJx1yWjtOQ&svctype=4&tempid=h5_group_info"

  /** QQ group number, shown as text next to the invite link. */
  const val COMMUNITY_QQ_GROUP_NUMBER = "465594080"

  /** Telegram group/channel invite. */
  const val COMMUNITY_TELEGRAM_URL = "https://t.me/+5zdHmNqXIZdmYWVl"

  // ---------------------------------------------------------------------------
  // HuggingFace
  // ---------------------------------------------------------------------------

  // 官方源根域名：模型清单里的官方源模型没有显式 url，下载/主页/token 判断都基于它拼接。
  // 国内用户请改用「国内镜像源」（清单已内置 ModelScope 直链，国内单步可达）；
  // 此处必须保持 huggingface.co 原站，走 hf-mirror 反而会因 LFS 302 回原站 CDN 而下载失败。
  const val HUGGINGFACE_BASE_URL = "https://huggingface.co"

  // ---------------------------------------------------------------------------
  // Model allowlists and documentation (hosted in this repo)
  // ---------------------------------------------------------------------------

  /**
   * URL for the master model allowlist JSON file.
   * Version filtering is handled by minAppVersion/maxAppVersion fields in the JSON.
   *
   * Note: raw.githubusercontent.com is unreachable from mainland China, which made every
   * refresh fail there; the request is routed through a GitHub proxy so the official
   * catalogue can still be refreshed. The bundled asset remains the offline fallback.
   */
  const val ALLOWLIST_URL =
    "https://gh-proxy.com/https://raw.githubusercontent.com/$OWNER/$REPO/refs/heads/main/model_allowlists/v1/model_allowlist.json"

  // ---------------------------------------------------------------------------
  // 中文版项目主页（基于 NightMean/OlliteRT 修改的分支）
  // ---------------------------------------------------------------------------

  /**
   * 中文版（OlliteRT 中文版 / OlliteRT-Zh）项目主页。
   * 设置页「项目主页」入口、README 均指向此处。
   * 注意：新建 GitHub 仓库前此处为占位，仓库创建后替换为真实地址。
   */
  const val PROJECT_GITHUB_URL = "https://github.com/PLACEHOLDER_OWNER/OlliteRT-Zh"

}
