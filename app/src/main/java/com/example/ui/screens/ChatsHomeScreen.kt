package com.example.ui.screens

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.MessageType
import com.example.data.local.PresenceState
import com.example.data.local.UserEntity
import com.example.network.LanConnectionState
import com.example.ui.ChatThreadSummary
import com.example.ui.components.MessageStatusTicks
import com.example.ui.components.UserAvatar
import com.example.ui.components.VerifiedBlueTick
import com.example.ui.components.formatClockTime
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.ChatBackgroundTheme
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceHigh
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

private enum class ThreadFilter(val label: String) {
    ALL("All"),
    DIRECT("Direct"),
    GROUPS("Groups")
}

@Composable
fun ChatsHomeScreen(
    currentUser: UserEntity,
    connectionState: LanConnectionState,
    theme: ChatBackgroundTheme,
    threads: List<ChatThreadSummary>,
    allUsers: List<UserEntity>,
    onOpenDm: (UserEntity) -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCreateGroup: () -> Unit,
    onOpenJoinLink: () -> Unit,
    onOpenHotspotNode: () -> Unit
) {
    var searchMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(ThreadFilter.ALL) }
    var showFabMenu by remember { mutableStateOf(false) }

    val filteredThreads = remember(threads, filter, searchQuery) {
        threads.filter { t ->
            val matchesFilter = when (filter) {
                ThreadFilter.ALL -> true
                ThreadFilter.DIRECT -> !t.isGroup
                ThreadFilter.GROUPS -> t.isGroup
            }
            val matchesQuery = searchQuery.isBlank() ||
                t.title.contains(searchQuery, ignoreCase = true) ||
                t.subtitleUsername.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesQuery
        }
    }

    val searchedUsers = remember(allUsers, searchQuery, currentUser.userId) {
        val clean = searchQuery.trim().removePrefix("@")
        allUsers.filter { u ->
            u.userId != currentUser.userId && !u.isBanned && (
                clean.isBlank() ||
                    u.username.contains(clean, ignoreCase = true) ||
                    u.displayName.contains(clean, ignoreCase = true)
                )
        }
    }

    Scaffold(
        containerColor = ObsidianBg,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianSurface)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("home_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Menu,
                            contentDescription = "Open settings and profile",
                            tint = TextPrimary
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Ammar Chat",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            if (currentUser.hasBlueTick) {
                                Spacer(Modifier.width(6.dp))
                                VerifiedBlueTick(size = 16.dp)
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(onClick = onOpenHotspotNode)
                                .padding(vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = if (connectionState.isHotspotActive) Icons.Rounded.Wifi else Icons.Rounded.WifiOff,
                                contentDescription = null,
                                tint = if (connectionState.isHotspotActive) EmeraldSignal else AmberWarn,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = if (connectionState.isHotspotActive) {
                                    "${connectionState.ssid} • ${connectionState.pingMs}ms"
                                } else {
                                    "Hotspot Offline • Tap to open Node"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (connectionState.isHotspotActive) EmeraldSignal else AmberWarn
                            )
                        }
                    }

                    IconButton(
                        onClick = onOpenHotspotNode,
                        modifier = Modifier.testTag("open_node_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Router,
                            contentDescription = "Open Ammar 5G Node",
                            tint = if (connectionState.isHotspotActive) EmeraldSignal else TextSecondary
                        )
                    }

                    IconButton(
                        onClick = {
                            searchMode = !searchMode
                            if (!searchMode) searchQuery = ""
                        },
                        modifier = Modifier.testTag("search_users_button")
                    ) {
                        Icon(
                            imageVector = if (searchMode) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription = "Search by username",
                            tint = TextPrimary
                        )
                    }
                }

                AnimatedVisibility(visible = searchMode) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search @username or group...") },
                        leadingIcon = { Icon(Icons.Rounded.PersonSearch, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = theme.accent,
                            unfocusedBorderColor = ObsidianOutline,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = ObsidianBg,
                            unfocusedContainerColor = ObsidianBg
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("username_search_input")
                    )
                }

                // Filter tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThreadFilter.entries.forEach { tab ->
                        val selected = filter == tab
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (selected) theme.accent.copy(alpha = 0.18f) else ObsidianSurfaceHigh,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (selected) theme.accent.copy(alpha = 0.65f) else Color.Transparent
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { filter = tab }
                        ) {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) theme.accent else TextSecondary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
                HorizontalDivider(color = ObsidianOutline.copy(alpha = 0.5f))
            }
        },
        floatingActionButton = {
            Box(modifier = Modifier.navigationBarsPadding()) {
                FloatingActionButton(
                    onClick = { showFabMenu = true },
                    containerColor = theme.accent,
                    contentColor = Color(0xFF04101A),
                    modifier = Modifier.testTag("home_fab")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "New chat or group")
                }
                DropdownMenu(
                    expanded = showFabMenu,
                    onDismissRequest = { showFabMenu = false },
                    modifier = Modifier.background(ObsidianSurfaceHigh)
                ) {
                    DropdownMenuItem(
                        text = { Text("Find User (@username)", color = TextPrimary) },
                        leadingIcon = { Icon(Icons.Rounded.PersonSearch, null, tint = theme.accent) },
                        onClick = {
                            showFabMenu = false
                            searchMode = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Create Group", color = TextPrimary) },
                        leadingIcon = { Icon(Icons.Rounded.GroupAdd, null, tint = theme.accent) },
                        onClick = {
                            showFabMenu = false
                            onOpenCreateGroup()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Join via Invite Link", color = TextPrimary) },
                        leadingIcon = { Icon(Icons.Rounded.Link, null, tint = theme.accent) },
                        onClick = {
                            showFabMenu = false
                            onOpenJoinLink()
                        }
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            if (searchMode) {
                item {
                    Text(
                        text = "LAN PEERS BY @USERNAME",
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.accent,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                    )
                }
                if (searchedUsers.isEmpty()) {
                    item {
                        Text(
                            text = "No matching users found on the LAN yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                        )
                    }
                } else {
                    items(searchedUsers, key = { "peer_${it.userId}" }) { peer ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenDm(peer) }
                                .padding(horizontal = 16.dp, vertical = 11.dp)
                                .testTag("search_user_${peer.username}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatar(
                                avatarPath = peer.avatarPath,
                                displayName = peer.displayName,
                                size = 48.dp,
                                isOnline = peer.isOnline
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = peer.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (peer.hasBlueTick) {
                                        Spacer(Modifier.width(6.dp))
                                        VerifiedBlueTick(size = 15.dp)
                                    }
                                }
                                Text(
                                    text = "@${peer.username} • ${if (peer.isOnline) "online" else "offline"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (peer.isOnline) EmeraldSignal else TextSecondary
                                )
                            }
                        }
                    }
                }
                item { HorizontalDivider(color = ObsidianOutline.copy(alpha = 0.4f)) }
            }

            if (filteredThreads.isEmpty() && !searchMode) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp, vertical = 64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Rounded.PersonSearch,
                                contentDescription = null,
                                tint = theme.accent.copy(alpha = 0.7f),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = "No conversations yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Activate AMMAR-HACKER-5G in Ammar 5G Node, then tap Search to find peers by @username or create a Group.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            } else {
                items(filteredThreads, key = { it.chatId }) { thread ->
                    ChatThreadRow(
                        thread = thread,
                        myUserId = currentUser.userId,
                        theme = theme,
                        onClick = {
                            if (thread.isGroup) {
                                onOpenGroup(thread.targetId)
                            } else {
                                val user = allUsers.firstOrNull { it.userId == thread.targetId }
                                if (user != null) onOpenDm(user)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatThreadRow(
    thread: ChatThreadSummary,
    myUserId: String,
    theme: ChatBackgroundTheme,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("thread_${thread.chatId}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(
            avatarPath = thread.avatarPath,
            displayName = thread.title,
            size = 54.dp,
            isOnline = thread.isOnline,
            isGroup = thread.isGroup
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = thread.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (thread.hasBlueTick) {
                        Spacer(Modifier.width(5.dp))
                        VerifiedBlueTick(size = 15.dp)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val last = thread.lastMessage
                    if (last != null && last.senderId == myUserId) {
                        MessageStatusTicks(status = last.status)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = last?.let { formatClockTime(it.timestamp) } ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val presence = thread.presence
                if (presence != null && presence.state != PresenceState.IDLE) {
                    val label = when (presence.state) {
                        PresenceState.TYPING -> if (thread.isGroup) "@${presence.fromUsername} typing..." else "typing..."
                        PresenceState.RECORDING_VOICE -> if (thread.isGroup) "@${presence.fromUsername} sending voice..." else "sending voice..."
                        PresenceState.IDLE -> ""
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (presence.state == PresenceState.RECORDING_VOICE) EmeraldSignal else theme.accent,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    val last = thread.lastMessage
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (last?.type == MessageType.VOICE.name) {
                            Icon(
                                imageVector = Icons.Rounded.Mic,
                                contentDescription = null,
                                tint = theme.accent,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        val previewText = when {
                            last == null -> thread.subtitleUsername
                            last.type == MessageType.VOICE.name -> "Voice message"
                            last.type == MessageType.GROUP_INVITE.name -> "Group Invite: ${last.groupInviteName ?: "Join"}"
                            else -> if (thread.isGroup && last.type != MessageType.SYSTEM.name) {
                                "${last.senderName}: ${last.text}"
                            } else {
                                last.text
                            }
                        }
                        Text(
                            text = previewText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (thread.unreadCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(theme.accent)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = thread.unreadCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF04101A),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
