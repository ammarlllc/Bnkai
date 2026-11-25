package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AmmarApplication
import com.example.HotspotNodeActivity
import com.example.audio.VoicePlaybackState
import com.example.audio.VoiceRecordingState
import com.example.data.local.GroupEntity
import com.example.data.local.MessageEntity
import com.example.data.local.MessageType
import com.example.data.local.PresenceState
import com.example.data.local.TickBadgeType
import com.example.data.local.UserEntity
import com.example.data.repo.ChatRepository
import com.example.data.repo.PresenceIndicator
import com.example.network.LanConnectionState
import com.example.ui.theme.ChatBackgroundTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatThreadSummary(
    val chatId: String,
    val isGroup: Boolean,
    val targetId: String,
    val title: String,
    val subtitleUsername: String,
    val avatarPath: String?,
    val videoAvatarPath: String?,
    val badgeType: TickBadgeType,
    val stickerVideoPath: String?,
    val isOnline: Boolean,
    val lastMessage: MessageEntity?,
    val unreadCount: Int,
    val presence: PresenceIndicator?
)

class AmmarChatViewModel(application: Application) : AndroidViewModel(application) {
    private val repo: ChatRepository = (application as AmmarApplication).chatRepository

    val currentUser: StateFlow<UserEntity?> = repo.currentUser
    val connectionState: StateFlow<LanConnectionState> = repo.connectionState
    val activeTheme: StateFlow<ChatBackgroundTheme> = repo.activeTheme
    val customThemeImagePath: StateFlow<String?> = repo.customThemeImagePath
    val allUsers: StateFlow<List<UserEntity>> = repo.allUsers
    val allGroups: StateFlow<List<GroupEntity>> = repo.allGroups
    val presenceMap: StateFlow<Map<String, PresenceIndicator>> = repo.presenceMap
    val recordingState: StateFlow<VoiceRecordingState> = repo.recordingState
    val playbackState: StateFlow<VoicePlaybackState> = repo.playbackState

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private var typingDebounceJob: Job? = null

