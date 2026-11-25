package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CobaltAccent
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun SplashCreditsScreen(
    totalDurationSeconds: Int = 15,
    onFinished: () -> Unit
) {
    var elapsedMs by remember { mutableIntStateOf(0) }
    val totalMs = totalDurationSeconds * 1000

    LaunchedEffect(totalDurationSeconds) {
        val step = 100
        while (elapsedMs < totalMs) {
            delay(step.toLong())
            elapsedMs += step
        }
        onFinished()
    }

    val progress = (elapsedMs.toFloat() / totalMs.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    val remainingSec = ((totalMs - elapsedMs + 999) / 1000).coerceAtLeast(0)

    val infinite = rememberInfiniteTransition(label = "splash_anim")
    val orbitAngle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing)),
        label = "orbit"
    )
    val pulseScale by infinite.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val smoothProgress by animateFloatAsState(targetValue = progress, label = "prog")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF0E2342), ObsidianBg, Color(0xFF03060B)),
                    radius = 1400f
                )
            )
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .testTag("splash_credits_screen")
    ) {
        // Animated Radar / Mesh Background
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height * 0.28f)
            val radii = listOf(88.dp.toPx(), 132.dp.toPx(), 178.dp.toPx())
            radii.forEachIndexed { idx, r ->
                drawCircle(
                    color = ElectricCyan.copy(alpha = 0.14f - idx * 0.03f),
                    radius = r * pulseScale,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
            drawArc(
                color = ElectricCyan.copy(alpha = 0.65f),
                startAngle = orbitAngle,
                sweepAngle = 75f,
                useCenter = false,
                topLeft = Offset(center.x - radii[1], center.y - radii[1]),
                size = androidx.compose.ui.geometry.Size(radii[1] * 2, radii[1] * 2),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = CobaltAccent.copy(alpha = 0.6f),
                startAngle = -orbitAngle * 1.3f,
                sweepAngle = 95f,
                useCenter = false,
                topLeft = Offset(center.x - radii[2], center.y - radii[2]),
                size = androidx.compose.ui.geometry.Size(radii[2] * 2, radii[2] * 2),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LAN MESH PROTOCOL v5.0",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan.copy(alpha = 0.85f)
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = ObsidianSurface.copy(alpha = 0.85f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = "${remainingSec}s",
                        style = MaterialTheme.typography.labelMedium,
                        color = ElectricCyan,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(ElectricCyan, CobaltAccent))
                        )
                        .border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = "Ammar Chat emblem",
                        tint = Color(0xFF04101A),
                        modifier = Modifier.size(48.dp)
                    )
                }

                Spacer(Modifier.height(22.dp))

                Text(
                    text = "AMMAR CHAT",
                    style = MaterialTheme.typography.displayLarge,
                    color = TextPrimary,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "OFFLINE 5GHz HOTSPOT & LAN MESSENGER",
                    style = MaterialTheme.typography.labelMedium,
                    color = ElectricCyan,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(28.dp))

                AnimatedVisibility(
                    visible = elapsedMs >= 600,
                    enter = fadeIn(tween(600)) + slideInVertically { 24 }
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = ObsidianSurface.copy(alpha = 0.88f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            Brush.linearGradient(
                                listOf(ElectricCyan.copy(alpha = 0.5f), CobaltAccent.copy(alpha = 0.3f))
                            )
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "DEVELOPED & ENGINEERED BY",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                letterSpacing = 1.5.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Λ௱௱Λ尺 н4ㄈҚ3尺",
                                style = MaterialTheme.typography.headlineMedium,
                                color = ElectricCyan,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(18.dp))

                            CreditBadgeRow(
                                visible = elapsedMs >= 1600,
                                icon = Icons.Rounded.Palette,
                                title = "App UI / UX Design",
                                author = "Λ௱௱Λ尺 н4ㄈҚ3尺"
                            )
                            Spacer(Modifier.height(10.dp))
                            CreditBadgeRow(
                                visible = elapsedMs >= 3200,
                                icon = Icons.Rounded.Code,
                                title = "Core Code & Real-Time Engine",
                                author = "Λ௱௱Λ尺 н4ㄈҚ3尺"
                            )
                            Spacer(Modifier.height(10.dp))
                            CreditBadgeRow(
                                visible = elapsedMs >= 4800,
                                icon = Icons.Rounded.Storage,
                                title = "LAN Backend & Hotspot Relay",
                                author = "Λ௱௱Λ尺 н4ㄈҚ3尺"
                            )
                            Spacer(Modifier.height(10.dp))
                            CreditBadgeRow(
                                visible = elapsedMs >= 6400,
                                icon = Icons.Rounded.Security,
                                title = "Security & Group Architecture",
                                author = "Λ௱௱Λ尺 н4ㄈҚ3尺"
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val stageText = when {
                    elapsedMs < 3500 -> "Initializing offline LAN mesh core..."
                    elapsedMs < 7500 -> "Mounting encrypted voice & message pipeline..."
                    elapsedMs < 11500 -> "Binding AMMAR-HACKER-5G auto-discovery..."
                    else -> "Ready. Launching Ammar Chat..."
                }
                Text(
                    text = stageText,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.alpha(0.9f)
                )
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { smoothProgress },
                    color = ElectricCyan,
                    trackColor = ObsidianSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                )
            }
        }
    }
}

@Composable
private fun CreditBadgeRow(
    visible: Boolean,
    icon: ImageVector,
    title: String,
    author: String
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(500)) + slideInVertically { 16 }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.04f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = EmeraldSignal,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Text(
                    text = author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
