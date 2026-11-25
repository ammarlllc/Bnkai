package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.TickBadgeType
import com.example.data.local.UserEntity
import com.example.ui.theme.ChatBackgroundTheme
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceHigh
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

private data class BanPreset(val label: String, val durationMs: Long)

private val BAN_PRESETS = listOf(
    BanPreset("1 Min", 60_000L),
    BanPreset("10 Min", 10 * 60_000L),
    BanPreset("1 Hour", 60 * 60_000L),
    BanPreset("1 Day", 24 * 60 * 60_000L),
    BanPreset("30 Days", 30L * 24 * 60 * 60_000L),
    BanPreset("1 Year", 365L * 24 * 60 * 60_000L),
    BanPreset("10 Years", 10L * 365 * 24 * 60 * 60_000L),
    BanPreset("100 Years", 100L * 365 * 24 * 60 * 60_000L),
    BanPreset("Permanent", -1L)
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimedBanDialog(
    targetUser: UserEntity,
    onDismiss: () -> Unit,
    onSelectDuration: (durationMs: Long) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = {
            Text(
                text = "Account Ban • @${targetUser.username}",
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (targetUser.isCurrentlyBanned()) {
                        "Currently banned (${formatBanRemaining(targetUser.banExpiresAt)}). Choose a new duration or unban:"
                    } else {
                        "Choose ban duration (1 minute to 100 years, or Permanent):"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BAN_PRESETS.forEach { preset ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = CrimsonAlert.copy(alpha = 0.18f),
                            modifier = Modifier.clickable {
                                onSelectDuration(preset.durationMs)
                                onDismiss()
                            }
                        ) {
                            Text(
                                text = preset.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = CrimsonAlert,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
                if (targetUser.isCurrentlyBanned()) {
                    Button(
                        onClick = {
                            onSelectDuration(0L)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldSignal, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Unban Account", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextSecondary)
            }
        }
    )
}

@Composable
fun GrantTickDialog(
    targetUser: UserEntity,
    theme: ChatBackgroundTheme,
    onDismiss: () -> Unit,
    onSelectBadge: (TickBadgeType) -> Unit
) {
    val options = listOf(
        Triple(TickBadgeType.GREEN, "Green Tick", "Grants 3s Video Emoji, Video Profile & Video Sticker"),
        Triple(TickBadgeType.RED, "Red Tick", "Distinctive Red Verified Badge only"),
        Triple(TickBadgeType.NONE, "Remove Tick", "Removes special tick badge from user")
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = {
            Text("Manage Tick • @${targetUser.username}", color = TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (badge, title, desc) ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ObsidianSurfaceHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectBadge(badge)
                                onDismiss()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (badge != TickBadgeType.NONE) {
                                UserBadgeView(badgeType = badge, size = 20.dp)
                                Spacer(Modifier.width(10.dp))
                            }
                            Column {
                                Text(title, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                                Text(desc, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = theme.accent)
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MuteMemberDialog(
    member: UserEntity,
    theme: ChatBackgroundTheme,
    onDismiss: () -> Unit,
    onMuteMinutes: (Long) -> Unit
) {
    val options = listOf(
        1L to "1 Min",
        5L to "5 Min",
        15L to "15 Min",
        60L to "1 Hour",
        1440L to "24 Hours",
        0L to "Unmute"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = {
            Text("Mute @${member.username} in Group", color = TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Temporarily block member from sending messages in this group:",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    options.forEach { (mins, label) ->
                        val isUnmute = mins == 0L
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isUnmute) EmeraldSignal.copy(alpha = 0.2f) else theme.accent.copy(alpha = 0.18f),
                            modifier = Modifier.clickable {
                                onMuteMinutes(mins)
                                onDismiss()
                            }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isUnmute) EmeraldSignal else theme.accent,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextSecondary)
            }
        }
    )
}
