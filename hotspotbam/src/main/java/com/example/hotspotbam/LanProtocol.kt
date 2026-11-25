package com.example.hotspotbam

import org.json.JSONArray
import org.json.JSONObject

object LanProtocol {
    const val TARGET_SSID = "AMMAR-HACKER-5G"
    const val DISCOVERY_UDP_PORT = 45570
    const val RELAY_TCP_PORT = 45571
    const val NSD_SERVICE_TYPE = "_ammarchat._tcp."
    const val NSD_SERVICE_NAME = "AMMAR-HACKER-5G"
    const val UDP_BEACON_PREFIX = "AMMAR_NODE_BEACON"

    const val TYPE_HELLO = "HELLO"
    const val TYPE_DIRECTORY = "DIRECTORY"
    const val TYPE_CHAT_MSG = "CHAT_MSG"
    const val TYPE_MSG_ACK = "MSG_ACK"
    const val TYPE_PRESENCE = "PRESENCE"
    const val TYPE_GROUP_SYNC = "GROUP_SYNC"
    const val TYPE_GROUP_KICK = "GROUP_KICK"
    const val TYPE_GROUP_JOIN_REQUEST = "GROUP_JOIN_REQUEST"
    const val TYPE_MOD_BAN = "MOD_BAN"
    const val TYPE_BADGE_SYNC = "BADGE_SYNC"
    const val TYPE_THEME_SYNC = "THEME_SYNC"
    const val TYPE_PING = "PING"
    const val TYPE_PONG = "PONG"
    const val TYPE_NODE_OFFLINE = "NODE_OFFLINE"

    fun packet(type: String, block: JSONObject.() -> Unit = {}): JSONObject {
        return JSONObject().apply {
            put("type", type)
            put("ts", System.currentTimeMillis())
            block()
        }
    }

    fun stringListToJsonArray(items: List<String>): JSONArray = JSONArray().apply {
        items.forEach { put(it) }
    }

    fun jsonArrayToStringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val list = ArrayList<String>(array.length())
        for (i in 0 until array.length()) {
            val s = array.optString(i, "").trim()
            if (s.isNotEmpty()) list.add(s)
        }
        return list
    }
}
