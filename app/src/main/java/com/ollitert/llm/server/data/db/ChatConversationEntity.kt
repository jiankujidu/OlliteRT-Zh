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
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single conversation, owned by a [ChatProjectEntity]. Ordered within its
 * project by [sortOrder], then most-recently-updated first.
 */
@Entity(
  tableName = "chat_conversations",
  foreignKeys = [
    ForeignKey(
      entity = ChatProjectEntity::class,
      parentColumns = ["id"],
      childColumns = ["projectId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index("projectId")],
)
data class ChatConversationEntity(
  @PrimaryKey val id: String,
  val projectId: String,
  val title: String,
  val createdAt: Long,
  val updatedAt: Long,
  val sortOrder: Int,
)
