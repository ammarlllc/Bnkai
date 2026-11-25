package com.example.hotspotbam

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class HotspotNodeState(
    val isNodeActive: Boolean = false,
    val radioStatus: HotspotRadioStatus = HotspotRadioStatus(),
    val relayTelemetry: RelayServerTelemetry = RelayServerTelemetry(),
    val localPingMs: Long = 0L,
    val statusBanner: String = "Standby — tap START NODE to activate AMMAR-HACKER-5G"
)

object HotspotNodeRuntime {
    private val _state = MutableStateFlow(HotspotNodeState())
    val state: StateFlow<HotspotNodeState> = _state.asStateFlow()

    fun update(transform: (HotspotNodeState) -> HotspotNodeState) {
        _state.value = transform(_state.value)
    }
}

class HotspotRelayService : Service() {

    companion object {
        const val ACTION_START_NODE = "com.example.hotspotbam.action.START_NODE"
        const val ACTION_STOP_NODE = "com.example.hotspotbam.action.STOP_NODE"
        private const val CHANNEL_ID = "hotspot_bam_node_channel"
        private const val NOTIFICATION_ID = 5571
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var hotspotController: HotspotController
    private lateinit var relayServer: LanRelayServer
    private var telemetryJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        hotspotController = HotspotController(this)
        relayServer = LanRelayServer(this, serviceScope)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_NODE -> {
                stopNode()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForegroundNotification()
                startNode()
            }
        }
        return START_STICKY
    }

    private fun startNode() {
        val radio = hotspotController.readRadioTelemetry()
        HotspotNodeRuntime.update {
            it.copy(
                isNodeActive = true,
                radioStatus = radio,
                statusBanner = "AMMAR-HACKER-5G LAN Relay active"
            )
        }

        relayServer.start(
            ssidProvider = { HotspotNodeRuntime.state.value.radioStatus.activeSsid },
            bandProvider = { HotspotNodeRuntime.state.value.radioStatus.frequencyLabel }
        )

        hotspotController.startLocalHotspot(
            onStarted = { startedRadio ->
                HotspotNodeRuntime.update {
                    it.copy(
                        isNodeActive = true,
                        radioStatus = startedRadio,
                        statusBanner = "5GHz Local Hotspot + LAN Relay active (${startedRadio.activeSsid})"
                    )
                }
            },
            onError = { err ->
                HotspotNodeRuntime.update {
                    it.copy(
                        isNodeActive = true,
                        radioStatus = it.radioStatus.copy(infoMessage = err),
                        statusBanner = "LAN Relay active • System Hotspot/Wi-Fi mode ($err)"
                    )
                }
            }
        )

        telemetryJob?.cancel()
        telemetryJob = serviceScope.launch {
            relayServer.telemetry.collect { tel ->
                HotspotNodeRuntime.update { it.copy(relayTelemetry = tel) }
            }
        }

        serviceScope.launch(Dispatchers.IO) {
            while (isActive && HotspotNodeRuntime.state.value.isNodeActive) {
                val freshRadio = hotspotController.readRadioTelemetry()
                val ping = measureLoopbackPing()
                HotspotNodeRuntime.update { cur ->
                    cur.copy(
                        radioStatus = cur.radioStatus.copy(
                            frequencyLabel = freshRadio.frequencyLabel,
                            linkSpeedMbps = freshRadio.linkSpeedMbps,
                            localIp = freshRadio.localIp
                        ),
                        localPingMs = ping
                    )
                }
                delay(2000)
            }
        }
    }

    private fun measureLoopbackPing(): Long {
        return runCatching {
            val t0 = System.nanoTime()
            java.net.Socket().use { sock ->
                sock.connect(java.net.InetSocketAddress("127.0.0.1", LanProtocol.RELAY_TCP_PORT), 600)
            }
            ((System.nanoTime() - t0) / 1_000_000L).coerceAtLeast(1L)
        }.getOrDefault(0L)
    }

    private fun stopNode() {
        telemetryJob?.cancel()
        hotspotController.stopLocalHotspot()
        relayServer.stop()
        HotspotNodeRuntime.update {
            HotspotNodeState(
                isNodeActive = false,
                radioStatus = hotspotController.readRadioTelemetry().copy(localHotspotActive = false),
                statusBanner = "Hotspot & LAN Relay OFF — chat messages blocked"
            )
        }
    }

    private fun startForegroundNotification() {
        val openIntent = Intent(this, HotspotBamActivity::class.java)
        val pending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("HOTSPOT BAM • ${LanProtocol.TARGET_SSID}")
            .setContentText("Offline LAN Chat Relay & Hotspot active")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(
                CHANNEL_ID,
                "HOTSPOT BAM Node",
                NotificationManager.IMPORTANCE_LOW
            )
            mgr.createNotificationChannel(ch)
        }
    }

    override fun onDestroy() {
        stopNode()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
