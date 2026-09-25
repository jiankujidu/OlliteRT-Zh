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

package com.ollitert.llm.server.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database for OlliteRT local data.
 *
 * Stores persisted request logs and, since v2, the in-app chat tree
 * (projects → conversations → messages). The v1→v2 migration only *adds*
 * new tables, so [AutoMigration] handles it without touching request logs.
 * [fallbackToDestructiveMigration] stays as a safety net.
 */
@Database(
  entities = [
    RequestLogEntity::class,
    ChatProjectEntity::class,
    ChatConversationEntity::class,
    ChatMessageEntity::class,
  ],
  version = 2,
  exportSchema = true,
  autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class OlliteDatabase : RoomDatabase() {
  abstract fun requestLogDao(): RequestLogDao
  abstract fun chatDao(): ChatDao
}
