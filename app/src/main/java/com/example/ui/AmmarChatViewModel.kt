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
    val hasBlueTick: Boolean,
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

        // 1. Groups user belongs to
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
                    subtitleUsername = "${grp.memberIds().size} members",
                    avatarPath = grp.avatarPath,
                    hasBlueTick = false,
                    isOnline = true,
                    lastMessage = chatMsgs.maxByOrNull { it.timestamp },
                    unreadCount = unread,
                    presence = presences[cid]
                )
            )
        }

        // 2. Direct chats with peers who have messages or are active peers
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
                        hasBlueTick = peer.hasBlueTick,
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

    fun startVoiceRecording(chatId: String, isGroup: Boolean, recipientId: String) {
        if (!repo.isHotspotReadyForMessaging()) {
            showToast("Hotspot is OFF. Turn on AMMAR-HACKER-5G in Ammar 5G Node.")
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

    fun createGroup(name: String, avatarUri: Uri?, onCreated: (GroupEntity) -> Unit) {
        viewModelScope.launch {
            repo.createGroup(name, avatarUri)
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

    fun joinGroupByInvite(groupId: String, token: String, onJoined: (GroupEntity) -> Unit) {
        viewModelScope.launch {
            repo.joinGroupByInvite(groupId, token)
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
        val groupId = match.groupValues[1]
        val token = match.groupValues[2]
        joinGroupByInvite(groupId, token, onJoined)
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
                .onSuccess { showToast("Member removed from group") }
                .onFailure { showToast(it.message ?: "Cannot remove member") }
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

    fun setUserBannedByOwner(targetUserId: String, banned: Boolean) {
        viewModelScope.launch {
            repo.setUserBannedByOwner(targetUserId, banned)
                .onSuccess { showToast(if (banned) "Account banned" else "Account unbanned") }
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

    fun openHotspotNodeApp() {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, HotspotNodeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }
}
