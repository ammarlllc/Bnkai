package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MessageType {
    TEXT,
    VOICE,
    GROUP_INVITE,
    SYSTEM
}

enum class MessageStatus {
    SENT,
    DELIVERED,
    READ
}

enum class PresenceState {
    IDLE,
    TYPING,
    RECORDING_VOICE
}

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val userId: String,
    val email: String = "",
    val displayName: String,
    val username: String,
    val avatarPath: String? = null,
    val avatarBase64: String? = null,
    val isOwner: Boolean = false,
    val hasBlueTick: Boolean = false,
    val isBanned: Boolean = false,
    val isOnline: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis()
)

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val groupId: String,
    val name: String,
    val avatarPath: String? = null,
    val avatarBase64: String? = null,
    val inviteToken: String,
    val creatorId: String,
    val adminIdsCsv: String,
    val memberIdsCsv: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun adminIds(): List<String> =
        adminIdsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    fun memberIds(): List<String> =
        memberIdsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val msgId: String,
    val chatId: String,
    val isGroup: Boolean,
    val senderId: String,
    val senderName: String,
    val senderUsername: String,
    val recipientId: String,
    val type: String = MessageType.TEXT.name,
    val text: String = "",
    val audioPath: String? = null,
    val durationMs: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = MessageStatus.SENT.name,
    val groupInviteId: String? = null,
    val groupInviteName: String? = null,
    val groupInviteToken: String? = null,
    val isReadByMe: Boolean = false
)
