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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.VideoCall
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.TickBadgeType
import com.example.data.local.UserEntity
import com.example.ui.components.GrantTickDialog
import com.example.ui.components.TimedBanDialog
import com.example.ui.components.UserAvatar
import com.example.ui.components.UserBadgeView
import com.example.ui.components.formatBanRemaining
import com.example.ui.theme.ChatBackgroundTheme
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceHigh
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsAndOwnerScreen(
    currentUser: UserEntity,
    allUsers: List<UserEntity>,
    activeTheme: ChatBackgroundTheme,
    customThemeImagePath: String?,
    onBack: () -> Unit,
    onUpdateAvatar: (Uri) -> Unit,
    onSetVideoAvatar: (Uri) -> Unit,
    onSetOwnerBadgeAndSticker: (TickBadgeType, Uri?) -> Unit,
    onSetUserBadge: (String, TickBadgeType) -> Unit,
    onSelectTheme: (ChatBackgroundTheme) -> Unit,
    onRandomTheme: () -> Unit,
    onSelectCustomGalleryTheme: (Uri) -> Unit,
    onBanWithDuration: (String, Long) -> Unit,
    onOpenHotspotNode: () -> Unit,
    onSignOut: () -> Unit
) {
    var banTargetUser by remember { mutableStateOf<UserEntity?>(null) }
    var tickTargetUser by remember { mutableStateOf<UserEntity?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onUpdateAvatar(uri)
    }

    val videoAvatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onSetVideoAvatar(uri)
    }

    val videoStickerPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onSetOwnerBadgeAndSticker(TickBadgeType.VIDEO_STICKER, uri)
    }

    val galleryThemePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onSelectCustomGalleryTheme(uri)
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
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
                                    isOnline = true,
                                    videoAvatarPath = currentUser.videoAvatarPath
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
                                    val myBadge = currentUser.effectiveBadgeType()
                                    if (myBadge != TickBadgeType.NONE) {
                                        Spacer(Modifier.width(6.dp))
                                        UserBadgeView(
                                            badgeType = myBadge,
                                            stickerVideoPath = currentUser.stickerVideoPath,
                                            size = 18.dp
                                        )
                                    }
                                }
                                Text(
                                    text = "@${currentUser.username}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = activeTheme.accent
                                )
                                Text(
                                    text = "Tap avatar to change photo",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        if (currentUser.canUseVideoFeatures()) {
                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        videoAvatarPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.VideoCall, null, tint = activeTheme.accent, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Video Profile", color = TextPrimary, style = MaterialTheme.typography.labelMedium)
                                }
                                OutlinedButton(
                                    onClick = {
                                        videoStickerPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.Verified, null, tint = EmeraldSignal, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Video Sticker", color = TextPrimary, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            if (currentUser.isOwner) {
                                Spacer(Modifier.height(10.dp))
                                Text("Your Badge Style:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Spacer(Modifier.height(6.dp))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(
                                        TickBadgeType.BLUE to "Blue Tick",
                                        TickBadgeType.GREEN to "Green Tick",
                                        TickBadgeType.RED to "Red Tick"
                                    ).forEach { (badge, label) ->
                                        val selected = currentUser.effectiveBadgeType() == badge
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = if (selected) activeTheme.accent.copy(alpha = 0.2f) else ObsidianSurfaceHigh,
                                            modifier = Modifier.clickable { onSetOwnerBadgeAndSticker(badge, null) }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                UserBadgeView(badgeType = badge, size = 14.dp)
                                                Spacer(Modifier.width(6.dp))
                                                Text(label, color = TextPrimary, style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            } else if (currentUser.effectiveBadgeType() == TickBadgeType.VIDEO_STICKER) {
                                Spacer(Modifier.height(8.dp))
                                TextButton(onClick = { onSetOwnerBadgeAndSticker(TickBadgeType.GREEN, null) }) {
                                    Text("Switch back to Green Tick", color = EmeraldSignal)
                                }
                            }
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
                            Text("HOTSPOT BAM Node", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text("Manage AMMAR-HACKER-5G hotspot & 6 km LAN relay", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Palette, contentDescription = null, tint = activeTheme.accent)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "3D Animated Themes",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Row {
                                    IconButton(onClick = onRandomTheme) {
                                        Icon(Icons.Rounded.Shuffle, contentDescription = "Random 3D Theme", tint = activeTheme.accent)
                                    }
                                    IconButton(onClick = {
                                        galleryThemePicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }) {
                                        Icon(Icons.Rounded.Image, contentDescription = "Choose Theme from Gallery", tint = EmeraldSignal)
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
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
                                            .clickable {
                                                if (itemTheme == ChatBackgroundTheme.CUSTOM_GALLERY) {
                                                    galleryThemePicker.launch(
                                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                    )
                                                } else {
                                                    onSelectTheme(itemTheme)
                                                }
                                            }
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
                                                val bgFile = customThemeImagePath?.let { File(it) }?.takeIf { it.exists() }
                                                if (itemTheme == ChatBackgroundTheme.CUSTOM_GALLERY && bgFile != null) {
                                                    AsyncImage(
                                                        model = bgFile,
                                                        contentDescription = null,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
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
                        text = "USER MODERATION & TICKS",
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
                        val bannedNow = u.isCurrentlyBanned()
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
                                    avatarPath = u.avatarPath,
                                    displayName = u.displayName,
                                    size = 42.dp,
                                    isOnline = u.isOnline,
                                    videoAvatarPath = u.videoAvatarPath
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(u.displayName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                        val badge = u.effectiveBadgeType()
                                        if (badge != TickBadgeType.NONE) {
                                            Spacer(Modifier.width(5.dp))
                                            UserBadgeView(
                                                badgeType = badge,
                                                stickerVideoPath = u.stickerVideoPath,
                                                size = 14.dp
                                            )
                                        }
                                    }
                                    Text(
                                        text = if (bannedNow) {
                                            "@${u.username} • ${formatBanRemaining(u.banExpiresAt)}"
                                        } else {
                                            "@${u.username} • Active"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (bannedNow) CrimsonAlert else TextSecondary
                                    )
                                }
                                IconButton(onClick = { tickTargetUser = u }) {
                                    Icon(Icons.Rounded.Verified, contentDescription = "Manage Tick", tint = EmeraldSignal)
                                }
                                Button(
                                    onClick = { banTargetUser = u },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (bannedNow) EmeraldSignal else CrimsonAlert,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("ban_btn_${u.username}")
                                ) {
                                    Icon(Icons.Rounded.Block, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (bannedNow) "Ban/Unban" else "Ban")
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

    banTargetUser?.let { target ->
        TimedBanDialog(
            targetUser = target,
            onDismiss = { banTargetUser = null },
            onSelectDuration = { durationMs -> onBanWithDuration(target.userId, durationMs) }
        )
    }

    tickTargetUser?.let { target ->
        GrantTickDialog(
            targetUser = target,
            theme = activeTheme,
            onDismiss = { tickTargetUser = null },
            onSelectBadge = { badge -> onSetUserBadge(target.userId, badge) }
        )
    }
}
