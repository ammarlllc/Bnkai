package com.example.hotspotbam

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class HotspotBamActivity : ComponentActivity() {
    private lateinit var hotspotController: HotspotController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hotspotController = HotspotController(this)
        enableEdgeToEdge()
        setContent {
            val state by HotspotNodeRuntime.state.collectAsStateWithLifecycle()
            HotspotBamTheme {
                HotspotBamScreen(
                    state = state,
                    onActivate = { startHotspotService() },
                    onDeactivate = { stopHotspotService() },
                    onOpenSystemHotspotSettings = { hotspotController.openSystemHotspotSettings() },
                    onOpenAmmarChat = { launchAmmarChat() }
                )
            }
        }
    }

    private fun startHotspotService() {
        val intent = Intent(this, HotspotRelayService::class.java).apply {
            action = HotspotRelayService.ACTION_START_NODE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopHotspotService() {
        val intent = Intent(this, HotspotRelayService::class.java).apply {
            action = HotspotRelayService.ACTION_STOP_NODE
        }
        startService(intent)
    }

    private fun launchAmmarChat() {
        val launchIntent = packageManager.getLaunchIntentForPackage("com.aistudio.ammarchat.kxmpzq")
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
        } else {
            Toast.makeText(this, "Install Ammar Chat APK to open chat", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun HotspotBamScreen(
    state: HotspotNodeState,
    onActivate: () -> Unit,
    onDeactivate: () -> Unit,
    onOpenSystemHotspotSettings: () -> Unit,
    onOpenAmmarChat: () -> Unit
) {
    val context = LocalContext.current
    val requiredPerms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES, Manifest.permission.ACCESS_FINE_LOCATION)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onActivate()
    }

    val infinite = rememberInfiniteTransition(label = "bam_pulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Scaffold(
        containerColor = ObsidianBg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianSurface)
                    .statusBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.WifiTethering,
                        contentDescription = null,
                        tint = if (state.isNodeActive) EmeraldSignal else ElectricCyan,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "HOTSPOT BAM",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "5GHz Hotspot & Offline LAN Relay",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (state.isNodeActive) EmeraldSignal.copy(alpha = 0.16f) else CrimsonAlert.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = if (state.isNodeActive) "ONLINE" else "OFFLINE",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (state.isNodeActive) EmeraldSignal else CrimsonAlert,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = ObsidianSurface,
                    border = BorderStroke(
                        1.dp,
                        if (state.isNodeActive) EmeraldSignal.copy(alpha = 0.45f) else ObsidianOutline
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(136.dp)
                                .scale(if (state.isNodeActive) pulse else 1f)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        if (state.isNodeActive) {
                                            listOf(EmeraldSignal.copy(alpha = 0.35f), Color(0xFF06261E))
                                        } else {
                                            listOf(ElectricCyan.copy(alpha = 0.2f), ObsidianSurfaceHigh)
                                        }
                                    )
                                )
                                .border(
                                    2.5.dp,
                                    if (state.isNodeActive) EmeraldSignal else ElectricCyan.copy(alpha = 0.6f),
                                    CircleShape
                                )
                                .clickable {
                                    if (state.isNodeActive) {
                                        onDeactivate()
                                    } else {
                                        val allGranted = requiredPerms.all {
                                            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                                        }
                                        if (allGranted) onActivate()
                                        else permLauncher.launch(requiredPerms)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Rounded.PowerSettingsNew,
                                    contentDescription = if (state.isNodeActive) "Stop Hotspot" else "Start Hotspot",
                                    tint = if (state.isNodeActive) EmeraldSignal else ElectricCyan,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = if (state.isNodeActive) "STOP NODE" else "START NODE",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = LanProtocol.TARGET_SSID,
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (state.isNodeActive) EmeraldSignal else TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = state.statusBanner,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )

                        if (state.radioStatus.localHotspotActive && state.radioStatus.passphrase.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = ObsidianSurfaceHigh
                            ) {
                                Text(
                                    text = "OS Local Hotspot SSID: ${state.radioStatus.activeSsid}  •  Key: ${state.radioStatus.passphrase}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricCyan,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BamTelemetryTile(
                        title = "RADIO BAND",
                        value = state.radioStatus.frequencyLabel,
                        icon = Icons.Rounded.CellTower,
                        accent = ElectricCyan,
                        modifier = Modifier.weight(1f)
                    )
                    BamTelemetryTile(
                        title = "LATENCY",
                        value = if (state.isNodeActive) "${state.localPingMs.coerceAtLeast(1L)} ms" else "-- ms",
                        icon = Icons.Rounded.Speed,
                        accent = EmeraldSignal,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BamTelemetryTile(
                        title = "CONNECTED PEERS",
                        value = "${state.relayTelemetry.connectedPeers}",
                        icon = Icons.Rounded.WifiTethering,
                        accent = ElectricCyan,
                        modifier = Modifier.weight(1f)
                    )
                    BamTelemetryTile(
                        title = "HOTSPOT RANGE",
                        value = "6 km",
                        icon = Icons.Rounded.CellTower,
                        accent = EmeraldSignal,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                BamTelemetryTile(
                    title = "LAN IP / PORT",
                    value = "${state.radioStatus.localIp}:${LanProtocol.RELAY_TCP_PORT}",
                    icon = Icons.Rounded.CellTower,
                    accent = AmberWarn,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenSystemHotspotSettings,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("System Hotspot")
                    }

                    Button(
                        onClick = onOpenAmmarChat,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = Color(0xFF04101A)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open Chat", fontWeight = FontWeight.Bold)
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ObsidianSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Multi-device LAN setup: Set your system Portable Hotspot SSID to AMMAR-HACKER-5G (5 GHz band, 6 km hotspot range profile) or start Local Node above. Turning Node OFF blocks all chat sending.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            item {
                Text(
                    text = "LIVE LAN RELAY LOG (${state.relayTelemetry.packetsRelayed} packets)",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }

            if (state.relayTelemetry.recentLogs.isEmpty()) {
                item {
                    Text(
                        text = "No relay events yet. Tap START NODE to open TCP/UDP discovery.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            } else {
                items(state.relayTelemetry.recentLogs) { log ->
                    Text(
                        text = "• $log",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan.copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BamTelemetryTile(
    title: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ObsidianSurface,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(title, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
