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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
fun SettingsAndOwnerScreen(
    currentUser: UserEntity,
    allUsers: List<UserEntity>,
    activeTheme: ChatBackgroundTheme,
    onBack: () -> Unit,
    onUpdateAvatar: (Uri) -> Unit,
    onSelectTheme: (ChatBackgroundTheme) -> Unit,
    onToggleBan: (String, Boolean) -> Unit,
    onOpenHotspotNode: () -> Unit,
    onSignOut: () -> Unit
) {
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onUpdateAvatar(uri)
    }

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
                    text = "Settings & Profile",
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
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.clickable {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        ) {
                            UserAvatar(
                                avatarPath = currentUser.avatarPath,
                                displayName = currentUser.displayName,
                                size = 72.dp,
                                isOnline = true
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = currentUser.displayName,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                if (currentUser.hasBlueTick) {
                                    Spacer(Modifier.width(6.dp))
                                    VerifiedBlueTick(size = 18.dp)
                                }
                            }
                            Text(
                                text = "@${currentUser.username}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = activeTheme.accent
                            )
                            Text(
                                text = "Tap photo to update profile picture",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ObsidianSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onOpenHotspotNode)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Router, contentDescription = null, tint = EmeraldSignal)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Ammar 5G Node", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text("Manage AMMAR-HACKER-5G hotspot & LAN relay", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }
            }

            if (currentUser.isOwner) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = ObsidianSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, activeTheme.accent.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Palette, contentDescription = null, tint = activeTheme.accent)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Background Theme",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(ChatBackgroundTheme.entries) { itemTheme ->
                                    val selected = itemTheme == activeTheme
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = ObsidianSurfaceHigh,
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.5.dp,
                                            if (selected) itemTheme.accent else ObsidianOutline
                                        ),
                                        modifier = Modifier
                                            .width(136.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable { onSelectTheme(itemTheme) }
                                            .testTag("theme_${itemTheme.id}")
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(48.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(
                                                        Brush.verticalGradient(listOf(itemTheme.bgTop, itemTheme.bgBottom))
                                                    )
                                                    .border(1.dp, itemTheme.accent.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                                                contentAlignment = Alignment.TopEnd
                                            ) {
                                                if (selected) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.CheckCircle,
                                                        contentDescription = "Selected",
                                                        tint = itemTheme.accent,
                                                        modifier = Modifier.padding(5.dp).size(18.dp)
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                text = itemTheme.label,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextPrimary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "ACCOUNT MODERATION",
                        style = MaterialTheme.typography.labelMedium,
                        color = activeTheme.accent
                    )
                }

                val otherUsers = allUsers.filter { it.userId != currentUser.userId }
                if (otherUsers.isEmpty()) {
                    item {
                        Text(
                            text = "Connected LAN users will appear here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                } else {
                    items(otherUsers, key = { it.userId }) { u ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = ObsidianSurface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UserAvatar(u.avatarPath, u.displayName, size = 42.dp, isOnline = u.isOnline)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(u.displayName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                    Text(
                                        text = "@${u.username} • ${if (u.isBanned) "BANNED" else "Active"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (u.isBanned) CrimsonAlert else TextSecondary
                                    )
                                }
                                Button(
                                    onClick = { onToggleBan(u.userId, !u.isBanned) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (u.isBanned) EmeraldSignal else CrimsonAlert,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("ban_btn_${u.username}")
                                ) {
                                    Icon(Icons.Rounded.Block, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (u.isBanned) "Unban" else "Ban")
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onSignOut,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CrimsonAlert.copy(alpha = 0.16f),
                        contentColor = CrimsonAlert
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("settings_signout_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Sign Out", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