    val chatThreads: StateFlow<List<ChatThreadSummary>> = combine(
        currentUser,
        allUsers,
        allGroups,
        repo.allMessages,
        presenceMap
    ) { me, users, groups, messages, presences ->
        if (me == null) return@combine emptyList()

        val list = mutableListOf<ChatThreadSummary>()

        // Groups user belongs to (or App Owner can inspect any group they are in)
        groups.filter { me.userId in it.memberIds() }.forEach { grp ->
            val cid = ChatRepository.groupChatId(grp.groupId)
            val chatMsgs = messages.filter { it.chatId == cid }
            val unread = chatMsgs.count { it.senderId != me.userId && !it.isReadByMe }
            list.add(
                ChatThreadSummary(
                    chatId = cid,
                    isGroup = true,
                    targetId = grp.groupId,
                    title = grp.name,
                    subtitleUsername = "${grp.memberIds().size} members • ${if (grp.isPublic) "Public" else "Private"}",
                    avatarPath = grp.avatarPath,
                    videoAvatarPath = null,
                    badgeType = TickBadgeType.NONE,
                    stickerVideoPath = null,
                    isOnline = true,
                    lastMessage = chatMsgs.maxByOrNull { it.timestamp },
                    unreadCount = unread,
                    presence = presences[cid]
                )
            )
        }

        val peerIdsWithMsgs = messages
            .filter { !it.isGroup }
            .mapNotNull { msg ->
                when {
                    msg.senderId == me.userId -> msg.recipientId
                    msg.recipientId == me.userId -> msg.senderId
                    else -> null
                }
            }
            .toSet()

        users.filter { it.userId != me.userId && (it.userId in peerIdsWithMsgs || it.isOnline) }
            .forEach { peer ->
                val cid = ChatRepository.dmChatId(me.userId, peer.userId)
                val chatMsgs = messages.filter { it.chatId == cid }
                val unread = chatMsgs.count { it.senderId != me.userId && !it.isReadByMe }
                list.add(
                    ChatThreadSummary(
                        chatId = cid,
                        isGroup = false,
                        targetId = peer.userId,
                        title = peer.displayName,
                        subtitleUsername = "@${peer.username}",
                        avatarPath = peer.avatarPath,
                        videoAvatarPath = peer.videoAvatarPath,
                        badgeType = peer.effectiveBadgeType(),
                        stickerVideoPath = peer.stickerVideoPath,
                        isOnline = peer.isOnline,
                        lastMessage = chatMsgs.maxByOrNull { it.timestamp },
                        unreadCount = unread,
                        presence = presences[cid]
                    )
                )
            }

        list.sortedByDescending { it.lastMessage?.timestamp ?: 0L }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun observeMessages(chatId: String): Flow<List<MessageEntity>> = repo.observeMessages(chatId)
    fun observeGroup(groupId: String): Flow<GroupEntity?> = repo.observeGroup(groupId)

    fun clearAuthError() {
        _authError.value = null
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun showToast(msg: String) {
        _toastMessage.value = msg
    }

    fun signUp(email: String, displayName: String, username: String, avatarUri: Uri?) {
        viewModelScope.launch {
            _authError.value = null
            repo.signUp(email, displayName, username, avatarUri)
                .onFailure { _authError.value = it.message ?: "Sign up failed" }
        }
    }

    fun signIn(email: String, displayName: String, username: String) {
        viewModelScope.launch {
            _authError.value = null
            repo.signIn(email, displayName, username)
                .onFailure { _authError.value = it.message ?: "Sign in failed" }
        }
    }

    fun signOut() {
        viewModelScope.launch { repo.signOut() }
    }

    fun updateProfile(displayName: String, avatarUri: Uri?) {
        viewModelScope.launch {
            repo.updateMyProfile(displayName, avatarUri)
                .onSuccess { showToast("Profile updated") }
                .onFailure { showToast(it.message ?: "Failed to update profile") }
        }
    }

    fun setMyVideoAvatar(videoUri: Uri) {
        viewModelScope.launch {
            showToast("Processing video profile...")
            repo.setMyVideoAvatar(videoUri)
                .onSuccess { showToast("Video profile updated!") }
                .onFailure { showToast(it.message ?: "Failed to set video profile") }
        }
    }

    fun setOwnerBadgeAndSticker(badgeType: TickBadgeType, stickerVideoUri: Uri? = null) {
        viewModelScope.launch {
            repo.setOwnerBadgeAndSticker(badgeType, stickerVideoUri)
                .onSuccess { showToast("Badge updated") }
                .onFailure { showToast(it.message ?: "Failed to update badge") }
        }
    }

    fun setUserBadgeByOwner(targetUserId: String, badgeType: TickBadgeType) {
        viewModelScope.launch {
            repo.setUserBadgeByOwner(targetUserId, badgeType)
                .onSuccess { showToast("User badge set to ${badgeType.name}") }
                .onFailure { showToast(it.message ?: "Failed to set badge") }
        }
    }

    fun setOpenChat(chatId: String?) {
        repo.setOpenChat(chatId)
    }

    fun onComposerTextChanged(chatId: String, isGroup: Boolean, recipientId: String, text: String) {
        typingDebounceJob?.cancel()
        if (text.isNotBlank()) {
            repo.sendPresence(chatId, isGroup, recipientId, PresenceState.TYPING)
            typingDebounceJob = viewModelScope.launch {
                delay(2400)
                repo.sendPresence(chatId, isGroup, recipientId, PresenceState.IDLE)
            }
        } else {
            repo.sendPresence(chatId, isGroup, recipientId, PresenceState.IDLE)
        }
    }

    fun sendText(chatId: String, isGroup: Boolean, recipientId: String, text: String, onSent: () -> Unit) {
        viewModelScope.launch {
            repo.sendTextMessage(chatId, isGroup, recipientId, text)
                .onSuccess { onSent() }
                .onFailure { showToast(it.message ?: "Cannot send message") }
        }
    }

    fun sendVideoEmoji(chatId: String, isGroup: Boolean, recipientId: String, videoUri: Uri) {
        viewModelScope.launch {
            showToast("Preparing 3s Video Emoji...")
            repo.sendVideoEmojiMessage(chatId, isGroup, recipientId, videoUri)
                .onSuccess { showToast("Video Emoji sent") }
                .onFailure { showToast(it.message ?: "Cannot send Video Emoji") }
        }
    }

    fun startVoiceRecording(chatId: String, isGroup: Boolean, recipientId: String) {
        if (!repo.isHotspotReadyForMessaging()) {
            showToast("Hotspot is OFF. Turn on AMMAR-HACKER-5G in HOTSPOT BAM.")
            return
        }
        val started = repo.voiceManager.startRecording()
        if (started) {
            repo.sendPresence(chatId, isGroup, recipientId, PresenceState.RECORDING_VOICE)
        } else {
            showToast("Unable to start microphone")
        }
    }

    fun stopAndSendVoice(chatId: String, isGroup: Boolean, recipientId: String) {
        viewModelScope.launch {
            val recorded = repo.voiceManager.stopAndSaveRecording()
            repo.sendPresence(chatId, isGroup, recipientId, PresenceState.IDLE)
            if (recorded != null) {
                repo.sendVoiceMessage(chatId, isGroup, recipientId, recorded)
                    .onFailure { showToast(it.message ?: "Cannot send voice message") }
            } else {
                showToast("Hold mic longer to record a voice note")
            }
        }
    }

    fun cancelVoiceRecording(chatId: String, isGroup: Boolean, recipientId: String) {
        repo.voiceManager.cancelRecording()
        repo.sendPresence(chatId, isGroup, recipientId, PresenceState.IDLE)
    }

    fun togglePlayVoice(msg: MessageEntity) {
        if (msg.type != MessageType.VOICE.name) return
        repo.voiceManager.togglePlay(msg.msgId, msg.audioPath, msg.durationMs)
    }

    fun createGroup(name: String, avatarUri: Uri?, isPublic: Boolean, onCreated: (GroupEntity) -> Unit) {
        viewModelScope.launch {
            repo.createGroup(name, avatarUri, isPublic)
                .onSuccess {
                    showToast("Group \"${it.name}\" created")
                    onCreated(it)
                }
                .onFailure { showToast(it.message ?: "Failed to create group") }
        }
    }

    fun sendGroupInvite(chatId: String, isGroup: Boolean, recipientId: String, group: GroupEntity) {
        viewModelScope.launch {
            repo.sendGroupInvite(chatId, isGroup, recipientId, group)
                .onSuccess { showToast("Invite sent") }
                .onFailure { showToast(it.message ?: "Cannot send invite") }
        }
    }

    fun joinGroupByInvite(
        groupId: String,
        token: String,
        snapshotJson: String? = null,
        onJoined: (GroupEntity) -> Unit
    ) {
        viewModelScope.launch {
            repo.joinGroupByInvite(groupId, token, snapshotJson)
                .onSuccess {
                    showToast("Joined ${it.name}")
                    onJoined(it)
                }
                .onFailure { showToast(it.message ?: "Could not join group") }
        }
    }

    fun joinGroupByLinkText(rawLink: String, onJoined: (GroupEntity) -> Unit) {
        val cleaned = rawLink.trim()
        val regex = Regex("ammarchat://join/([^/]+)/([^/\\s]+)")
        val match = regex.find(cleaned)
        if (match == null) {
            showToast("Invalid invite link format")
            return
        }
        joinGroupByInvite(match.groupValues[1], match.groupValues[2], null, onJoined)
    }

    fun joinPublicGroup(groupId: String, onJoined: (GroupEntity) -> Unit) {
        viewModelScope.launch {
            repo.joinPublicGroup(groupId)
                .onSuccess {
                    showToast("Joined ${it.name}")
                    onJoined(it)
                }
                .onFailure { showToast(it.message ?: "Could not join group") }
        }
    }

    fun toggleGroupPrivacy(groupId: String) {
        viewModelScope.launch {
            repo.toggleGroupPrivacy(groupId)
                .onSuccess { showToast(if (it.isPublic) "Group is now Public" else "Group is now Private") }
                .onFailure { showToast(it.message ?: "Failed to change privacy") }
        }
    }

    fun regenerateGroupInviteLink(groupId: String) {
        viewModelScope.launch {
            repo.regenerateGroupInviteLink(groupId)
                .onSuccess { showToast("Invite link changed — old link invalidated") }
                .onFailure { showToast(it.message ?: "Failed to reset link") }
        }
    }

    fun muteGroupMember(groupId: String, targetUserId: String, durationMinutes: Long) {
        viewModelScope.launch {
            repo.muteGroupMember(groupId, targetUserId, durationMinutes)
                .onSuccess {
                    showToast(if (durationMinutes <= 0L) "Member unmuted" else "Member muted for ${durationMinutes}m")
                }
                .onFailure { showToast(it.message ?: "Failed to mute member") }
        }
    }

    fun changeGroupOwner(groupId: String, newOwnerUserId: String) {
        viewModelScope.launch {
            repo.changeGroupOwner(groupId, newOwnerUserId)
                .onSuccess { showToast("Group ownership transferred") }
                .onFailure { showToast(it.message ?: "Cannot transfer ownership") }
        }
    }

    fun buildInviteLink(group: GroupEntity): String = repo.buildInviteLink(group)

    fun updateGroupDp(groupId: String, avatarUri: Uri) {
        viewModelScope.launch {
            repo.updateGroupDp(groupId, avatarUri)
                .onSuccess { showToast("Group photo updated") }
                .onFailure { showToast(it.message ?: "Failed to update group photo") }
        }
    }

    fun kickMember(groupId: String, targetUserId: String) {
        viewModelScope.launch {
            repo.kickMemberFromGroup(groupId, targetUserId)
                .onSuccess { showToast("Member kicked from group") }
                .onFailure { showToast(it.message ?: "Cannot kick member") }
        }
    }

    fun toggleGroupAdmin(groupId: String, targetUserId: String) {
        viewModelScope.launch {
            repo.toggleGroupAdmin(groupId, targetUserId)
                .onSuccess { showToast("Admin role updated") }
                .onFailure { showToast(it.message ?: "Cannot update role") }
        }
    }

    fun canManageGroup(user: UserEntity, group: GroupEntity): Boolean =
        repo.canManageGroup(user, group)

    fun banUserWithDuration(targetUserId: String, durationMs: Long) {
        viewModelScope.launch {
            repo.banUserWithDuration(targetUserId, durationMs)
                .onSuccess {
                    showToast(if (durationMs == 0L) "Account unbanned" else "Account banned")
                }
                .onFailure { showToast(it.message ?: "Action failed") }
        }
    }

    fun setThemeByOwner(theme: ChatBackgroundTheme) {
        viewModelScope.launch {
            repo.setThemeByOwner(theme)
                .onSuccess { showToast("Theme updated: ${theme.label}") }
                .onFailure { showToast(it.message ?: "Action failed") }
        }
    }

    fun setRandomThemeByOwner() {
        val current = activeTheme.value
        val candidates = ChatBackgroundTheme.animatedPresets.filter { it != current }
        val picked = candidates.randomOrNull() ?: ChatBackgroundTheme.SPIDY
        setThemeByOwner(picked)
    }

    fun setCustomGalleryThemeByOwner(imageUri: Uri) {
        viewModelScope.launch {
            repo.setCustomGalleryThemeByOwner(imageUri)
                .onSuccess { showToast("Gallery theme applied") }
                .onFailure { showToast(it.message ?: "Failed to apply gallery theme") }
        }
    }

    fun openHotspotNodeApp() {
        val ctx = getApplication<Application>()
        val externalIntent = ctx.packageManager.getLaunchIntentForPackage("com.aistudio.hotspotbam.kxmpzq")
        val intent = (externalIntent ?: Intent(ctx, HotspotNodeActivity::class.java)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }
}
