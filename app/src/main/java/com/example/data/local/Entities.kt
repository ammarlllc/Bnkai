package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MessageType {
    TEXT,
    VOICE,
    VIDEO_EMOJI,
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

enum class TickBadgeType {
    NONE,
    BLUE,
    GREEN,
    RED,
    VIDEO_STICKER
}

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val userId: String,
    val email: String = "",
    val displayName: String,
    val username: String,
    val avatarPath: String? = null,
    val avatarBase64: String? = null,
    val videoAvatarPath: String? = null,
    val videoAvatarBase64: String? = null,
    val isOwner: Boolean = false,
    val hasBlueTick: Boolean = false,
    val badgeType: String = TickBadgeType.NONE.name,
    val stickerVideoPath: String? = null,
    val stickerVideoBase64: String? = null,
    val isBanned: Boolean = false,
    val banExpiresAt: Long = 0L, // 0 = not banned, -1 = permanent, >0 = epoch ms
    val isOnline: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis()
) {
    fun effectiveBadgeType(): TickBadgeType {
        val parsed = runCatching { TickBadgeType.valueOf(badgeType) }.getOrNull()
        if (parsed != null && parsed != TickBadgeType.NONE) return parsed
        return if (hasBlueTick) TickBadgeType.BLUE else TickBadgeType.NONE
    }

    fun canUseVideoFeatures(): Boolean =
        isOwner || effectiveBadgeType() == TickBadgeType.GREEN || effectiveBadgeType() == TickBadgeType.VIDEO_STICKER

    fun isCurrentlyBanned(now: Long = System.currentTimeMillis()): Boolean {
        if (!isBanned) return false
        if (banExpiresAt == -1L || banExpiresAt == 0L) return true
        return now < banExpiresAt
    }
}

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
    val isPublic: Boolean = true,
    val mutedUsersCsv: String = "", // userId:expiryMs,userId:expiryMs
    val createdAt: Long = System.currentTimeMillis()
) {
    fun adminIds(): List<String> =
        adminIdsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    fun memberIds(): List<String> =
        memberIdsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    fun mutedMap(): Map<String, Long> {
        if (mutedUsersCsv.isBlank()) return emptyMap()
        val map = mutableMapOf<String, Long>()
        mutedUsersCsv.split(",").forEach { entry ->
            val parts = entry.split(":")
            if (parts.size == 2) {
                val uid = parts[0].trim()
                val exp = parts[1].trim().toLongOrNull()
                if (uid.isNotEmpty() && exp != null) {
                    map[uid] = exp
                }
            }
        }
        return map
    }

    fun mutedUntil(userId: String, now: Long = System.currentTimeMillis()): Long {
        val exp = mutedMap()[userId] ?: return 0L
        return if (exp > now) exp else 0L
    }
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
    val videoPath: String? = null,
    val durationMs: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = MessageStatus.SENT.name,
    val groupInviteId: String? = null,
    val groupInviteName: String? = null,
    val groupInviteToken: String? = null,
    val groupSnapshotJson: String? = null,
    val isReadByMe: Boolean = false
)
