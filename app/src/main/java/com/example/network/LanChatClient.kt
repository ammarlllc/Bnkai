package com.example.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.example.data.local.SessionPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.Socket

data class LanConnectionState(
    val isConnected: Boolean = false,
    val isHotspotActive: Boolean = false,
    val nodeIp: String = "",
    val ssid: String = LanProtocol.TARGET_SSID,
    val pingMs: Long = 0L,
    val bandLabel: String = "Offline"
)

class LanChatClient(
    private val context: Context,
    private val scope: CoroutineScope,
    private val prefs: SessionPreferences
) {
    private val hotspotController = HotspotController(context)

    private val _connectionState = MutableStateFlow(LanConnectionState())
    val connectionState: StateFlow<LanConnectionState> = _connectionState.asStateFlow()

    private val _incomingPackets = MutableSharedFlow<JSONObject>(extraBufferCapacity = 128)
    val incomingPackets: SharedFlow<JSONObject> = _incomingPackets.asSharedFlow()

    @Volatile private var activeSocket: Socket? = null
    @Volatile private var activeWriter: BufferedWriter? = null
    @Volatile private var discoveredHostIp: String? = null
    @Volatile private var currentHelloUserJson: JSONObject? = null

    private var connectionLoopJob: Job? = null
    private var udpListenerJob: Job? = null
    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun startAutoConnect(helloUserProvider: () -> JSONObject?) {
        hotspotController.registerAutoConnectSuggestion()
        startUdpDiscovery()
        startNsdDiscovery()

        connectionLoopJob?.cancel()
        connectionLoopJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                currentHelloUserJson = helloUserProvider()
                val localNodeOn = HotspotNodeRuntime.state.value.isNodeActive ||
                    runCatching { prefs.hotspotNodeActiveFlow.first() }.getOrDefault(false)

                val candidateIps = buildList {
                    if (localNodeOn) add("127.0.0.1")
                    discoveredHostIp?.let { add(it) }
                    // Common Android Wi-Fi Hotspot / Wi-Fi Direct gateway IPs
                    add("192.168.43.1")
                    add("192.168.49.1")
                }.distinct()

                if (activeSocket == null || activeSocket?.isClosed == true) {
                    var connected = false
                    for (ip in candidateIps) {
                        if (ip == "127.0.0.1" && !localNodeOn) continue
                        if (tryConnect(ip)) {
                            connected = true
                            break
                        }
                    }
                    if (!connected) {
                        _connectionState.value = LanConnectionState(
                            isConnected = false,
                            isHotspotActive = false,
                            ssid = LanProtocol.TARGET_SSID,
                            bandLabel = "Hotspot Offline"
                        )
                    }
                } else {
                    // Check if local node was turned off while connected via loopback
                    val sockIp = activeSocket?.inetAddress?.hostAddress
                    if ((sockIp == "127.0.0.1" || sockIp == "localhost") && !HotspotNodeRuntime.state.value.isNodeActive) {
                        disconnectSocket()
                    } else {
                        sendPacket(LanProtocol.packet(LanProtocol.TYPE_PING))
                    }
                }
                delay(1800)
            }
        }
    }

    fun updateHelloUser(userJson: JSONObject?) {
        currentHelloUserJson = userJson
        if (userJson != null && activeSocket != null && activeSocket?.isClosed == false) {
            sendPacket(LanProtocol.packet(LanProtocol.TYPE_HELLO) { put("user", userJson) })
        }
    }

    private fun tryConnect(ip: String): Boolean {
        return runCatching {
            val socket = Socket()
            socket.connect(InetSocketAddress(ip, LanProtocol.RELAY_TCP_PORT), 700)
            socket.tcpNoDelay = true
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))

            activeSocket = socket
            activeWriter = writer

            val radio = hotspotController.readRadioTelemetry()
            _connectionState.value = LanConnectionState(
                isConnected = true,
                isHotspotActive = true,
                nodeIp = ip,
                ssid = LanProtocol.TARGET_SSID,
                pingMs = 1L,
                bandLabel = radio.frequencyLabel
            )

            currentHelloUserJson?.let { userJson ->
                sendPacket(LanProtocol.packet(LanProtocol.TYPE_HELLO) { put("user", userJson) })
            }
            sendPacket(LanProtocol.packet(LanProtocol.TYPE_PING))

            scope.launch(Dispatchers.IO) {
                try {
                    while (isActive && !socket.isClosed) {
                        val line = reader.readLine() ?: break
                        if (line.isBlank()) continue
                        val json = runCatching { JSONObject(line) }.getOrNull() ?: continue
                        when (json.optString("type")) {
                            LanProtocol.TYPE_PONG -> {
                                val echo = json.optLong("echoTs", System.currentTimeMillis())
                                val rtt = (System.currentTimeMillis() - echo).coerceAtLeast(1L)
                                _connectionState.value = _connectionState.value.copy(pingMs = rtt)
                            }
                            LanProtocol.TYPE_NODE_OFFLINE -> {
                                disconnectSocket()
                                break
                            }
                            else -> _incomingPackets.tryEmit(json)
                        }
                    }
                } catch (_: Throwable) {
                } finally {
                    if (activeSocket === socket) {
                        disconnectSocket()
                    }
                }
            }
            true
        }.getOrDefault(false)
    }

    fun sendPacket(json: JSONObject): Boolean {
        val writer = activeWriter ?: return false
        if (activeSocket?.isClosed != false) return false
        return runCatching {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    synchronized(writer) {
                        writer.write(json.toString())
                        writer.newLine()
                        writer.flush()
                    }
                }.onFailure {
                    disconnectSocket()
                }
            }
            true
        }.getOrDefault(false)
    }

    private fun disconnectSocket() {
        runCatching { activeSocket?.close() }
        activeSocket = null
        activeWriter = null
        _connectionState.value = LanConnectionState(
            isConnected = false,
            isHotspotActive = false,
            ssid = LanProtocol.TARGET_SSID,
            bandLabel = "Hotspot Offline"
        )
    }

    private fun startUdpDiscovery() {
        udpListenerJob?.cancel()
        udpListenerJob = scope.launch(Dispatchers.IO) {
            runCatching {
                val sock = DatagramSocket(LanProtocol.DISCOVERY_UDP_PORT).apply {
                    reuseAddress = true
                    broadcast = true
                }
                val buf = ByteArray(1024)
                while (isActive) {
                    val pkt = DatagramPacket(buf, buf.size)
                    sock.receive(pkt)
                    val msg = String(pkt.data, 0, pkt.length, Charsets.UTF_8)
                    if (msg.startsWith(LanProtocol.UDP_BEACON_PREFIX)) {
                        val parts = msg.split("|")
                        val status = parts.getOrNull(1)
                        if (status == "ONLINE") {
                            discoveredHostIp = pkt.address.hostAddress
                        } else if (status == "OFFLINE") {
                            if (pkt.address.hostAddress == discoveredHostIp) {
                                discoveredHostIp = null
                            }
                            disconnectSocket()
                        }
                    }
                }
                sock.close()
            }
        }
    }

    private fun startNsdDiscovery() {
        runCatching {
            val mgr = context.getSystemService(Context.NSD_SERVICE) as NsdManager
            nsdManager = mgr
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {}
                override fun onServiceFound(service: NsdServiceInfo) {
                    if (service.serviceType.contains("ammarchat")) {
                        runCatching {
                            mgr.resolveService(service, object : NsdManager.ResolveListener {
                                override fun onResolveFailed(info: NsdServiceInfo, code: Int) {}
                                override fun onServiceResolved(info: NsdServiceInfo) {
                                    @Suppress("DEPRECATION")
                                    val host = info.host?.hostAddress
                                    if (!host.isNullOrBlank()) {
                                        discoveredHostIp = host
                                    }
                                }
                            })
                        }
                    }
                }
                override fun onServiceLost(service: NsdServiceInfo) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            }
            discoveryListener = listener
            mgr.discoverServices(LanProtocol.NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    fun stop() {
        connectionLoopJob?.cancel()
        udpListenerJob?.cancel()
        runCatching { discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) } }
        disconnectSocket()
    }
}
