package com.example.data.repo

import android.content.Context
import android.net.Uri
import com.example.audio.MediaUtils
import com.example.audio.RecordedVoiceResult
import com.example.audio.VideoUtils
import com.example.audio.VoiceAudioManager
import com.example.audio.VoicePlaybackState
import com.example.audio.VoiceRecordingState
import com.example.data.local.AppDatabase
import com.example.data.local.GroupEntity
import com.example.data.local.MessageEntity
import com.example.data.local.MessageStatus
import com.example.data.local.MessageType
import com.example.data.local.PresenceState
import com.example.data.local.SessionPreferences
import com.example.data.local.TickBadgeType
import com.example.data.local.UserEntity
import com.example.network.LanChatClient
import com.example.network.LanConnectionState
import com.example.network.LanProtocol
import com.example.ui.theme.ChatBackgroundTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

data class PresenceIndicator(
    val chatId: String,
    val fromUserId: String,
    val fromUsername: String,
    val state: PresenceState,
    val updatedAt: Long = System.currentTimeMillis()
)

class ChatRepository(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private object OwnerCredentials {
        const val EMAIL = "nk3577850@gmail.com"
        const val USERNAME = "ammar35570"
        const val DISPLAY_NAME = "Λ௱௱Λ尺 н4ㄈҚ3尺"
    }

    private val db = AppDatabase.getInstance(context)
    private val userDao = db.userDao()
    private val groupDao = db.groupDao()
    private val messageDao = db.messageDao()
    val prefs = SessionPreferences(context)

    val lanClient = LanChatClient(context, scope, prefs)
    val voiceManager = VoiceAudioManager(context, scope)

    val connectionState: StateFlow<LanConnectionState> = lanClient.connectionState
    val recordingState: StateFlow<VoiceRecordingState> = voiceManager.recordingState
    val playbackState: StateFlow<VoicePlaybackState> = voiceManager.playbackState

    val activeTheme: StateFlow<ChatBackgroundTheme> = prefs.activeThemeIdFlow
        .map { id -> ChatBackgroundTheme.fromId(id) }
        .stateIn(scope, SharingStarted.Eagerly, ChatBackgroundTheme.HACKER)

    val customThemeImagePath: StateFlow<String?> = prefs.customThemeImagePathFlow
        .stateIn(scope, SharingStarted.Eagerly, null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val currentUser: StateFlow<UserEntity?> = prefs.currentUserIdFlow
        .flatMapLatest { uid ->
            if (uid.isNullOrBlank()) flowOf(null) else userDao.observeUser(uid)
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val allUsers: StateFlow<List<UserEntity>> = userDao.observeAllUsers()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val allGroups: StateFlow<List<GroupEntity>> = groupDao.observeAllGroups()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val allMessages: StateFlow<List<MessageEntity>> = messageDao.observeAllMessages()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _presenceMap = MutableStateFlow<Map<String, PresenceIndicator>>(emptyMap())
    val presenceMap: StateFlow<Map<String, PresenceIndicator>> = _presenceMap.asStateFlow()

    @Volatile
    private var openChatId: String? = null
    private val presenceExpiryJobs = mutableMapOf<String, Job>()

    init {
        lanClient.startAutoConnect {
            currentUser.value?.let { toPublicUserJson(it) }
        }
        scope.launch {
            currentUser.collect { user ->
                lanClient.updateHelloUser(user?.let { toPublicUserJson(it) })
            }
        }
        scope.launch {
            lanClient.incomingPackets.collect { packet ->
                runCatching {
                    handleIncomingPacket(packet)
                }
            }
        }
        // Automatically clear expired timed bans
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                val now = System.currentTimeMillis()
                userDao.getAllUsersOnce().forEach { u ->
                    if (u.isBanned && u.banExpiresAt > 0L && now >= u.banExpiresAt) {
                        userDao.setUserBan(u.userId, false, 0L)
                    }
                }
                delay(5000)
            }
        }
    }

    fun observeMessages(chatId: String): Flow<List<MessageEntity>> =
        messageDao.observeMessagesForChat(chatId)

    fun observeGroup(groupId: String): Flow<GroupEntity?> =
        groupDao.observeGroup(groupId)

    fun setOpenChat(chatId: String?) {
        openChatId = chatId
        if (!chatId.isNullOrBlank()) {
            scope.launch { markChatAsRead(chatId) }
        }
    }

    private fun isOwnerCredentials(email: String, username: String): Boolean {
        val cleanEmail = email.trim()
        val cleanUsername = username.trim().removePrefix("@")
        return cleanEmail.equals(OwnerCredentials.EMAIL, ignoreCase = true) &&
            cleanUsername.equals(OwnerCredentials.USERNAME, ignoreCase = true)
    }

    suspend fun signUp(
        email: String,
        displayName: String,
        username: String,
        avatarUri: Uri?
    ): Result<UserEntity> {
        val cleanEmail = email.trim()
        val cleanUsername = username.trim().removePrefix("@").lowercase()
        val cleanName = displayName.trim()

        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
            return Result.failure(IllegalArgumentException("Please enter a valid Gmail address."))
        }
        if (cleanName.isBlank()) {
            return Result.failure(IllegalArgumentException("Please enter your full name."))
        }
        if (cleanUsername.length < 3 || cleanUsername.contains(" ")) {
            return Result.failure(IllegalArgumentException("Username must be at least 3 characters with no spaces."))
        }

        val owner = isOwnerCredentials(cleanEmail, cleanUsername)
        val userId = "u_$cleanUsername"
        val existing = userDao.getUserById(userId)

        val processedAvatar = avatarUri?.let {
            MediaUtils.savePickedImage(context, it, "avatar_$cleanUsername")
        }

        val resolvedName = if (owner) OwnerCredentials.DISPLAY_NAME else cleanName
        val badge = existing?.badgeType?.takeIf { it != TickBadgeType.NONE.name }
            ?: if (owner) TickBadgeType.BLUE.name else TickBadgeType.NONE.name

        val user = UserEntity(
            userId = userId,
            email = cleanEmail,
            displayName = resolvedName,
            username = cleanUsername,
            avatarPath = processedAvatar?.filePath ?: existing?.avatarPath,
            avatarBase64 = processedAvatar?.base64 ?: existing?.avatarBase64,
            videoAvatarPath = existing?.videoAvatarPath,
            videoAvatarBase64 = existing?.videoAvatarBase64,
            isOwner = owner,
            hasBlueTick = owner || badge == TickBadgeType.BLUE.name,
            badgeType = badge,
            stickerVideoPath = existing?.stickerVideoPath,
            stickerVideoBase64 = existing?.stickerVideoBase64,
            isBanned = existing?.isBanned ?: false,
            banExpiresAt = existing?.banExpiresAt ?: 0L,
            isOnline = true,
            lastSeen = System.currentTimeMillis()
        )
        userDao.upsertUser(user)
        prefs.setCurrentUserId(user.userId)
        lanClient.updateHelloUser(toPublicUserJson(user))
        return Result.success(user)
    }

    suspend fun signIn(
        email: String,
        displayName: String,
        username: String
    ): Result<UserEntity> {
        val cleanEmail = email.trim()
        val cleanUsername = username.trim().removePrefix("@").lowercase()
        val cleanName = displayName.trim()

        if (cleanEmail.isBlank() || cleanUsername.isBlank()) {
            return Result.failure(IllegalArgumentException("Please enter your email and username."))
        }

        if (isOwnerCredentials(cleanEmail, cleanUsername)) {
            val userId = "u_${OwnerCredentials.USERNAME}"
            val existing = userDao.getUserById(userId)
            val badge = existing?.badgeType?.takeIf { it != TickBadgeType.NONE.name } ?: TickBadgeType.BLUE.name
            val ownerUser = UserEntity(
                userId = userId,
                email = OwnerCredentials.EMAIL,
                displayName = OwnerCredentials.DISPLAY_NAME,
                username = OwnerCredentials.USERNAME,
                avatarPath = existing?.avatarPath,
                avatarBase64 = existing?.avatarBase64,
                videoAvatarPath = existing?.videoAvatarPath,
                videoAvatarBase64 = existing?.videoAvatarBase64,
                isOwner = true,
                hasBlueTick = badge == TickBadgeType.BLUE.name,
                badgeType = badge,
                stickerVideoPath = existing?.stickerVideoPath,
                stickerVideoBase64 = existing?.stickerVideoBase64,
                isBanned = false,
                banExpiresAt = 0L,
                isOnline = true,
                lastSeen = System.currentTimeMillis()
            )
            userDao.upsertUser(ownerUser)
            prefs.setCurrentUserId(ownerUser.userId)
            lanClient.updateHelloUser(toPublicUserJson(ownerUser))
            return Result.success(ownerUser)
        }

        val existing = userDao.getUserByUsername(cleanUsername)
            ?: return Result.failure(IllegalArgumentException("Account not found on this device. Please Sign Up."))

        if (!existing.email.equals(cleanEmail, ignoreCase = true)) {
            return Result.failure(IllegalArgumentException("Email does not match this username."))
        }
        if (cleanName.isNotBlank() && !existing.displayName.equals(cleanName, ignoreCase = true)) {
            return Result.failure(IllegalArgumentException("Name does not match this account."))
        }

        val updated = existing.copy(isOnline = true, lastSeen = System.currentTimeMillis())
        userDao.upsertUser(updated)
        prefs.setCurrentUserId(updated.userId)
        lanClient.updateHelloUser(toPublicUserJson(updated))
        return Result.success(updated)
    }

    suspend fun signOut() {
        voiceManager.stopPlayback()
        voiceManager.cancelRecording()
        prefs.setCurrentUserId(null)
    }

    suspend fun updateMyProfile(displayName: String, avatarUri: Uri?): Result<Unit> {
        val me = currentUser.value ?: return Result.failure(IllegalStateException("Not signed in"))
        val processedAvatar = avatarUri?.let {
            MediaUtils.savePickedImage(context, it, "avatar_${me.username}")
        }
        val updated = me.copy(
            displayName = displayName.trim().ifBlank { me.displayName },
            avatarPath = processedAvatar?.filePath ?: me.avatarPath,
            avatarBase64 = processedAvatar?.base64 ?: me.avatarBase64
        )
        userDao.upsertUser(updated)
        lanClient.updateHelloUser(toPublicUserJson(updated))
        return Result.success(Unit)
    }

    suspend fun setMyVideoAvatar(videoUri: Uri): Result<Unit> {
        val me = currentUser.value ?: return Result.failure(IllegalStateException("Not signed in"))
        if (!me.canUseVideoFeatures()) {
            return Result.failure(IllegalStateException("Video profile requires Owner or Green Tick privilege"))
        }
        val processed = VideoUtils.trimAndSaveVideo(context, videoUri, "vavatar_${me.username}", 4000L)
            .getOrElse { return Result.failure(it) }
        val updated = me.copy(
            videoAvatarPath = processed.filePath,
            videoAvatarBase64 = processed.base64
        )
        userDao.upsertUser(updated)
        lanClient.updateHelloUser(toPublicUserJson(updated))
        return Result.success(Unit)
    }

    suspend fun setOwnerBadgeAndSticker(
        badgeType: TickBadgeType,
        stickerVideoUri: Uri? = null
    ): Result<Unit> {
        val me = currentUser.value ?: return Result.failure(IllegalStateException("Not signed in"))
        if (!me.isOwner && !(me.canUseVideoFeatures() && badgeType == TickBadgeType.VIDEO_STICKER)) {
            return Result.failure(IllegalStateException("Unauthorized"))
        }
        var stickerPath = me.stickerVideoPath
        var stickerB64 = me.stickerVideoBase64
        if (stickerVideoUri != null) {
            val processed = VideoUtils.trimAndSaveVideo(context, stickerVideoUri, "sticker_${me.username}", 3000L)
                .getOrElse { return Result.failure(it) }
            stickerPath = processed.filePath
            stickerB64 = processed.base64
        }
        if (badgeType == TickBadgeType.VIDEO_STICKER && stickerB64.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("Please select a 3s video for your sticker badge"))
        }
        val updated = me.copy(
            badgeType = badgeType.name,
            hasBlueTick = badgeType == TickBadgeType.BLUE,
            stickerVideoPath = stickerPath,
            stickerVideoBase64 = stickerB64
        )
        userDao.upsertUser(updated)
        lanClient.updateHelloUser(toPublicUserJson(updated))
        lanClient.sendPacket(LanProtocol.packet(LanProtocol.TYPE_BADGE_SYNC) {
            put("targetUserId", me.userId)
            put("badgeType", badgeType.name)
            put("stickerVideoBase64", stickerB64 ?: "")
        })
        return Result.success(Unit)
    }

    suspend fun setUserBadgeByOwner(targetUserId: String, badgeType: TickBadgeType): Result<Unit> {
        val me = currentUser.value ?: return Result.failure(IllegalStateException("Not signed in"))
        if (!me.isOwner) return Result.failure(IllegalStateException("Unauthorized"))
        val hasBlue = badgeType == TickBadgeType.BLUE
        userDao.setUserBadge(targetUserId, badgeType.name, hasBlue)
        lanClient.sendPacket(LanProtocol.packet(LanProtocol.TYPE_BADGE_SYNC) {
            put("targetUserId", targetUserId)
            put("badgeType", badgeType.name)
        })
        return Result.success(Unit)
    }

    fun isHotspotReadyForMessaging(): Boolean {
        val state = lanClient.connectionState.value
        return state.isConnected && state.isHotspotActive
    }

    suspend fun sendTextMessage(
        chatId: String,
        isGroup: Boolean,
        recipientId: String,
        text: String
    ): Result<MessageEntity> {
        val me = currentUser.value ?: return Result.failure(IllegalStateException("Not signed in"))
        if (me.isCurrentlyBanned()) return Result.failure(IllegalStateException("Account restricted"))
        if (!isHotspotReadyForMessaging()) {
            return Result.failure(IllegalStateException("Hotspot is OFF. Turn on AMMAR-HACKER-5G in HOTSPOT BAM to send messages."))
        }
        if (isGroup) {
            val grp = groupDao.getGroupById(recipientId)
            val mutedUntil = grp?.mutedUntil(me.userId) ?: 0L
            if (mutedUntil > System.currentTimeMillis()) {
                return Result.failure(IllegalStateException("You are muted from sending messages in this group."))
            }
        }
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Message cannot be empty"))

        val msg = MessageEntity(
            msgId = UUID.randomUUID().toString(),
            chatId = chatId,
            isGroup = isGroup,
            senderId = me.userId,
            senderName = me.displayName,
            senderUsername = me.username,
            recipientId = recipientId,
            type = MessageType.TEXT.name,
            text = trimmed,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENT.name,
            isReadByMe = true
        )
        messageDao.upsertMessage(msg)
        broadcastChatMessage(msg, mediaBase64 = null)
        sendPresence(chatId, isGroup, recipientId, PresenceState.IDLE)
        return Result.success(msg)
    }

    suspend fun sendVoiceMessage(
        chatId: String,
        isGroup: Boolean,
        recipientId: String,
        recorded: RecordedVoiceResult
    ): Result<MessageEntity> {
        val me = currentUser.value ?: return Result.failure(IllegalStateException("Not signed in"))
        if (me.isCurrentlyBanned()) return Result.failure(IllegalStateException("Account restricted"))
        if (!isHotspotReadyForMessaging()) {
            return Result.failure(IllegalStateException("Hotspot is OFF. Turn on AMMAR-HACKER-5G in HOTSPOT BAM."))
        }
        if (isGroup) {
            val grp = groupDao.getGroupById(recipientId)
            if ((grp?.mutedUntil(me.userId) ?: 0L) > System.currentTimeMillis()) {
                return Result.failure(IllegalStateException("You are muted from sending messages in this group."))
            }
        }

        val msg = MessageEntity(
            msgId = UUID.randomUUID().toString(),
            chatId = chatId,
            isGroup = isGroup,
            senderId = me.userId,
            senderName = me.displayName,
            senderUsername = me.username,
            recipientId = recipientId,
            type = MessageType.VOICE.name,
            text = "Voice message",
            audioPath = recorded.filePath,
            durationMs = recorded.durationMs,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENT.name,
            isReadByMe = true
        )
        messageDao.upsertMessage(msg)
        broadcastChatMessage(msg, mediaBase64 = recorded.base64)
        sendPresence(chatId, isGroup, recipientId, PresenceState.IDLE)
        return Result.success(msg)
    }

    suspend fun sendVideoEmojiMessage(
        chatId: String,
        isGroup: Boolean,
        recipientId: String,
        videoUri: Uri
    ): Result<MessageEntity> {
        val me = currentUser.value ?: return Result.failure(IllegalStateException("Not signed in"))
        if (!me.canUseVideoFeatures()) {
            return Result.failure(IllegalStateException("Video Emoji requires Owner or Green Tick privilege"))
        }
        if (me.isCurrentlyBanned()) return Result.failure(IllegalStateException("Account restricted"))
        if (!isHotspotReadyForMessaging()) {
            return Result.failure(IllegalStateException("Hotspot is OFF. Cannot send video emoji."))
        }
        if (isGroup) {
            val grp = groupDao.getGroupById(recipientId)
            if ((grp?.mutedUntil(me.userId) ?: 0L) > System.currentTimeMillis()) {
                return Result.failure(IllegalStateException("You are muted from sending messages in this group."))
            }
        }

        val processed = VideoUtils.trimAndSaveVideo(context, videoUri, "vemoji", 3000L)
            .getOrElse { return Result.failure(it) }

        val msg = MessageEntity(
            msgId = UUID.randomUUID().toString(),
            chatId = chatId,
            isGroup = isGroup,
            senderId = me.userId,
            senderName = me.displayName,
            senderUsername = me.username,
            recipientId = recipientId,
            type = MessageType.VIDEO_EMOJI.name,
            text = "Video Emoji (3s)",
            videoPath = processed.filePath,
            durationMs = processed.durationMs,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENT.name,
            isReadByMe = true
        )
        messageDao.upsertMessage(msg)
        broadcastChatMessage(msg, mediaBase64 = processed.base64)
        return Result.success(msg)
    }

    suspend fun sendGroupInvite(
        chatId: String,
        isGroup: Boolean,
        recipientId: String,
        group: GroupEntity
    ): Result<MessageEntity> {
        val me = currentUser.value ?: return Result.failure(IllegalStateException("Not signed in"))
        if (!isHotspotReadyForMessaging()) {
            return Result.failure(IllegalStateException("Hotspot is OFF. Cannot send group invite."))
        }
        val link = buildInviteLink(group)
        val snapshot = groupToJson(group).toString()
        val msg = MessageEntity(
            msgId = UUID.randomUUID().toString(),
            chatId = chatId,
            isGroup = isGroup,
            senderId = me.userId,
            senderName = me.displayName,
            senderUsername = me.username,
            recipientId = recipientId,
            type = MessageType.GROUP_INVITE.name,
            text = "Join ${group.name}: $link",
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENT.name,
            groupInviteId = group.groupId,
            groupInviteName = group.name,
            groupInviteToken = group.inviteToken,
            groupSnapshotJson = snapshot,
            isReadByMe = true
        )
        messageDao.upsertMessage(msg)
        broadcastChatMessage(msg, mediaBase64 = null)
        return Result.success(msg)
    }

    fun sendPresence(chatId: String, isGroup: Boolean, recipientId: String, state: PresenceState) {
        val me = currentUser.value ?: return
        if (!isHotspotReadyForMessaging()) return
        val pkt = LanProtocol.packet(LanProtocol.TYPE_PRESENCE) {
            put("chatId", chatId)
            put("isGroup", isGroup)
            put("recipientId", recipientId)
            put("fromUserId", me.userId)
            put("fromUsername", me.username)
            put("state", state.name)
        }
        lanClient.sendPacket(pkt)
    }

    suspend fun createGroup(name: String, avatarUri: Uri?, isPublic: Boolean): Result<GroupEntity> {
        val me = currentUser.value ?: return Result.failure(IllegalStateException("Not signed in"))
        val cleanName = name.trim()
        if (cleanName.isBlank()) return Result.failure(IllegalArgumentException("Group name required"))

        val groupId = "grp_${UUID.randomUUID().toString().take(8)}"
        val processedAvatar = avatarUri?.let {
            MediaUtils.savePickedImage(context, it, "grp_$groupId")
        }
        val group = GroupEntity(
            groupId = groupId,
            name = cleanName,
            avatarPath = processedAvatar?.filePath,
            avatarBase64 = processedAvatar?.base64,
            inviteToken = UUID.randomUUID().toString().take(10),
            creatorId = me.userId,
            adminIdsCsv = me.userId,
            memberIdsCsv = me.userId,
            isPublic = isPublic
        )
        groupDao.upsertGroup(group)
        broadcastGroupSync(group)

        val sysMsg = MessageEntity(
            msgId = UUID.randomUUID().toString(),
            chatId = groupChatId(group.groupId),
            isGroup = true,
            senderId = me.userId,
            senderName = me.displayName,
            senderUsername = me.username,
            recipientId = group.groupId,
            type = MessageType.SYSTEM.name,
            text = "@${me.username} created ${if (isPublic) "public" else "private"} group \"${group.name}\"",
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.READ.name,
            isReadByMe = true
        )
        messageDao.upsertMessage(sysMsg)
        return Result.success(group)
    }

    fun buildInviteLink(group: GroupEntity): String =
        "ammarchat://join/${group.groupId}/${group.inviteToken}"

    suspend fun joinGroupByInvite(
        groupId: String,
        token: String,
        snapshotJson: String? = null
    ): Result<GroupEntity> {
        val me = currentUser.value ?: return Result.failure(IllegalStateException("Not signed in"))

        // 1. Check local DB first; if missing, reconstruct from embedded invite snapshot if valid
        var group = groupDao.getGroupById(groupId)
        if (group == null && !snapshotJson.isNullOrBlank()) {
            val snapObj = runCatching { JSONObject(snapshotJson) }.getOrNull()
            if (snapObj != null && snapObj.optString("groupId") == groupId) {
                group = parseGroupJson(snapObj, existing = null)
                groupDao.upsertGroup(group)
            }
        }

        // 2. If still missing, ask LAN Relay Server and wait briefly for GROUP_SYNC
        if (group == null) {
            lanClient.sendPacket(LanProtocol.packet(LanProtocol.TYPE_GROUP_JOIN_REQUEST) {
                put("groupId", groupId)
                put("token", token)
                put("userId", me.userId)
            })
            repeat(10) {
                delay(200)
                val synced = groupDao.getGroupById(groupId)
                if (synced != null) {
                    group = synced
                    return@repeat
                }
            }
        }

        val resolved = group
            ?: return Result.failure(IllegalArgumentException("Group not found on LAN. Ensure hotspot host is active."))

        if (!resolved.isPublic && resolved.inviteToken != token) {
            return Result.failure(IllegalArgumentException("Invalid or expired invite link"))
        }

        val currentMembers = resolved.memberIds().toMutableSet()
        currentMembers.add(me.userId)
        val updated = resolved.copy(memberIdsCsv = currentMembers.joinToString(","))
        groupDao.upsertGroup(updated)
        broadcastGroupSync(updated)
        return Result.success(updated)
    }

    suspend fun joinPublicGroup(groupId: String): Result<GroupEntity> {
        val me = currentUser.value ?: return Result.failure(IllegalStateException("Not signed in"))
        val grp = groupDao.getGroupById(groupId)
            ?: return Result.failure(IllegalArgumentException("Group not found"))
        if (!grp.isPublic) {
            return Result.failure(IllegalStateException("This group is private. Join via invite link."))
        }
        val members = grp.memberIds().toMutableSet().apply { add(me.userId) }
        val updated = grp.copy(memberIdsCsv = members.joinToString(","))
        groupDao.upsertGroup(updated)
        broadcastGroupSync(updated)
        return Result.success(updated)
    }

    suspend fun toggleGroupPrivacy(groupId: String): Result<GroupEntity> {
        val me = currentUser.value ?: return Result.failure(IllegalStateException("Not signed in"))
        val group = groupDao.getGroupById(groupId) ?: return Result.failure(IllegalArgumentException("Group not found"))
        if (!canManageGroup(me, group)) {
            return Result.failure(IllegalStateException("Only group admins or owner can change privacy"))
        }
        val updated = group.copy(isPublic = !group.isPublic)
        groupDao.upsertGroup(updated)
        broadcastGroupSync(updated)
        return Result.success(updated)
    }

    suspend fun regenerateGroupInviteLink(groupId: String): Result<GroupEntity> {
        val me = currentUser.value ?: return Result.failure(IllegalStateException("Not signed in"))
        val group = groupDao.getGroupById(groupId) ?: return Result.failure(IllegalArgumentException("Group not found"))
        if (!canManageGroup(me, group)) {
            return Result.failure(IllegalStateException("Only group admins or owner can change invite link"))
        }
        val updated = group.copy(inviteToken = UUID.randomUUID().toString().take(10))
        groupDao.upsertGroup(updated)
        broadcastGroupSync(updated)
        return Result.success(updated)
    }

    suspend fun muteGroupMember(groupId: String, targetUserId: String, durationMinutes: Long): Result<Unit> {
        val me = currentUser.value ?: return Result.failure(IllegalStateException("Not signed in"))
        val group = groupDao.getGroupById(groupId) ?: return Result.failure(IllegalArgumentException("Group not found"))
        if (!canManageGroup(me, group)) {
            return Result.failure(IllegalStateException("Only admins or owner can mute members"))
        }
        if (targetUserId == group.creatorId && !me.isOwner) {
            return Result.failure(IllegalArgumentException("Cannot mute the group owner"))
        }
        val map = group.mutedMap().toMutableMap()
        if (durationMinutes <= 0L) {
            map.remove(targetUserId)
        } else {
            map[targetUserId] = System.currentTimeMillis() + durationMinutes * 60_000L
        }
        val csv = map.entries.joinToString(",") { "${it.key}:${it.value}" }
        val updated = group.copy(mutedUsersCsv = csv)
        groupDao.upsertGroup(updated)
        broadcastGroupSync(updated)
        return Result.success(Unit)
    }

    suspend fun changeGroupOwner(groupId: String, newOwnerUserId: String): Result<Unit> {
        val me = currentUser.value ?: return Result.failure(IllegalStateException("Not signed in"))
        val group = groupDao.getGroupById(groupId) ?: return Result.failure(IllegalArgumentException("Group not found"))
        if (!me.isOwner && me.userId != group.creatorId) {
            return Result.failure(IllegalStateException("Only App Owner or current Group Owner can transfer ownership"))
        }
        val members = group.memberIds().toMutableSet().apply { add(newOwnerUserId) }
        val admins = group.adminIds().toMutableSet().apply { add(newOwnerUserId) }
        val updated = group.copy(
            creatorId = newOwnerUserId,
            adminIdsCsv = admins.joinToString(","),
            memberIdsCsv = members.joinToString(",")
        )
        groupDao.upsertGroup(updated)
        broadcastGroupSync(updated)
        return Result.success(Unit)
    }

    suspend fun updateGroupDp(groupId: String, avatarUri: Uri): Result<Unit> {
        val me = currentUser.value ?: return Result.failure(IllegalStateException("Not signed in"))
        val group = groupDao.getGroupById(groupId) ?: return Result.failure(IllegalArgumentException("Group not found"))
        if (!canManageGroup(me, group)) {
            return Result.failure(IllegalStateException("Only group admins can change the group photo"))
        }
        val processed = MediaUtils.savePickedImage(context, avatarUri, "grp_$groupId")
            ?: return Result.failure(IllegalStateException("Could not process image"))
        val updated = group.copy(
            avatarPath = processed.filePath,
            avatarBase64 = processed.base64
        )
        groupDao.upsertGroup(updated)
        broadcastGroupSync(updated)
        return Result.success(Unit)
    }

    suspend fun kickMemberFromGroup(groupId: String, targetUserId: String): Result<Unit> {
        val me = currentUser.value ?: return Result.failure(IllegalStateException("Not signed in"))
        val group = groupDao.getGroupById(groupId) ?: return Result.failure(IllegalArgumentException("Group not found"))
        if (!canManageGroup(me, group)) {
            return Result.failure(IllegalStateException("Only group admins can kick members"))
        }
        if (targetUserId == me.userId) {
            return Result.failure(IllegalArgumentException("Cannot kick yourself"))
        }
        if (targetUserId == group.creatorId && !me.isOwner) {
            return Result.failure(IllegalArgumentException("Cannot kick the group owner"))
        }

        val updatedMembers = group.memberIds().filterNot { it == targetUserId }
        val updatedAdmins = group.adminIds().filterNot { it == targetUserId }
        val updated = group.copy(
            memberIdsCsv = updatedMembers.joinToString(","),
            adminIdsCsv = updatedAdmins.joinToString(",")
        )
        groupDao.upsertGroup(updated)

        val pkt = LanProtocol.packet(LanProtocol.TYPE_GROUP_KICK) {
            put("groupId", groupId)
            put("targetUserId", targetUserId)
            put("kickedByUserId", me.userId)
        }
        lanClient.sendPacket(pkt)
        broadcastGroupSync(updated)

        val targetUser = userDao.getUserById(targetUserId)
        val sysMsg = MessageEntity(
            msgId = UUID.randomUUID().toString(),
            chatId = groupChatId(groupId),
            isGroup = true,
            senderId = me.userId,
            senderName = me.displayName,
            senderUsername = me.username,
            recipientId = groupId,
            type = MessageType.SYSTEM.name,
            text = "@${targetUser?.username ?: targetUserId} was kicked",
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.READ.name,
            isReadByMe = true
        )
        messageDao.upsertMessage(sysMsg)
        return Result.success(Unit)
    }

    suspend fun toggleGroupAdmin(groupId: String, targetUserId: String): Result<Unit> {
        val me = currentUser.value ?: return Result.failure(IllegalStateException("Not signed in"))
        val group = groupDao.getGroupById(groupId) ?: return Result.failure(IllegalArgumentException("Group not found"))
        if (!me.isOwner && me.userId != group.creatorId && me.userId !in group.adminIds()) {
            return Result.failure(IllegalStateException("Only group owner/admins can change roles"))
        }
        val admins = group.adminIds().toMutableSet()
        if (targetUserId in admins && (targetUserId != group.creatorId || me.isOwner)) {
            admins.remove(targetUserId)
        } else {
            admins.add(targetUserId)
        }
        val updated = group.copy(adminIdsCsv = admins.joinToString(","))
        groupDao.upsertGroup(updated)
        broadcastGroupSync(updated)
        return Result.success(Unit)
    }

    fun canManageGroup(user: UserEntity, group: GroupEntity): Boolean =
        user.isOwner || user.userId in group.adminIds() || user.userId == group.creatorId

    suspend fun banUserWithDuration(targetUserId: String, durationMs: Long): Result<Unit> {
        val me = currentUser.value ?: return Result.failure(IllegalStateException("Not signed in"))
        if (!me.isOwner) return Result.failure(IllegalStateException("Unauthorized"))
        if (targetUserId == me.userId) return Result.failure(IllegalArgumentException("Cannot ban this account"))

        val banned = durationMs != 0L
        val expiresAt = when {
            !banned -> 0L
            durationMs < 0L -> -1L // permanent
            else -> System.currentTimeMillis() + durationMs
        }

        userDao.setUserBan(targetUserId, banned, expiresAt)
        val pkt = LanProtocol.packet(LanProtocol.TYPE_MOD_BAN) {
            put("targetUserId", targetUserId)
            put("isBanned", banned)
            put("banExpiresAt", expiresAt)
            put("issuedByOwnerId", me.userId)
        }
        lanClient.sendPacket(pkt)
        return Result.success(Unit)
    }

    suspend fun setThemeByOwner(theme: ChatBackgroundTheme): Result<Unit> {
        val me = currentUser.value ?: return Result.failure(IllegalStateException("Not signed in"))
        if (!me.isOwner) return Result.failure(IllegalStateException("Unauthorized"))
        prefs.setActiveThemeId(theme.id)
        val pkt = LanProtocol.packet(LanProtocol.TYPE_THEME_SYNC) {
            put("themeId", theme.id)
            put("customBgBase64", "")
            put("issuedByOwnerId", me.userId)
        }
        lanClient.sendPacket(pkt)
        return Result.success(Unit)
    }

    suspend fun setCustomGalleryThemeByOwner(imageUri: Uri): Result<Unit> {
        val me = currentUser.value ?: return Result.failure(IllegalStateException("Not signed in"))
        if (!me.isOwner) return Result.failure(IllegalStateException("Unauthorized"))
        val processed = MediaUtils.savePickedImage(context, imageUri, "theme_bg")
            ?: return Result.failure(IllegalStateException("Could not load image from Gallery"))
        prefs.setActiveThemeId(ChatBackgroundTheme.CUSTOM_GALLERY.id, processed.filePath)
        val pkt = LanProtocol.packet(LanProtocol.TYPE_THEME_SYNC) {
            put("themeId", ChatBackgroundTheme.CUSTOM_GALLERY.id)
            put("customBgBase64", processed.base64)
            put("issuedByOwnerId", me.userId)
        }
        lanClient.sendPacket(pkt)
        return Result.success(Unit)
    }

    suspend fun markChatAsRead(chatId: String) {
        val me = currentUser.value ?: return
        val unread = messageDao.getUnreadIncomingMessages(chatId, me.userId)
        unread.forEach { msg ->
            messageDao.markReadByMe(msg.msgId)
            sendAck(msg.msgId, msg.chatId, MessageStatus.READ, me.userId)
        }
    }

    private fun sendAck(msgId: String, chatId: String, status: MessageStatus, fromUserId: String) {
        val pkt = LanProtocol.packet(LanProtocol.TYPE_MSG_ACK) {
            put("msgId", msgId)
            put("chatId", chatId)
            put("ackType", status.name)
            put("fromUserId", fromUserId)
        }
        lanClient.sendPacket(pkt)
    }

    private fun broadcastChatMessage(msg: MessageEntity, mediaBase64: String?) {
        val pkt = LanProtocol.packet(LanProtocol.TYPE_CHAT_MSG) {
            put("msgId", msg.msgId)
            put("chatId", msg.chatId)
            put("isGroup", msg.isGroup)
            put("senderId", msg.senderId)
            put("senderName", msg.senderName)
            put("senderUsername", msg.senderUsername)
            put("recipientId", msg.recipientId)
            put("msgType", msg.type)
            put("text", msg.text)
            put("audioBase64", if (msg.type == MessageType.VOICE.name) mediaBase64 ?: "" else "")
            put("videoBase64", if (msg.type == MessageType.VIDEO_EMOJI.name) mediaBase64 ?: "" else "")
            put("durationMs", msg.durationMs)
            put("timestamp", msg.timestamp)
            put("groupInviteId", msg.groupInviteId ?: "")
            put("groupInviteName", msg.groupInviteName ?: "")
            put("groupInviteToken", msg.groupInviteToken ?: "")
            put("groupSnapshotJson", msg.groupSnapshotJson ?: "")
        }
        lanClient.sendPacket(pkt)
    }

    private fun groupToJson(group: GroupEntity): JSONObject = JSONObject().apply {
        put("groupId", group.groupId)
        put("name", group.name)
        put("avatarBase64", group.avatarBase64 ?: "")
        put("inviteToken", group.inviteToken)
        put("creatorId", group.creatorId)
        put("adminIds", LanProtocol.stringListToJsonArray(group.adminIds()))
        put("memberIds", LanProtocol.stringListToJsonArray(group.memberIds()))
        put("isPublic", group.isPublic)
        put("mutedUsersCsv", group.mutedUsersCsv)
        put("createdAt", group.createdAt)
    }

    private suspend fun parseGroupJson(gObj: JSONObject, existing: GroupEntity?): GroupEntity {
        val gid = gObj.optString("groupId")
        val b64 = gObj.optString("avatarBase64", "")
        val avatarPath = if (b64.isNotBlank() && b64 != existing?.avatarBase64) {
            MediaUtils.saveBase64Avatar(context, b64, "grp_$gid")
        } else {
            existing?.avatarPath
        }
        return GroupEntity(
            groupId = gid,
            name = gObj.optString("name", existing?.name ?: "Group"),
            avatarPath = avatarPath,
            avatarBase64 = b64.ifBlank { existing?.avatarBase64 },
            inviteToken = gObj.optString("inviteToken", existing?.inviteToken ?: ""),
            creatorId = gObj.optString("creatorId", existing?.creatorId ?: ""),
            adminIdsCsv = LanProtocol.jsonArrayToStringList(gObj.optJSONArray("adminIds")).joinToString(","),
            memberIdsCsv = LanProtocol.jsonArrayToStringList(gObj.optJSONArray("memberIds")).joinToString(","),
            isPublic = gObj.optBoolean("isPublic", existing?.isPublic ?: true),
            mutedUsersCsv = gObj.optString("mutedUsersCsv", existing?.mutedUsersCsv ?: ""),
            createdAt = gObj.optLong("createdAt", existing?.createdAt ?: System.currentTimeMillis())
        )
    }

    private fun broadcastGroupSync(group: GroupEntity) {
        val pkt = LanProtocol.packet(LanProtocol.TYPE_GROUP_SYNC) {
            put("group", groupToJson(group))
        }
        lanClient.sendPacket(pkt)
    }

    private suspend fun handleIncomingPacket(pkt: JSONObject) {
        val me = currentUser.value
        when (pkt.optString("type")) {
            LanProtocol.TYPE_DIRECTORY -> {
                val usersArr = pkt.optJSONArray("users")
                if (usersArr != null) {
                    for (i in 0 until usersArr.length()) {
                        val u = usersArr.optJSONObject(i) ?: continue
                        val uid = u.optString("userId")
                        if (uid.isBlank()) continue
                        val existing = userDao.getUserById(uid)

                        val b64 = u.optString("avatarBase64", "")
                        val avatarPath = if (b64.isNotBlank() && b64 != existing?.avatarBase64) {
                            MediaUtils.saveBase64Avatar(context, b64, "peer_$uid")
                        } else {
                            existing?.avatarPath
                        }

                        val vAvatarB64 = u.optString("videoAvatarBase64", "")
                        val vAvatarPath = if (vAvatarB64.isNotBlank() && vAvatarB64 != existing?.videoAvatarBase64) {
                            VideoUtils.saveBase64Video(context, vAvatarB64, "vavatar_${uid}_${vAvatarB64.hashCode()}")
                        } else {
                            existing?.videoAvatarPath
                        }

                        val stickerB64 = u.optString("stickerVideoBase64", "")
                        val stickerPath = if (stickerB64.isNotBlank() && stickerB64 != existing?.stickerVideoBase64) {
                            VideoUtils.saveBase64Video(context, stickerB64, "sticker_${uid}_${stickerB64.hashCode()}")
                        } else {
                            existing?.stickerVideoPath
                        }

                        val incomingBadge = u.optString("badgeType", existing?.badgeType ?: TickBadgeType.NONE.name)
                        val merged = UserEntity(
                            userId = uid,
                            email = existing?.email ?: "",
                            displayName = u.optString("displayName", existing?.displayName ?: uid),
                            username = u.optString("username", existing?.username ?: uid),
                            avatarPath = avatarPath,
                            avatarBase64 = b64.ifBlank { existing?.avatarBase64 },
                            videoAvatarPath = vAvatarPath,
                            videoAvatarBase64 = vAvatarB64.ifBlank { existing?.videoAvatarBase64 },
                            isOwner = existing?.isOwner ?: false,
                            hasBlueTick = u.optBoolean("hasBlueTick", existing?.hasBlueTick ?: false),
                            badgeType = incomingBadge,
                            stickerVideoPath = stickerPath,
                            stickerVideoBase64 = stickerB64.ifBlank { existing?.stickerVideoBase64 },
                            isBanned = u.optBoolean("isBanned", existing?.isBanned ?: false),
                            banExpiresAt = u.optLong("banExpiresAt", existing?.banExpiresAt ?: 0L),
                            isOnline = u.optBoolean("isOnline", false),
                            lastSeen = System.currentTimeMillis()
                        )
                        userDao.upsertUser(merged)
                    }
                }
                val themeId = pkt.optString("themeId")
                val customBgB64 = pkt.optString("customBgBase64", "")
                if (themeId.isNotBlank()) {
                    val savedPath = if (themeId == ChatBackgroundTheme.CUSTOM_GALLERY.id && customBgB64.isNotBlank()) {
                        MediaUtils.saveBase64Avatar(context, customBgB64, "theme_bg_${customBgB64.hashCode()}")
                    } else null
                    prefs.setActiveThemeId(themeId, savedPath)
                }
            }

            LanProtocol.TYPE_THEME_SYNC -> {
                val themeId = pkt.optString("themeId")
                val customBgB64 = pkt.optString("customBgBase64", "")
                if (themeId.isNotBlank()) {
                    val savedPath = if (themeId == ChatBackgroundTheme.CUSTOM_GALLERY.id && customBgB64.isNotBlank()) {
                        MediaUtils.saveBase64Avatar(context, customBgB64, "theme_bg_${customBgB64.hashCode()}")
                    } else null
                    prefs.setActiveThemeId(themeId, savedPath)
                }
            }

            LanProtocol.TYPE_MOD_BAN -> {
                val targetId = pkt.optString("targetUserId")
                val banned = pkt.optBoolean("isBanned", false)
                val expiresAt = pkt.optLong("banExpiresAt", if (banned) -1L else 0L)
                if (targetId.isNotBlank()) {
                    userDao.setUserBan(targetId, banned, expiresAt)
                }
            }

            LanProtocol.TYPE_BADGE_SYNC -> {
                val targetId = pkt.optString("targetUserId")
                val badgeType = pkt.optString("badgeType", TickBadgeType.NONE.name)
                val stickerB64 = pkt.optString("stickerVideoBase64", "")
                if (targetId.isNotBlank()) {
                    val existing = userDao.getUserById(targetId)
                    val stickerPath = if (stickerB64.isNotBlank()) {
                        VideoUtils.saveBase64Video(context, stickerB64, "sticker_${targetId}_${stickerB64.hashCode()}")
                    } else existing?.stickerVideoPath
                    if (existing != null) {
                        userDao.upsertUser(
                            existing.copy(
                                badgeType = badgeType,
                                hasBlueTick = badgeType == TickBadgeType.BLUE.name,
                                stickerVideoPath = stickerPath,
                                stickerVideoBase64 = stickerB64.ifBlank { existing.stickerVideoBase64 }
                            )
                        )
                    } else {
                        userDao.setUserBadge(targetId, badgeType, badgeType == TickBadgeType.BLUE.name)
                    }
                }
            }

            LanProtocol.TYPE_GROUP_SYNC -> {
                val gObj = pkt.optJSONObject("group") ?: return
                val gid = gObj.optString("groupId")
                if (gid.isBlank()) return
                val existing = groupDao.getGroupById(gid)
                val group = parseGroupJson(gObj, existing)
                groupDao.upsertGroup(group)
            }

            LanProtocol.TYPE_GROUP_KICK -> {
                val gid = pkt.optString("groupId")
                val targetId = pkt.optString("targetUserId")
                val group = groupDao.getGroupById(gid) ?: return
                val updatedMembers = group.memberIds().filterNot { it == targetId }
                val updatedAdmins = group.adminIds().filterNot { it == targetId }
                groupDao.upsertGroup(
                    group.copy(
                        memberIdsCsv = updatedMembers.joinToString(","),
                        adminIdsCsv = updatedAdmins.joinToString(",")
                    )
                )
            }

            LanProtocol.TYPE_PRESENCE -> {
                val chatId = pkt.optString("chatId")
                val isGroup = pkt.optBoolean("isGroup", false)
                val recipientId = pkt.optString("recipientId")
                val fromId = pkt.optString("fromUserId")
                val fromUsername = pkt.optString("fromUsername")
                if (me != null && fromId == me.userId) return
                if (!isGroup && me != null && recipientId != me.userId) return

                val state = runCatching {
                    PresenceState.valueOf(pkt.optString("state", PresenceState.IDLE.name))
                }.getOrDefault(PresenceState.IDLE)

                updatePresence(chatId, fromId, fromUsername, state)
            }

            LanProtocol.TYPE_CHAT_MSG -> {
                val senderId = pkt.optString("senderId")
                if (me != null && senderId == me.userId) return
                val isGroup = pkt.optBoolean("isGroup", false)
                val recipientId = pkt.optString("recipientId")
                if (!isGroup && me != null && recipientId != me.userId) return
                if (isGroup && me != null) {
                    val grp = groupDao.getGroupById(recipientId)
                    if (grp != null && me.userId !in grp.memberIds()) return
                }

                val msgId = pkt.optString("msgId")
                val chatId = pkt.optString("chatId")
                val msgType = pkt.optString("msgType", MessageType.TEXT.name)
                val audioB64 = pkt.optString("audioBase64", "")
                val videoB64 = pkt.optString("videoBase64", "")

                val audioPath = if (msgType == MessageType.VOICE.name && audioB64.isNotBlank()) {
                    voiceManager.saveIncomingVoiceBase64(msgId, audioB64)
                } else null

                val videoPath = if (msgType == MessageType.VIDEO_EMOJI.name && videoB64.isNotBlank()) {
                    VideoUtils.saveBase64Video(context, videoB64, "vemoji_$msgId")
                } else null

                // Also sync embedded group snapshot if invite message arrives before GROUP_SYNC
                val snapStr = pkt.optString("groupSnapshotJson", "")
                if (msgType == MessageType.GROUP_INVITE.name && snapStr.isNotBlank()) {
                    runCatching {
                        val snapObj = JSONObject(snapStr)
                        val gid = snapObj.optString("groupId")
                        if (gid.isNotBlank() && groupDao.getGroupById(gid) == null) {
                            groupDao.upsertGroup(parseGroupJson(snapObj, null))
                        }
                    }
                }

                val isCurrentlyViewing = openChatId == chatId
                val entity = MessageEntity(
                    msgId = msgId,
                    chatId = chatId,
                    isGroup = isGroup,
                    senderId = senderId,
                    senderName = pkt.optString("senderName", senderId),
                    senderUsername = pkt.optString("senderUsername", senderId),
                    recipientId = recipientId,
                    type = msgType,
                    text = pkt.optString("text", ""),
                    audioPath = audioPath,
                    videoPath = videoPath,
                    durationMs = pkt.optLong("durationMs", 0L),
                    timestamp = pkt.optLong("timestamp", System.currentTimeMillis()),
                    status = if (isCurrentlyViewing) MessageStatus.READ.name else MessageStatus.DELIVERED.name,
                    groupInviteId = pkt.optString("groupInviteId").ifBlank { null },
                    groupInviteName = pkt.optString("groupInviteName").ifBlank { null },
                    groupInviteToken = pkt.optString("groupInviteToken").ifBlank { null },
                    groupSnapshotJson = snapStr.ifBlank { null },
                    isReadByMe = isCurrentlyViewing
                )
                messageDao.upsertMessage(entity)
                updatePresence(chatId, senderId, entity.senderUsername, PresenceState.IDLE)

                if (me != null) {
                    sendAck(msgId, chatId, MessageStatus.DELIVERED, me.userId)
                    if (isCurrentlyViewing) {
                        sendAck(msgId, chatId, MessageStatus.READ, me.userId)
                    }
                }
            }

            LanProtocol.TYPE_MSG_ACK -> {
                val msgId = pkt.optString("msgId")
                val ackType = pkt.optString("ackType")
                val existing = messageDao.getMessageById(msgId) ?: return
                if (ackType == MessageStatus.READ.name) {
                    messageDao.updateMessageStatus(msgId, MessageStatus.READ.name)
                } else if (ackType == MessageStatus.DELIVERED.name && existing.status != MessageStatus.READ.name) {
                    messageDao.updateMessageStatus(msgId, MessageStatus.DELIVERED.name)
                }
            }
        }
    }

    private fun updatePresence(chatId: String, fromUserId: String, fromUsername: String, state: PresenceState) {
        presenceExpiryJobs[chatId]?.cancel()
        if (state == PresenceState.IDLE) {
            _presenceMap.value = _presenceMap.value - chatId
            return
        }
        _presenceMap.value = _presenceMap.value + (chatId to PresenceIndicator(chatId, fromUserId, fromUsername, state))
        presenceExpiryJobs[chatId] = scope.launch {
            delay(4000)
            _presenceMap.value = _presenceMap.value - chatId
        }
    }

    private fun toPublicUserJson(user: UserEntity): JSONObject =
        LanProtocol.userToPublicJson(
            userId = user.userId,
            displayName = user.displayName,
            username = user.username,
            avatarBase64 = user.avatarBase64,
            videoAvatarBase64 = user.videoAvatarBase64,
            hasBlueTick = user.hasBlueTick,
            badgeType = user.badgeType,
            stickerVideoBase64 = user.stickerVideoBase64,
            isBanned = user.isBanned,
            banExpiresAt = user.banExpiresAt,
            isOnline = true
        )

    companion object {
        fun dmChatId(userA: String, userB: String): String {
            val sorted = listOf(userA, userB).sorted()
            return "dm_${sorted[0]}__${sorted[1]}"
        }

        fun groupChatId(groupId: String): String = "grp_$groupId"
    }
}
