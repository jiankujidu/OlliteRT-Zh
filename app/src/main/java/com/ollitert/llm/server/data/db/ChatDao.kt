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

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** A project together with every conversation it contains, for the sidebar tree. */
data class ProjectWithConversations(
  @Embedded val project: ChatProjectEntity,
  @Relation(parentColumn = "id", entityColumn = "projectId")
  val conversations: List<ChatConversationEntity>,
)

@Dao
interface ChatDao {
  // ── project tree (for sidebar) ──────────────────────────────────────────
  @Transaction
  @Query("SELECT * FROM chat_projects ORDER BY sortOrder ASC, createdAt ASC")
  fun observeProjectsWithConversations(): Flow<List<ProjectWithConversations>>

  @Query("SELECT * FROM chat_projects ORDER BY sortOrder ASC, createdAt ASC")
  fun observeProjects(): Flow<List<ChatProjectEntity>>

  @Query("SELECT MAX(sortOrder) FROM chat_projects")
  suspend fun maxProjectSort(): Int?

  // ── conversations ───────────────────────────────────────────────────────
  @Query(
    "SELECT * FROM chat_conversations WHERE projectId = :projectId " +
      "ORDER BY sortOrder ASC, updatedAt DESC",
  )
  fun observeConversations(projectId: String): Flow<List<ChatConversationEntity>>

  @Query("SELECT * FROM chat_conversations WHERE id = :id")
  suspend fun getConversation(id: String): ChatConversationEntity?

  @Query(
    "SELECT * FROM chat_conversations WHERE projectId = :projectId " +
      "ORDER BY sortOrder ASC LIMIT 1",
  )
  suspend fun firstConversation(projectId: String): ChatConversationEntity?

  @Query("SELECT * FROM chat_conversations WHERE projectId = :projectId")
  suspend fun allConversations(projectId: String): List<ChatConversationEntity>

  @Query("SELECT MAX(sortOrder) FROM chat_conversations WHERE projectId = :projectId")
  suspend fun maxConvSort(projectId: String): Int?

  // ── messages ────────────────────────────────────────────────────────────
  @Query(
    "SELECT * FROM chat_messages WHERE conversationId = :conversationId " +
      "ORDER BY sortOrder ASC, createdAt ASC",
  )
  fun observeMessages(conversationId: String): Flow<List<ChatMessageEntity>>

  @Query(
    "SELECT * FROM chat_messages WHERE conversationId = :conversationId " +
      "ORDER BY sortOrder ASC, createdAt ASC",
  )
  suspend fun getMessages(conversationId: String): List<ChatMessageEntity>

  @Query("UPDATE chat_messages SET streaming = 0 WHERE conversationId = :conversationId")
  suspend fun clearStreaming(conversationId: String)

  // ── writes ──────────────────────────────────────────────────────────────
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertProject(project: ChatProjectEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertConversation(conversation: ChatConversationEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMessage(message: ChatMessageEntity)

  @Update
  suspend fun updateProject(project: ChatProjectEntity)

  @Update
  suspend fun updateConversation(conversation: ChatConversationEntity)

  @Update
  suspend fun updateMessage(message: ChatMessageEntity)

  @Query("DELETE FROM chat_projects WHERE id = :id")
  suspend fun deleteProject(id: String)

  @Query("DELETE FROM chat_conversations WHERE id = :id")
  suspend fun deleteConversation(id: String)

  @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId")
  suspend fun deleteMessages(conversationId: String)

  @Query("DELETE FROM chat_messages WHERE id = :id")
  suspend fun deleteMessage(id: String)
}
