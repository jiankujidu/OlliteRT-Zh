/*
 * Copyright 2025 Google LLC
 * Modifications Copyright 2025-2026 @NightMean (https://github.com/NightMean)
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

package com.ollitert.llm.server.data.allowlist
import com.ollitert.llm.server.data.model.Model

// Model allowlist asset/disk-cache filenames.
// Master lives at /model_allowlists/v1/model_allowlist.json (repo root) — Gradle copies it
// into assets/ on every build (see syncAllowlist task in app/build.gradle.kts).
const val MODEL_ALLOWLIST_CACHE_PREFIX = "model_allowlist_"
const val MODEL_ALLOWLIST_FILENAME = "model_allowlist.json"
const val MODEL_ALLOWLIST_TEST_FILENAME = "${MODEL_ALLOWLIST_CACHE_PREFIX}test.json"
const val MODEL_ALLOWLIST_OFFICIAL_FILENAME = "${MODEL_ALLOWLIST_CACHE_PREFIX}official.json"
const val OFFICIAL_REPO_ID = "official"

/** Built-in repository id for the mainland-China catalogue (offline-friendly, mirrors for downloads). */
const val CN_REPO_ID = "cn_mirror"
/** Asset name of the China catalogue; generated from /model_allowlists/v1/model_allowlist_cn.json. */
const val MODEL_ALLOWLIST_CN_FILENAME = "model_allowlist_cn.json"

/** Maps a built-in repository id to the allowlist asset bundled in the APK. */
fun allowlistAssetFilename(repoId: String): String =
  if (repoId == CN_REPO_ID) MODEL_ALLOWLIST_CN_FILENAME else MODEL_ALLOWLIST_FILENAME

const val MAX_REPO_NAME_LENGTH = 100
const val MAX_REPO_DESCRIPTION_LENGTH = 500
const val MAX_REPO_ICON_URL_LENGTH = 2048
const val MAX_ALLOWLIST_RESPONSE_BYTES = 10L * 1024 * 1024
const val MAX_REDIRECTS = 5
const val MAX_MODELS_PER_REPO = 500
const val REPO_LIMIT_WARNING_THRESHOLD = 16
const val UNKNOWN_REPO_LABEL = "Unknown"
const val MAX_REPO_ERROR_LENGTH = 200
const val UNKNOWN_ERROR_FALLBACK = "Unknown error"
