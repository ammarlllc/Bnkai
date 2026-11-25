package com.example

import com.example.data.repo.ChatRepository
import com.example.network.LanProtocol
import com.example.ui.theme.ChatBackgroundTheme
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun protocolConstants_matchExpectedTargetSsid() {
        assertEquals("AMMAR-HACKER-5G", LanProtocol.TARGET_SSID)
    }

    @Test
    fun dmChatId_isDeterministicRegardlessOfOrder() {
        val id1 = ChatRepository.dmChatId("u_alice", "u_ammar35570")
        val id2 = ChatRepository.dmChatId("u_ammar35570", "u_alice")
        assertEquals(id1, id2)
    }

    @Test
    fun chatBackgroundThemes_resolveAndFallbackCleanly() {
        assertEquals(ChatBackgroundTheme.EMERALD_MATRIX, ChatBackgroundTheme.fromId("emerald_matrix"))
        assertEquals(ChatBackgroundTheme.OBSIDIAN_CYBER, ChatBackgroundTheme.fromId("unknown"))
    }
}
