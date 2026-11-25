package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.ChatBackgroundTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ammar_session_prefs")

class SessionPreferences(private val context: Context) {
    private object Keys {
        val CURRENT_USER_ID = stringPreferencesKey("current_user_id")
        val ACTIVE_THEME_ID = stringPreferencesKey("active_theme_id")
        val CUSTOM_THEME_IMAGE_PATH = stringPreferencesKey("custom_theme_image_path")
        val HOTSPOT_NODE_ACTIVE = booleanPreferencesKey("hotspot_node_active")
        val HOTSPOT_ACTUAL_SSID = stringPreferencesKey("hotspot_actual_ssid")
        val HOTSPOT_PASSPHRASE = stringPreferencesKey("hotspot_passphrase")
    }

    val currentUserIdFlow: Flow<String?> = context.dataStore.data.map { it[Keys.CURRENT_USER_ID] }

    val activeThemeIdFlow: Flow<String> = context.dataStore.data.map {
        it[Keys.ACTIVE_THEME_ID] ?: ChatBackgroundTheme.HACKER.id
    }

    val customThemeImagePathFlow: Flow<String?> = context.dataStore.data.map {
        it[Keys.CUSTOM_THEME_IMAGE_PATH]
    }

    val hotspotNodeActiveFlow: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.HOTSPOT_NODE_ACTIVE] ?: false
    }

    suspend fun setCurrentUserId(userId: String?) {
        context.dataStore.edit { prefs ->
            if (userId == null) prefs.remove(Keys.CURRENT_USER_ID)
            else prefs[Keys.CURRENT_USER_ID] = userId
        }
    }

    suspend fun setActiveThemeId(themeId: String, customImagePath: String? = null) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ACTIVE_THEME_ID] = themeId
            if (customImagePath != null) {
                prefs[Keys.CUSTOM_THEME_IMAGE_PATH] = customImagePath
            } else if (themeId != ChatBackgroundTheme.CUSTOM_GALLERY.id) {
                prefs.remove(Keys.CUSTOM_THEME_IMAGE_PATH)
            }
        }
    }

    suspend fun setHotspotNodeState(active: Boolean, actualSsid: String = "AMMAR-HACKER-5G", passphrase: String = "") {
        context.dataStore.edit { prefs ->
            prefs[Keys.HOTSPOT_NODE_ACTIVE] = active
            prefs[Keys.HOTSPOT_ACTUAL_SSID] = actualSsid
            prefs[Keys.HOTSPOT_PASSPHRASE] = passphrase
        }
    }
}
