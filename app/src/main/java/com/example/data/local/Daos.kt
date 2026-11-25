package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY isOnline DESC, displayName ASC")
    fun observeAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE userId = :userId LIMIT 1")
    fun observeUser(userId: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE userId = :userId LIMIT 1")
    suspend fun getUserById(userId: String): UserEntity?

    @Query("SELECT * FROM users WHERE LOWER(username) = LOWER(:username) LIMIT 1")
    suspend fun getUserByUsername(username: String): UserEntity?

    @Query("SELECT * FROM users")
    suspend fun getAllUsersOnce(): List<UserEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUser(user: UserEntity)

    @Query("UPDATE users SET isBanned = :banned WHERE userId = :userId")
    suspend fun setUserBanned(userId: String, banned: Boolean)

    @Query("UPDATE users SET isOnline = :online, lastSeen = :lastSeen WHERE userId = :userId")
    suspend fun setUserOnline(userId: String, online: Boolean, lastSeen: Long = System.currentTimeMillis())

    @Query("UPDATE users SET isOnline = 0")
    suspend fun markAllOffline()
}

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups ORDER BY createdAt DESC")
    fun observeAllGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups WHERE groupId = :groupId LIMIT 1")
    fun observeGroup(groupId: String): Flow<GroupEntity?>

    @Query("SELECT * FROM groups WHERE groupId = :groupId LIMIT 1")
    suspend fun getGroupById(groupId: String): GroupEntity?

    @Query("SELECT * FROM groups")
    suspend fun getAllGroupsOnce(): List<GroupEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGroup(group: GroupEntity)

    @Query("DELETE FROM groups WHERE groupId = :groupId")
    suspend fun deleteGroup(groupId: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun observeMessagesForChat(chatId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun observeAllMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE msgId = :msgId LIMIT 1")
    suspend fun getMessageById(msgId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE chatId = :chatId AND senderId != :myUserId AND isReadByMe = 0")
    suspend fun getUnreadIncomingMessages(chatId: String, myUserId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: MessageEntity)

    @Query("UPDATE messages SET status = :status WHERE msgId = :msgId")
    suspend fun updateMessageStatus(msgId: String, status: String)

    @Query("UPDATE messages SET isReadByMe = 1 WHERE msgId = :msgId")
    suspend fun markReadByMe(msgId: String)
}
