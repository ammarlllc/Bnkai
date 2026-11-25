package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.network.LanProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Ammar Chat", appName)
    }

    @Test
    fun `public user json hides owner email and internal flags`() {
        val json = LanProtocol.userToPublicJson(
            userId = "u_ammar35570",
            displayName = "Λ௱௱Λ尺 н4ㄈҚ3尺",
            username = "ammar35570",
            avatarBase64 = null,
            hasBlueTick = true,
            isBanned = false,
            isOnline = true
        )
        assertFalse(json.has("email"))
        assertFalse(json.has("isOwner"))
        assertTrue(json.getBoolean("hasBlueTick"))
        assertEquals("ammar35570", json.getString("username"))
    }
}
