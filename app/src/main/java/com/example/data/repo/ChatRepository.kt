package com.example.data.repo

import android.content.Context
import android.net.Uri
import com.example.audio.MediaUtils
import com.example.audio.RecordedVoiceResult
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
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
        .combine(flowOf(Unit)) { id, _ -> ChatBackgroundTheme.fromId(id) }
        .stateIn(scope, SharingStarted.Eagerly, ChatBackgroundTheme.OBSIDIAN_CYBER)

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
                handleIncomingPacket(packet)
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

        val resolvedName = if (owner && cleanName.equals(OwnerCredentials.DISPLAY_NAME, ignoreCase = true)) {
            OwnerCredentials.DISPLAY_NAME
        } else if (owner) {
            OwnerCredentials.DISPLAY_NAME
        } else {
            cleanName
        }

        val user = UserEntity(
            userId = userId,
            email = cleanEmail,
            displayName = resolvedName,
            username = cleanUsername,
            avatarPath = processedAvatar?.filePath ?: existing?.avatarPath,
            avatarBase64 = processedAvatar?.base64 ?: existing?.avatarBase64,
            isOwner = owner,
            hasBlueTick = owner,
            isBanned = existing?.isBanned ?: false,
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
            val ownerUser = UserEntity(
                userId = userId,
                email = OwnerCredentials.EMAIL,
                displayName = OwnerCredentials.DISPLAY_NAME,
                username = OwnerCredentials.USERNAME,
                avatarPath = existing?.avatarPath,
                avatarBase64 = existing?.avatarBase64,
                isOwner = true,
                hasBlueTick = true,
                isBanned = false,
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
        if (me.isBanned) return Result.failure(IllegalStateException("Account restricted"))
        if (!isHotspotReadyForMessaging()) {
            return Result.failure(IllegalStateException("Hotspot is OFF. Turn on AMMAR-HACKER-5G in Ammar 5G Node to send messages."))
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
        broadcastChatMessage(msg, audioBase64 = null)
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
        if (me.isBanned) return Result.failure(IllegalStateException("Account restricted"))
        if (!isHotspotReadyForMessaging()) {
            return Result.failure(IllegalStateException("Hotspot is OFF. Turn on AMMAR-HACKER-5G in Ammar 5G Node to send voice messages."))
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
        broadcastChatMessage(msg, audioBase64 = recorded.base64)
        sendPresence(chatId, isGroup, recipientId, PresenceState.IDLE)
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
            isReadByMe = true
        )
        messageDao.upsertMessage(msg)
        broadcastChatMessage(msg, audioBase64 = null)
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

    suspend fun createGroup(name: String, avatarUri: Uri?): Result<GroupEntity> {
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
            memberIdsCsv = me.userId
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
            text = "@${me.username} created group \"${group.name}\"",
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.READ.name,
            isReadByMe = true
        )
        messageDao.upsertMessage(sysMsg)
        return Result.success(group)
    }

    fun buildInviteLink(group: GroupEntity): String =
        "ammarchat://join/${group.groupId}/${group.inviteToken}"

    suspend fun joinGroupByInvite(groupId: String, token: String): Result<GroupEntity> {
        val me = currentUser.value ?: return Result.failure(IllegalStateException("Not signed in"))
        val group = groupDao.getGroupById(groupId)
            ?: return Result.failure(IllegalArgumentException("Group not found on LAN yet"))
        if (group.inviteToken != token) {
            return Result.failure(IllegalArgumentException("Invalid or expired invite link"))
        }
        val currentMembers = group.memberIds().toMutableSet()
        currentMembers.add(me.userId)
        val updated = group.copy(memberIdsCsv = currentMembers.joinToString(","))
        groupDao.upsertGroup(updated)
        broadcastGroupSync(updated)
        return Result.success(updated)
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
        if (targetUserId == me.userId || targetUserId == group.creatorId) {
            return Result.failure(IllegalArgumentException("Cannot kick the group creator"))
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
            text = "@${targetUser?.username ?: targetUserId} was removed by admin",
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
        if (!canManageGroup(me, group)) {
            return Result.failure(IllegalStateException("Only admins can change roles"))
        }
        val admins = group.adminIds().toMutableSet()
        if (targetUserId in admins && targetUserId != group.creatorId) {
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

    suspend fun setUserBannedByOwner(targetUserId: String, banned: Boolean): Result<Unit> {
        val me = currentUser.value ?: return Result.failure(IllegalStateException("Not signed in"))
        if (!me.isOwner) return Result.failure(IllegalStateException("Unauthorized"))
        if (targetUserId == me.userId) return Result.failure(IllegalArgumentException("Cannot ban this account"))

        userDao.setUserBanned(targetUserId, banned)
        val pkt = LanProtocol.packet(LanProtocol.TYPE_MOD_BAN) {
            put("targetUserId", targetUserId)
            put("isBanned", banned)
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

    private fun broadcastChatMessage(msg: MessageEntity, audioBase64: String?) {
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
            put("audioBase64", audioBase64 ?: "")
            put("durationMs", msg.durationMs)
            put("timestamp", msg.timestamp)
            put("groupInviteId", msg.groupInviteId ?: "")
            put("groupInviteName", msg.groupInviteName ?: "")
            put("groupInviteToken", msg.groupInviteToken ?: "")
        }
        lanClient.sendPacket(pkt)
    }

    private fun broadcastGroupSync(group: GroupEntity) {
        val pkt = LanProtocol.packet(LanProtocol.TYPE_GROUP_SYNC) {
            put("group", JSONObject().apply {
                put("groupId", group.groupId)
                put("name", group.name)
                put("avatarBase64", group.avatarBase64 ?: "")
                put("inviteToken", group.inviteToken)
                put("creatorId", group.creatorId)
                put("adminIds", LanProtocol.stringListToJsonArray(group.adminIds()))
                put("memberIds", LanProtocol.stringListToJsonArray(group.memberIds()))
                put("createdAt", group.createdAt)
            })
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
                        val merged = UserEntity(
                            userId = uid,
                            email = existing?.email ?: "",
                            displayName = u.optString("displayName", existing?.displayName ?: uid),
                            username = u.optString("username", existing?.username ?: uid),
                            avatarPath = avatarPath,
                            avatarBase64 = b64.ifBlank { existing?.avatarBase64 },
                            isOwner = existing?.isOwner ?: false,
                            hasBlueTick = u.optBoolean("hasBlueTick", existing?.hasBlueTick ?: false),
                            isBanned = u.optBoolean("isBanned", existing?.isBanned ?: false),
                            isOnline = u.optBoolean("isOnline", false),
                            lastSeen = System.currentTimeMillis()
                        )
                        userDao.upsertUser(merged)
                    }
                }
                val themeId = pkt.optString("themeId")
                if (themeId.isNotBlank()) {
                    prefs.setActiveThemeId(themeId)
                }
            }

            LanProtocol.TYPE_THEME_SYNC -> {
                val themeId = pkt.optString("themeId")
                if (themeId.isNotBlank()) {
                    prefs.setActiveThemeId(themeId)
                }
            }

            LanProtocol.TYPE_MOD_BAN -> {
                val targetId = pkt.optString("targetUserId")
                val banned = pkt.optBoolean("isBanned", false)
                if (targetId.isNotBlank()) {
                    userDao.setUserBanned(targetId, banned)
                }
            }

            LanProtocol.TYPE_GROUP_SYNC -> {
                val gObj = pkt.optJSONObject("group") ?: return
                val gid = gObj.optString("groupId")
                if (gid.isBlank()) return
                val existing = groupDao.getGroupById(gid)
                val b64 = gObj.optString("avatarBase64", "")
                val avatarPath = if (b64.isNotBlank() && b64 != existing?.avatarBase64) {
                    MediaUtils.saveBase64Avatar(context, b64, "grp_$gid")
                } else {
                    existing?.avatarPath
                }
                val group = GroupEntity(
                    groupId = gid,
                    name = gObj.optString("name", "Group"),
                    avatarPath = avatarPath,
                    avatarBase64 = b64.ifBlank { existing?.avatarBase64 },
                    inviteToken = gObj.optString("inviteToken", ""),
                    creatorId = gObj.optString("creatorId", ""),
                    adminIdsCsv = LanProtocol.jsonArrayToStringList(gObj.optJSONArray("adminIds")).joinToString(","),
                    memberIdsCsv = LanProtocol.jsonArrayToStringList(gObj.optJSONArray("memberIds")).joinToString(","),
                    createdAt = gObj.optLong("createdAt", System.currentTimeMillis())
                )
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
                // For DM, match whether message is intended for me
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
                val audioPath = if (msgType == MessageType.VOICE.name && audioB64.isNotBlank()) {
                    voiceManager.saveIncomingVoiceBase64(msgId, audioB64)
                } else null

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
                    durationMs = pkt.optLong("durationMs", 0L),
                    timestamp = pkt.optLong("timestamp", System.currentTimeMillis()),
                    status = if (isCurrentlyViewing) MessageStatus.READ.name else MessageStatus.DELIVERED.name,
                    groupInviteId = pkt.optString("groupInviteId").ifBlank { null },
                    groupInviteName = pkt.optString("groupInviteName").ifBlank { null },
                    groupInviteToken = pkt.optString("groupInviteToken").ifBlank { null },
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
            hasBlueTick = user.hasBlueTick,
            isBanned = user.isBanned,
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
