package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.GroupEntity
import com.example.data.local.UserEntity
import com.example.ui.components.UserAvatar
import com.example.ui.components.VerifiedBlueTick
import com.example.ui.theme.ChatBackgroundTheme
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceHigh
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun CreateGroupDialog(
    theme: ChatBackgroundTheme,
    onDismiss: () -> Unit,
    onCreate: (name: String, avatarUri: Uri?) -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) selectedUri = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = { Text("Create Group", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(ObsidianSurfaceHigh)
                        .border(2.dp, theme.accent.copy(alpha = 0.6f), CircleShape)
                        .clickable {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        .testTag("group_dp_picker"),
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedUri != null) {
                        AsyncImage(
                            model = selectedUri,
                            contentDescription = "Group photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(Icons.Rounded.AddAPhoto, contentDescription = "Pick group photo", tint = theme.accent)
                    }
                }
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Group Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = theme.accent,
                        unfocusedBorderColor = ObsidianOutline,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_group_name_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(groupName, selectedUri) },
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent, contentColor = Color(0xFF04101A)),
                modifier = Modifier.testTag("confirm_create_group")
            ) {
                Text("Create", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@Composable
fun JoinInviteLinkDialog(
    theme: ChatBackgroundTheme,
    onDismiss: () -> Unit,
    onJoin: (String) -> Unit
) {
    var link by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = { Text("Join via Invite Link", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = link,
                onValueChange = { link = it },
                label = { Text("ammarchat://join/...") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = theme.accent,
                    unfocusedBorderColor = ObsidianOutline,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("join_link_input")
            )
        },
        confirmButton = {
            Button(
                onClick = { onJoin(link) },
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent, contentColor = Color(0xFF04101A)),
                modifier = Modifier.testTag("confirm_join_link")
            ) {
                Text("Join Group", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@Composable
fun GroupInfoScreen(
    group: GroupEntity,
    currentUser: UserEntity,
    allUsers: List<UserEntity>,
    inviteLink: String,
    canManage: Boolean,
    theme: ChatBackgroundTheme,
    onBack: () -> Unit,
    onUpdateGroupDp: (Uri) -> Unit,
    onKickMember: (String) -> Unit,
    onToggleAdmin: (String) -> Unit,
    onCopiedLink: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val dpPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onUpdateGroupDp(uri)
    }

    val members = group.memberIds().map { uid ->
        allUsers.firstOrNull { it.userId == uid } ?: UserEntity(
            userId = uid,
            displayName = uid,
            username = uid.removePrefix("u_")
        )
    }
    val admins = group.adminIds().toSet()

    Scaffold(
        containerColor = ObsidianBg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianSurface)
                    .statusBarsPadding()
                    .padding(horizontal = 6.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Text(
                    text = "Group Info & Admin",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = ObsidianSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier.clickable(enabled = canManage) {
                                dpPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        ) {
                            UserAvatar(
                                avatarPath = group.avatarPath,
                                displayName = group.name,
                                size = 90.dp,
                                isGroup = true
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (canManage) "${members.size} members • Tap photo to change Group DP" else "${members.size} members",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (canManage) theme.accent else TextSecondary
                        )
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ObsidianSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Group Invite Link", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Spacer(Modifier.height(3.dp))
                            Text(inviteLink, style = MaterialTheme.typography.labelSmall, color = theme.accent)
                        }
                        IconButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(inviteLink))
                                onCopiedLink()
                            },
                            modifier = Modifier.testTag("copy_group_invite")
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy invite link", tint = theme.accent)
                        }
                    }
                }
            }

            item {
                Text(
                    text = "MEMBERS (${members.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }

            items(members, key = { it.userId }) { member ->
                val isAdmin = member.userId in admins || member.userId == group.creatorId
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ObsidianSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserAvatar(
                            avatarPath = member.avatarPath,
                            displayName = member.displayName,
                            size = 44.dp,
                            isOnline = member.isOnline
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(member.displayName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                if (member.hasBlueTick) {
                                    Spacer(Modifier.width(5.dp))
                                    VerifiedBlueTick(size = 14.dp)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("@${member.username}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                if (isAdmin) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (member.userId == group.creatorId) "Creator" else "Admin",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldSignal
                                    )
                                }
                            }
                        }

                        if (canManage && member.userId != currentUser.userId && member.userId != group.creatorId) {
                            IconButton(onClick = { onToggleAdmin(member.userId) }) {
                                Icon(
                                    Icons.Rounded.AdminPanelSettings,
                                    contentDescription = "Toggle admin",
                                    tint = if (isAdmin) EmeraldSignal else TextSecondary
                                )
                            }
                            IconButton(
                                onClick = { onKickMember(member.userId) },
                                modifier = Modifier.testTag("kick_${member.username}")
                            ) {
                                Icon(
                                    Icons.Rounded.PersonRemove,
                                    contentDescription = "Kick member",
                                    tint = CrimsonAlert
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
