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

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A chat project groups conversations (mirrors how Doubao / Notion let you
 * bucket chats under a project). Projects are ordered by [sortOrder] then
 * [createdAt].
 */
@Entity(tableName = "chat_projects")
data class ChatProjectEntity(
  @PrimaryKey val id: String,
  val name: String,
  val createdAt: Long,
  val updatedAt: Long,
  val sortOrder: Int,
)
