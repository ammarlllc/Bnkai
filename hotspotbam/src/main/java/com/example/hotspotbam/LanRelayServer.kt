package com.example.hotspotbam

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
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

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
    private val recentChatPackets = CopyOnWriteArrayList<JSONObject>()
    private val seenMsgIds = ConcurrentHashMap.newKeySet<String>()

    @Volatile private var currentThemeId: String = "hacker"
    @Volatile private var currentCustomThemeBase64: String = ""

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
        appendLog("Starting HOTSPOT BAM Relay on TCP:${LanProtocol.RELAY_TCP_PORT} & UDP:${LanProtocol.DISCOVERY_UDP_PORT}")

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
        appendLog("Stopping HOTSPOT BAM Relay & disconnecting peers")
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
                    val targets = collectBroadcastAddresses()
                    targets.forEach { addr ->
                        runCatching {
                            sock.send(DatagramPacket(data, data.size, addr, LanProtocol.DISCOVERY_UDP_PORT))
                        }
                    }
                    delay(1200)
                }
            }
        }
    }

    private fun collectBroadcastAddresses(): List<InetAddress> {
        val list = mutableSetOf<InetAddress>()
        runCatching { list.add(InetAddress.getByName("255.255.255.255")) }
        runCatching {
            val intfs = NetworkInterface.getNetworkInterfaces()
            while (intfs != null && intfs.hasMoreElements()) {
                val ni = intfs.nextElement()
                if (!ni.isUp || ni.isLoopback) continue
                for (ia in ni.interfaceAddresses) {
                    ia.broadcast?.let { list.add(it) }
                }
            }
        }
        return list.toList()
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
                val stillConnected = clients.values.any { it.userId == conn.userId }
                if (!stillConnected) {
                    knownDirectory[conn.userId]?.put("isOnline", false)
                    broadcastDirectory()
                }
                appendLog("Peer disconnected: @${conn.publicUserJson?.optString("username") ?: conn.userId}")
            }
            runCatching { socket.close() }
            updatePeerCount()
        }
    }

    private fun processPacket(sender: ClientConn, json: JSONObject) {
        when (json.optString("type")) {
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

                val prev = knownDirectory[uid]
                if (prev != null) {
                    if (prev.optBoolean("isBanned", false)) {
                        userObj.put("isBanned", true)
                        userObj.put("banExpiresAt", prev.optLong("banExpiresAt", -1L))
                    }
                    val prevBadge = prev.optString("badgeType", "NONE")
                    if (prevBadge != "NONE" && userObj.optString("badgeType", "NONE") == "NONE") {
                        userObj.put("badgeType", prevBadge)
                        userObj.put("hasBlueTick", prev.optBoolean("hasBlueTick", false))
                    }
                }
                userObj.put("isOnline", true)
                sender.publicUserJson = userObj
                knownDirectory[uid] = userObj

                appendLog("Peer joined: @${userObj.optString("username")} (${sender.socket.inetAddress.hostAddress})")
                broadcastDirectory()
                sendDirectoryGroupsAndMissedMsgsTo(sender, uid)
            }

            LanProtocol.TYPE_THEME_SYNC -> {
                currentThemeId = json.optString("themeId", currentThemeId)
                currentCustomThemeBase64 = json.optString("customBgBase64", "")
                broadcastAll(json.toString())
                appendLog("Theme synced: $currentThemeId")
            }

            LanProtocol.TYPE_MOD_BAN -> {
                val targetId = json.optString("targetUserId")
                val banned = json.optBoolean("isBanned", false)
                val expiresAt = json.optLong("banExpiresAt", if (banned) -1L else 0L)
                knownDirectory[targetId]?.apply {
                    put("isBanned", banned)
                    put("banExpiresAt", expiresAt)
                }
                broadcastAll(json.toString())
                broadcastDirectory()
                appendLog("Moderation update for $targetId -> banned=$banned")
            }

            LanProtocol.TYPE_BADGE_SYNC -> {
                val targetId = json.optString("targetUserId")
                val badgeType = json.optString("badgeType", "NONE")
                val hasBlue = badgeType == "BLUE"
                val stickerB64 = json.optString("stickerVideoBase64", "")
                knownDirectory[targetId]?.apply {
                    put("badgeType", badgeType)
                    put("hasBlueTick", hasBlue)
                    if (stickerB64.isNotBlank()) put("stickerVideoBase64", stickerB64)
                }
                broadcastAll(json.toString())
                broadcastDirectory()
                appendLog("Badge update for $targetId -> $badgeType")
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

            LanProtocol.TYPE_GROUP_JOIN_REQUEST -> {
                val gid = json.optString("groupId")
                val token = json.optString("token")
                val reqUserId = json.optString("userId")
                val grp = groupsCache[gid]
                if (grp != null && reqUserId.isNotBlank()) {
                    val isPublic = grp.optBoolean("isPublic", true)
                    val grpToken = grp.optString("inviteToken", "")
                    if (isPublic || (token.isNotBlank() && token == grpToken)) {
                        val members = LanProtocol.jsonArrayToStringList(grp.optJSONArray("memberIds")).toMutableSet()
                        members.add(reqUserId)
                        grp.put("memberIds", LanProtocol.stringListToJsonArray(members.toList()))
                        val syncPkt = LanProtocol.packet(LanProtocol.TYPE_GROUP_SYNC) { put("group", grp) }
                        broadcastAll(syncPkt.toString())
                        appendLog("Joined group $gid: $reqUserId")
                    }
                }
            }

            LanProtocol.TYPE_GROUP_KICK -> {
                val gid = json.optString("groupId")
                val targetId = json.optString("targetUserId")
                groupsCache[gid]?.let { grp ->
                    val members = LanProtocol.jsonArrayToStringList(grp.optJSONArray("memberIds"))
                        .filterNot { it == targetId }
                    val admins = LanProtocol.jsonArrayToStringList(grp.optJSONArray("adminIds"))
                        .filterNot { it == targetId }
                    grp.put("memberIds", LanProtocol.stringListToJsonArray(members))
                    grp.put("adminIds", LanProtocol.stringListToJsonArray(admins))
                }
                broadcastAll(json.toString())
                appendLog("Removed $targetId from group $gid")
            }

            LanProtocol.TYPE_CHAT_MSG -> {
                val msgId = json.optString("msgId")
                if (msgId.isNotBlank() && seenMsgIds.add(msgId)) {
                    recentChatPackets.add(json)
                    while (recentChatPackets.size > 300) {
                        recentChatPackets.removeAt(0)
                    }
                }
                broadcastExcept(sender, json.toString())
                incrementPackets()
            }

            LanProtocol.TYPE_MSG_ACK,
            LanProtocol.TYPE_PRESENCE -> {
                broadcastExcept(sender, json.toString())
                incrementPackets()
            }
        }
    }

    private fun sendDirectoryGroupsAndMissedMsgsTo(target: ClientConn, uid: String) {
        val dirPacket = LanProtocol.packet(LanProtocol.TYPE_DIRECTORY) {
            val arr = JSONArray()
            knownDirectory.values.forEach { arr.put(it) }
            put("users", arr)
            put("themeId", currentThemeId)
            put("customBgBase64", currentCustomThemeBase64)
        }
        sendToClient(target, dirPacket.toString())

        groupsCache.values.forEach { grp ->
            val pkt = LanProtocol.packet(LanProtocol.TYPE_GROUP_SYNC) {
                put("group", grp)
            }
            sendToClient(target, pkt.toString())
        }

        recentChatPackets.forEach { msgJson ->
            val isGroup = msgJson.optBoolean("isGroup", false)
            val senderId = msgJson.optString("senderId")
            val recipientId = msgJson.optString("recipientId")
            if (isGroup || recipientId == uid || senderId == uid) {
                sendToClient(target, msgJson.toString())
            }
        }
    }

    private fun broadcastDirectory() {
        val dirPacket = LanProtocol.packet(LanProtocol.TYPE_DIRECTORY) {
            val arr = JSONArray()
            knownDirectory.values.forEach { arr.put(it) }
            put("users", arr)
            put("themeId", currentThemeId)
            put("customBgBase64", currentCustomThemeBase64)
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
