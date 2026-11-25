package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.audio.VoicePlaybackState
import com.example.data.local.MessageEntity
import com.example.data.local.MessageStatus
import com.example.ui.theme.ChatBackgroundTheme
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VerifiedBlue
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun VerifiedBlueTick(
    modifier: Modifier = Modifier,
    size: Dp = 17.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(VerifiedBlue),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = "Verified account",
            tint = Color.White,
            modifier = Modifier.size(size * 0.68f)
        )
    }
}

@Composable
fun UserAvatar(
    avatarPath: String?,
    displayName: String,
    size: Dp = 48.dp,
    isOnline: Boolean = false,
    isGroup: Boolean = false,
    modifier: Modifier = Modifier
) {
    val palette = listOf(
        Color(0xFF0EA5E9) to Color(0xFF2563EB),
        Color(0xFF10B981) to Color(0xFF059669),
        Color(0xFF8B5CF6) to Color(0xFF6366F1),
        Color(0xFFF59E0B) to Color(0xFFD97706),
        Color(0xFFEC4899) to Color(0xFFE11D48)
    )
    val pair = palette[abs(displayName.hashCode()) % palette.size]
    val initials = displayName
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.take(1).uppercase() }
        .ifEmpty { "?" }

    Box(modifier = modifier.size(size)) {
        val file = avatarPath?.let { File(it) }
        if (file != null && file.exists()) {
            AsyncImage(
                model = file,
                contentDescription = "$displayName avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(pair.first, pair.second))),
                contentAlignment = Alignment.Center
            ) {
                if (isGroup) {
                    Icon(
                        imageVector = Icons.Rounded.Groups,
                        contentDescription = "$displayName group icon",
                        tint = Color.White,
                        modifier = Modifier.size(size * 0.54f)
                    )
                } else {
                    Text(
                        text = initials,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = (size.value * 0.36f).sp
                    )
                }
            }
        }

        if (isOnline && !isGroup) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.28f)
                    .clip(CircleShape)
                    .background(ObsidianBg)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(EmeraldSignal)
            )
        }
    }
}

@Composable
fun MessageStatusTicks(
    status: String,
    modifier: Modifier = Modifier
) {
    when (status) {
        MessageStatus.READ.name -> Icon(
            imageVector = Icons.Rounded.DoneAll,
            contentDescription = "Read",
            tint = VerifiedBlue,
            modifier = modifier.size(16.dp)
        )
        MessageStatus.DELIVERED.name -> Icon(
            imageVector = Icons.Rounded.DoneAll,
            contentDescription = "Delivered",
            tint = TextSecondary,
            modifier = modifier.size(16.dp)
        )
        else -> Icon(
            imageVector = Icons.Rounded.Done,
            contentDescription = "Sent",
            tint = TextMuted,
            modifier = modifier.size(15.dp)
        )
    }
}

@Composable
fun VoiceMessageBubble(
    msg: MessageEntity,
    isMine: Boolean,
    playbackState: VoicePlaybackState,
    theme: ChatBackgroundTheme,
    onTogglePlay: () -> Unit
) {
    val isThisPlaying = playbackState.playingMsgId == msg.msgId && playbackState.isPlaying
    val isThisActive = playbackState.playingMsgId == msg.msgId
    val progress = if (isThisActive) playbackState.progress else 0f
    val bars = listOf(0.35f, 0.72f, 0.52f, 0.9f, 0.65f, 0.42f, 0.84f, 0.58f, 0.95f, 0.48f, 0.75f, 0.6f, 0.88f, 0.4f, 0.7f, 0.5f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.widthIn(min = 210.dp, max = 265.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (isMine) theme.accent else theme.accent.copy(alpha = 0.22f))
                .clickable(onClick = onTogglePlay)
                .testTag("voice_play_${msg.msgId}"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isThisPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isThisPlaying) "Pause voice message" else "Play voice message",
                tint = if (isMine) Color(0xFF04101A) else theme.accent,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.5.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
            ) {
                bars.forEachIndexed { idx, h ->
                    val filled = (idx.toFloat() / bars.size.toFloat()) <= progress
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height((22f * h).dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (filled) theme.accent
                                else TextPrimary.copy(alpha = 0.28f)
                            )
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                val timeLabel = if (isThisActive && playbackState.currentPositionMs > 0) {
                    formatDuration(playbackState.currentPositionMs)
                } else {
                    formatDuration(msg.durationMs)
                }
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
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

@Composable
fun GroupInviteBubble(
    msg: MessageEntity,
    isMine: Boolean,
    theme: ChatBackgroundTheme,
    onJoinClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.Black.copy(alpha = 0.22f),
        modifier = Modifier.widthIn(min = 210.dp, max = 270.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(theme.accent.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Groups,
                        contentDescription = "Group invite",
                        tint = theme.accent
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = msg.groupInviteName ?: "Group Invite",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "LAN Group Invite Link",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onJoinClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.accent,
                    contentColor = Color(0xFF04101A)
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .testTag("join_invite_${msg.msgId}")
            ) {
                Text("Join Group", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
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

@Composable
fun ThemedChatBackground(
    theme: ChatBackgroundTheme,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(theme.bgTop, theme.bgBottom)))
    ) {
        val step = 64.dp.toPx()
        val dotColor = theme.patternColor.copy(alpha = 0.06f)
        val lineColor = theme.patternColor.copy(alpha = 0.035f)
        var y = 0f
        var row = 0
        while (y < size.height + step) {
            var x = if (row % 2 == 0) 0f else step / 2f
            while (x < size.width + step) {
                drawCircle(color = dotColor, radius = 2.5.dp.toPx(), center = Offset(x, y))
                if (row % 3 == 0) {
                    drawLine(
                        color = lineColor,
                        start = Offset(x, y),
                        end = Offset(x + step * 0.5f, y + step),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                x += step
            }
            y += step
            row++
        }
    }
}

fun formatClockTime(ts: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))

fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return String.format(Locale.getDefault(), "%02d:%02d", min, sec)
}
