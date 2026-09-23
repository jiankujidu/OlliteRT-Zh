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

package com.ollitert.llm.server.data.allowlist

import android.content.Context
import android.util.Log
import com.ollitert.llm.server.data.model.repoCacheFilename
import java.io.File

private const val TAG = "OlliteRT.AllowCache"

/** Built-in repos whose bundled asset must also exist as an on-disk cache file. */
private val BUILTIN_ASSET_REPO_IDS = listOf(OFFICIAL_REPO_ID, CN_REPO_ID)

/**
 * Materialises each built-in repo's bundled allowlist asset into its on-disk cache file.
 *
 * The China catalogue is asset-only (url == ""), so [RepositoryManager.refreshAll] never
 * writes its cache file. Catalog loaders that read disk-cache files directly — notably
 * ServerService's [ModelCatalogMerger] reached via ModelLifecycle.pickModelByName — would
 * therefore never see its models, and starting a downloaded CN model failed with
 * "Model not found" even though the UI (which reads the bundled asset) listed it.
 *
 * The official asset is materialised too: once any cache file exists on disk the merger
 * no longer falls back to a bundled asset, so a missing official cache would drop the
 * official models instead.
 *
 * Idempotent — writes only when the cache file is absent or older than the bundled asset.
 */
fun materializeBuiltinAllowlistAssets(context: Context, externalFilesDir: File?) {
  if (externalFilesDir == null) return
  for (repoId in BUILTIN_ASSET_REPO_IDS) {
    materializeBuiltinAllowlistAsset(context, externalFilesDir, repoId)
  }
}

private fun materializeBuiltinAllowlistAsset(
  context: Context,
  externalFilesDir: File,
  repoId: String,
) {
  val assetFilename = allowlistAssetFilename(repoId)
  val cacheFilename = repoCacheFilename(repoId)
  try {
    val raw = context.assets.open(assetFilename).bufferedReader().use { it.readText() }
    val bundled = ModelAllowlistJson.decode(raw)

    val cacheFile = File(externalFilesDir, cacheFilename)
    val onDiskVersion = if (cacheFile.exists() && cacheFile.length() > 0L) {
      try {
        ModelAllowlistJson.decode(cacheFile.readText()).contentVersion
      } catch (e: Exception) {
        Log.w(TAG, "Unreadable allowlist cache '$cacheFilename' — will overwrite", e)
        null
      }
    } else {
      null
    }
    if (onDiskVersion != null && onDiskVersion >= bundled.contentVersion) return

    val tmpFile = File(externalFilesDir, "$cacheFilename.tmp")
    tmpFile.writeText(raw)
    if (tmpFile.renameTo(cacheFile)) {
      Log.d(TAG, "Materialised bundled '$assetFilename' -> '$cacheFilename' (v${bundled.contentVersion})")
    } else {
      tmpFile.delete()
      Log.w(TAG, "Failed to rename '$cacheFilename.tmp' into place")
    }
  } catch (e: Exception) {
    Log.w(TAG, "Failed to materialise bundled allowlist for repo '$repoId'", e)
  }
}
