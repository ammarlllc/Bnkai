package com.example

import com.example.data.local.GroupEntity
import com.example.data.local.TickBadgeType
import com.example.data.local.UserEntity
import com.example.data.repo.ChatRepository
import com.example.network.LanProtocol
import com.example.ui.theme.ChatBackgroundTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        assertEquals(ChatBackgroundTheme.SPIDY, ChatBackgroundTheme.fromId("spidy"))
        assertEquals(ChatBackgroundTheme.GOJO, ChatBackgroundTheme.fromId("gojo"))
        assertEquals(ChatBackgroundTheme.SUKUNA, ChatBackgroundTheme.fromId("sukuna"))
        assertEquals(ChatBackgroundTheme.MAKIMA, ChatBackgroundTheme.fromId("makima"))
        assertEquals(ChatBackgroundTheme.YUTA, ChatBackgroundTheme.fromId("yuta"))
        assertEquals(ChatBackgroundTheme.HACKER, ChatBackgroundTheme.fromId("unknown"))
    }

    @Test
    fun timedBan_expiresAccurately() {
        val now = 1_000_000L
        val activeBan = UserEntity(
            userId = "u_1",
            displayName = "Test",
            username = "test",
            isBanned = true,
            banExpiresAt = now + 60_000L
        )
        assertTrue(activeBan.isCurrentlyBanned(now))
        assertFalse(activeBan.isCurrentlyBanned(now + 61_000L))

        val permBan = activeBan.copy(banExpiresAt = -1L)
        assertTrue(permBan.isCurrentlyBanned(now + 999_999_999L))
    }

    @Test
    fun greenTick_grantsVideoFeaturesOnly() {
        val normalUser = UserEntity(userId = "u_1", displayName = "User", username = "u1")
        assertFalse(normalUser.canUseVideoFeatures())

        val redTickUser = normalUser.copy(badgeType = TickBadgeType.RED.name)
        assertFalse(redTickUser.canUseVideoFeatures())

        val greenTickUser = normalUser.copy(badgeType = TickBadgeType.GREEN.name)
        assertTrue(greenTickUser.canUseVideoFeatures())
        assertFalse(greenTickUser.isOwner)
    }

    @Test
    fun groupMute_respectsExpiration() {
        val now = 5_000_000L
        val group = GroupEntity(
            groupId = "grp_1",
            name = "Squad",
            inviteToken = "tok123",
            creatorId = "u_owner",
            adminIdsCsv = "u_owner",
            memberIdsCsv = "u_owner,u_target",
            mutedUsersCsv = "u_target:${now + 60_000L}"
        )
        assertTrue(group.mutedUntil("u_target", now) > 0L)
        assertEquals(0L, group.mutedUntil("u_target", now + 65_000L))
    }
}
