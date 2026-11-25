package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.audio.VoicePlaybackState
import com.example.audio.VoiceRecordingState
import com.example.data.local.GroupEntity
import com.example.data.local.MessageEntity
import com.example.data.local.MessageType
import com.example.data.local.PresenceState
import com.example.data.local.UserEntity
import com.example.data.repo.PresenceIndicator
import com.example.network.LanConnectionState
import com.example.ui.components.GroupInviteBubble
import com.example.ui.components.MessageStatusTicks
import com.example.ui.components.ThemedChatBackground
import com.example.ui.components.UserAvatar
import com.example.ui.components.VerifiedBlueTick
import com.example.ui.components.VoiceMessageBubble
import com.example.ui.components.formatClockTime
import com.example.ui.components.formatDuration
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.ChatBackgroundTheme
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceHigh
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ChatDetailScreen(
    chatId: String,
    isGroup: Boolean,
    recipientId: String,
    currentUser: UserEntity,
    peerUser: UserEntity?,
    group: GroupEntity?,
    myGroups: List<GroupEntity>,
    messages: List<MessageEntity>,
    presence: PresenceIndicator?,
    connectionState: LanConnectionState,
    theme: ChatBackgroundTheme,
    recordingState: VoiceRecordingState,
    playbackState: VoicePlaybackState,
    onBack: () -> Unit,
    onSetOpenChat: (String?) -> Unit,
    onTextChanged: (String) -> Unit,
    onSendText: (String, () -> Unit) -> Unit,
    onStartVoice: () -> Unit,
    onStopAndSendVoice: () -> Unit,
    onCancelVoice: () -> Unit,
    onTogglePlayVoice: (MessageEntity) -> Unit,
    onOpenGroupInfo: (String) -> Unit,
    onShareGroupInvite: (GroupEntity) -> Unit,
    onJoinGroupInvite: (String, String) -> Unit,
    onOwnerBanToggle: (String, Boolean) -> Unit,
    onOpenHotspotNode: () -> Unit
) {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var showShareGroupPicker by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onStartVoice()
    }

    DisposableEffect(chatId) {
        onSetOpenChat(chatId)
        onDispose { onSetOpenChat(null) }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val title = if (isGroup) group?.name ?: "Group" else peerUser?.displayName ?: "Chat"
    val avatarPath = if (isGroup) group?.avatarPath else peerUser?.avatarPath
    val hasBlueTick = !isGroup && (peerUser?.hasBlueTick == true)

    val subtitle = when {
        presence != null && presence.state == PresenceState.TYPING ->
            if (isGroup) "@${presence.fromUsername} is typing..." else "typing..."
        presence != null && presence.state == PresenceState.RECORDING_VOICE ->
            if (isGroup) "@${presence.fromUsername} is sending voice..." else "sending voice..."
        !connectionState.isHotspotActive -> "hotspot offline"
        isGroup -> "${group?.memberIds()?.size ?: 0} members • ${connectionState.ssid}"
        peerUser?.isOnline == true -> "online • ${connectionState.pingMs}ms"
        else -> "offline"
    }
    val subtitleColor = when {
        presence?.state == PresenceState.TYPING -> theme.accent
        presence?.state == PresenceState.RECORDING_VOICE -> EmeraldSignal
        !connectionState.isHotspotActive -> AmberWarn
        peerUser?.isOnline == true || isGroup -> EmeraldSignal
        else -> TextSecondary
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianSurface.copy(alpha = 0.96f))
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("chat_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = isGroup && group != null) {
                                group?.let { onOpenGroupInfo(it.groupId) }
                            }
                            .padding(vertical = 4.dp, horizontal = 4.dp)
                    ) {
                        UserAvatar(
                            avatarPath = avatarPath,
                            displayName = title,
                            size = 42.dp,
                            isOnline = peerUser?.isOnline == true,
                            isGroup = isGroup
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (hasBlueTick) {
                                    Spacer(Modifier.width(5.dp))
                                    VerifiedBlueTick(size = 15.dp)
                                }
                            }
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = subtitleColor,
                                fontWeight = if (presence != null) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (isGroup && group != null) {
                        IconButton(
                            onClick = { onOpenGroupInfo(group.groupId) },
                            modifier = Modifier.testTag("group_info_button")
                        ) {
                            Icon(Icons.Rounded.Group, contentDescription = "Group info", tint = theme.accent)
                        }
                    }

                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "Chat options", tint = TextPrimary)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(ObsidianSurfaceHigh)
                        ) {
                            if (myGroups.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Send Group Invite Link", color = TextPrimary) },
                                    leadingIcon = { Icon(Icons.Rounded.Link, null, tint = theme.accent) },
                                    onClick = {
                                        showMenu = false
                                        showShareGroupPicker = true
                                    }
                                )
                            }
                            if (!isGroup && peerUser != null && currentUser.isOwner) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (peerUser.isBanned) "Unban @${peerUser.username}" else "Ban @${peerUser.username}",
                                            color = CrimsonAlert
                                        )
                                    },
                                    leadingIcon = { Icon(Icons.Rounded.Block, null, tint = CrimsonAlert) },
                                    onClick = {
                                        showMenu = false
                                        onOwnerBanToggle(peerUser.userId, !peerUser.isBanned)
                                    }
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = !connectionState.isHotspotActive) {
                    Surface(
                        color = CrimsonAlert.copy(alpha = 0.18f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WifiOff,
                                contentDescription = null,
                                tint = AmberWarn,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Hotspot OFF — Turn on AMMAR-HACKER-5G to send messages",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onOpenHotspotNode) {
                                Icon(Icons.Rounded.Router, null, tint = theme.accent, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Node", color = theme.accent, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianSurface.copy(alpha = 0.96f))
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                if (recordingState.isRecording) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(26.dp))
                            .background(ObsidianSurfaceHigh)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onCancelVoice,
                            modifier = Modifier.testTag("cancel_voice_button")
                        ) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Cancel voice note", tint = CrimsonAlert)
                        }
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(CrimsonAlert)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = formatDuration(recordingState.elapsedMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary
                        )
                        Spacer(Modifier.width(10.dp))
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(26.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            val amps = recordingState.amplitudes.ifEmpty { listOf(0.2f, 0.4f, 0.3f) }
                            amps.takeLast(18).forEach { a ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height((24f * a).coerceAtLeast(4f).dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(EmeraldSignal)
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(EmeraldSignal)
                                .clickable(onClick = onStopAndSendVoice)
                                .testTag("send_voice_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "Send voice message",
                                tint = Color(0xFF04101A)
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        TextField(
                            value = inputText,
                            onValueChange = {
                                inputText = it
                                onTextChanged(it)
                            },
                            placeholder = {
                                Text(
                                    if (connectionState.isHotspotActive) "Message..." else "Hotspot offline...",
                                    color = TextSecondary
                                )
                            },
                            enabled = connectionState.isHotspotActive,
                            maxLines = 4,
                            shape = RoundedCornerShape(24.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = ObsidianSurfaceHigh,
                                unfocusedContainerColor = ObsidianSurfaceHigh,
                                disabledContainerColor = ObsidianSurfaceHigh.copy(alpha = 0.5f),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                cursorColor = theme.accent
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_message_input")
                        )

                        Spacer(Modifier.width(8.dp))

                        val hasText = inputText.isNotBlank()
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(
                                    if (connectionState.isHotspotActive) theme.accent
                                    else ObsidianSurfaceHigh
                                )
                                .clickable(enabled = connectionState.isHotspotActive) {
                                    if (hasText) {
                                        val sending = inputText
                                        onSendText(sending) { inputText = "" }
                                    } else {
                                        val hasMic = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (hasMic) {
                                            onStartVoice()
                                        } else {
                                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                }
                                .testTag(if (hasText) "send_text_button" else "record_voice_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (hasText) Icons.AutoMirrored.Rounded.Send else Icons.Rounded.Mic,
                                contentDescription = if (hasText) "Send message" else "Record voice message",
                                tint = if (connectionState.isHotspotActive) Color(0xFF04101A) else TextSecondary
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ThemedChatBackground(theme = theme)

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.msgId }) { msg ->
                    if (msg.type == MessageType.SYSTEM.name) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color.Black.copy(alpha = 0.42f)
                            ) {
                                Text(
                                    text = msg.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    } else {
                        val isMine = msg.senderId == currentUser.userId
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
                        ) {
                            Surface(
                                shape = RoundedCornerShape(
                                    topStart = 18.dp,
                                    topEnd = 18.dp,
                                    bottomStart = if (isMine) 18.dp else 4.dp,
                                    bottomEnd = if (isMine) 4.dp else 18.dp
                                ),
                                color = if (isMine) theme.outgoingBubble else theme.incomingBubble,
                                tonalElevation = 2.dp,
                                modifier = Modifier.widthIn(max = 300.dp)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    if (isGroup && !isMine) {
                                        Text(
                                            text = "${msg.senderName} (@${msg.senderUsername})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = theme.accent,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(Modifier.height(3.dp))
                                    }

                                    when (msg.type) {
                                        MessageType.VOICE.name -> {
                                            VoiceMessageBubble(
                                                msg = msg,
                                                isMine = isMine,
                                                playbackState = playbackState,
                                                theme = theme,
                                                onTogglePlay = { onTogglePlayVoice(msg) }
                                            )
                                        }
                                        MessageType.GROUP_INVITE.name -> {
                                            GroupInviteBubble(
                                                msg = msg,
                                                isMine = isMine,
                                                theme = theme,
                                                onJoinClick = {
                                                    val gid = msg.groupInviteId
                                                    val tok = msg.groupInviteToken
                                                    if (!gid.isNullOrBlank() && !tok.isNullOrBlank()) {
                                                        onJoinGroupInvite(gid, tok)
                                                    }
                                                }
                                            )
                                        }
                                        else -> {
                                            Text(
                                                text = msg.text,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = TextPrimary
                                            )
                                            Spacer(Modifier.height(3.dp))
                                            Row(
                                                modifier = Modifier.align(Alignment.End),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = formatClockTime(msg.timestamp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = TextSecondary
                                                )
                                                if (isMine) {
                                                    Spacer(Modifier.width(4.dp))
                                                    MessageStatusTicks(status = msg.status)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showShareGroupPicker) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showShareGroupPicker = false },
            containerColor = ObsidianSurface,
            title = { Text("Share Group Invite Link", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    myGroups.forEach { grp ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ObsidianSurfaceHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showShareGroupPicker = false
                                    onShareGroupInvite(grp)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UserAvatar(grp.avatarPath, grp.name, size = 36.dp, isGroup = true)
                                Spacer(Modifier.width(10.dp))
                                Text(grp.name, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showShareGroupPicker = false }) {
                    Text("Close", color = theme.accent)
                }
            }
        )
    }
}
