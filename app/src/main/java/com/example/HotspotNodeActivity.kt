package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.HotspotNodeViewModel
import com.example.ui.screens.HotspotNodeScreen
import com.example.ui.theme.AmmarChatTheme
import com.example.ui.theme.EmeraldSignal

class HotspotNodeActivity : ComponentActivity() {
    private val viewModel: HotspotNodeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.nodeState.collectAsStateWithLifecycle()
            AmmarChatTheme(accentColor = EmeraldSignal) {
                HotspotNodeScreen(
                    state = state,
                    onActivate = { viewModel.activateHotspotNode() },
                    onDeactivate = { viewModel.deactivateHotspotNode() },
                    onOpenSystemHotspotSettings = { viewModel.openSystemHotspotSettings() },
                    onOpenAmmarChat = { viewModel.openMainChatApp() }
                )
            }
        }
    }
}
