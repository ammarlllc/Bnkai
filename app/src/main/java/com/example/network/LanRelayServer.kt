package com.example.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

data class RelayServerTelemetry(
    val isRunning: Boolean = false,
    val connectedPeers: Int = 0,
    val packetsRelayed: Long = 0L,
    val lastMeasuredRttMs: Long = 0L,
    val recentLogs: List<String> = emptyList()
)

class LanRelayServer(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private class ClientConn(
        val socket: Socket,
        val writer: BufferedWriter,
        @Volatile var userId: String = "",
        @Volatile var publicUserJson: JSONObject? = null
    )

    private val clients = ConcurrentHashMap<String, ClientConn>()
    private val knownDirectory = ConcurrentHashMap<String, JSONObject>()
    private val groupsCache = ConcurrentHashMap<String, JSONObject>()
    @Volatile private var currentThemeId: String = "obsidian_cyber"

    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private var beaconJob: Job? = null
    private var udpSocket: DatagramSocket? = null
    private var nsdManager: NsdManager? = null
    private var nsdListener: NsdManager.RegistrationListener? = null

    private val _telemetry = MutableStateFlow(RelayServerTelemetry())
    val telemetry: StateFlow<RelayServerTelemetry> = _telemetry.asStateFlow()

    fun start(ssidProvider: () -> String, bandProvider: () -> String) {
        if (_telemetry.value.isRunning) return
        appendLog("Starting LAN Relay on TCP:${LanProtocol.RELAY_TCP_PORT} & UDP:${LanProtocol.DISCOVERY_UDP_PORT}")

        acceptJob = scope.launch(Dispatchers.IO) {
            try {
                val ss = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(LanProtocol.RELAY_TCP_PORT))
                }
                serverSocket = ss
                _telemetry.value = _telemetry.value.copy(isRunning = true)
                registerNsd()
                startUdpBeacon(ssidProvider, bandProvider)

                while (isActive && !ss.isClosed) {
                    val socket = ss.accept()
                    socket.tcpNoDelay = true
                    val connKey = "${socket.inetAddress.hostAddress}:${socket.port}:${System.nanoTime()}"
                    launch(Dispatchers.IO) {
                        handleClient(connKey, socket)
                    }
                }
            } catch (t: Throwable) {
                if (_telemetry.value.isRunning) {
                    appendLog("Relay server stopped: ${t.message}")
                }
            } finally {
                _telemetry.value = _telemetry.value.copy(isRunning = false, connectedPeers = 0)
            }
        }
    }

    fun stop() {
        appendLog("Stopping LAN Relay & disconnecting peers")
        val offlineMsg = LanProtocol.packet(LanProtocol.TYPE_NODE_OFFLINE).toString()
        clients.values.forEach { conn ->
            runCatching {
                synchronized(conn.writer) {
                    conn.writer.write(offlineMsg)
                    conn.writer.newLine()
                    conn.writer.flush()
                }
            }
            runCatching { conn.socket.close() }
        }
        clients.clear()

        beaconJob?.cancel()
        beaconJob = null
        runCatching {
            val broadcast = "${LanProtocol.UDP_BEACON_PREFIX}|OFFLINE".toByteArray()
            val pkt = DatagramPacket(
                broadcast,
                broadcast.size,
                InetAddress.getByName("255.255.255.255"),
                LanProtocol.DISCOVERY_UDP_PORT
            )
            udpSocket?.send(pkt)
        }
        runCatching { udpSocket?.close() }
        udpSocket = null

        unregisterNsd()
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null

        _telemetry.value = _telemetry.value.copy(isRunning = false, connectedPeers = 0)
    }

    private fun startUdpBeacon(ssidProvider: () -> String, bandProvider: () -> String) {
        beaconJob?.cancel()
        beaconJob = scope.launch(Dispatchers.IO) {
            runCatching {
                val sock = DatagramSocket().apply { broadcast = true }
                udpSocket = sock
                while (isActive && _telemetry.value.isRunning) {
                    val payload = "${LanProtocol.UDP_BEACON_PREFIX}|ONLINE|${ssidProvider()}|${LanProtocol.RELAY_TCP_PORT}|${bandProvider()}"
                    val data = payload.toByteArray()
                    runCatching {
                        sock.send(
                            DatagramPacket(
                                data,
                                data.size,
                                InetAddress.getByName("255.255.255.255"),
                                LanProtocol.DISCOVERY_UDP_PORT
                            )
                        )
                    }
                    delay(1200)
                }
            }
        }
    }

    private fun handleClient(connKey: String, socket: Socket) {
        val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
        val conn = ClientConn(socket, writer)
        clients[connKey] = conn
        updatePeerCount()

        try {
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue
                val json = runCatching { JSONObject(line) }.getOrNull() ?: continue
                processPacket(conn, json)
            }
        } catch (_: Throwable) {
        } finally {
            clients.remove(connKey)
            if (conn.userId.isNotEmpty()) {
                knownDirectory[conn.userId]?.put("isOnline", false)
                broadcastDirectory()
                appendLog("Peer disconnected: @${conn.publicUserJson?.optString("username") ?: conn.userId}")
            }
            runCatching { socket.close() }
            updatePeerCount()
        }
    }

    private fun processPacket(sender: ClientConn, json: JSONObject) {
        val type = json.optString("type")
        when (type) {
            LanProtocol.TYPE_PING -> {
                val pong = LanProtocol.packet(LanProtocol.TYPE_PONG) {
                    put("echoTs", json.optLong("ts", System.currentTimeMillis()))
                }
                sendToClient(sender, pong.toString())
                val delta = (System.currentTimeMillis() - json.optLong("ts", System.currentTimeMillis())).coerceAtLeast(1L)
                _telemetry.value = _telemetry.value.copy(lastMeasuredRttMs = delta)
            }

            LanProtocol.TYPE_HELLO -> {
                val userObj = json.optJSONObject("user") ?: return
                val uid = userObj.optString("userId")
                if (uid.isBlank()) return
                sender.userId = uid
                val existingBan = knownDirectory[uid]?.optBoolean("isBanned", false) ?: false
                userObj.put("isOnline", true)
                if (existingBan) userObj.put("isBanned", true)
                sender.publicUserJson = userObj
                knownDirectory[uid] = userObj

                appendLog("Peer joined: @${userObj.optString("username")} (${sender.socket.inetAddress.hostAddress})")
                broadcastDirectory()
                sendDirectoryAndGroupsTo(sender)
            }

            LanProtocol.TYPE_THEME_SYNC -> {
                currentThemeId = json.optString("themeId", currentThemeId)
                broadcastAll(json.toString())
                appendLog("Owner synced theme: $currentThemeId")
            }

            LanProtocol.TYPE_MOD_BAN -> {
                val targetId = json.optString("targetUserId")
                val banned = json.optBoolean("isBanned", false)
                knownDirectory[targetId]?.put("isBanned", banned)
                broadcastAll(json.toString())
                broadcastDirectory()
                appendLog("Moderation update for $targetId -> banned=$banned")
            }

            LanProtocol.TYPE_GROUP_SYNC -> {
                val groupObj = json.optJSONObject("group")
                val gid = groupObj?.optString("groupId")
                if (!gid.isNullOrBlank()) {
                    groupsCache[gid] = groupObj
                }
                broadcastAll(json.toString())
                appendLog("Group synced: ${groupObj?.optString("name") ?: gid}")
            }

            LanProtocol.TYPE_GROUP_KICK -> {
                val gid = json.optString("groupId")
                val targetId = json.optString("targetUserId")
                groupsCache[gid]?.let { grp ->
                    val members = LanProtocol.jsonArrayToStringList(grp.optJSONArray("memberIds"))
                        .filterNot { it == targetId }
                    grp.put("memberIds", LanProtocol.stringListToJsonArray(members))
                }
                broadcastAll(json.toString())
                appendLog("Removed $targetId from group $gid")
            }

            LanProtocol.TYPE_CHAT_MSG,
            LanProtocol.TYPE_MSG_ACK,
            LanProtocol.TYPE_PRESENCE -> {
                broadcastExcept(sender, json.toString())
                incrementPackets()
            }
        }
    }

    private fun sendDirectoryAndGroupsTo(target: ClientConn) {
        val dirPacket = LanProtocol.packet(LanProtocol.TYPE_DIRECTORY) {
            val arr = JSONArray()
            knownDirectory.values.forEach { arr.put(it) }
            put("users", arr)
            put("themeId", currentThemeId)
        }
        sendToClient(target, dirPacket.toString())

        groupsCache.values.forEach { grp ->
            val pkt = LanProtocol.packet(LanProtocol.TYPE_GROUP_SYNC) {
                put("group", grp)
            }
            sendToClient(target, pkt.toString())
        }
    }

    private fun broadcastDirectory() {
        val dirPacket = LanProtocol.packet(LanProtocol.TYPE_DIRECTORY) {
            val arr = JSONArray()
            knownDirectory.values.forEach { arr.put(it) }
            put("users", arr)
            put("themeId", currentThemeId)
        }
        broadcastAll(dirPacket.toString())
    }

    private fun broadcastAll(raw: String) {
        clients.values.forEach { sendToClient(it, raw) }
        incrementPackets()
    }

    private fun broadcastExcept(exclude: ClientConn, raw: String) {
        clients.values.forEach { conn ->
            if (conn !== exclude) sendToClient(conn, raw)
        }
    }

    private fun sendToClient(conn: ClientConn, raw: String) {
        runCatching {
            synchronized(conn.writer) {
                conn.writer.write(raw)
                conn.writer.newLine()
                conn.writer.flush()
            }
        }
    }

    private fun updatePeerCount() {
        _telemetry.value = _telemetry.value.copy(connectedPeers = clients.size)
    }

    private fun incrementPackets() {
        _telemetry.value = _telemetry.value.copy(packetsRelayed = _telemetry.value.packetsRelayed + 1)
    }

    private fun appendLog(msg: String) {
        val current = _telemetry.value.recentLogs
        _telemetry.value = _telemetry.value.copy(recentLogs = (listOf(msg) + current).take(25))
    }

    private fun registerNsd() {
        runCatching {
            val mgr = context.getSystemService(Context.NSD_SERVICE) as NsdManager
            nsdManager = mgr
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = LanProtocol.NSD_SERVICE_NAME
                serviceType = LanProtocol.NSD_SERVICE_TYPE
                port = LanProtocol.RELAY_TCP_PORT
            }
            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) {
                    appendLog("NSD registered: ${info.serviceName}")
                }
                override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
                override fun onServiceUnregistered(info: NsdServiceInfo) {}
                override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
            }
            nsdListener = listener
            mgr.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    private fun unregisterNsd() {
        runCatching {
            nsdListener?.let { nsdManager?.unregisterService(it) }
        }
        nsdListener = null
    }
}
